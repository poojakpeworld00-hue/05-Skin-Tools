package com.fffemotes.skintools.master.launcherhost

import android.app.Activity
import android.content.Intent
import android.widget.FrameLayout
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.launcher.api.LauncherEvents
import com.fffemotes.skintools.launcher.api.LauncherPanel
import com.fffemotes.skintools.launcher.api.LauncherSlot
import com.fffemotes.skintools.launcher.api.SponsoredApp
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.fffemotes.skintools.master.presentation.uninstall.UninstallDialogActivity
import com.fffemotes.skintools.master.presentation.uninstall.UninstallFlowActivity

/** The single place launcher ads go: each moment calls its `ads.launcher.<place>` through the common Ads methods. */
class AppLauncherEvents : LauncherEvents {
    override fun onPanelOpened(activity: Activity, panel: LauncherPanel, proceed: () -> Unit) =
        Ads.panelOpened(activity, if (panel == LauncherPanel.HOST_APP) "host_app_panel" else "suggested_panel", proceed)

    override fun onDrawerOpened(activity: Activity, proceed: () -> Unit) = Ads.drawerOpened(activity, proceed)

    override fun onAppIconTapped(activity: Activity, packageName: String, proceed: () -> Unit) = Ads.appIconTapped(activity, proceed)

    override fun onReturnedFromApp(activity: Activity) = Ads.returnedFromApp(activity)

    override fun onSponsoredTileTapped(activity: Activity, tile: SponsoredApp, proceed: () -> Unit) = Ads.sponsoredTileTapped(activity, proceed)

    override fun onReturnedFromSponsored(activity: Activity) = Ads.returnedFromSponsored(activity)

    override fun bindSlot(activity: Activity, slot: LauncherSlot, container: FrameLayout) = when (slot) {
        LauncherSlot.SUGGESTED_BOTTOM -> Ads.bottom(activity, container, "launcher.suggested_panel")
        LauncherSlot.SUGGESTED_LIST -> Ads.list(activity, container, "launcher.suggested_panel")
        LauncherSlot.DRAWER -> Ads.bottom(activity, container, Ads.APP_DRAWER)
    }

    override fun onUninstallHostApp(activity: Activity): Boolean {
        val flow = AppConfigStore.current.value.uninstallFlow
        if (!flow.premium) return false // normal flow: the menu has no Uninstall
        activity.startActivity(Intent(activity, if (flow.systemDialog) UninstallDialogActivity::class.java else UninstallFlowActivity::class.java))
        return true
    }

    override fun onScreen(name: String) = Screens.show(name)

    override fun onClick(button: String, value: String?) = Analytics.click(button, value)

    override fun onKey(key: String) = Analytics.keyPress(key)

    override fun onStep(event: String, step: String) = Analytics.step(event, step)

    override fun onHideHostApp(activity: Activity): Boolean {
        OurIcon.hide(activity)
        return true
    }
}
