package com.fffemotes.skintools.ads.render

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.fffemotes.skintools.ads.R
import kotlin.math.abs

/**
 * White backdrop under an expanded collapsible banner, on any screen.
 *
 * Google draws the expanded ad in its own PopupWindow, and parts of it are see-through (the screen behind shows
 * through, which reads as two ads). While the ad is expanded we put a plain white view in OUR window, exactly
 * under the popup, so those parts show white. Google's ad itself is not touched.
 *
 * Expanded / collapsed come from the banner's AdListener (onAdOpened / onAdClosed, verified on device). The popup's
 * bounds come from the public WindowInspector API (Android 10+); below that nothing is added.
 */
object CollapsibleBackdrop {
    private const val FIND_TRIES = 12
    private const val FIND_STEP_MS = 50L
    private const val BOTTOM_SLACK_PX = 24

    /** Backdrop top and height in our decor's coordinates, or null when the popup has no height. */
    fun placement(popupTopOnScreen: Int, popupBottomOnScreen: Int, decorTopOnScreen: Int): Pair<Int, Int>? {
        val height = popupBottomOnScreen - popupTopOnScreen
        if (height <= 0) return null
        return (popupTopOnScreen - decorTopOnScreen) to height
    }

    fun show(activity: Activity, banner: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        find(activity, banner, FIND_TRIES)
    }

    fun hide(activity: Activity?) {
        val decor = activity?.window?.decorView as? ViewGroup ?: return
        (decor.getTag(R.id.tag_collapsible_backdrop) as? View)?.let { decor.removeView(it) }
        decor.setTag(R.id.tag_collapsible_backdrop, null)
    }

    fun hideFor(context: Context) = hide(context.findActivity())

    private fun find(activity: Activity, banner: View, triesLeft: Int) {
        if (activity.isFinishing || activity.isDestroyed || !banner.isAttachedToWindow) return
        val popup = expandedPopup(banner)
        if (popup == null) {
            if (triesLeft > 0) banner.postDelayed({ find(activity, banner, triesLeft - 1) }, FIND_STEP_MS)
            return
        }
        val decor = activity.window.decorView as? FrameLayout ?: return
        val p = IntArray(2).also(popup::getLocationOnScreen)
        val d = IntArray(2).also(decor::getLocationOnScreen)
        val (top, height) = placement(p[1], p[1] + popup.height, d[1]) ?: return
        hide(activity)
        val backdrop = View(activity).apply {
            setBackgroundColor(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        decor.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply { topMargin = top })
        decor.setTag(R.id.tag_collapsible_backdrop, backdrop)
    }

    /** Google's expanded popup: a PopupWindow root in our process whose bottom sits on the banner's bottom. */
    private fun expandedPopup(banner: View): View? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val b = IntArray(2).also(banner::getLocationOnScreen)
        val bannerBottom = b[1] + banner.height
        return runCatching { android.view.inspector.WindowInspector.getGlobalWindowViews() }.getOrNull().orEmpty()
            .filter { it.isAttachedToWindow && it.javaClass.name.contains("PopupDecorView") && it.height > banner.height }
            .firstOrNull { v ->
                val loc = IntArray(2).also(v::getLocationOnScreen)
                abs(loc[1] + v.height - bannerBottom) <= BOTTOM_SLACK_PX
            }
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
