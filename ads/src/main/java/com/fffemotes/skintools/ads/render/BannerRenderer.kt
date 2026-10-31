package com.fffemotes.skintools.ads.render
import com.fffemotes.skintools.ads.host.onceWindowFocused
import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.BaseAdView
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.LoadAdError
import com.fffemotes.skintools.ads.entity.AdSlotKind
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.host.AdEvents

enum class BannerSizing { ADAPTIVE, INLINE, NORMAL }
enum class BannerStyle { AUTO, GOOGLE, CUSTOM }

interface BannerLifecycleHook {
    fun onAdLoaded() {}
    fun onAdFailed() {}
    /** Banner intentionally not shown (switched off or frequency skip): callers must not fall back. */
    fun onAdSkipped() {}
}

/** [place] = the RC place key this banner runs for (`ad` / `ad_impression` events). */
class BannerRenderer(private val place: String = AdEvents.place) {
    private var googleBanner: BaseAdView? = null

    companion object {
        var bannerCounter = 0
    }

    fun presentBanner(
        activity: Activity,
        container: FrameLayout,
        type: BannerStyle = BannerStyle.AUTO,
        size: BannerSizing = BannerSizing.ADAPTIVE,
        isCollapsable: Boolean = false,
        shimmer: ShimmerFrameLayout? = null,
        observer: BannerLifecycleHook? = null,
        customAdUnitId: String? = null,
        disableInternalFallback: Boolean = false
    ) {
        val pref = AdSettingsVault.getInstance(activity)

        if (!pref.getBoolean("IsAdsON") || !pref.getBoolean("BannerAdPresenter")) {
            hide(container)
            observer?.onAdSkipped()
            return
        }
        if (!hasNetwork(activity)) {
            hide(container)
            observer?.onAdFailed()
            return
        }

        if (bannerCounter < pref.getInt("BannerCounter")) {
            bannerCounter++
            hide(container)
            observer?.onAdSkipped()
            return
        }
        bannerCounter = 0

        when (type) {
            BannerStyle.AUTO -> {
                when (AdSlotKind.fromString(pref.getString("IsAdType"))) {
                    AdSlotKind.GOOGLE -> fetchGmsBanner(
                        activity,
                        container,
                        size,
                        isCollapsable,
                        shimmer,
                        observer,
                        customAdUnitId,
                        disableInternalFallback
                    )

                    AdSlotKind.CUSTOM, AdSlotKind.UNKNOWN -> {
                        if (disableInternalFallback) {
                            observer?.onAdFailed()
                        } else {
                            HouseAdDirector(place).fetchHouseAd(
                                activity,
                                container,
                                HouseAdDirector.HouseAdStyle.BANNER
                            )
                        }
                    }
                }
            }

            BannerStyle.GOOGLE -> fetchGmsBanner(
                activity,
                container,
                size,
                isCollapsable,
                shimmer,
                observer,
                customAdUnitId,
                disableInternalFallback
            )

            BannerStyle.CUSTOM -> {
                if (disableInternalFallback) {
                    observer?.onAdFailed()
                } else {
                    HouseAdDirector(place).fetchHouseAd(
                        activity,
                        container,
                        HouseAdDirector.HouseAdStyle.BANNER
                    )
                }
            }
        }
    }

    private fun fetchGmsBanner(
        activity: Activity,
        container: FrameLayout,
        size: BannerSizing,
        isCollapsable: Boolean,
        shimmer: ShimmerFrameLayout? = null,
        observer: BannerLifecycleHook?,
        customAdUnitId: String? = null,
        disableInternalFallback: Boolean = false
    ) {
        val pref = AdSettingsVault.getInstance(activity)
        val adUnitId = customAdUnitId ?: pref.getString("googleBanner")

        if (!AdSlotGate.isOpen(container)) return // slot off screen (AdSlotGate)
        if (!adUnitId.isNullOrEmpty() && !AdSlotGate.beginLoad(container)) return // this slot is already loading

        if (adUnitId.isNullOrEmpty()) {
            if (disableInternalFallback) observer?.onAdFailed()
            else degradeToFanOrHouse(activity, container, shimmer, observer)
            return
        }

        hide(container)

        shimmer?.startShimmer()
        shimmer?.visibility = View.VISIBLE
        container.removeAllViews()
        shimmer?.let { container.addView(it) }
        container.visibility = View.VISIBLE

        // Ad Manager units ("/network/...") load through AdManagerAdView + AdManagerAdRequest, per the Ad Manager banner guide.
        val adManager = adUnitId.startsWith("/")
        if (googleBanner == null) googleBanner = if (adManager) AdManagerAdView(activity) else AdView(activity)
        googleBanner?.adUnitId = adUnitId

        if (isCollapsable) {
            googleBanner?.setAdSize(getAdSize(activity, container))
        } else {
            googleBanner?.setAdSize(gmsBannerSize(activity, container, size, isCollapsable))
        }

        googleBanner?.adListener = object : AdEvents.Listener(AdEvents.BANNER, place) {
            override fun onAdLoaded() {
                AdEvents.send(AdEvents.BANNER, "loaded", place)
                // Attach only while this screen is in front: a collapsible opens its expanded popup on attach,
                // and behind a full-screen ad (app-open / interstitial) that popup would show through.
                activity.onceWindowFocused { attachLoaded() }
            }

            private fun attachLoaded() {
                AdSlotGate.endLoad(container)
                // The slot closed meanwhile (panel slid away, side menu opened): drop the ad instead of
                // letting a collapsible open its expanded popup over another screen.
                if (!AdSlotGate.isOpen(container) || activity.isFinishing || activity.isDestroyed) {
                    runCatching { googleBanner?.destroy() }
                    googleBanner = null
                    return
                }
                Log.i(
                    "BannerRenderer",
                    "Ad loaded. adView.isCollapsible() is ${googleBanner?.isCollapsible}.",
                )

                val banner = googleBanner!!
                banner.setOnPaidEventListener { AdEvents.paid(it, "banner", banner.adUnitId, banner.responseInfo, place) }

                shimmer?.stopShimmer()
                shimmer?.visibility = View.GONE
                AdSlotGate.destroyAds(container) // a ribbon from an earlier load may still sit here; removed-but-live keeps its popup
                container.removeAllViews()

                container.addView(AdFrameStyling.bannerStrip(activity, banner))
                container.visibility = View.VISIBLE
                AdEvents.send(AdEvents.BANNER, "show", place)
                // Any screen: the slot (and an expanded ad's popup) goes away with its screen.
                AdSlotGate.closeWithScreen(activity, container)
                observer?.onAdLoaded()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                AdSlotGate.endLoad(container)
                shimmer?.stopShimmer()
                shimmer?.visibility = View.GONE
                Log.e("BannerRenderer", "Google Banner Failed: ${error.message}")
                observer?.onAdFailed()
                if (!disableInternalFallback) {
                    degradeToFanOrHouse(activity, container, shimmer, observer)
                }
            }

            // Collapsible expanded / collapsed (onAdOpened / onAdClosed): white backdrop under Google's popup.
            override fun onAdOpened() {
                val banner = googleBanner ?: return
                if (banner.isCollapsible) CollapsibleBackdrop.show(activity, banner)
            }

            override fun onAdClosed() = CollapsibleBackdrop.hide(activity)
        }

        // RC banner_collapsible -> every load asks for the expanding version (no app-side limit).
        val expand = isCollapsable
        val extras = Bundle().apply { putString("collapsible", "bottom") }
        val request: AdRequest = when {
            adManager && expand -> AdManagerAdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, extras).build()
            adManager -> AdManagerAdRequest.Builder().build()
            expand -> AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, extras).build()
            else -> AdRequest.Builder().build()
        }

        AdEvents.send(AdEvents.BANNER, "request", place)
        googleBanner?.loadAd(request)
    }

    private fun getAdSize(
        activity: Activity,
        sponsorContainer: FrameLayout
    ): com.google.android.gms.ads.AdSize {
        val display = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(display)

        val density = display.density
        val widthPixels =
            if (sponsorContainer.width == 0) display.widthPixels.toFloat()
            else sponsorContainer.width.toFloat()

        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    private fun gmsBannerSize(
        activity: Activity,
        container: FrameLayout,
        size: BannerSizing,
        isCollapsable: Boolean
    ): AdSize {
        return when (size) {
            BannerSizing.NORMAL -> AdSize.BANNER
            BannerSizing.INLINE -> AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(
                activity,
                displayWidthDp(activity)
            )

            BannerSizing.ADAPTIVE -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                activity,
                displayWidthDp(activity)
            )
        }
    }

    private fun displayWidthDp(activity: Activity): Int {
        val display = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(display)
        return (display.widthPixels / display.density).toInt()
    }

    private fun degradeToFanOrHouse(
        activity: Activity,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        observer: BannerLifecycleHook?
    ) {
        shimmer?.startShimmer()
        shimmer?.visibility = View.VISIBLE
        HouseAdDirector(place).fetchHouseAd(
            activity,
            container,
            HouseAdDirector.HouseAdStyle.BANNER
        )
    }

    private fun hide(container: FrameLayout) {
        CollapsibleBackdrop.hideFor(container.context)
        AdSlotGate.destroyAds(container) // the banner being replaced must not keep drawing (half-and-half look)
        container.removeAllViews()
        container.visibility = View.GONE
    }
}
