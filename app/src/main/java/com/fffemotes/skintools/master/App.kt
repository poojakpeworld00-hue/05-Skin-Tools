package com.fffemotes.skintools.master

import android.app.Application
import android.content.Context
import com.fffemotes.skintools.master.presentation.prompts.AppUpdatePrompts
import com.fffemotes.skintools.master.presentation.permissions.Permissions
import kotlin.concurrent.thread
import com.fffemotes.skintools.ads.AdKit
import com.fffemotes.skintools.ads.ExitAdRunner
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.DebugEventLog
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.render.ResumeAdDirector
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.master.core.crash.CrashReporting
import com.fffemotes.skintools.master.core.system.MainScreens
import com.fffemotes.skintools.master.core.system.NoInternet
import com.fffemotes.skintools.master.core.system.RecentsPolicy
import com.fffemotes.skintools.master.core.system.SystemKeys
import com.fffemotes.skintools.master.data.remote.config.ExclusiveLocations
import com.fffemotes.skintools.master.data.remote.config.InstallAttribution
import com.fffemotes.skintools.master.data.remote.config.TrackResolver
import com.fffemotes.skintools.master.launcherhost.LauncherSetup
import com.fffemotes.skintools.master.presentation.onboarding.DefaultHomeStep
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.fffemotes.skintools.master.data.remote.config.RcSyncMessagingService
import com.fffemotes.skintools.master.data.remote.config.Track
import com.fffemotes.skintools.master.di.AppContainer
import com.fffemotes.skintools.master.presentation.splash.SplashActivity
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** This app's Kotlin package: its applicationId differs, so screen checks compare class names against this. */
val CODE_PACKAGE: String = App::class.java.name.substringBeforeLast('.')

/** Startup order follows the reference app: crash reporting → Remote Config → attribution → analytics → ads → launcher. */
class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporting.attach(this)
        // Loads the startup prefs on a background thread, so the first reads on the main thread don't wait on disk (ANR guard).
        thread(name = "prefs-preload", isDaemon = true) {
            STARTUP_PREFS.forEach { runCatching { getSharedPreferences(it, MODE_PRIVATE).all } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporting.install(this)
        container = AppContainer(this)

//        AppConfigStore.forcePaidUser(true) // testing only: remove before the Play Store release
        AppConfigStore.trackResolver = TrackResolver { InstallAttribution.track(this) }
        AppConfigStore.init(this)
        // Every install hears the silent `rc_sync` push (RcSyncMessagingService): one push to the topic re-syncs everyone.
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(RcSyncMessagingService.TOPIC) }

        InstallAttribution.start(this, { AppConfigStore.current.value.base.attribution }) { AppConfigStore.refreshTrack() }
        ExclusiveLocations.start(this, { AppConfigStore.current.value.base.exclusiveLocations }) { AppConfigStore.refreshTrack() }

        Analytics.init(this) { if (AppConfigStore.current.value.track == Track.B) "paid" else "organic" }
        // FFF has no debug event-list screen, so the module's debug notification stays off; events still log to Logcat.
        DebugEventLog.enabled = { false }
        // Leaving for an ad, a permission prompt or something the app opened itself (share, store, links) doesn't end a screen visit.
        Screens.ownLeave = { ExitAdRunner.busy() || ResumeAdDirector.skipNextAppOpenAd || DefaultHomeStep.shown || Permissions.asking }
        Screens.install()
        SystemKeys.listen(this)
        NoInternet.install(this) { AppConfigStore.current.value.base.noInternetDialog }
        RecentsPolicy.install(this) { MainScreens.onboardingDone(this) }

        AppUpdatePrompts.install(this) { AppConfigStore.current.value.base }

        AdKit.init(this)
        AdKit.onError = CrashReporting::nonFatal
        AdKit.isHomeScreen = { MainScreens.onboardingDone(this) && Launcher.isDefaultHome(it) }
        AdKit.noResumeAdOn = { it is SplashActivity || DefaultHomeStep.shown } // the splash runs its own ad
        AdKit.recentsAdScreenOn = { MainScreens.onboardingDone(this) } // during onboarding a Recents press only ever opens the Play Store jump
        MainScope().launch {
            AppConfigStore.current.map { it.ads }.distinctUntilChanged().collect { AdKit.apply(it) }
        }
        // Now when the saved config has it on (before any launcher screen starts); a first launch installs once the fetch does.
        if (AppConfigStore.current.value.launcher.on) LauncherSetup.install(this)
        else MainScope().launch { AppConfigStore.current.first { it.launcher.on }; LauncherSetup.install(this@App) }
    }
}

/** SharedPreferences read during startup (app, config, attribution, ads vault, launcher). */
private val STARTUP_PREFS = listOf(
    "skinhub", "live_config", "user_profile", "rate_prompt", "install_attribution", "exclusive_locations",
    "crash_reporting", "qr_ads_config", "our_icon", "Prefs",
)
