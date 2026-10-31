package com.fffemotes.skintools.ads

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.fffemotes.skintools.ads.config.AdNetwork
import com.fffemotes.skintools.ads.config.AdPalette
import com.fffemotes.skintools.ads.config.AdsConfig
import com.fffemotes.skintools.ads.entity.HouseAdCard
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.policy.AdUnitIds
import com.fffemotes.skintools.ads.render.HouseAdDirector
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes the engine's settings (AdSettingsVault keys) from an [AdsConfig]. Replaces MSG-06's
 * RC JSON → v1 blob → AdConfigParser path. Exit ads, slots and place options are read from the
 * config itself by [ExitAdRunner] / [BottomAdLoader] / [Ads]; only what the ported engine reads lives here.
 */
object AdsConfigApplier {
    /** [adsAllowed] = consent / premium gate on top of `ads.on`. */
    fun apply(context: Context, config: AdsConfig, adsAllowed: Boolean = true) {
        val vault = AdSettingsVault.getInstance(context)
        val units = config.units
        vault.update {
            // One editor: keys of the last config are removed and the new ones written in one apply().
            vault.clearManagedIn(this)
            putBoolean("IsAdsON", config.on && adsAllowed)
            putString("IsAdType", if (config.network == AdNetwork.GOOGLE) "Google" else "Custom")
            putInt("inter_load_timeout_sec", config.loaderWaitSec)

            putBoolean("InterAds", config.formatsOn.inter)
            putBoolean("NativeAd", config.formatsOn.native)
            putBoolean("BannerAdPresenter", config.formatsOn.banner)
            putBoolean("NativeBannerPresenter", config.formatsOn.nativeBanner)
            putBoolean("AppopenAds", config.formatsOn.appOpen)
            putBoolean("RewardedAds", config.formatsOn.rewarded)

            putBoolean("is_preload_ads", config.preload.inter)
            putBoolean("is_preload_native", config.preload.native)
            putBoolean("is_preload_app_open", config.preload.appOpen)
            putBoolean("is_preload_rewarded", config.preload.rewarded)

            putString("googleInter", units.inter)
            putString("googleS_Inter", units.interForSplash())
            putString("googleBackInter", units.interForBack())
            putString("googleAppopen", units.appOpen)
            putString("googleNative", units.native)
            putString("googleBanner", units.banner)
            putString("googleRewarded", units.rewarded)

            putBoolean("IsCustomADS", config.customAds.on)
            putString("CUSTOM_ADS", Gson().toJson(config.customAds.list.map {
                HouseAdCard(it.icon, it.title, it.description, it.bannerImage, it.buttonText)
            }))
            putString("DirectLink", JSONArray(config.links).toString())
            putString("link_open_in", config.linksOpenIn.key)
            putBoolean("IsBack", config.places["launcher.back_button"]?.exitAd?.on == true)
            // A launcher place's own `preload` (its interstitial over ads.preload), read by AdLoadMode.
            val placements = JSONObject()
            config.places.forEach { (key, place) ->
                (place.options["preload"] as? Boolean)?.let { placements.put(key, JSONObject().put("preload", it)) }
            }
            putString(AdUnitIds.PLACEMENTS_KEY, placements.toString())
        }
        HouseAdDirector.clearCache()
        writePalette(context, config)
    }

    /** Native colours of the current light / dark mode (`ads.colors`); called again before each slot. */
    fun writePalette(context: Context, config: AdsConfig) {
        val p: AdPalette = if (isDark(context)) config.colors.dark else config.colors.light
        AdSettingsVault.getInstance(context).update {
            putString("NativeBgColor", p.background)
            putString("NativebtnColor", p.button)
            putString("NativetxtColor", p.text)
            putString("NativebtntxtColor", p.buttonText)
        }
    }

    private fun isDark(context: Context): Boolean = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
