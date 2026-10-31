package com.fffemotes.skintools.launcher.intro

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.fffemotes.skintools.launcher.Launcher

/**
 * While the host's onboarding is unfinished, the launcher hands every start / HOME press back to the
 * host (LauncherHost.openHostApp). A burst guard (MSG-L-06 LaunchpadHomeAsBack) stops an endless
 * redirect when the host cannot take the screen: past [BURST_MAX] hand-backs in [BURST_WINDOW_MS]
 * that the host never showed its screen for, the launcher home is shown instead. Fast HOME taps on
 * onboarding never trip it: the host reports each time it is back on screen ([hostShown]).
 */
object HubHomeAsBack {
    private const val BURST_WINDOW_MS = 3000L
    private const val BURST_MAX = 6

    private var burstStart = 0L
    private var burstCount = 0

    /** The host's screen is on screen again: the hand-backs so far worked, so the guard starts over. */
    fun hostShown() {
        burstCount = 0
    }

    /** True when the launcher handed control to the host and must not show itself. */
    fun onHome(launcher: Activity): Boolean {
        if (Launcher.onboardingDone()) return false
        val host = Launcher.host()?.takeIf { it.takesHome() } ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - burstStart > BURST_WINDOW_MS) {
            burstStart = now
            burstCount = 0
        }
        if (++burstCount > BURST_MAX) {
            Log.w("LauncherHomeAsBack", "host did not take the screen $BURST_MAX times - showing the launcher")
            return false
        }
        return runCatching { host.openHostApp(launcher) }
            .onFailure { Log.e("LauncherHomeAsBack", "openHostApp failed", it) }
            .isSuccess
    }
}
