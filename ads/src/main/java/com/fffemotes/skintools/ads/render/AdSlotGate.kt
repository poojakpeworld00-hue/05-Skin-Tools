package com.fffemotes.skintools.ads.render

import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.BaseAdView
import com.fffemotes.skintools.ads.R

/**
 * Lets a screen close its ad slot while the slot is off screen (a launcher panel sliding away, a side menu on top).
 * A closed slot takes no new ad: a banner still loading is dropped when it arrives. Closing also destroys the
 * ad already in the slot, which removes Google's expanded collapsible popup (a separate window that would
 * otherwise stay over whatever is now in front).
 */
object AdSlotGate {
    fun isOpen(slot: View): Boolean = slot.getTag(R.id.tag_ad_slot_closed) != true

    fun open(slot: View) = slot.setTag(R.id.tag_ad_slot_closed, null)

    fun close(slot: ViewGroup) {
        slot.setTag(R.id.tag_ad_slot_closed, true)
        endLoad(slot)
        CollapsibleBackdrop.hideFor(slot.context)
        // Hide and detach first: Google's expanded popup is pinned to the banner view, and destroy() alone
        // only removes it ~1 s later (seen on device: popup lingering over the launcher home).
        val ads = adViewsIn(slot)
        ads.forEach { runCatching { it.visibility = View.GONE } }
        slot.visibility = View.GONE
        val natives = nativeAdsIn(slot)
        slot.removeAllViews()
        ads.forEach { runCatching { it.destroy() } }
        natives.forEach { runCatching { it.destroy() } }
    }

    /** Native ads bound in [view] (renderers tag each NativeAdView with its ad); destroyed with their slot. */
    private fun nativeAdsIn(view: View): List<com.google.android.gms.ads.nativead.NativeAd> {
        val own = (view.getTag(R.id.tag_native_ad) as? com.google.android.gms.ads.nativead.NativeAd)?.let(::listOf).orEmpty()
        return if (view is ViewGroup) own + (0 until view.childCount).flatMap { nativeAdsIn(view.getChildAt(it)) } else own
    }

    private fun adViewsIn(view: View): List<BaseAdView> = when (view) {
        is BaseAdView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { adViewsIn(view.getChildAt(it)) }
        else -> emptyList()
    }

    /**
     * Closes [slot] as soon as [activity] starts finishing (Back) or is destroyed, so an expanded ad's popup
     * (Google collapsible or a rich-media expandable creative) never outlives its screen. Registered once per slot.
     * Also pauses / resumes the slot's ads with the screen, as the banner guide recommends.
     */
    fun closeWithScreen(activity: android.app.Activity, slot: ViewGroup) {
        if (slot.getTag(R.id.tag_ad_slot_watched) == true) return
        slot.setTag(R.id.tag_ad_slot_watched, true)
        activity.application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(a: android.app.Activity) {
                if (a !== activity) return
                if (a.isFinishing) close(slot) else adViewsIn(slot).forEach { runCatching { it.pause() } }
            }
            override fun onActivityResumed(a: android.app.Activity) {
                if (a === activity) adViewsIn(slot).forEach { runCatching { it.resume() } }
            }
            override fun onActivityDestroyed(a: android.app.Activity) {
                if (a !== activity) return
                close(slot)
                a.application.unregisterActivityLifecycleCallbacks(this)
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) = Unit
            override fun onActivityStarted(a: android.app.Activity) = Unit
            override fun onActivityStopped(a: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) = Unit
        })
    }

    /** A load that never reported back is given up after this long, so the slot can't stay blocked. */
    const val STALE_LOAD_MS = 30_000L

    /** One load per slot at a time: a second request while one is loading would stack two creatives. */
    fun mayStartLoad(loadingSinceMs: Long?, nowMs: Long): Boolean =
        loadingSinceMs == null || nowMs - loadingSinceMs >= STALE_LOAD_MS

    /** Marks a load as started; false when the slot is already loading (the caller then does nothing). */
    fun beginLoad(slot: View): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!mayStartLoad(slot.getTag(R.id.tag_ad_slot_loading) as? Long, now)) return false
        slot.setTag(R.id.tag_ad_slot_loading, now)
        return true
    }

    fun endLoad(slot: View) = slot.setTag(R.id.tag_ad_slot_loading, null)

    /** Destroys (not just removes) every ad view in [view]: a removed but live banner keeps drawing its popup. */
    fun destroyAds(view: View) {
        if (view is BaseAdView) {
            runCatching { view.destroy() }
            return
        }
        if (view is ViewGroup) (0 until view.childCount).map(view::getChildAt).forEach(::destroyAds)
    }
}
