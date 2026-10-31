package com.fffemotes.skintools.master.launcherhost

import android.app.Application
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hands the app's host + events to the launcher module. Call once from App.onCreate. */
object LauncherSetup {
    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        Launcher.install(app, AppLauncherHost(app), AppLauncherEvents())
        MainScope().launch {
            // drop(1): install() already read the current values.
            AppConfigStore.current.map { it.launcher }.distinctUntilChanged().drop(1).collect { Launcher.refresh(app) }
        }
    }
}
