package com.fffemotes.skintools.ads.host

import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.config.AdStep
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo

/**
 * The `ad` and `ad_impression` events of every renderer, both sent through [Analytics].
 * `ad_place` is the RC place key the ad runs for: [place] while an exit_ad runs ([com.fffemotes.skintools.ads.ExitAdRunner]),
 * the slot's key for bottom / list slots, and left out ("") for background preloads.
 */
object AdEvents {
    const val BANNER = "banner"
    const val NATIVE = "native"
    const val INTER = "inter"
    const val REWARDED = "rewarded"
    const val APP_OPEN = "app_open"
    const val CUSTOM = "custom"

    /** Place key of the exit_ad run in progress; "" outside one. */
    @Volatile var place = ""

    fun send(format: String, step: String, place: String, errorCode: Int? = null) = Analytics.log(
        Analytics.AD,
        "screen" to Screens.current,
        "ad_place" to place.ifEmpty { null },
        "format" to format,
        "step" to step,
        "error_code" to errorCode,
    )

    fun loadFailed(format: String, code: Int, place: String) = send(format, failStep(code), place, code)

    /** AdMob error code 3 (ERROR_CODE_NO_FILL) is `no_fill`, any other code `failed`. */
    fun failStep(code: Int) = if (code == 3) "no_fill" else "failed"

    /** AdMob paid event → `ad_impression`. [label] = engine placement (banner, native_big, inter_chain, app_open_resume…). */
    fun paid(value: AdValue, label: String, unitId: String?, info: ResponseInfo?, place: String) = Analytics.log(
        Analytics.AD_IMPRESSION,
        "ad_platform" to "admob",
        "ad_source" to (runCatching { info?.loadedAdapterResponseInfo?.adSourceName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"),
        "ad_format" to standardFormat(label),
        "ad_unit_name" to (unitId?.takeIf { it.isNotBlank() } ?: "unknown"),
        "value" to value.valueMicros / 1_000_000.0,
        "currency" to (value.currencyCode.takeUnless { it.isNullOrBlank() } ?: "USD"),
        "ad_placement" to label,
        "ad_screen" to Screens.current,
        "user_segment" to Analytics.segment(),
        "ad_place" to place.ifEmpty { null },
    )

    /** Engine label → Firebase ad_format (banner / native / interstitial / rewarded / app_open). */
    fun standardFormat(label: String): String = when {
        label.startsWith("app_open") -> "app_open"
        label.startsWith("rewarded") -> "rewarded"
        label.startsWith("inter") -> "interstitial"
        label.startsWith("native") -> "native"
        label.startsWith("banner") -> "banner"
        else -> label
    }

    /** `format` of an exit_ad step. */
    fun formatOf(step: AdStep): String = when (step) {
        AdStep.INTER -> INTER
        AdStep.REWARDED -> REWARDED
        AdStep.APP_OPEN -> APP_OPEN
        AdStep.FULL_NATIVE -> NATIVE
        AdStep.CUSTOM, AdStep.LINK -> CUSTOM
    }

    /** Banner / native AdListener: failed or no_fill, impression, click. Overrides must call super. */
    open class Listener(private val format: String, var place: String) : AdListener() {
        override fun onAdFailedToLoad(error: LoadAdError) = loadFailed(format, error.code, place)
        override fun onAdImpression() = send(format, "impression", place)
        override fun onAdClicked() = send(format, "click", place)
    }

    /** Full-screen callback: show, show_failed, impression, click, close. Overrides must call super. */
    open class FullScreen(private val format: String, private val place: String) : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = send(format, "show", place)
        override fun onAdFailedToShowFullScreenContent(error: AdError) = send(format, "show_failed", place, error.code)
        override fun onAdImpression() = send(format, "impression", place)
        override fun onAdClicked() = send(format, "click", place)
        override fun onAdDismissedFullScreenContent() = send(format, "close", place)
    }
}
