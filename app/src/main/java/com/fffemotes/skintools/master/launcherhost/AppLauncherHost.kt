package com.fffemotes.skintools.master.launcherhost

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import com.fffemotes.skintools.launcher.api.LauncherHost
import com.fffemotes.skintools.launcher.api.LauncherSettings
import com.fffemotes.skintools.master.core.system.MainScreens
import com.fffemotes.skintools.master.core.system.RecentsPolicy
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.fffemotes.skintools.master.data.remote.config.KeyAction
import com.fffemotes.skintools.master.data.remote.config.SystemKey
import com.fffemotes.skintools.master.presentation.onboarding.DefaultHomeStep

class AppLauncherHost(context: Context) : LauncherHost {
    private val app = context.applicationContext

    // Remote Config decides what exists; hiding our icon turns it off.
    override fun settings(): LauncherSettings = AppConfigStore.current.value.launcher.let { rc ->
        val hidden = OurIcon.hidden(app)
        rc.copy(
            iconInDrawer = rc.iconInDrawer && !hidden, iconOnHome = rc.iconOnHome && !hidden,
            // uninstall_flow.flow: premium offers Uninstall on our icon; normal offers only Hide.
            hostUninstall = AppConfigStore.current.value.uninstallFlow.premium,
        )
    }

    override fun isOnboardingDone() = MainScreens.onboardingDone(app)

    override fun createHostPanel(): Fragment = HostPanelFragment()

    // The launcher calls this when HOME is pressed before onboarding finished.
    override fun openHostApp(activity: Activity) {
        // Onboarding gets Home only for a real Home press: MIUI also sends Home when its Recents screen closes.
        val home = !isOnboardingDone() && RecentsPolicy.homeKeyJustPressed()
        if (!isOnboardingDone() && !home) RecentsPolicy.handBackWithoutKey()
        MainScreens.open(activity, SystemKey.HOME.key.takeIf { home })
    }

    // system_keys.home "" = device default: Home shows the launcher home. The default-home step still gets it (picking us sends Home).
    override fun takesHome() =
        AppConfigStore.current.value.systemKeys.home != KeyAction.DEFAULT || DefaultHomeStep.shown || RecentsPolicy.resuming
}
