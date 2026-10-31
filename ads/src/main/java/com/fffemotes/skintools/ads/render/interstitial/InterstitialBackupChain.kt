package com.fffemotes.skintools.ads.render.interstitial

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import com.fffemotes.skintools.ads.R
import com.fffemotes.skintools.ads.render.ResumeAdDirector
import com.fffemotes.skintools.ads.render.NativeAdRenderer
import com.fffemotes.skintools.ads.render.HouseAdDirector
import com.fffemotes.skintools.ads.render.RewardAdRenderer
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.policy.AdLoadMode
import com.fffemotes.skintools.ads.policy.AdUnitIds
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.host.AdEvents

object InterstitialBackupChain {
    private const val DEFAULT_ORDER = "full_native,custom"







    internal fun fullNative(
        activity: Activity,
        placement: String?,
        next: () -> Unit,
        done: () -> Unit,
        loadIfMissing: Boolean = false,
    ) {
        if (!NativeAdRenderer.holdsCachedAd()) {
            // On-load natives: load this step's full native now; a miss moves to the next step.
            if (!loadIfMissing && AdLoadMode.preload(activity, AdLoadMode.Format.NATIVE)) return next()
            return NativeAdRenderer().presentFullNativeWithUnit(
                activity, AdUnitIds.pickFullNative(activity, placement), onClose = done, onMiss = next,
            )
        }
        val overlay = addOverlay(activity, dark = true)
        NativeAdRenderer().presentFullNative(activity, overlay, onClose = {
            dropOverlay(activity, overlay)
            done()
        })
    }

    internal fun custom(activity: Activity, next: () -> Unit, done: () -> Unit) {
        if (!AdSettingsVault.getInstance(activity).getBoolean("IsCustomADS")) return next()
        val overlay = addOverlay(activity, dark = true)
        // The house ad gets its own slot: fetchHouseAd clears its container, which used to delete the close button.
        val slot = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        overlay.addView(slot)
        val place = AdEvents.place
        val close = attachDismissButton(overlay) {
            Analytics.click("custom_ad_close")
            AdEvents.send(AdEvents.CUSTOM, "close", place)
            dropOverlay(activity, overlay)
            done()
        }
        HouseAdDirector(place).fetchHouseAd(
            context = activity,
            container = slot,
            type = HouseAdDirector.HouseAdStyle.FULLSCREEN_NATIVE,
            onFail = {
                dropOverlay(activity, overlay)
                next()
            },
        )

        close.bringToFront()
    }



    private fun addOverlay(activity: Activity, dark: Boolean): FrameLayout {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            if (dark) setBackgroundColor(android.graphics.Color.BLACK) // previous version: black
            elevation = 64f
            isClickable = true
        }
        content.addView(overlay)
        return overlay
    }

    private fun dropOverlay(activity: Activity, overlay: FrameLayout) {
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private fun attachDismissButton(overlay: FrameLayout, onClick: () -> Unit): View {
        val density = overlay.resources.displayMetrics.density
        val size = (40 * density).toInt()
        val margin = (10 * density).toInt()
        val close = ImageView(overlay.context).apply {
            setImageResource(R.drawable.glyph_close_cross)
            setColorFilter(ContextCompat.getColor(context, R.color.on_ink))
            // Previous version geometry: top-end, 40dp from the top, 20dp from the edge.
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(margin, (40 * density).toInt(), (20 * density).toInt(), margin)
            }
            setPadding(margin / 2, margin / 2, margin / 2, margin / 2)
            elevation = 65f
            setOnClickListener { onClick() }
        }
        overlay.addView(close)
        return close
    }
}
