package com.fffemotes.skintools.ads.render

import com.fffemotes.skintools.ads.AdKit
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.fffemotes.skintools.ads.entity.AdSlotKind
import com.fffemotes.skintools.ads.entity.AdSlotKind.*
import com.fffemotes.skintools.ads.policy.AdLoadMode
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.policy.AdUnitIds
import com.fffemotes.skintools.ads.policy.AdWaits
import com.fffemotes.skintools.ads.policy.ScreenAdPlanner
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.host.AdEvents
import com.fffemotes.skintools.ads.databinding.AdGmsNativeLargeBinding
import androidx.core.content.ContextCompat
import com.fffemotes.skintools.ads.R
import com.fffemotes.skintools.ads.databinding.AdGmsNativeFullpageBinding
import com.fffemotes.skintools.ads.databinding.AdGmsNativeMediumAltBinding
import com.fffemotes.skintools.ads.databinding.AdGmsNativeMediumBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

/** [place] = the RC place key this native runs for (`ad` / `ad_impression` events). */
class NativeAdRenderer(private val place: String = AdEvents.place) {
    interface NativeAdWatcher {
        fun onNativeAdLoaded()
        fun onNativeAdFailed()
    }

    companion object {
        private var nativeAd: NativeAd? = null
        /** Events and unit of the cached [nativeAd]: its place is set when it goes on screen. */
        private var cachedEvents: AdEvents.Listener? = null
        private var cachedUnit = ""

        var isFullNativeShowing: Boolean = false

        fun holdsCachedAd(): Boolean = nativeAd != null

        fun isLoading(): Boolean = sharedLoad.inFlight

        /** One native load at a time; preload and screens share it instead of racing separate loads. */
        private val sharedLoad = com.fffemotes.skintools.ads.policy.SharedLoad<Boolean>()
    }

    /**
     * The cached native is empty. On-load natives load one for this slot now, in [format]
     * ("big" / "mid" / "mid2"); [missing] runs as before if they are preloaded or the load fails.
     */
    private fun loadNowOr(
        context: Activity,
        format: String,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout?,
        missing: () -> Unit,
    ) {
        if (AdLoadMode.preload(context, AdLoadMode.Format.NATIVE)) return missing()
        presentWithUnit(context, ScreenAdPlanner.nativeUnitId(context), format, layout, shimmer) { missing() }
    }

    private fun Activity.isActivityGone(): Boolean {
        if (isFinishing) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
    }

    /** [place]: the slot waiting for this load; "" for a warm-up preload. */
    fun fetchNativeAds(context: Activity, observer: NativeAdWatcher? = null, place: String = "") {
        val adsPreference = AdSettingsVault.getInstance(context)

        // On-load natives (AdLoadMode): nothing is warmed; each slot loads its own when shown.
        if (!AdLoadMode.preload(context, AdLoadMode.Format.NATIVE)) {
            observer?.onNativeAdFailed()
            return
        }

        if (!adsPreference.getBoolean("IsAdsON") || AdSlotKind.fromString(adsPreference.getString("IsAdType")) != AdSlotKind.GOOGLE) {
            observer?.onNativeAdFailed()
            return
        }

        if (!adsPreference.getBoolean("NativeAd")) {
            observer?.onNativeAdFailed()
            return
        }

        val adUnit = ScreenAdPlanner.nativeUnitId(context)
        if (adUnit.isEmpty()) {
            observer?.onNativeAdFailed()
            return
        }
        val starts = sharedLoad.join { ok ->
            if (context.isActivityGone()) return@join // each requester's own screen must still be alive
            if (ok) observer?.onNativeAdLoaded() else observer?.onNativeAdFailed()
        }
        if (!starts) return // a load is already running; this observer hears its result
        val events = object : AdEvents.Listener(AdEvents.NATIVE, place) {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                sharedLoad.finish(false)
                if (context.isActivityGone()) return
                Log.e(
                    "NativeAds",
                    "Google onAdFailedToLoad:nativeAd ${loadAdError.message}"
                )
                nativeAd = null
            }
        }
        val adLoader =
            AdLoader.Builder(context, adUnit)
                .forNativeAd { nativeAds ->

                    nativeAd?.destroy()
                    nativeAd = nativeAds
                    cachedEvents = events
                    cachedUnit = adUnit
                    AdEvents.send(AdEvents.NATIVE, "loaded", place)
                    // Waiters check their own activity; the cached ad is kept for the next screen either way.
                    sharedLoad.finish(true)

                    Log.e("NativeAds", "Google Load: nativeAd")
                }
                .withAdListener(events)
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

        AdEvents.send(AdEvents.NATIVE, "request", place)
        adLoader.loadAd(AdRequest.Builder().build())
    }

    /** The cached ad goes on screen here: its show, impression, click and paid events carry this [place]. */
    private fun shownCached(ad: NativeAd, label: String) {
        cachedEvents?.place = place
        AdEvents.send(AdEvents.NATIVE, "show", place)
        val unit = cachedUnit
        ad.setOnPaidEventListener { AdEvents.paid(it, label, unit, ad.responseInfo, place) }
    }

    fun presentWithUnit(
        context: Activity,
        unitId: String,
        format: String,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        fallback: () -> Unit,
    ) {
        val pref = AdSettingsVault.getInstance(context)
        val kind = when (format.lowercase()) {
            "bignative", "big_native", "big" -> 0
            "mediumnative", "mid_native", "mid" -> 1
            "mediumnative2", "mid_native_2", "mid2" -> 2
            else -> -1
        }
        if (unitId.isBlank() || kind < 0 || context.isActivityGone() || !hasNetwork(context) ||
            !pref.getBoolean("IsAdsON") || !pref.getBoolean("NativeAd") ||
            AdSlotKind.fromString(pref.getString("IsAdType")) != AdSlotKind.GOOGLE
        ) return fallback()

        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true
        AdEvents.send(AdEvents.NATIVE, "request", place)
        AdLoader.Builder(context, unitId)
            .forNativeAd { ad ->
                AdEvents.send(AdEvents.NATIVE, "loaded", place)
                if (context.isActivityGone() || !layout.isAttachedToWindow) {
                    ad.destroy()
                    return@forNativeAd
                }
                val root: View = when (kind) {
                    0 -> AdGmsNativeLargeBinding.inflate(context.layoutInflater)
                        .also { largeNativeLayout(ad, it, context) }.root
                    1 -> AdGmsNativeMediumBinding.inflate(context.layoutInflater)
                        .also { mediumNativeLayout(ad, it, context) }.root
                    else -> AdGmsNativeMediumAltBinding.inflate(context.layoutInflater)
                        .also { mediumNativeLayoutAlt(ad, it, context) }.root
                }
                ad.setOnPaidEventListener { AdEvents.paid(it, "native_unit", unitId, ad.responseInfo, place) }
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                layout.addView(root)
                AdEvents.send(AdEvents.NATIVE, "show", place)
            }
            .withAdListener(object : AdEvents.Listener(AdEvents.NATIVE, place) {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    if (context.isActivityGone()) return
                    Log.e("NativeAds", "slot unit $unitId failed: ${loadAdError.message} — shared native")
                    fallback()
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun presentLargeNative(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        isButtonTop: Boolean? = false,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPreference = AdSettingsVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPreference.getBoolean("IsAdsON")
            || !adsPreference.getBoolean("NativeAd")
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()

        when (AdSlotKind.fromString(adsPreference.getString("IsAdType"))) {
            AdSlotKind.GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post

                        if (nativeAd != null) {
                            val rootView: View = run {
                                val binding = AdGmsNativeLargeBinding.inflate(context.layoutInflater)
                                largeNativeLayout(nativeAd!!, binding, context)
                                binding.root
                                }

                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(rootView)

                            shownCached(nativeAd!!, "native_big")

                            nativeAd = null
                            fetchNativeAds(context)

                            return@post
                        }

                        loadNowOr(context, "big", layout, shimmer) {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false

                            HouseAdDirector(place).fetchHouseAd(
                                context,
                                layout,
                                HouseAdDirector.HouseAdStyle.BIG_NATIVE,
                                imageView
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("NativeAds", "Google BigNative crash", e)
                        AdKit.onError(e, "native.big")
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.GONE
                        layout.removeAllViews()
                        layout.visibility = View.GONE
                    }
                }
            }


            AdSlotKind.UNKNOWN, AdSlotKind.CUSTOM -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                HouseAdDirector(place).fetchHouseAd(
                    context,
                    layout,
                    HouseAdDirector.HouseAdStyle.BIG_NATIVE,
                    imageView
                )
            }
        }
    }

    fun presentFullNative(
        context: Activity,
        layout: FrameLayout,
        onClose: () -> Unit,
        shimmer: ShimmerFrameLayout? = null,
    ) {
        val adsPreference = AdSettingsVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPreference.getBoolean("IsAdsON")
            || !adsPreference.getBoolean("NativeAd")
        ) {
            layout.removeAllViews()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            onClose()
            return
        }

        shimmer?.startShimmer()
        shimmer?.isVisible = true

        if (AdSlotKind.fromString(adsPreference.getString("IsAdType")) != AdSlotKind.GOOGLE) {
            onClose()
            return
        }

        layout.post {
            try {
                if (context.isFinishing || context.isDestroyed) return@post

                if (nativeAd != null) {
                    val binding = AdGmsNativeFullpageBinding.inflate(context.layoutInflater)

                    isFullNativeShowing = true
                    fullpageNativeLayout(nativeAd!!, binding, context) {
                        isFullNativeShowing = false
                        onClose()
                    }

                    layout.removeAllViews()
                    shimmer?.stopShimmer()
                    shimmer?.isVisible = false
                    layout.addView(binding.root)

                    shownCached(nativeAd!!, "native_full")

                    nativeAd = null
                    fetchNativeAds(context)
                    return@post
                }

                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                // On-load natives: nothing was warmed, so load one now over the screen.
                if (!AdLoadMode.preload(context, AdLoadMode.Format.NATIVE)) {
                    presentFullNativeWithUnit(context, AdUnitIds.pickFullNative(context, null), onClose = onClose)
                } else {
                    onClose()
                }
            } catch (e: Exception) {
                Log.e("NativeAds", "Google FullNative crash", e)
                AdKit.onError(e, "native.full")
                onClose()
            }
        }
    }

    /**
     * Loads [unitId] now and shows it full screen. [onClose] runs once it is closed; [onMiss]
     * (null: [onClose]) if it fails to load or takes longer than ads.loader_wait_sec.
     */
    fun presentFullNativeWithUnit(
        activity: Activity,
        unitId: String,
        onMiss: (() -> Unit)? = null,
        onClose: () -> Unit,
    ) {
        var fired = false
        val done = { if (!fired) { fired = true; onClose() } }
        val miss = { if (!fired) { fired = true; (onMiss ?: onClose)() } }

        val pref = AdSettingsVault.getInstance(activity)
        if (!pref.getBoolean("IsAdsON") || !pref.getBoolean("NativeAd") || unitId.isBlank()) return miss()
        if (AdSlotKind.fromString(pref.getString("IsAdType")) != AdSlotKind.GOOGLE) return miss()
        if (activity.isFinishing || activity.isDestroyed) return miss()

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(android.graphics.Color.BLACK) // previous version: black
            elevation = 64f
            isClickable = true
        }
        content.addView(overlay)
        val dropOverlay = { (overlay.parent as? ViewGroup)?.removeView(overlay) }
        // A slow load gives up like an on-load interstitial does; a late ad is then dropped.
        val wait = AdWaits.main(activity)
        wait.start {
            if (fired) return@start
            Log.d("NativeAds", "FullNative unit $unitId timed out — skip")
            AdEvents.send(AdEvents.NATIVE, "timeout", place)
            dropOverlay()
            miss()
        }

        AdEvents.send(AdEvents.NATIVE, "request", place)
        AdLoader.Builder(activity, unitId)
            .forNativeAd { ad ->
                AdEvents.send(AdEvents.NATIVE, "loaded", place)
                if (!wait.arrived() || fired || activity.isFinishing || activity.isDestroyed) {
                    ad.destroy(); dropOverlay(); return@forNativeAd miss()
                }
                val binding = AdGmsNativeFullpageBinding.inflate(activity.layoutInflater)
                isFullNativeShowing = true
                fullpageNativeLayout(ad, binding, activity) {
                    isFullNativeShowing = false
                    ad.destroy()
                    dropOverlay()
                    done()
                }
                overlay.removeAllViews()
                overlay.addView(binding.root)
                AdEvents.send(AdEvents.NATIVE, "show", place)
                ad.setOnPaidEventListener { AdEvents.paid(it, "native_full_unit", unitId, ad.responseInfo, place) }
            }
            .withAdListener(object : AdEvents.Listener(AdEvents.NATIVE, place) {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                    if (!wait.arrived()) return
                    Log.e("NativeAds", "FullNative unit load failed: ${error.message}")
                    dropOverlay()
                    miss()
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun fullpageNativeLayout(
        nativeAd: NativeAd,
        binding: AdGmsNativeFullpageBinding,
        context: Activity,
        onClose: () -> Unit,
    ) {
        binding.apply {
            mainNativeadView.mediaView = adMedia
            mainNativeadView.headlineView = adHeadline
            mainNativeadView.bodyView = adBody
            mainNativeadView.callToActionView = adCallToAction
            mainNativeadView.iconView = adAppIcon
            mainNativeadView.advertiserView = adAdvertiser

            adHeadline.text = nativeAd.headline
            adMedia.mediaContent = nativeAd.mediaContent

            // Full-page native keeps its own layout colours (previous version applied no palette here).

            adBody.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                text = nativeAd.body
            }
            adAdvertiser.apply {
                visibility = if (nativeAd.advertiser == null) View.GONE else View.VISIBLE
                text = nativeAd.advertiser
            }
            adAppIcon.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                setImageDrawable(nativeAd.icon?.drawable)
            }
            adCallToAction.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                text = nativeAd.callToAction
            }

            adFullClose.setOnClickListener {
                Analytics.click("native_ad_close")
                AdEvents.send(AdEvents.NATIVE, "close", place)
                onClose()
            }

            mainNativeadView.setNativeAd(nativeAd)

            mainNativeadView.setTag(R.id.tag_native_ad, nativeAd)

            fullNativeContent.padSystemBars()
        }
    }

    private fun View.padSystemBars() {
        fun barsOf(insets: WindowInsetsCompat) = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )

        fun applyFromRoot() {
            val bars = ViewCompat.getRootWindowInsets(this)?.let(::barsOf) ?: return
            updatePadding(top = bars.top, bottom = bars.bottom)
        }

        if (isAttachedToWindow) applyFromRoot() else doOnAttach { applyFromRoot() }
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val bars = barsOf(insets)
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    private fun largeNativeLayout(
        nativeAd: NativeAd,
        binding: AdGmsNativeLargeBinding,
        context: Activity
    ) {
        binding.apply {
            binding.mainNativeadView.mediaView = adMedia
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadView.mediaView?.mediaContent = nativeAd.mediaContent

            val bgColor = AdSettingsVault.getInstance(context).getString("NativeBgColor")
            val btnColor = AdSettingsVault.getInstance(context).getString("NativebtnColor")
            val txtColor = AdSettingsVault.getInstance(context).getString("NativetxtColor")
            val btntxtColor = AdSettingsVault.getInstance(context).getString("NativebtntxtColor")

            AdFrameStyling.paintCardBackground(context, binding.mainNativeadView, bgColor)
            AdFrameStyling.paintCtaBackground(context, binding.mainNativeadView.callToActionView, btnColor)

            (binding.mainNativeadView.headlineView as TextView).setTextColor(AdFrameStyling.titleColor(context, txtColor))
            AdFrameStyling.paintDesignExtras(context, binding.mainNativeadView)
            (binding.mainNativeadView.bodyView as TextView).setTextColor(AdFrameStyling.bodyColor(context, txtColor))
            (adCallToAction as TextView).setTextColor(AdFrameStyling.ctaTextColor(context, btntxtColor))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)

            binding.mainNativeadView.setTag(R.id.tag_native_ad, nativeAd)
        }
    }

    fun presentMediumNative(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPref = AdSettingsVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context)
            || !adsPref.getBoolean("IsAdsON")
            || !adsPref.getBoolean("NativeAd")
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }


        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()
        layout.visible()

        when (AdSlotKind.fromString(adsPref.getString("IsAdType"))) {
            AdSlotKind.GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (nativeAd != null) {
                            val binding = AdGmsNativeMediumBinding.inflate(context.layoutInflater)
                            mediumNativeLayout(
                                nativeAd!!,
                                binding,
                                context
                            )

                            layout.removeAllViews()

                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(binding.root)

                            shownCached(nativeAd!!, "native_medium")

                            nativeAd = null
                            fetchNativeAds(context)
                            return@post
                        }

                        loadNowOr(context, "mid", layout, shimmer) {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            HouseAdDirector(place).fetchHouseAd(
                                context,
                                layout,
                                HouseAdDirector.HouseAdStyle.MID_NATIVE
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("MidNativeAds", "Google MidNative failed: ${e.message}")
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.GONE
                        layout.removeAllViews()
                        layout.visibility = View.GONE
                    }
                }
            }


            AdSlotKind.CUSTOM, AdSlotKind.UNKNOWN -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                HouseAdDirector(place).fetchHouseAd(
                    context,
                    layout,
                    HouseAdDirector.HouseAdStyle.MID_NATIVE
                )
            }
        }
    }

    private fun mediumNativeLayout(
        nativeAd: NativeAd,
        binding: AdGmsNativeMediumBinding,
        context: Activity
    ) {
        binding.apply {
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline

            val bgColor = AdSettingsVault.getInstance(context).getString("NativeBgColor")
            val btnColor = AdSettingsVault.getInstance(context).getString("NativebtnColor")
            val txtColor = AdSettingsVault.getInstance(context).getString("NativetxtColor")
            val btntxtColor = AdSettingsVault.getInstance(context).getString("NativebtntxtColor")

            AdFrameStyling.paintCardBackground(context, binding.mainNativeadView, bgColor)
            AdFrameStyling.paintCtaBackground(context, binding.mainNativeadView.callToActionView, btnColor)

            (binding.mainNativeadView.headlineView as TextView).setTextColor(AdFrameStyling.titleColor(context, txtColor))
            AdFrameStyling.paintDesignExtras(context, binding.mainNativeadView)
            (binding.mainNativeadView.bodyView as TextView).setTextColor(AdFrameStyling.bodyColor(context, txtColor))
            (adCallToAction as TextView).setTextColor(AdFrameStyling.ctaTextColor(context, btntxtColor))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)

            binding.mainNativeadView.setTag(R.id.tag_native_ad, nativeAd)
        }
    }

    fun View.visible() {
        this.visibility = View.VISIBLE
    }

    fun View.invisible() {
        this.visibility = View.GONE
    }

    fun presentMediumNativeAlt(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPref = AdSettingsVault.getInstance(context)

        if (context.isFinishing || context.isDestroyed) return

        if (!hasNetwork(context) || !adsPref.getBoolean("IsAdsON") || !adsPref.getBoolean("NativeAd")) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }


        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()
        layout.visible()
        when (AdSlotKind.fromString(adsPref.getString("IsAdType"))) {
            GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (Companion.nativeAd != null) {
                            val binding = AdGmsNativeMediumAltBinding.inflate(context.layoutInflater)
                            mediumNativeLayoutAlt(
                                Companion.nativeAd!!,
                                binding,
                                context
                            )

                            layout.removeAllViews()

                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(binding.root)

                            shownCached(nativeAd!!, "native_medium2")

                            nativeAd = null
                            fetchNativeAds(context)
                            return@post
                        }

                        loadNowOr(context, "mid2", layout, shimmer) {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            HouseAdDirector(place).fetchHouseAd(
                                context,
                                layout,
                                HouseAdDirector.HouseAdStyle.MID_NATIVE
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("MidNativeAds", "Google MidNative failed: ${e.message}")
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.GONE
                        layout.removeAllViews()
                        layout.visibility = View.GONE
                    }
                }
            }

            CUSTOM, UNKNOWN -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                HouseAdDirector(place).fetchHouseAd(
                    context,
                    layout,
                    HouseAdDirector.HouseAdStyle.MID_NATIVE
                )
            }

        }
    }

    private fun mediumNativeLayoutAlt(
        nativeAd: NativeAd,
        binding: AdGmsNativeMediumAltBinding,
        context: Activity
    ) {
        binding.apply {
            binding.mainNativeadView.mediaView = adMedia
            binding.mainNativeadView.headlineView = adHeadline
            binding.mainNativeadView.bodyView = adBody
            binding.mainNativeadView.callToActionView = adCallToAction
            binding.mainNativeadView.iconView = adAppIcon

            (binding.mainNativeadView.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadView.mediaView?.mediaContent = nativeAd.mediaContent

            val bgColor = AdSettingsVault.getInstance(context).getString("NativeBgColor")
            val btnColor = AdSettingsVault.getInstance(context).getString("NativebtnColor")
            val txtColor = AdSettingsVault.getInstance(context).getString("NativetxtColor")
            val btntxtColor = AdSettingsVault.getInstance(context).getString("NativebtntxtColor")

            AdFrameStyling.paintCardBackground(context, binding.mainNativeadView, bgColor)
            AdFrameStyling.paintCtaBackground(context, binding.mainNativeadView.callToActionView, btnColor)

            (binding.mainNativeadView.headlineView as TextView).setTextColor(AdFrameStyling.titleColor(context, txtColor))
            AdFrameStyling.paintDesignExtras(context, binding.mainNativeadView)
            (binding.mainNativeadView.bodyView as TextView).setTextColor(AdFrameStyling.bodyColor(context, txtColor))
            (adCallToAction as TextView).setTextColor(AdFrameStyling.ctaTextColor(context, btntxtColor))

            binding.mainNativeadView.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadView.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadView.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadView.callToActionView as TextView).text = nativeAd.callToAction
            }

            binding.mainNativeadView.setNativeAd(nativeAd)

            binding.mainNativeadView.setTag(R.id.tag_native_ad, nativeAd)
        }
    }
}
