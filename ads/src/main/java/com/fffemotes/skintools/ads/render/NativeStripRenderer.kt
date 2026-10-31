package com.fffemotes.skintools.ads.render

import com.fffemotes.skintools.ads.R
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.fffemotes.skintools.ads.entity.AdSlotKind
import com.fffemotes.skintools.ads.policy.AdLoadMode
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.host.AdEvents
import com.fffemotes.skintools.ads.databinding.AdGmsNativeCompactBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

/** [place] = the RC place key this strip runs for (`ad` / `ad_impression` events). */
class NativeStripRenderer(private val place: String = AdEvents.place) {
    companion object {
        private var nativeAdBanner: NativeAd? = null
        /** Events of [nativeAdBanner]: its place is set when it goes on screen. */
        private var stripEvents: AdEvents.Listener? = null
    }

    /**
     * Warms the strip native. On-load natives (AdLoadMode) warm nothing; a slot then calls this
     * with [now] = true to load its own, and hears back through [onLoaded] / [onFailed].
     */
    fun fetchNativeStripAds(
        activity: Activity,
        now: Boolean = false,
        onLoaded: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        val adsPref = AdSettingsVault.getInstance(activity)
        if (!now && !AdLoadMode.preload(activity, AdLoadMode.Format.NATIVE)) return
        if (!adsPref.getBoolean("IsAdsON")) return onFailed()

        if (!adsPref.getBoolean("NativeBannerPresenter")) return onFailed()

        when (AdSlotKind.fromString(adsPref.getString("IsAdType"))) {
            AdSlotKind.GOOGLE -> {
                val adUnitId = adsPref.getString("googleNative") ?: return onFailed()

                // A slot's own load ([now]) runs for its place; a warm-up preload has none.
                val events = object : AdEvents.Listener(AdEvents.NATIVE, if (now) place else "") {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        super.onAdFailedToLoad(error)
                        Log.e("NativeStripRenderer", "Ad failed to load: ${error.message}")
                        nativeAdBanner = null
                        onFailed()
                    }
                }
                val adLoader = AdLoader.Builder(activity, adUnitId).forNativeAd { ad ->
                    nativeAdBanner?.destroy()
                    nativeAdBanner = ad
                    stripEvents = events
                    AdEvents.send(AdEvents.NATIVE, "loaded", events.place)

                    Log.d("NativeStripRenderer", "Ad loaded successfully")
                    onLoaded()
                }.withAdListener(events).withNativeAdOptions(NativeAdOptions.Builder().build()).build()

                AdEvents.send(AdEvents.NATIVE, "request", events.place)
                adLoader.loadAd(AdRequest.Builder().build())
            }


            AdSlotKind.UNKNOWN, AdSlotKind.CUSTOM -> {
                Log.e("NativeStripRenderer", "AdSlotKind Custom NOt Pre load Google Native")
                return onFailed()
            }
        }
    }

    fun presentNativeStrip(
        context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout? = null
    ) {
        Log.e("NativeAds", "Google Show: nativeAd")
        val adsPref = AdSettingsVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPref.getBoolean("IsAdsON")
            || !adsPref.getBoolean("NativeBannerPresenter")
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            return
        }


        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true

        when (AdSlotKind.fromString(adsPref.getString("IsAdType"))) {
            AdSlotKind.GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (nativeAdBanner != null) {
                            showCached(context, layout, shimmer)
                            return@post
                        } else {
                            // Nothing cached (on-load mode, or the warmed strip was already used): load this slot's strip now.
                            fetchNativeStripAds(
                                context, now = true,
                                onLoaded = {
                                    if (!context.isFinishing && !context.isDestroyed && layout.isAttachedToWindow) {
                                        showCached(context, layout, shimmer)
                                    }
                                },
                                onFailed = { if (!context.isFinishing && !context.isDestroyed) stripMissing(context, layout, shimmer) },
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("NativeStripRenderer", "Google NativeStripRenderer failed: ${e.message}")
                    }
                }
            }


            AdSlotKind.UNKNOWN, AdSlotKind.CUSTOM -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                HouseAdDirector(place).fetchHouseAd(
                    context, layout, HouseAdDirector.HouseAdStyle.BANNER
                )
            }
        }
    }

    private fun showCached(context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout?) {
        val ad = nativeAdBanner ?: return stripMissing(context, layout, shimmer)
        val binding = AdGmsNativeCompactBinding.inflate(context.layoutInflater)
        populateGmsNative(ad, binding, context)

        layout.removeAllViews()
        shimmer?.stopShimmer()
        shimmer?.isVisible = false

        layout.addView(binding.root)
        stripEvents?.place = place
        AdEvents.send(AdEvents.NATIVE, "show", place)
        nativeAdBanner = null
        fetchNativeStripAds(context)
    }

    private fun stripMissing(context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout?) {
        layout.removeAllViews()
        shimmer?.stopShimmer()
        shimmer?.isVisible = false
        HouseAdDirector(place).fetchHouseAd(context, layout, HouseAdDirector.HouseAdStyle.BANNER)
    }

    private fun populateGmsNative(
        nativeAd: NativeAd, binding: AdGmsNativeCompactBinding, context: Activity
    ) {
        val unit = AdSettingsVault.getInstance(context).getString("googleNative")
        nativeAd.setOnPaidEventListener { AdEvents.paid(it, "native_strip", unit, nativeAd.responseInfo, place) }

        binding.apply {
            mainNativeadView.headlineView = adHeadline
            mainNativeadView.bodyView = adBody
            mainNativeadView.callToActionView = adCallToAction
            mainNativeadView.iconView = adAppIcon

            (adHeadline as TextView).text = nativeAd.headline

            val bgColor = AdSettingsVault.getInstance(context).getString("NativeBgColor")
            val btnColor = AdSettingsVault.getInstance(context).getString("NativebtnColor")

            val txtColor =
                AdSettingsVault.getInstance(context).getString("NativetxtColor")

            (binding.mainNativeadView.headlineView as TextView).apply {
                setTextColor(AdFrameStyling.titleColor(context, txtColor))
            }

            (binding.mainNativeadView.bodyView as TextView).apply {
                setTextColor(AdFrameStyling.bodyColor(context, txtColor))
            }

            val btntxtColor =
                AdSettingsVault.getInstance(context).getString("NativebtntxtColor")

            (adCallToAction as TextView).apply {
                setTextColor(AdFrameStyling.ctaTextColor(context, btntxtColor))
            }

            AdFrameStyling.paintCardBackground(context, mainNativeadView, bgColor)

            AdFrameStyling.paintCtaBackground(context, adCallToAction, btnColor)
            AdFrameStyling.paintDesignExtras(context, mainNativeadView)

            if (nativeAd.body != null) {
                adBody.visibility = View.VISIBLE
                (adBody as TextView).text = nativeAd.body
            } else {
                adBody.visibility = View.GONE
            }

            if (nativeAd.icon != null) {
                adAppIcon.visibility = View.VISIBLE
                (adAppIcon as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            } else {
                adAppIcon.visibility = View.GONE
            }

            if (nativeAd.callToAction != null) {
                adCallToAction.visibility = View.VISIBLE
                (adCallToAction as TextView).text = nativeAd.callToAction
            } else {
                adCallToAction.visibility = View.GONE
            }

            mainNativeadView.setNativeAd(nativeAd)

            mainNativeadView.setTag(R.id.tag_native_ad, nativeAd)
        }
    }

}
