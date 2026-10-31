package com.fffemotes.skintools.master.data.remote.config

import com.fffemotes.skintools.ads.config.AdsConfig
import com.fffemotes.skintools.launcher.api.LauncherSettings

/** The resolved Remote Config for this user (audience + country already applied). Defaults = documented missing-key defaults. */
data class AppConfig(
    val track: Track = Track.A,
    val base: BaseConfig = BaseConfig(),
    val ads: AdsConfig = AdsConfig(),
    val onboarding: OnboardingConfig = OnboardingConfig(),
    /** Screen name ("permission_hub", "home", …) → permissions asked there, in order. */
    val permissions: Map<String, List<AppPermission>> = emptyMap(),
    val launcher: LauncherSettings = LauncherSettings(),
    val systemKeys: SystemKeys = SystemKeys(),
    val uninstallFlow: UninstallFlowConfig = UninstallFlowConfig(),
    val prompts: Prompts = Prompts(),
    val settingsRows: SettingsRows = SettingsRows(),
)

/** track_a = organic, track_b = paid. */
enum class Track(val key: String) { A("track_a"), B("track_b") }

data class BaseConfig(
    val privacyLink: String = "",
    val termsLink: String = "",
    val storeLink: String = "",
    val updateLink: String = "",
    val appUpdateShow: Boolean = false,
    val appUpdateForce: Boolean = false,
    val splashWaitSec: Int = 4,
    /** `no_internet_dialog`: offline, the app's screens show a "No internet" dialog until the connection is back. */
    val noInternetDialog: Boolean = true,
    val attribution: AttributionSpec = AttributionSpec(),
    val exclusiveLocations: ExclusiveLocationsSpec = ExclusiveLocationsSpec(),
    /** `rc_sync_hours`: Remote Config syncs at most once per this many hours, also while the app keeps running; 0 = on every app start only. */
    val rcSyncHours: Int = 1,
)

/** `base.exclusive_locations`: country / state / city names or codes whose users always get track_a; see [ExclusiveLocations]. */
data class ExclusiveLocationsSpec(
    /** false = no lookup and no override. */
    val on: Boolean = false,
    val endpoint: String = "",
    val list: List<String> = emptyList(),
)

/** `base.attribution`: how a new install is classed organic (track_a) or paid (track_b); see [InstallAttribution]. */
data class AttributionSpec(
    val gclidMinLength: Int = 20,
    /** gclid values used by Google's test / review installs: always organic. */
    val fakeGclids: List<String> = emptyList(),
    val googleIpCheck: Boolean = false,
    val ipEndpoint: String = "",
    val ipField: String = "asn_org",
    /** [ipField] values of Google's own network: such an install is a review / test device, so organic. */
    val ipMatch: List<String> = emptyList(),
)

data class OnboardingConfig(
    /** Code default for a first launch that never reached Remote Config: every FFF step, so onboarding isn't skipped. */
    val order: List<OnboardingScreen> = OnboardingScreen.entries.filter { it !in OnboardingScreen.EXTRA },
    val skipButton: Boolean = false,
    val defaultHomeSkipButton: Boolean = false,
    /** `default_home.auto_open_settings_sec`: after a cancelled request, seconds before the Settings page opens again by itself; 0 = never. */
    val defaultHomeAutoOpenSettingsSec: Int = 3,
)

/** Onboarding screen names exactly as they appear in Remote Config (FFF steps + the launcher step). */
enum class OnboardingScreen(val key: String) {
    // The FFF questions, as OB_1… in Remote Config (the reference app's names): OB_1 language, OB_2 gender, OB_3 age,
    // OB_4 fighter style, OB_5 fav content, OB_6 game mode, OB_7 weapon, OB_8 goal, OB_9 character, OB_10 tip type,
    // OB_11 custom rooms, OB_12 map, OB_13 daily tips.
    LANGUAGE("OB_1"), GENDER("OB_2"), AGE("OB_3"), STYLE("OB_4"), FAV("OB_5"), MODE("OB_6"), WEAPON("OB_7"),
    GOAL("OB_8"), CHAR("OB_9"), TIPS("OB_10"), ROOM("OB_11"), MAP("OB_12"), DAILY("OB_13"),

    /** Not in the default order: a welcome screen after the questions. */
    WELCOME("welcome"),

    /** Not in the default order: asks `permissions.permission_hub` on Allow. */
    PERMISSION_HUB("permission_hub"),

    /** The launcher step (set as default home). */
    DEFAULT_HOME("default_home");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }

        /** Steps outside the default order (as the reference app): only Remote Config's `onboarding.order` adds them. */
        val EXTRA = setOf(WELCOME, PERMISSION_HUB, DEFAULT_HOME)
    }
}

enum class AppPermission(val key: String) {
    CAMERA("camera"), PHOTOS("photos"), NOTIFICATION("notification"),
    CONTACTS("contacts"), PHONE("phone"), OVERLAY("overlay");

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
}

/** [DEFAULT] (`""`): the key does what Android normally does; onboarding doesn't react. */
enum class KeyAction(val key: String) { NEXT("next"), BACK("back"), RESET("reset"), DEFAULT("") }

/** Keys that can steer onboarding (`system_keys`). Recents' first press stays the Play Store jump (`launcher.play_store_return`). */
enum class SystemKey(val key: String) { HOME("home"), BACK("back"), RECENTS("recents") }

data class SystemKeys(
    val home: KeyAction = KeyAction.NEXT,
    val back: KeyAction = KeyAction.NEXT,
    /** null (key missing): Recents does nothing and the screen stays, as before the key existed. */
    val recents: KeyAction? = null,
    val sheetOn: Boolean = false,
    val sheetKeys: Set<SystemKey> = emptySet(),
) {
    /** True when [key] opens the onboarding bottom sheet (`system_keys.bottom_sheet`) before doing its action. */
    fun opensSheet(key: SystemKey) = sheetOn && key in sheetKeys
    fun actionFor(key: SystemKey): KeyAction? = when (key) { SystemKey.HOME -> home; SystemKey.BACK -> back; SystemKey.RECENTS -> recents }
}

/**
 * `uninstall_flow`. [premium] (`flow: "premium"`): the long-press menu on our icon offers Uninstall, which opens our flow —
 * paid: splash → language → steps, ends by hiding our icon; organic: starts on step 1, ends on App info.
 * Normal (`flow: "normal"`, or any other value): the menu offers only Hide.
 */
data class UninstallFlowConfig(
    val premium: Boolean = false,
    /** `system_dialog` (premium only): Uninstall opens an Android-style uninstall dialog instead of the steps. */
    val systemDialog: Boolean = false,
    val splashMs: Long = 3000,
    val progressMs: Long = 4000,
)

data class Prompts(val rateAfterOpens: Int = 10, val rateRetryAfterHours: Int = 24)

/** `settings`: which About rows Settings shows (per track, so organic and paid users can differ; countries can overlay it). */
data class SettingsRows(val share: Boolean = true, val rate: Boolean = true, val feedback: Boolean = true, val update: Boolean = true)
