package com.fffemotes.skintools.ads.policy

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdSize
import org.json.JSONObject
import com.fffemotes.skintools.ads.BuildConfig
import com.fffemotes.skintools.ads.render.BannerRenderer
import com.fffemotes.skintools.ads.render.BannerSizing
import com.fffemotes.skintools.ads.render.BannerLifecycleHook
import com.fffemotes.skintools.ads.render.BannerStyle
import com.fffemotes.skintools.ads.render.NativeAdRenderer
import com.fffemotes.skintools.ads.render.NativeStripRenderer

object ScreenAdPlanner {
    private const val TAG = "ScreenAdPlanner"

    data class ScreenAdSpec(
        val show: Boolean,
        val type: String,
        val bannerId: String,
        val bannerType: String,
        val nativeId: String,
        val nativeType: String
    ) {
        val isNative: Boolean get() = type.equals("native", ignoreCase = true)
    }



    fun nativeUnitId(context: Context): String {
        val pref = AdSettingsVault.getInstance(context)
        val globalNative = pref.getString("googleNative").orEmpty()
        val screenWise = pref.getBoolean("screen_wise_ad")

        val result: String
        val source: String
        if (!screenWise) {
            result = globalNative
            source = "googleNative (screen_wise_ad=false)"
        } else {
            val root = runCatching {
                JSONObject(pref.getString("ScreenAds", "{}") ?: "{}")
            }.getOrNull()
            val entryNative = root?.optJSONObject("default")?.optString("native").orEmpty()
            if (entryNative.isNotBlank()) {
                result = entryNative
                source = "ScreenAds.default.native"
            } else {
                result = globalNative
                source = "googleNative (default.native blank/missing)"
            }
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "nativeUnitId() = " + result + "   <- " + source)
        return result
    }




}
