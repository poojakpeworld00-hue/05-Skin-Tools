package com.fffemotes.skintools.ads.render

import com.fffemotes.skintools.ads.host.AdAge
import com.fffemotes.skintools.ads.host.AdEvents
import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.fffemotes.skintools.ads.policy.AdLoadMode
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.policy.AdWaits
import com.fffemotes.skintools.ads.render.interstitial.InterstitialPreloader
import com.fffemotes.skintools.ads.render.interstitial.NavigationInterstitial

class RewardAdRenderer {
    companion object {
        private var loadedAd: RewardedAd? = null
        private var loadedAt = 0L
        val isLoaded: Boolean get() = dropStale() != null

        private fun dropStale(): RewardedAd? {
            if (loadedAd != null && AdAge.stale(loadedAt, AdAge.FULL_SCREEN_MS)) loadedAd = null
            return loadedAd
        }
        private var isLoading = false

        /**
         * Warms the rewarded ad. On-load rewarded (AdLoadMode) warms nothing unless [force]d by
         * the moment about to show it; [onResult] then hears whether an ad is ready.
         */
        fun preload(
            context: Context,
            ignoreMasterSwitch: Boolean = false,
            force: Boolean = false,
            onResult: ((Boolean) -> Unit)? = null,
        ) {
            val pref = AdSettingsVault.getInstance(context)
            if (dropStale() != null) return onResult?.invoke(true) ?: Unit
            if (!force && !AdLoadMode.preload(context, AdLoadMode.Format.REWARDED)) return
            if ((!ignoreMasterSwitch && !pref.getBoolean("IsAdsON")) || !pref.getBoolean("RewardedAds") || isLoading) {
                return onResult?.invoke(false) ?: Unit
            }

            val unitId = pref.getString("googleRewarded")
            if (unitId.isNullOrEmpty()) return onResult?.invoke(false) ?: Unit

            isLoading = true
            Log.d("RewardAdRenderer", "Preloading…")
            // A forced load is for the place about to show it; a warm-up preload has none.
            val place = if (force) AdEvents.place else ""
            AdEvents.send(AdEvents.REWARDED, "request", place)

            RewardedAd.load(
                context,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        AdEvents.send(AdEvents.REWARDED, "loaded", place)
                        loadedAd = ad
                        loadedAt = AdAge.now()
                        isLoading = false
                        Log.d("RewardAdRenderer", "Preloaded OK")
                        onResult?.invoke(true)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AdEvents.loadFailed(AdEvents.REWARDED, error.code, place)
                        loadedAd = null
                        isLoading = false
                        Log.e("RewardAdRenderer", "Preload failed: ${error.message}")
                        onResult?.invoke(false)
                    }
                }
            )
        }

        fun presentIfLoaded(activity: Activity, onDone: () -> Unit, onMiss: () -> Unit = onDone): Boolean {
            val ad = dropStale() ?: return false
            loadedAd = null
            val place = AdEvents.place
            ad.fullScreenContentCallback = object : AdEvents.FullScreen(AdEvents.REWARDED, place) {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    preload(activity)
                    onDone()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    super.onAdFailedToShowFullScreenContent(error)
                    Log.e("RewardAdRenderer", "Fallback show failed: ${error.message}")
                    preload(activity)
                    // Not shown: let the chain fall back (if_ad_not_ready / next step) instead of counting it as shown.
                    onMiss()
                }
            }
            ad.setOnPaidEventListener { AdEvents.paid(it, "rewarded_fallback", ad.adUnitId, ad.responseInfo, place) }
            ad.show(activity) {}
            return true
        }

        /**
         * A chain's rewarded step: the ready ad, else - on-load rewarded - one loaded now behind
         * the loader. [onMiss] when there is none (or the load outlasts ads.loader_wait_sec).
         */
        fun presentOrLoad(activity: Activity, onDone: () -> Unit, onMiss: () -> Unit, loadIfMissing: Boolean = false) {
            if (presentIfLoaded(activity, onDone, onMiss)) return
            if (!loadIfMissing && AdLoadMode.preload(activity, AdLoadMode.Format.REWARDED)) {
                preload(activity)
                return onMiss()
            }
            loadNow(activity, ignoreMasterSwitch = false) { ok ->
                if (!ok || !presentIfLoaded(activity, onDone, onMiss)) onMiss()
            }
        }

        /** Loads now behind the full-screen loader; [onResult] is false on failure or after ads.loader_wait_sec. */
        private fun loadNow(activity: Activity, ignoreMasterSwitch: Boolean, onResult: (Boolean) -> Unit) {
            InterstitialPreloader.show(activity, true)
            val wait = AdWaits.main(activity)
            wait.start {
                InterstitialPreloader.hide()
                Log.d("RewardAdRenderer", "on-load: timed out — skip")
                AdEvents.send(AdEvents.REWARDED, "timeout", AdEvents.place)
                onResult(false)
            }
            preload(activity, ignoreMasterSwitch, force = true) { ok ->
                if (!wait.arrived()) return@preload // late: the ad stays cached for next time
                InterstitialPreloader.hide()
                onResult(ok && !activity.isFinishing && !activity.isDestroyed)
            }
        }

    }


}
