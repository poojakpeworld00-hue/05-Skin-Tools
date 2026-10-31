package com.fffemotes.skintools.ads.launcher

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.fffemotes.skintools.ads.AdKit
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.ExitAdRunner
import com.fffemotes.skintools.ads.PlaceRules
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phone-unlock ad (MSG-06 port), RC `launcher.phone_unlock`: on, start_after_hours, gap_minutes, max_per_day,
 * exit_ad. Shown only on screens [AdKit.isHomeScreen] accepts (the host's "default home, onboarding done").
 */
object UnlockAdWatcher {
    private const val TAG = "UnlockAd"
    private const val FRESH_MS = 10_000L
    /** A return to the launcher this soon after the screen came on is the unlock, not an ordinary app resume. */
    private const val SCREEN_ON_MS = 30_000L
    private const val SETTLE_MS = 400L
    private const val KEY_LAST = "__unlock_last_ms"
    private const val KEY_DAY = "__unlock_day"
    private const val KEY_DAY_COUNT = "__unlock_day_count"

    private var registered = false
    private var front = WeakReference<Activity>(null)
    private var unlockedAt = 0L
    private var screenOnAt = 0L
    private var shownAt = 0L

    fun register(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(Tracker)
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_USER_PRESENT -> Ads.phoneUnlocked(context)
                        Intent.ACTION_SCREEN_ON -> screenOnAt = SystemClock.uptimeMillis()
                    }
                }
            },
            IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    internal fun onUnlock(context: Context) {
        if (!eligible(context)) return
        val top = front.get()
        if (top != null && isHome(top) && !top.isFinishing && !top.isDestroyed) {
            present(top)
        } else {
            unlockedAt = SystemClock.uptimeMillis()
        }
    }

    /**
     * The launcher came back because the phone was just unlocked and the unlock ad is due: it takes this moment, so
     * `launcher.app_resume` stays quiet instead of covering it (both would land on the same screen at the same time).
     */
    internal fun claimsResume(activity: Activity): Boolean {
        val recent = screenOnAt != 0L && SystemClock.uptimeMillis() - screenOnAt <= SCREEN_ON_MS
        screenOnAt = 0L
        // Also when the unlock ad already ran for this screen-on: the app-open must not follow straight after it.
        val justShown = shownAt != 0L && SystemClock.uptimeMillis() - shownAt <= SCREEN_ON_MS
        return recent && isHome(activity) && (justShown || eligible(activity))
    }

    private fun isHome(activity: Activity) = runCatching { AdKit.isHomeScreen(activity) }.getOrDefault(false) &&
        !activity.javaClass.name.startsWith("com.fffemotes.skintools.ads.")

    private fun present(activity: Activity) {
        unlockedAt = 0L
        activity.window.decorView.postDelayed({
            if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) return@postDelayed
            if (ExitAdRunner.busy() || !eligible(activity)) return@postDelayed
            countShown(activity)
            shownAt = SystemClock.uptimeMillis()
            Ads.exit(activity, Ads.PHONE_UNLOCK) {}
        }, SETTLE_MS)
    }

    private fun eligible(context: Context): Boolean = runCatching {
        if (!Ads.placeOn(Ads.PHONE_UNLOCK) || !Ads.exitOn(Ads.PHONE_UNLOCK)) return false
        val place = AdKit.config.places[Ads.PHONE_UNLOCK] ?: return false
        val pref = AdSettingsVault.getInstance(context)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        PlaceRules.unlockAllowed(
            nowMs = System.currentTimeMillis(),
            installMs = installed,
            startAfterHours = place.int("start_after_hours", 24).toLong(),
            gapMinutes = place.int("gap_minutes", 30).toLong(),
            lastRunMs = pref.getLong(KEY_LAST, 0L),
            todayCount = todayCount(pref),
            maxPerDay = place.int("max_per_day", 5),
        )
    }.getOrElse { Log.w(TAG, "eligibility failed", it); AdKit.onError(it, "unlock.eligibility"); false }

    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun todayCount(pref: AdSettingsVault): Int =
        PlaceRules.countToday(pref.getString(KEY_DAY, null), pref.getInt(KEY_DAY_COUNT, 0), today())

    private fun countShown(context: Context) {
        val pref = AdSettingsVault.getInstance(context)
        val count = todayCount(pref) + 1
        pref.putString(KEY_DAY, today())
        pref.putInt(KEY_DAY_COUNT, count)
        pref.putLong(KEY_LAST, System.currentTimeMillis())
    }

    private object Tracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            front = WeakReference(activity)
            if (unlockedAt == 0L || !isHome(activity)) return
            val fresh = SystemClock.uptimeMillis() - unlockedAt <= FRESH_MS
            unlockedAt = 0L
            if (fresh && eligible(activity)) present(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (front.get() === activity) front.clear()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (front.get() === activity) front.clear()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
