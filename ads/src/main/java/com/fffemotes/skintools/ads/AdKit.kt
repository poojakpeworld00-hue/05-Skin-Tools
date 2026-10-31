package com.fffemotes.skintools.ads

import android.app.Activity
import android.app.Application
import com.fffemotes.skintools.ads.config.AdsConfig
import com.fffemotes.skintools.ads.events.charging.ChargeEventWatcher
import com.fffemotes.skintools.ads.events.pkgresult.PackageEventWatcher
import com.fffemotes.skintools.ads.events.recent.RecentAdWatcher
import com.fffemotes.skintools.ads.host.AdLifecycle
import com.fffemotes.skintools.ads.host.AdPreload
import com.fffemotes.skintools.ads.host.AppResumeWatcher
import com.fffemotes.skintools.ads.launcher.UnlockAdWatcher
import com.fffemotes.skintools.ads.policy.ConsentFlowGate
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Module setup: init once, apply every config, UMP consent. Screens use [Ads] for the ads themselves. */
object AdKit {
    /** The config last given to [apply]; default = ads off. */
    @Volatile
    var config: AdsConfig = AdsConfig()
        private set

    /** Screens where `launcher.phone_unlock` may show (e.g. "we are the default home and onboarding is done"). */
    @Volatile
    var isHomeScreen: (Activity) -> Boolean = { true }

    /** Screens that never get the `launcher.app_resume` ad (permission / default-home / settings returns, …). */
    @Volatile
    var noResumeAdOn: (Activity) -> Boolean = { false }

    /** Screens where a Recents press may lead to the `launcher.play_store_return` ad screen (e.g. not during onboarding). */
    @Volatile
    var recentsAdScreenOn: (Activity) -> Boolean = { true }

    /** Caught failures worth fixing (an ad flow that threw), with where: the app sends them to Crashlytics. */
    @Volatile
    var onError: (Throwable, String) -> Unit = { _, _ -> }

    private var app: Application? = null
    private val mobileAdsStarted = AtomicBoolean(false)

    internal fun context(): Application = checkNotNull(app) { "AdKit.init(app) was not called" }

    fun init(app: Application) {
        if (this.app != null) return
        this.app = app
        AdLifecycle.register(app)
        AppResumeWatcher.register(app)
        UnlockAdWatcher.register(app)
        ChargeEventWatcher.register(app)
        PackageEventWatcher.register(app)
        RecentAdWatcher.register(app)
        if (ConsentFlowGate.getInstance(app).canRequestAds()) startMobileAds()
        AdsConfigApplier.apply(app, config, consentAllows())
    }

    /** Safe on every config refresh. */
    fun apply(config: AdsConfig) {
        this.config = config
        val app = context()
        AdsConfigApplier.apply(app, config, consentAllows())
        PackageEventWatcher.seedIfNeeded(app)
    }

    /** UMP: update consent info, show the form when required, then start the Mobile Ads SDK. [onDone] once. */
    fun requestConsent(activity: Activity, onDone: () -> Unit) {
        val once = AtomicBoolean(false)
        val done = {
            if (once.compareAndSet(false, true)) {
                if (ConsentFlowGate.getInstance(activity).canRequestAds()) startMobileAds()
                AdsConfigApplier.apply(activity, config, consentAllows())
                onDone()
            }
        }
        runCatching { ConsentFlowGate.getInstance(activity).askConsent(activity) { done() } }.onFailure { done() }
    }

    /** Settings "Privacy options" entry is needed when this is true. */
    fun privacyOptionsRequired(): Boolean = ConsentFlowGate.getInstance(context()).isPrivacyOptionsRequired

    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) =
        ConsentFlowGate.getInstance(activity).openPrivacyOptions(activity) { onDone() }

    fun preload(activity: Activity) = AdPreload.all(activity)

    /** `ads.on` and the consent gate. */
    internal fun adsOn(): Boolean = config.on && consentAllows()

    /** MSG-06 rule (AdIngest.gateOpen): no ads only when consent is required and was not given. */
    private fun consentAllows(): Boolean {
        val app = app ?: return false
        return runCatching {
            val info = UserMessagingPlatform.getConsentInformation(app)
            info.canRequestAds() || info.consentStatus != ConsentInformation.ConsentStatus.REQUIRED
        }.getOrDefault(true)
    }

    private fun startMobileAds() {
        val app = app ?: return
        if (!mobileAdsStarted.compareAndSet(false, true)) return
        Executors.newSingleThreadExecutor().execute {
            runCatching { MobileAds.initialize(app) {} }.onFailure { mobileAdsStarted.set(false) }
        }
    }
}
