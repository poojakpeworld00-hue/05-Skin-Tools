package com.fffemotes.skintools.ads

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.fffemotes.skintools.ads.config.AdNetwork
import com.fffemotes.skintools.ads.config.Slot
import com.fffemotes.skintools.ads.config.SlotType
import com.fffemotes.skintools.ads.host.AdEvents
import com.fffemotes.skintools.ads.policy.AdWaits
import com.fffemotes.skintools.ads.render.AdSlotGate
import com.fffemotes.skintools.ads.render.BannerLifecycleHook
import com.fffemotes.skintools.ads.render.BannerRenderer
import com.fffemotes.skintools.ads.render.BannerSizing
import com.fffemotes.skintools.ads.render.BannerStyle
import com.fffemotes.skintools.ads.render.HouseAdDirector
import com.fffemotes.skintools.ads.render.NativeAdRenderer
import com.fffemotes.skintools.ads.render.NativeStripRenderer
import com.fffemotes.skintools.ads.render.AdFrameStyling
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * An on-screen slot (`bottom_ad` / `list_ad`): shimmer while loading, then the slot's [SlotType] on the ported
 * renderers. A failed banner shows the slot's `on_fail` type instead (when that format is on in `formats_on`).
 * Fail or no ad within `loader_wait_sec` → a custom-ad card when `custom_ads.on`, else the container
 * collapses. `slot.on` false → collapse, no request.
 */
object BottomAdLoader {
    fun load(activity: Activity, container: FrameLayout, slot: Slot, placeKey: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val config = AdKit.config
        AdSlotGate.open(container)
        // Banners and natives in this slot are paused / destroyed with the screen.
        AdSlotGate.closeWithScreen(activity, container)
        container.setOnHierarchyChangeListener(null)
        if (!AdKit.adsOn() || !slot.on) return collapse(container)
        edgeToEdge(container, isBanner(slot.type))
        AdsConfigApplier.writePalette(activity, config)

        val customOn = config.customAds.on && config.customAds.list.isNotEmpty()
        if (config.network == AdNetwork.CUSTOM || !formatOn(slot.type)) {
            return if (customOn) showCustom(activity, container, slot.type, placeKey) else collapse(container)
        }

        val shimmer = shimmerFor(activity, slot.type)
        container.removeAllViews()
        container.minimumHeight = 0
        container.visibility = View.VISIBLE
        container.addView(shimmer)
        shimmer.startShimmer()

        val wait = AdWaits.main(activity)
        container.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                if (child !== shimmer) wait.arrived()
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        wait.start {
            AdEvents.send(if (isBanner(slot.type)) AdEvents.BANNER else AdEvents.NATIVE, "timeout", placeKey)
            if (activity.isFinishing || activity.isDestroyed) return@start
            AdSlotGate.close(container) // a late banner is dropped, not stacked over the card
            AdSlotGate.open(container)
            if (customOn) showCustom(activity, container, slot.type, placeKey) else collapse(container)
        }
        val onFail = slot.onFail?.takeIf { isBanner(slot.type) && !isBanner(it) && formatOn(it) }
        render(activity, container, shimmer, slot.type, placeKey, onFail) { !wait.timedOut }
    }

    /** A banner fills the slot's width with no margin; other types keep the margins the layout gave the slot. */
    private fun edgeToEdge(container: FrameLayout, on: Boolean) {
        val lp = container.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val layout = container.getTag(R.id.tag_ad_slot_margins) as? IntArray
            ?: intArrayOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin).also { container.setTag(R.id.tag_ad_slot_margins, it) }
        if (on) lp.setMargins(0, layout[1], 0, 0) else lp.setMargins(layout[0], layout[1], layout[2], layout[3])
        container.layoutParams = lp
    }

    private fun isBanner(type: SlotType) =
        type == SlotType.BANNER || type == SlotType.BANNER_INLINE || type == SlotType.BANNER_COLLAPSIBLE

    private fun formatOn(type: SlotType) = AdKit.config.formatsOn.let {
        when {
            isBanner(type) -> it.banner
            type == SlotType.NATIVE_BANNER -> it.nativeBanner
            else -> it.native
        }
    }

    private fun render(
        activity: Activity,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout,
        type: SlotType,
        placeKey: String,
        /** A failed banner renders this type instead of the house-ad fallback. */
        onFail: SlotType?,
        stillWanted: () -> Boolean,
    ) {
        when (type) {
            SlotType.BANNER, SlotType.BANNER_INLINE, SlotType.BANNER_COLLAPSIBLE -> BannerRenderer(placeKey).presentBanner(
                activity = activity,
                container = container,
                type = BannerStyle.AUTO,
                size = if (type == SlotType.BANNER_INLINE) BannerSizing.INLINE else BannerSizing.ADAPTIVE,
                isCollapsable = type == SlotType.BANNER_COLLAPSIBLE,
                shimmer = shimmer,
                observer = onFail?.let { next ->
                    object : BannerLifecycleHook {
                        override fun onAdFailed() {
                            if (!stillWanted() || activity.isFinishing || activity.isDestroyed) return
                            shimmer.layoutParams = shimmerFor(activity, next).layoutParams
                            edgeToEdge(container, false)
                            render(activity, container, shimmer, next, placeKey, null, stillWanted)
                        }
                    }
                },
                disableInternalFallback = onFail != null,
            )
            SlotType.NATIVE_BANNER -> NativeStripRenderer(placeKey).presentNativeStrip(activity, container, shimmer)
            else -> {
                val native = NativeAdRenderer(placeKey)
                val paint: () -> Unit = {
                    if (stillWanted() && !activity.isFinishing && !activity.isDestroyed) when (type) {
                        SlotType.NATIVE_BIG -> native.presentLargeNative(activity, container, shimmer)
                        SlotType.NATIVE_MID -> native.presentMediumNative(activity, container, shimmer)
                        SlotType.NATIVE_MID2 -> native.presentMediumNativeAlt(activity, container, shimmer)
                        else -> native.presentFullNative(activity, container, onClose = { collapse(container) }, shimmer)
                    }
                }
                // MSG-06 ScreenAdPlanner.presentNative: no native cached yet → load one, render when it arrives.
                if (NativeAdRenderer.holdsCachedAd()) paint() else native.fetchNativeAds(
                    activity,
                    object : NativeAdRenderer.NativeAdWatcher {
                        override fun onNativeAdLoaded() = paint()
                        override fun onNativeAdFailed() = paint()
                    },
                    placeKey,
                )
            }
        }
    }

    private fun showCustom(activity: Activity, container: FrameLayout, type: SlotType, placeKey: String) {
        val style = when (type) {
            SlotType.NATIVE_BIG -> HouseAdDirector.HouseAdStyle.BIG_NATIVE
            SlotType.NATIVE_MID, SlotType.NATIVE_MID2 -> HouseAdDirector.HouseAdStyle.MID_NATIVE
            SlotType.NATIVE_FULL -> HouseAdDirector.HouseAdStyle.FULLSCREEN_NATIVE
            else -> HouseAdDirector.HouseAdStyle.BANNER
        }
        HouseAdDirector(placeKey).fetchHouseAd(activity, container, style)
    }

    private fun collapse(container: FrameLayout) {
        AdSlotGate.destroyAds(container)
        container.removeAllViews()
        container.minimumHeight = 0
        container.visibility = View.GONE
    }

    private fun shimmerFor(activity: Activity, type: SlotType): ShimmerFrameLayout {
        val dp = activity.resources.displayMetrics.density
        val heightDp = when (type) {
            SlotType.BANNER, SlotType.BANNER_INLINE, SlotType.BANNER_COLLAPSIBLE -> 54
            SlotType.NATIVE_BANNER -> 74
            SlotType.NATIVE_MID, SlotType.NATIVE_MID2 -> 150
            SlotType.NATIVE_BIG -> 242 // design sizes + the 4dp / 8dp dock spacing around natives
            SlotType.NATIVE_FULL -> 480
        }
        val block = View(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = AdFrameStyling.sdp(activity, 16f)
                setColor(Color.parseColor("#1F888888"))
            }
        }
        return ShimmerFrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AdFrameStyling.sdp(activity, heightDp.toFloat()).toInt())
            addView(block, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}
