package com.fffemotes.skintools.launcher.intro

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.api.LauncherSlot
import com.fffemotes.skintools.launcher.exts.reviveNavigationBand

object HubInlinePromo {
    private const val TAG = "LAUNCHER_AD"

    fun wireOnReveal(activity: Activity?, key: String, container: FrameLayout?) {
        if (activity == null || container == null) return
        val slot = when (key) {
            HubPromosSwitches.KEY_PANEL_BOTTOM -> LauncherSlot.SUGGESTED_BOTTOM
            HubPromosSwitches.KEY_PANEL_MIDDLE -> LauncherSlot.SUGGESTED_LIST
            HubPromosSwitches.KEY_APP_DRAWER -> LauncherSlot.DRAWER
            else -> return
        }
        runCatching {
            Launcher.events.bindSlot(activity, slot, container)
            ViewCompat.requestApplyInsets(container)
            activity.reviveNavigationBand()
        }.onFailure { Log.w(TAG, "could not render $key", it) }
    }
}
