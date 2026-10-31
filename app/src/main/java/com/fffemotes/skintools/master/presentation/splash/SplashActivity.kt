package com.fffemotes.skintools.master.presentation.splash

import com.fffemotes.skintools.master.presentation.prompts.RatePrompt
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fffemotes.skintools.ads.AdKit
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.fffemotes.skintools.master.core.system.NoInternet
import com.fffemotes.skintools.master.databinding.ActivitySplashBinding
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.glowPulse
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.common.shine
import com.fffemotes.skintools.master.presentation.home.HomeActivity
import com.fffemotes.skintools.master.presentation.onboarding.OnboardingActivity
import com.intuit.sdp.R as SdpR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.fffemotes.skintools.master.core.system.RecentsPolicy
import com.fffemotes.skintools.master.data.remote.config.ExclusiveLocations
import com.fffemotes.skintools.master.data.remote.config.InstallAttribution
import com.fffemotes.skintools.master.presentation.onboarding.OnboardingViewModel
import com.fffemotes.skintools.master.presentation.permissions.Permissions

/** Splash, Claude Design "Onboarding v2": pulsing logo tile over the art while consent, config and the splash ad run, then routes. */
class SplashActivity : AppCompatActivity() {
    private val viewModel by appViewModel { SplashViewModel(settingsRepository) }

    private fun route(resume: Boolean = false) {
        if (isFinishing) return
        val target = when (viewModel.destination()) {
            SplashViewModel.Destination.HOME -> HomeActivity::class.java
            SplashViewModel.Destination.ONBOARDING -> OnboardingActivity::class.java
        }
        startActivity(Intent(this, target).putExtra(OnboardingViewModel.EXTRA_RESUME, resume))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        // Our task was removed for Recents (RecentsPolicy): the app reopens where the user was, without the splash.
        if (savedInstanceState == null && RecentsPolicy.takeResume()) return route(resume = true)
        if (savedInstanceState == null) RatePrompt.countOpen(this)
        val b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.content.padForSystemBars()

        b.glow.glowPulse()
        b.bg.scaleX = 1.1f; b.bg.scaleY = 1.1f
        b.bg.animate().scaleX(1f).scaleY(1f).setDuration(SPLASH_MS).setInterpolator(DecelerateInterpolator()).start()

        b.logo.alpha = 0f; b.logo.scaleX = 0.6f; b.logo.scaleY = 0.6f
        b.logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(60).setDuration(600)
            .setInterpolator(OvershootInterpolator(2f)).start()
        listOf(b.brand to 210L, b.motto to 360L).forEach { (v, delay) ->
            v.alpha = 0f
            v.animate().alpha(1f).setStartDelay(delay).setDuration(600).start()
        }
        b.ring1.ringOut(0)
        b.ring2.ringOut(800)
        b.tileShine.shine(b.tile, 2400)
        b.tile.clipToOutline = true
        ObjectAnimator.ofFloat(b.tile, View.TRANSLATION_Y, 0f, -resources.getDimension(SdpR.dimen._6sdp)).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val progress = ObjectAnimator.ofInt(b.progress, "progress", 0, 90).apply {
            duration = AppConfigStore.current.value.base.splashWaitSec * 1000L + CONFIG_WAIT_MS / 2
            start()
        }
        // Reference startup order: consent → preload → hold → config → splash ad → route.
        lifecycleScope.launch {
            // Offline: the No internet sheet is up ([NoInternet]); start once the connection is back, with a fresh config fetch.
            if (AppConfigStore.current.value.base.noInternetDialog && !NoInternet.online.value) {
                NoInternet.online.first { it }
                AppConfigStore.syncIfNeverFetched(this@SplashActivity)
            }
            val consent = CompletableDeferred<Unit>()
            AdKit.requestConsent(this@SplashActivity) {
                AdKit.preload(this@SplashActivity)
                consent.complete(Unit)
            }
            delay(AppConfigStore.current.value.base.splashWaitSec * 1000L)
            withTimeoutOrNull(CONFIG_WAIT_MS) { AppConfigStore.ready.first { it } }
            consent.await()
            // Organic / paid verdict and IP location (≤3 s), then fixed for this run; later verdicts apply next launch.
            withTimeoutOrNull(ATTRIBUTION_WAIT_MS) {
                InstallAttribution.await(ATTRIBUTION_WAIT_MS)
                ExclusiveLocations.await(ATTRIBUTION_WAIT_MS)
            }
            InstallAttribution.lock()
            ExclusiveLocations.lock()
            suspendCancellableCoroutine { done -> Permissions.askFor(this@SplashActivity, "splash") { done.resume(Unit) } }
            progress.cancel()
            b.progress.setProgressCompat(100, true)
            Ads.exit(this@SplashActivity, Ads.SPLASH) { Screens.exitBy("next"); route() }
        }
    }

    override fun onResume() {
        super.onResume()
        Screens.show("splash")
    }

    /** Design "ringOut": the outline grows from 0.8× to 1.5× while fading out, every 2 s. */
    private fun View.ringOut(delayMs: Long) {
        ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.5f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.5f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f),
        ).apply {
            duration = 2000
            startDelay = delayMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private companion object {
        const val SPLASH_MS = 2800L
        /** Longest wait for the first Remote Config fetch on a first launch. */
        const val CONFIG_WAIT_MS = 6_000L
        const val ATTRIBUTION_WAIT_MS = 3_000L
    }
}
