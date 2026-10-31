package com.fffemotes.skintools.launcher.api

/** Typed form of the Remote Config `launcher` block (plus drawer sponsored tiles). Defaults = documented missing-key defaults. */
data class LauncherSettings(
    val on: Boolean = false,
    val osStyle: Boolean = false,
    val defaultHomeBanner: Boolean = false,
    val iconInDrawer: Boolean = true,
    val iconOnHome: Boolean = true,
    val hostAppPanel: Boolean = true,
    val suggestedPanel: Boolean = false,
    val swipeGuide: SwipeGuide = SwipeGuide(),
    val timeWidget: Boolean = true,
    /** "google" or "chrome". */
    val searchWidget: String = "google",
    val sponsoredApps: SponsoredApps = SponsoredApps(),
    /** The long-press menu on the host's own icon offers Uninstall (the host's uninstall flow); false = Hide only. */
    val hostUninstall: Boolean = false,
    /** `ads.launcher.app_drawer.bottom_ad_row`: app rows above the drawer ad (0 = top). */
    val drawerAdRow: Int = 1,
    /** Raw `home_profile_rules` / `home_profile_probes` Remote Config values (device grid tables); "" = built-in. */
    val homeProfileRulesJson: String = "",
    val homeProfileProbesJson: String = "",
)

data class SwipeGuide(
    val on: Boolean = false,
    val swipeRightHostApp: Boolean = true,
    val swipeLeftSuggestedPanel: Boolean = true,
    val swipeUpDrawer: Boolean = true,
)

data class SponsoredApps(val on: Boolean = false, val every: Int = 8, val list: List<SponsoredApp> = emptyList())

data class SponsoredApp(val logo: String, val landingUrl: String, val label: String)
