package com.fffemotes.skintools.master.core.crash

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.master.core.system.MainScreens
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Crashlytics wiring, as the reference app's CrashGuard: every crash, ANR and caught failure carries the keys `crash_kind`,
 * `screen`, `onboarding_step`, `track` and `default_home`; a watchdog reports a main thread stuck for [ANR_MS]; Android 11+
 * past exits (ANR, crash, low memory) are reported once.
 *
 * As the reference, the app (and our launcher home) stays on screen: after Crashlytics saves a main-thread crash, [attach]'s
 * handler reopens the current screen, or the launcher home, in a fresh process and ends this one. No "app stopped" dialog,
 * and Android never clears our default-home role (it does that when the home process crashes). Background-thread crashes
 * are recorded as non-fatal and the process lives on. Crash loops: see [CrashLoopGuard].
 */
object CrashReporting {
    private const val TAG = "CrashReporting"
    private const val ANR_MS = 5_000L
    private const val HOME_ACTIVITY = "com.fffemotes.skintools.launcher.screens.XHubStage"

    private lateinit var app: Application
    private var crashlytics: FirebaseCrashlytics? = null
    private val live = mutableListOf<Activity>() // main thread only; last resumed last
    private val keysThread by lazy { Executors.newSingleThreadExecutor { Thread(it, "crash-keys").apply { isDaemon = true } } }

    /** From Application.attachBaseContext, before Firebase starts: Crashlytics will call this handler after saving a crash. */
    fun attach(app: Application) {
        this.app = app
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            if (thread !== Looper.getMainLooper().thread || !runCatching { restart() }.getOrDefault(false)) {
                system?.uncaughtException(thread, e)
            }
        }
    }

    /** From Application.onCreate, first thing (Crashlytics is installed by then). */
    fun install(app: Application) {
        this.app = app
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: Bundle?) { live += a }
            override fun onActivityDestroyed(a: Activity) { live -= a }
            override fun onActivityResumed(a: Activity) {
                live -= a
                live += a
                runCatching { setKeys("none", homeOffMain = true) } // context for Crashlytics' own ANR reports
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
        })
        if (FirebaseApp.getApps(app).isEmpty()) return
        crashlytics = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() ?: return
        // Main thread: keys, then Crashlytics saves it and hands it to [attach]'s restart. Background: non-fatal, process lives.
        val crashlyticsHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            if (thread === Looper.getMainLooper().thread) {
                runCatching { setKeys("main_crash") }
                crashlyticsHandler?.uncaughtException(thread, e)
            } else {
                record(e, "background:${thread.name}")
            }
        }
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority >= Log.ERROR && t != null) nonFatal(t, tag ?: message.take(60))
            }
        })
        startAnrWatchdog()
        if (Build.VERSION.SDK_INT >= 30) thread(name = "exit-reasons") { runCatching { reportPastExits() } }
    }

    /** A caught failure worth fixing (ads, launcher): sent as a non-fatal with [where]. Never throws. */
    fun nonFatal(error: Throwable, where: String) = record(error, "caught:$where")

    private fun record(e: Throwable, kind: String) {
        Log.e(TAG, "recorded ($kind)", e)
        val c = crashlytics ?: return
        runCatching {
            setKeys(kind)
            c.recordException(e)
        }
    }

    /** [homeOffMain]: every screen resume sets these; the default-home check is a system call, kept off the main thread. */
    private fun setKeys(kind: String, homeOffMain: Boolean = false) {
        val c = crashlytics ?: return
        c.setCustomKey("crash_kind", kind)
        c.setCustomKey("screen", Screens.current)
        c.setCustomKey("onboarding_step", if (MainScreens.onboardingDone(app)) "done" else Screens.current)
        c.setCustomKey("track", AppConfigStore.current.value.track.name)
        if (homeOffMain) keysThread.execute { runCatching { c.setCustomKey("default_home", Launcher.isDefaultHome(app)) } }
        else c.setCustomKey("default_home", Launcher.isDefaultHome(app))
    }

    /**
     * After a saved main-thread crash: opens a fresh screen (only if the app was in front, never over another app) and
     * ends the process quietly. False = crash loop, let the system handle it.
     */
    private fun restart(): Boolean {
        val prefs = app.getSharedPreferences("crash_reporting", 0)
        val history = prefs.getString("crash_times", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        val (action, keep) = CrashLoopGuard().onCrash(history, System.currentTimeMillis())
        prefs.edit().putString("crash_times", keep.joinToString(",")).commit()
        Log.e(TAG, "main-thread crash: $action")
        if (action == CrashLoopGuard.Action.GIVE_UP) return false
        val top = live.lastOrNull()
        val intent = when {
            action == CrashLoopGuard.Action.SAFE_SCREEN -> launchIntent()
            top?.javaClass?.name == HOME_ACTIVITY -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.packageName)
            top != null && top.javaClass.name.startsWith(app.packageName) -> Intent(top.intent)
            else -> launchIntent() // an ad / library screen broke: back to ours
        }
        // A new record (CLEAR_TASK: not handed to the crashed one) started before this process dies launches in a fresh process.
        if (intent != null && inFront()) app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    private fun launchIntent() = app.packageManager.getLaunchIntentForPackage(app.packageName)

    private fun inFront() = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        .importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    /** Main thread stuck for [ANR_MS]: its stack goes to Crashlytics once per stall (Crashlytics' own ANR reports stay on). */
    private fun startAnrWatchdog() = thread(name = "anr-watchdog", isDaemon = true) {
        val main = Handler(Looper.getMainLooper())
        while (true) {
            Thread.sleep(1_000)
            val answered = AtomicBoolean(false)
            main.post { answered.set(true) }
            var waited = 0L
            while (!answered.get() && waited < ANR_MS) { Thread.sleep(500); waited += 500 }
            if (answered.get() || Debug.isDebuggerConnected()) continue
            record(AppNotResponding("ANR: main thread blocked ${ANR_MS / 1000}s", Looper.getMainLooper().thread.stackTrace), "anr")
            while (!answered.get()) Thread.sleep(500)
        }
    }

    /** Android 11+: how earlier runs of this app ended (ANR, crash, low memory), each sent once. */
    private fun reportPastExits() {
        if (Build.VERSION.SDK_INT < 30) return
        val prefs = app.getSharedPreferences("crash_reporting", 0)
        val seen = prefs.getLong("exit_seen", 0L)
        val exits = app.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 20)
            .filter { it.timestamp > seen && it.reason in REPORTED_EXITS }
        for (exit in exits.sortedBy { it.timestamp }) {
            val reason = REPORTED_EXITS.getValue(exit.reason)
            if (exit.reason == ApplicationExitInfo.REASON_ANR) runCatching {
                exit.traceInputStream?.bufferedReader()?.use { r -> CharArray(16_000).let { crashlytics?.log(String(it, 0, r.read(it).coerceAtLeast(0))) } }
            }
            record(PastExit("$reason: ${exit.description} (process ${exit.processName}, importance ${exit.importance}, at ${exit.timestamp})"), "exit_$reason")
        }
        exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("exit_seen", it).apply() }
    }

    private val REPORTED_EXITS = mapOf(
        ApplicationExitInfo.REASON_ANR to "anr",
        ApplicationExitInfo.REASON_CRASH to "crash",
        ApplicationExitInfo.REASON_CRASH_NATIVE to "crash_native",
        ApplicationExitInfo.REASON_LOW_MEMORY to "low_memory",
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to "init_failure",
    )
}

/** The main-thread stack of an ANR (the stack trace is the blocked thread's, not the watchdog's). */
class AppNotResponding(message: String, stack: Array<StackTraceElement>) : Exception(message) {
    init { stackTrace = stack }
}

/** An earlier process exit read from ApplicationExitInfo. */
class PastExit(message: String) : Exception(message)

/**
 * Pure rules (unit-tested): main-thread crashes within [windowMs] are counted (the history is kept on disk, each crash
 * ends the process). Below [safeAt] the crashed screen reopens; from [safeAt] the app's start screen opens instead; at
 * [giveUpAt] the crash goes to the system, so a screen that crashes every time can't trap the user in a restart loop.
 */
class CrashLoopGuard(private val windowMs: Long = 60_000L, private val safeAt: Int = 3, private val giveUpAt: Int = 5) {
    enum class Action { RESTART, SAFE_SCREEN, GIVE_UP }

    /** [history] = earlier crash times; returns what to do now and the crash times to keep. */
    fun onCrash(history: List<Long>, now: Long): Pair<Action, List<Long>> {
        val recent = history.filter { now - it <= windowMs } + now
        val action = when {
            recent.size >= giveUpAt -> Action.GIVE_UP
            recent.size >= safeAt -> Action.SAFE_SCREEN
            else -> Action.RESTART
        }
        return action to recent
    }
}
