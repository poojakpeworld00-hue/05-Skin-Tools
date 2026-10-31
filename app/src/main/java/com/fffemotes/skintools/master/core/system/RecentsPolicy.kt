package com.fffemotes.skintools.master.core.system

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.ExitAdRunner
import com.fffemotes.skintools.ads.events.recent.RecentAdWatcher
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.fffemotes.skintools.master.data.remote.config.KeyAction
import com.fffemotes.skintools.master.data.remote.config.SystemKey
import java.lang.ref.WeakReference

/**
 * Recents on our screens.
 * - Never listed: every resume (ours, ads, launcher) re-applies excludeFromRecents to all the app's tasks; the manifest
 *   flag is lost when the screen that started a task finishes. Android still lists the task in front, which the cases
 *   below cover.
 * - The first press may be the Play Store jump (ads `launcher.play_store_return`, when due).
 * - Our screen in a normal task (before we are the default home, or opened from the icon): our tasks are removed, the only
 *   way the card goes (MIUI draws Recents before we hear the press), and the app reopens where it was, without the splash.
 * - Our screen in the home task (onboarding moves there once we are the default home): Recents never lists it and Android
 *   lets it come back, so `system_keys.recents` runs (next / back / reset / bottom sheet, like Home; `""` leaves the
 *   overview open, the device's own), and after onboarding `launcher.recents_button` shows its ad.
 * Another app in front (the Play Store page we opened, say): nothing. Android lets only the app on screen react.
 */
object RecentsPolicy : Application.ActivityLifecycleCallbacks {
    /** Some launchers (Pixel) keep the screen live behind Recents, so it never pauses: it comes back after this. */
    private const val NO_PAUSE_MS = 250L

    /** How long a screen brought back gets to regain focus before it counts as refused. */
    private const val CHECK_MS = 700L

    /** Xiaomi HyperOS sends each press twice, ~20 ms apart: a copy this soon is the same press (no one presses twice that fast). */
    private const val DUPLICATE_MS = 300L
    private var broadcastAt = 0L

    /**
     * A Recents press waiting for our screen to be back in front, handed to onboarding on that resume. Android sends the
     * press while the screen is still up (Xiaomi), so acting at once ran `next` behind the overview. While one waits, later
     * presses only bring the screen back: one press acts at a time. Dropped if the screen never comes back.
     */
    private var pendingAt = 0L
    private const val PENDING_MAX_MS = 3_000L
    private fun pending(now: Long) = pendingAt != 0L && now - pendingAt < PENDING_MAX_MS

    private var top = WeakReference<Activity>(null)
    private var started = 0 // our started screens; 0 = another app is in front
    private var leftViaHome = false // Home pressed since our last screen: a task of ours is no longer in front

    /** Our task was removed for Recents: the next screen opens where the user was, not on the splash ([takeResume]). */
    private var resumeOnReopen = false

    /** True once after our task was removed for Recents (this app session only; a cold start shows the splash). */
    fun takeResume(): Boolean = resumeOnReopen.also { resumeOnReopen = false }

    /** Our task was removed and the next screen should resume: our launcher must hand back even if Home is `""`. */
    val resuming: Boolean get() = resumeOnReopen

    /**
     * A Home intent alone is not a Home press: MIUI also sends one when its Recents screen closes. Only the "homekey"
     * broadcast is, and it can reach us just before or just after the Home intent reaches our launcher.
     */
    private const val HOME_KEY_MS = 1_000L
    private var homeKeyAt = 0L
    private var handBackAt = 0L // our launcher handed back during onboarding without a Home press (yet)

    /** A real Home press came in just now: our launcher passes it on to onboarding. */
    fun homeKeyJustPressed(): Boolean = SystemClock.uptimeMillis() - homeKeyAt < HOME_KEY_MS

    /** Our launcher handed back without a Home press: if the "homekey" broadcast follows, the step gets its Home then. */
    fun handBackWithoutKey() {
        handBackAt = SystemClock.uptimeMillis()
    }

    /**
     * Onboarding goes on in the home task, which Recents never lists, so there is no race with Recents' own list (MIUI
     * draws it before our broadcast). The icon task closes and our launcher hands back to the same step, no splash.
     */
    fun moveToHomeTask(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || !Launcher.isDefaultHome(activity)) return
        if (ourTasks(activity).none { runCatching { MainScreens.isAppTaskBase(it.taskInfo?.baseIntent?.component?.className) }.getOrDefault(false) }) return
        resumeOnReopen = true
        if (runCatching { activity.startActivity(MainScreens.launcherHomeIntent(activity).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)) }.isFailure) {
            resumeOnReopen = false
            return
        }
        activity.finishAndRemoveTask()
    }
    private var stayOn = WeakReference<Activity>(null)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var onboardingDone: () -> Boolean

    fun install(app: Application, onboardingDone: () -> Boolean) {
        this.onboardingDone = onboardingDone
        app.registerActivityLifecycleCallbacks(this)
        @Suppress("DEPRECATION") // still delivered to apps for Home/Recents
        ContextCompat.registerReceiver(app, receiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_EXPORTED)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reason = intent.getStringExtra("reason")
            if (reason == "homekey") {
                leftViaHome = true
                val at = SystemClock.uptimeMillis()
                homeKeyAt = at
                // The Home intent got to our launcher first and it handed back without the key: the step gets it now.
                if (at - handBackAt < HOME_KEY_MS) {
                    handBackAt = 0L
                    top.get()?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { (it as? SystemKeyTarget)?.systemKey(SystemKey.HOME) }
                }
            }
            if (reason != "recentapps" && reason != "recent_apps") return
            val now = SystemClock.uptimeMillis()
            if (now - broadcastAt < DUPLICATE_MS) return
            broadcastAt = now
            // The Play Store jump opens over Recents: our task is behind it, so it isn't listed.
            if (RecentAdWatcher.claimsPress(context)) return
            // In the home task Recents never lists us and Android lets our screen come back over it, so the RC actions
            // run there. Anywhere else Android always lists the task on screen and may refuse the comeback: our tasks
            // are removed instead (only that drops the card) and the app reopens where it was.
            val homeTask = inHomeTask(context)
            val activity = top.get()?.takeIf { started > 0 && !it.isFinishing && !it.isDestroyed }
            if (activity == null) {
                if (homeTask && !onboardingDone() && ExitAdRunner.busy() && Launcher.isDefaultHome(context)) {
                    // Onboarding exit ad with its link (a Chrome tab in our task) in front: bring that back over Recents.
                    val app = context.applicationContext
                    main.postDelayed({ MainScreens.appTaskToFront(app) }, NO_PAUSE_MS)
                    main.postDelayed({ MainScreens.appTaskToFront(app) }, NO_PAUSE_MS + CHECK_MS) // Recents kept the front while animating in
                } else if (!leftViaHome && otherAppOnOurTask(context)) {
                    removeOurTasks(context) // an ad's link tab on top of our task is the one listed
                }
                return
            }
            when {
                !homeTask -> removeOurTasks(context)
                // shortcut: an ad skipped by its `every` leaves the overview open (our card isn't listed in the home task); add a "would run" check if `every` is used here.
                onboardingDone() -> if (Launcher.isDefaultHome(context) && Ads.recentsAdOn() && !ExitAdRunner.busy()) Ads.systemKey(activity, "recents") {}
                else -> {
                    val keys = AppConfigStore.current.value.systemKeys
                    val home = Launcher.isDefaultHome(context)
                    // `""` that doesn't open the bottom sheet: the overview stays, as Android's own Recents.
                    if (home && keys.recents == KeyAction.DEFAULT && !keys.opensSheet(SystemKey.RECENTS)) return
                    // Every press brings our screen back. It also acts only when nothing is in progress: no earlier press
                    // waiting for the screen, no exit ad (ad or links) running. Otherwise it just brings that back.
                    if (home && activity is SystemKeyTarget && !ExitAdRunner.busy() && !pending(now)) pendingAt = now
                    stay(activity)
                }
            }
        }
    }

    /** [activity] comes back over Recents once it pauses for it, or after [NO_PAUSE_MS] if it stays live behind it. */
    private fun stay(activity: Activity) {
        stayOn = WeakReference(activity)
        main.removeCallbacks(bringBack)
        main.postDelayed(bringBack, NO_PAUSE_MS)
    }

    private val bringBack = Runnable {
        val activity = stayOn.get() ?: return@Runnable
        stayOn.clear()
        if (activity.isFinishing || activity.isDestroyed) return@Runnable
        // Starting our screen closes the Recents overview; moving the task to front covers the other screens (ads, hints).
        if (MainScreens.isMain(activity)) runCatching { MainScreens.open(activity) }
        runCatching { activity.getSystemService(ActivityManager::class.java)?.moveTaskToFront(activity.taskId, 0) }
        main.postDelayed({
            if (activity.isFinishing || activity.isDestroyed || activity.hasWindowFocus() || ExitAdRunner.busy()) return@postDelayed
            if (Launcher.isDefaultHome(activity)) {
                // Recents was still animating in and kept the front: one more try, so the user isn't left in Recents.
                if (MainScreens.isMain(activity)) runCatching { MainScreens.open(activity) }
                runCatching { activity.getSystemService(ActivityManager::class.java)?.moveTaskToFront(activity.taskId, 0) }
            } else {
                // Refused (some devices block it before we're the default home): step behind, so Recents doesn't list us.
                runCatching { activity.moveTaskToBack(true) }
            }
        }, CHECK_MS)
    }

    /**
     * Removes every task of ours but our launcher home's: as the default home it is never listed, and removing it would
     * close the home screen. Screens, ad screens and link tabs in those tasks close with them.
     */
    private fun removeOurTasks(context: Context) {
        resumeOnReopen = true
        val home = launcherHomeClass(context)
        ourTasks(context).forEach { task ->
            if (runCatching { task.taskInfo?.baseIntent?.component?.className == home }.getOrDefault(false)) return@forEach
            runCatching { task.finishAndRemoveTask() }
        }
    }

    /** No task of ours was started on an app screen: our screens run in the home task (RecentsPolicy.moveToHomeTask). */
    private fun inHomeTask(context: Context): Boolean =
        ourTasks(context).none { runCatching { MainScreens.isAppTaskBase(it.taskInfo?.baseIntent?.component?.className) }.getOrDefault(true) }

    /** One of our tasks has another app's screen on top (an ad's link in a Chrome tab). */
    private fun otherAppOnOurTask(context: Context): Boolean = ourTasks(context).any { task ->
        runCatching { task.taskInfo?.topActivity?.packageName.let { it != null && it != context.packageName } }.getOrDefault(false)
    }

    private fun ourTasks(context: Context): List<ActivityManager.AppTask> =
        runCatching { context.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty() }.getOrDefault(emptyList())

    private fun launcherHomeClass(context: Context): String? = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        context.packageManager.resolveActivity(home, 0)?.activityInfo?.name
    }.getOrNull()

    override fun onActivityResumed(activity: Activity) {
        top = WeakReference(activity)
        leftViaHome = false
        if (pendingAt != 0L && activity is SystemKeyTarget) {
            if (pending(SystemClock.uptimeMillis())) activity.systemKey(SystemKey.RECENTS)
            pendingAt = 0L
        }
        val tasks = runCatching { activity.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty() }.getOrDefault(emptyList())
        tasks.forEach { runCatching { it.setExcludeFromRecents(true) } }
    }

    override fun onActivityPaused(activity: Activity) {
        if (stayOn.get() === activity) {
            main.removeCallbacks(bringBack)
            bringBack.run()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
