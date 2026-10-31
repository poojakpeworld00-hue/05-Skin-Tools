package com.fffemotes.skintools.ads.host

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fffemotes.skintools.ads.render.interstitial.NavigationInterstitial

/**
 * App-wide hooks the engine needs (port of QR ScannerApplication.onActivityResumed):
 * when the user comes back from sponsor links (webview / Chrome tab), resume the flow that opened them
 * (link-first follow-up ads, then the screen's own action — e.g. the next onboarding step).
 */
object AdLifecycle : Application.ActivityLifecycleCallbacks {
    private var registered = false

    fun register(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        NavigationInterstitial.onReturnFromSettings(activity)
        keepNativeWarm(activity)
    }

    /** Previous version kept a native ready in the background so screens find one at once (shared load, no spam). */
    private fun keepNativeWarm(activity: Activity) {
        if (activity is com.fffemotes.skintools.ads.render.SponsorWebActivity) return
        val vault = com.fffemotes.skintools.ads.policy.AdSettingsVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON") || !vault.getBoolean("NativeAd")) return
        if (com.fffemotes.skintools.ads.render.NativeAdRenderer.holdsCachedAd()) return
        runCatching { com.fffemotes.skintools.ads.render.NativeAdRenderer().fetchNativeAds(activity) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = NavigationInterstitial.onAppCovered()
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
