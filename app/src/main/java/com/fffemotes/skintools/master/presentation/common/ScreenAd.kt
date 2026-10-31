package com.fffemotes.skintools.master.presentation.common

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.fffemotes.skintools.ads.AdKit
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.config.SlotType

/**
 * A screen's `bottom_ad`, placed as in the reference app: a big native (native_big / native_mid / native_mid2 / native_full)
 * sits in the scrolling content ([inScroll]); a banner or native banner stays pinned at the bottom ([pinned]).
 */
/**
 * A place's `list_ad`: first after `list_ad_after` items, then again every `list_ad_every` items (0 = once).
 * [load] fills one ad container; each ad row gets its own.
 */
class ListAd(val after: Int, val every: Int, val load: (FrameLayout) -> Unit)

/** The place's `list_ad` when RC has it on, else null (the list shows no ad row). */
fun Activity.listAd(place: String): ListAd? {
    val p = AdKit.config.places[place] ?: return null
    if (p.listAd?.on != true) return null
    return ListAd(p.int("list_ad_after").coerceAtLeast(0), p.int("list_ad_every").coerceAtLeast(0)) { Ads.list(this, it, place) }
}

/** Puts one ad into a vertical [rows] container: at [at], by default after its `list_ad_after` rows. */
fun ListAd.insertInto(rows: android.widget.LinearLayout, topMargin: Int, at: Int = after) {
    val view = FrameLayout(rows.context).also(load)
    rows.addView(view, at.coerceIn(0, rows.childCount), android.widget.LinearLayout.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { this.topMargin = topMargin })
}

fun Activity.bindScreenAd(place: String, inScroll: FrameLayout, pinned: FrameLayout) {
    val type = AdKit.config.places[place]?.bottomAd?.type ?: SlotType.BANNER
    val scrolling = type.key.startsWith("native") && type != SlotType.NATIVE_BANNER
    val (use, other) = if (scrolling) inScroll to pinned else pinned to inScroll
    other.removeAllViews()
    other.visibility = View.GONE
    Ads.bottom(this, use, place)
}
