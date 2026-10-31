package com.fffemotes.skintools.launcher.intro

import android.app.Activity
import android.util.Log
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.api.LauncherPanel

/**
 * The ad / analytics moments of MSG-05's launcher, handed to [com.fffemotes.skintools.launcher.api.LauncherEvents].
 * [action] runs once, when the host calls `proceed` (or at once if the host throws).
 */
object HubOfferCoordinator {
    private const val TAG = "LAUNCHER_PROMO"

    /** What the next launcher resume returns from (MSG-05 LauncherAds.onAppLaunched / onHomeResume). */
    enum class Away { NONE, APP, SPONSORED }

    var away = Away.NONE

    fun run(activity: Activity?, gesture: String, packageName: String = "", action: () -> Unit) {
        var done = false
        val once = {
            if (!done) {
                done = true
                action()
            }
        }
        if (activity == null) return once()
        val events = Launcher.events
        runCatching {
            when (gesture) {
                HubPromosSwitches.LEFT_SWIPE -> events.onPanelOpened(activity, LauncherPanel.SUGGESTED, once)
                HubPromosSwitches.RIGHT_SWIPE -> events.onPanelOpened(activity, LauncherPanel.HOST_APP, once)
                HubPromosSwitches.DRAWER_OPEN -> events.onDrawerOpened(activity, once)
                HubPromosSwitches.APP_LAUNCH -> events.onAppIconTapped(activity, packageName, once)
                else -> once()
            }
        }.onFailure {
            Log.w(TAG, "$gesture: host event failed - running the action", it)
            once()
        }
    }

    /** Launcher home came back to the front: tell the host where the user returns from. */
    fun onHomeResume(activity: Activity) {
        val from = away
        away = Away.NONE
        when (from) {
            Away.APP -> Launcher.events.onReturnedFromApp(activity)
            Away.SPONSORED -> Launcher.events.onReturnedFromSponsored(activity)
            Away.NONE -> Unit
        }
    }
}
