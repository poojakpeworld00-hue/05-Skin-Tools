package com.fffemotes.skintools.ads.render.interstitial

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.fffemotes.skintools.ads.entity.AdSlotKind
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.policy.AdLoadMode
import com.fffemotes.skintools.ads.host.AdAge
import com.fffemotes.skintools.ads.host.AdEvents
import com.fffemotes.skintools.ads.render.NativeAdRenderer
import com.fffemotes.skintools.ads.policy.AdWaits
import com.fffemotes.skintools.ads.render.ResumeAdDirector

// The "inter" step of an ad chain.
// Preload mode (AdLoadMode: ads.preload / the place's own "preload"): show the preloaded ad if there is one, otherwise skip the step.
// On-load mode: load it now behind a full-screen loader; a failure or ads.loader_wait_sec (AdWaits) skips the step.
// Each ad leaves the cache before show(), and one load per unit runs at a time, so two chains can't stack two inters.
object ChainInterstitial {
    private const val TAG = "ChainInter"

    private val ready = mutableMapOf<String, InterstitialAd>()
    private val readyAt = mutableMapOf<String, Long>()
    private val loading = mutableSetOf<String>()
    private val main = Handler(Looper.getMainLooper())

    // Warms the ad ahead of the step; a no-op in on-load mode, which loads only when the step is reached.
    fun preload(context: Context, unitId: String, placement: String? = null) {
        dropStale(unitId)
        if (unitId.isBlank() || unitId in ready || unitId in loading) return
        val pref = AdSettingsVault.getInstance(context)
        if (!AdLoadMode.preload(context, AdLoadMode.Format.INTER, placement)) return
        // The global unit already sits in NavigationInterstitial's preload: don't load it twice.
        if (isGlobal(pref, unitId) && (NavigationInterstitial.hasPreloadedGoogle || NavigationInterstitial.googleInterLoading)) return
        if (!pref.getBoolean("IsAdsON")) return
        if (AdSlotKind.fromString(pref.getString("IsAdType")) != AdSlotKind.GOOGLE) return

        loading += unitId
        AdEvents.send(AdEvents.INTER, "request", "") // a warm-up preload has no place
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdEvents.send(AdEvents.INTER, "loaded", "")
                    loading -= unitId
                    keep(unitId, ad)
                    Log.d(TAG, "preloaded $unitId")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdEvents.loadFailed(AdEvents.INTER, error.code, "")
                    loading -= unitId
                    Log.e(TAG, "preload failed for $unitId: ${error.message}")
                }
            },
        )
    }

    /** A preloaded ad is waiting for [unitId]. */
    fun hasReady(unitId: String): Boolean = unitId in ready

    fun show(
        activity: Activity,
        unitId: String,
        placement: String? = null,
        onMiss: () -> Unit,
        /** true (exit_ad steps): with nothing preloaded, load now behind the loader even in preload mode. */
        loadIfMissing: Boolean = false,
        onDone: () -> Unit,
    ) {
        if (busy(activity) || unitId.isBlank()) return onMiss()

        val pref = AdSettingsVault.getInstance(activity)
        dropStale(unitId)
        val ad = ready.remove(unitId)
            ?: if (isGlobal(pref, unitId)) NavigationInterstitial.takePreloadedGoogle() else null
        if (ad != null) return present(activity, unitId, placement, ad, onMiss, onDone)

        if (!loadIfMissing && AdLoadMode.preload(activity, AdLoadMode.Format.INTER, placement)) {
            Log.d(TAG, "no preloaded ad for $unitId — skip")
            preload(activity, unitId, placement)
            return onMiss()
        }
        // Another chain is already loading this unit and will show it: this one skips.
        if (!loadIfMissing && unitId in loading) return onMiss()

        Log.d(TAG, "on-load: loading $unitId behind the loader")
        loading += unitId
        var settled = false
        val place = AdEvents.place
        val timeout = Runnable {
            if (settled) return@Runnable
            settled = true
            AdEvents.send(AdEvents.INTER, "timeout", place)
            InterstitialPreloader.hide()
            Log.d(TAG, "on-load: $unitId timed out — skip")
            onMiss()
        }
        InterstitialPreloader.show(activity, true)
        main.postDelayed(timeout, loadTimeoutMs(activity))
        AdEvents.send(AdEvents.INTER, "request", place)

        InterstitialAd.load(
            activity,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    AdEvents.send(AdEvents.INTER, "loaded", place)
                    loading -= unitId
                    if (settled) { keep(unitId, loaded); return } // late: keep it for the next step
                    settled = true
                    main.removeCallbacks(timeout)
                    InterstitialPreloader.hide()
                    if (busy(activity)) return onMiss()
                    present(activity, unitId, placement, loaded, onMiss, onDone)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdEvents.loadFailed(AdEvents.INTER, error.code, place)
                    loading -= unitId
                    if (settled) return
                    settled = true
                    main.removeCallbacks(timeout)
                    InterstitialPreloader.hide()
                    Log.e(TAG, "on-load failed for $unitId: ${error.message} — skip")
                    onMiss()
                }
            },
        )
    }

    private fun present(
        activity: Activity,
        unitId: String,
        placement: String?,
        ad: InterstitialAd,
        onMiss: () -> Unit,
        onDone: () -> Unit,
    ) {
        var fired = false
        val place = AdEvents.place
        ad.fullScreenContentCallback = object : AdEvents.FullScreen(AdEvents.INTER, place) {
            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                NavigationInterstitial.isInterShow = true
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                NavigationInterstitial.isInterShow = false
                preload(activity, unitId, placement)
                if (!fired) { fired = true; onDone() }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                super.onAdFailedToShowFullScreenContent(error)
                NavigationInterstitial.isInterShow = false
                Log.e(TAG, "show failed for $unitId: ${error.message} — skip")
                preload(activity, unitId, placement)
                if (!fired) { fired = true; onMiss() }
            }
        }
        ad.setOnPaidEventListener { AdEvents.paid(it, "inter_chain", ad.adUnitId, ad.responseInfo, place) }
        // Flag it before show(): a second chain arriving before onAdShowedFullScreenContent must skip.
        NavigationInterstitial.isInterShow = true
        ad.show(activity)
    }

    private fun busy(activity: Activity) = activity.isFinishing || activity.isDestroyed ||
        NavigationInterstitial.isInterShow || ResumeAdDirector.isShowingAd || NativeAdRenderer.isFullNativeShowing

    internal fun loadTimeoutMs(context: Context): Long = AdWaits.waitMs(context)

    private fun isGlobal(pref: AdSettingsVault, unitId: String) = unitId == pref.getString("googleInter").orEmpty()

    private fun keep(unitId: String, ad: InterstitialAd) {
        ready[unitId] = ad
        readyAt[unitId] = AdAge.now()
    }

    private fun dropStale(unitId: String) {
        if (unitId in ready && AdAge.stale(readyAt[unitId] ?: 0L, AdAge.FULL_SCREEN_MS)) ready.remove(unitId)
    }
}
