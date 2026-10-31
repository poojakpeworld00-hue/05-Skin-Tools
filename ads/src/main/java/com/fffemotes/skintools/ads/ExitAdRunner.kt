package com.fffemotes.skintools.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import com.fffemotes.skintools.ads.config.AdStep
import com.fffemotes.skintools.ads.config.ExitAd
import com.fffemotes.skintools.ads.host.AdEvents
import com.fffemotes.skintools.ads.policy.AdWaits
import com.fffemotes.skintools.ads.render.NativeAdRenderer
import com.fffemotes.skintools.ads.render.ResumeAdDirector
import com.fffemotes.skintools.ads.render.RewardAdRenderer
import com.fffemotes.skintools.ads.render.interstitial.ChainInterstitial
import com.fffemotes.skintools.ads.render.interstitial.InterstitialBackupChain
import com.fffemotes.skintools.ads.render.interstitial.InterstitialPreloader
import com.fffemotes.skintools.ads.render.interstitial.NavigationInterstitial

/**
 * Runs a place's `exit_ad` (see [ExitAdFlow] for the rules) on the ported engine.
 * [onDone] runs exactly once, also when the activity finishes; never two full-screen ads at a time.
 */
object ExitAdRunner {
    /** Places whose `gap_sec` limits their event screen, not their close ad (see [Ads]). */
    internal val SCREEN_GAP_PLACES = setOf(
        Ads.CHARGER_CONNECTED, Ads.CHARGER_DISCONNECTED, Ads.APP_INSTALLED, Ads.APP_UNINSTALLED, Ads.PLAY_STORE_RETURN,
    )

    private const val PREFS = "adkit_exit_gap"
    private val every = EveryCounter()
    private val main = Handler(Looper.getMainLooper())
    private var active: ExitAdFlow? = null

    /** A full-screen ad, loader or exit flow is on screen. */
    fun busy(): Boolean = active != null || NavigationInterstitial.isInterShow || ResumeAdDirector.isShowingAd ||
        NativeAdRenderer.isFullNativeShowing || InterstitialPreloader.isShowing

    fun run(activity: Activity, placeKey: String, onDone: () -> Unit) =
        run(activity, placeKey, AdKit.config.places[placeKey]?.exitAd, onDone)

    fun run(activity: Activity, placeKey: String, exit: ExitAd?, onDone: () -> Unit) {
        var called = false
        val done = { if (!called) { called = true; onDone() } }
        val config = AdKit.config
        if (!PlaceRules.exitRuns(AdKit.adsOn(), exit, every, placeKey)) return done()
        exit!!
        if (activity.isFinishing || activity.isDestroyed || busy()) return done()
        val gapSec = (config.places[placeKey]?.int("gap_sec") ?: 0).toLong()
        val gapped = placeKey !in SCREEN_GAP_PLACES && gapSec > 0
        if (gapped && !PlaceRules.gapPassed(lastRun(activity, placeKey), gapSec, System.currentTimeMillis())) return done()
        if (gapped) markRun(activity, placeKey)

        AdsConfigApplier.writePalette(activity, config)
        val watcher = FinishWatcher(activity)
        val flow = ExitAdFlow(
            config, exit, EngineSource(activity, placeKey),
            isAlive = { !activity.isFinishing && !activity.isDestroyed },
        ) {
            active = null
            AdEvents.place = ""
            watcher.stop()
            done()
        }
        active = flow
        // Every full-screen ad of this run reports this place (see AdEvents).
        AdEvents.place = placeKey
        watcher.onGone = { flow.finish() }
        flow.start()
    }

    private fun lastRun(context: Context, key: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, 0L)

    private fun markRun(context: Context, key: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(key, System.currentTimeMillis()) }

    /** Ends the flow when its activity is destroyed (onDone still runs once). */
    private class FinishWatcher(private val activity: Activity) : Application.ActivityLifecycleCallbacks {
        var onGone: () -> Unit = {}

        init {
            activity.application.registerActivityLifecycleCallbacks(this)
        }

        fun stop() = activity.application.unregisterActivityLifecycleCallbacks(this)

        override fun onActivityDestroyed(a: Activity) {
            if (a === activity) onGone()
        }

        override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
        override fun onActivityStarted(a: Activity) = Unit
        override fun onActivityResumed(a: Activity) = Unit
        override fun onActivityPaused(a: Activity) = Unit
        override fun onActivityStopped(a: Activity) = Unit
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
    }

    /** Each step on the ported engine; a missing ad is loaded behind the loader for up to loader_wait_sec. */
    private class EngineSource(private val activity: Activity, private val placeKey: String) : ExitAdSource {
        /** Splash: no step starts later than loader_wait_sec after the first one, so the user never waits longer. */
        private var splashDeadline = 0L

        override fun show(step: AdStep, onShown: () -> Unit, onNotReady: () -> Unit) {
            if (activity.isFinishing || activity.isDestroyed) return onNotReady()
            if (placeKey == Ads.SPLASH) {
                val now = SystemClock.elapsedRealtime()
                if (splashDeadline == 0L) splashDeadline = now + AdWaits.waitMs(activity)
                else if (now >= splashDeadline) {
                    AdEvents.send(AdEvents.formatOf(step), "timeout", placeKey)
                    return onNotReady()
                }
            }
            val units = AdKit.config.units
            // ads.loader_on_miss false: an ad that is not cached yet is skipped instead of loaded behind "Please wait".
            // The splash keeps loading: it is a waiting screen already. A place's own `loader_on_miss` wins over the global.
            val loadIfMissing = placeKey == Ads.SPLASH ||
                (AdKit.config.places[placeKey]?.options?.get("loader_on_miss") as? Boolean ?: AdKit.config.loaderOnMiss)
            when (step) {
                AdStep.INTER -> {
                    val unit = when (placeKey) {
                        Ads.SPLASH -> units.interForSplash()
                        Ads.BACK_BUTTON -> units.interForBack()
                        else -> units.inter
                    }
                    ChainInterstitial.show(activity, unit, placeKey, onMiss = onNotReady, loadIfMissing = loadIfMissing) { onShown() }
                }
                AdStep.REWARDED -> RewardAdRenderer.presentOrLoad(activity, onShown, onNotReady, loadIfMissing = loadIfMissing)
                AdStep.APP_OPEN -> ResumeAdDirector.showLoaded(activity, onShown, onNotReady, loadIfMissing = loadIfMissing)
                AdStep.FULL_NATIVE ->
                    InterstitialBackupChain.fullNative(activity, placeKey, next = onNotReady, done = onShown, loadIfMissing = loadIfMissing)
                AdStep.CUSTOM -> InterstitialBackupChain.custom(activity, next = onNotReady, done = onShown)
                AdStep.LINK -> onNotReady() // never asked: ExitAdFlow opens links itself
            }
        }

        override fun openLinks(urls: List<String>, onBack: () -> Unit) {
            // Our own return: the app-open / resume ad must not fire on it (cleared after the return settles).
            ResumeAdDirector.skipNextAppOpenAd = true
            AdEvents.send(AdEvents.CUSTOM, "link_open", placeKey)
            NavigationInterstitial.openSponsorLink(activity, placeKey, urls) {
                main.postDelayed({ ResumeAdDirector.skipNextAppOpenAd = false }, 1_000L)
                onBack()
            }
        }
    }
}
