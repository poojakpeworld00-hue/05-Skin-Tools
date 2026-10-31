package com.fffemotes.skintools.ads.host

import android.app.Activity
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.render.NativeAdRenderer
import com.fffemotes.skintools.ads.render.NativeStripRenderer
import com.fffemotes.skintools.ads.render.ResumeAdDirector
import com.fffemotes.skintools.ads.render.RewardAdRenderer
import com.fffemotes.skintools.ads.render.interstitial.ChainInterstitial
import com.fffemotes.skintools.ads.render.interstitial.NavigationInterstitial

/** Warms every full-screen + slot ad so most places show instantly (spec §6 rule 1). Safe to call repeatedly. */
object AdPreload {
    fun all(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val vault = AdSettingsVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON")) return
        runCatching { ResumeAdDirector.loadAd(activity) }
        runCatching { NavigationInterstitial().fetchInterstitials(activity) }
        // Back-button inter has its own unit (ad_units.inter_back); MSG-06 warmed it in BackPressInterstitial.
        val back = vault.getString("googleBackInter").orEmpty()
        if (vault.getBoolean("IsBack") && back.isNotBlank()) runCatching { ChainInterstitial.preload(activity, back) }
        runCatching { RewardAdRenderer.preload(activity) }
        runCatching { if (!NativeAdRenderer.holdsCachedAd()) NativeAdRenderer().fetchNativeAds(activity) }
        runCatching { NativeStripRenderer().fetchNativeStripAds(activity) }
    }
}
