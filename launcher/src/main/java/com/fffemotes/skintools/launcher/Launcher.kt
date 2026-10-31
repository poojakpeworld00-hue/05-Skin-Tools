package com.fffemotes.skintools.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import com.fffemotes.skintools.launcher.api.LauncherApi
import com.fffemotes.skintools.launcher.api.LauncherEvents
import com.fffemotes.skintools.launcher.api.LauncherHost
import com.fffemotes.skintools.launcher.api.LauncherSettings
import com.fffemotes.skintools.launcher.exts.isPrimaryLaunchpad
import com.fffemotes.skintools.launcher.intro.HubHomeAsBack
import com.fffemotes.skintools.launcher.intro.HubRecentsCharter
import com.fffemotes.skintools.launcher.screens.XHubStage
import com.fffemotes.skintools.launcher.support.MainHubRequester

/** Module entry point: the MSG-05 launcher behind the [LauncherApi] contract. */
object Launcher : LauncherApi {
    private var host: LauncherHost? = null

    internal var events: LauncherEvents = LauncherEvents.None
        private set

    override fun install(app: Application, host: LauncherHost, events: LauncherEvents) {
        val first = this.host == null
        this.host = host
        this.events = events
        if (first) HubRecentsCharter.install(app)
    }

    /** Current settings; documented defaults (launcher off) until [install]. */
    internal fun settings(): LauncherSettings = host?.settings() ?: LauncherSettings()

    internal fun onboardingDone(): Boolean = host?.isOnboardingDone() ?: true

    internal fun host(): LauncherHost? = host

    override fun isDefaultHome(context: Context): Boolean = context.isPrimaryLaunchpad()

    override fun requestDefaultHome(activity: Activity, onResult: (Boolean) -> Unit) =
        MainHubRequester.request(activity, onResult)

    override fun openLauncherHome(activity: Activity) {
        activity.startActivity(
            Intent(activity, XHubStage::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    override fun hostShown() = HubHomeAsBack.hostShown()

    override fun refresh(context: Context) {
        XHubStage.current?.let { it.runOnUiThread { it.onSettingsChanged() } }
    }
}
