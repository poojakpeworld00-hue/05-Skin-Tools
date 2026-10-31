package com.fffemotes.skintools.master.data.remote.config

import com.fffemotes.skintools.ads.config.AdColors
import com.fffemotes.skintools.ads.config.AdNetwork
import com.fffemotes.skintools.ads.config.AdPalette
import com.fffemotes.skintools.ads.config.AdPlace
import com.fffemotes.skintools.ads.config.AdStep
import com.fffemotes.skintools.ads.config.AdUnits
import com.fffemotes.skintools.ads.config.AdsConfig
import com.fffemotes.skintools.ads.config.CustomAdCard
import com.fffemotes.skintools.ads.config.CustomAds
import com.fffemotes.skintools.ads.config.ExitAd
import com.fffemotes.skintools.ads.config.FallbackStep
import com.fffemotes.skintools.ads.config.FormatFlags
import com.fffemotes.skintools.ads.config.FormatsOn
import com.fffemotes.skintools.ads.config.LinksOpenIn
import com.fffemotes.skintools.ads.config.Slot
import com.fffemotes.skintools.ads.config.SlotType
import com.fffemotes.skintools.launcher.api.LauncherSettings
import com.fffemotes.skintools.launcher.api.SponsoredApp
import com.fffemotes.skintools.launcher.api.SponsoredApps
import com.fffemotes.skintools.launcher.api.SwipeGuide
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote Config JSON → [AppConfig]. Pure (org.json only) so it runs in JVM tests.
 * Merge: `track_x` → its `countries[CC]` (objects key by key, lists/scalars replaced); `base` only from the root.
 * Missing key, wrong type or unknown value → the documented default; unknown names in lists are dropped.
 */
object AppConfigParser {

    private val AD_GROUPS = listOf("screens", "onboarding", "launcher", "uninstall_flow")
    private val SLOT_KEYS = setOf("bottom_ad", "list_ad", "exit_ad")
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    /** @throws org.json.JSONException when [json] is not a JSON object (callers keep their previous config). */
    fun parse(json: String, resolvedTrack: Track, countryCode: String, location: Location? = null): AppConfig {
        val root = JSONObject(json)
        val base = root.obj("base")
        // base.exclusive_locations (when on): a listed country / state / city gets the organic config, paid or not.
        val excl = base.obj("exclusive_locations")
        val list = if (excl.bool("on", false)) excl.arr("list").strings() else emptyList()
        val track = if (LocationRules.matches(list, location)) Track.A else resolvedTrack
        val t = root.obj(track.key) ?: JSONObject()
        val cc = countryCode.trim().uppercase()
        t.obj("countries")?.let { countries ->
            for (key in countries.keys()) {
                val overlay = countries.obj(key) ?: continue
                if (cc.isNotEmpty() && key.split(",").any { it.trim().uppercase() == cc }) t.mergeIn(overlay)
            }
        }
        val ads = t.obj("ads")
        val launcher = t.obj("launcher")
        return AppConfig(
            track = track,
            base = base(base),
            ads = ads(ads, base),
            onboarding = onboarding(t.obj("onboarding")),
            permissions = permissions(t.obj("permissions")),
            launcher = launcher(launcher, ads.obj("launcher")),
            systemKeys = systemKeys(launcher.obj("system_keys")),
            uninstallFlow = uninstallFlow(t.obj("uninstall_flow")),
            prompts = t.obj("prompts").obj("rate_us").let {
                Prompts(rateAfterOpens = it.int("after_opens", 10), rateRetryAfterHours = it.int("retry_after_hours", 24))
            },
            settingsRows = t.obj("settings").let {
                SettingsRows(
                    share = it.bool("share_app", true), rate = it.bool("rate_app", true),
                    feedback = it.bool("feedback", true), update = it.bool("update_app", true),
                )
            },
        )
    }

    private fun base(b: JSONObject?) = BaseConfig(
        privacyLink = b.str("privacy_link"),
        termsLink = b.str("terms_link"),
        storeLink = b.str("store_link"),
        updateLink = b.str("update_link"),
        appUpdateShow = b.obj("app_update").bool("show", false),
        appUpdateForce = b.obj("app_update").bool("force", false),
        splashWaitSec = b.int("splash_wait_sec", 4),
        noInternetDialog = b.bool("no_internet_dialog", true),
        rcSyncHours = b.int("rc_sync_hours", 1),
        attribution = b.obj("attribution").let { a ->
            val ip = a.obj("google_ip_check")
            AttributionSpec(
                gclidMinLength = a.int("gclid_min_length", 20, min = 1),
                fakeGclids = a.arr("fake_gclids").strings(),
                googleIpCheck = ip.bool("on", false),
                ipEndpoint = ip.str("endpoint"),
                ipField = ip.str("field").ifBlank { "asn_org" },
                ipMatch = ip.arr("match").strings(),
            )
        },
        exclusiveLocations = b.obj("exclusive_locations").let {
            ExclusiveLocationsSpec(on = it.bool("on", false), endpoint = it.str("endpoint"), list = it.arr("list").strings().map(String::trim).filter(String::isNotEmpty))
        },
    )

    private fun ads(a: JSONObject?, base: JSONObject?): AdsConfig {
        val preload = a.obj("preload")
        val formats = a.obj("formats_on")
        val units = base.obj("ad_units")
        val custom = a.obj("custom_ads")
        return AdsConfig(
            on = a.bool("on", false),
            loaderWaitSec = a.int("loader_wait_sec", 8),
            loaderOnMiss = a.bool("loader_on_miss", true),
            preload = FormatFlags(
                inter = preload.bool("inter", true),
                native = preload.bool("native", true),
                appOpen = preload.bool("app_open", true),
                rewarded = preload.bool("rewarded", true),
            ),
            formatsOn = FormatsOn(
                inter = formats.bool("inter", true),
                native = formats.bool("native", true),
                banner = formats.bool("banner", true),
                nativeBanner = formats.bool("native_banner", true),
                appOpen = formats.bool("app_open", true),
                rewarded = formats.bool("rewarded", true),
            ),
            ifAdNotReady = a.arr("if_ad_not_ready").strings().mapNotNull(::fallbackStep),
            links = a.arr("links").strings(),
            linksCount = a.int("links_count", 0),
            showAll = a.bool("show_all", true),
            linksOpenIn = LinksOpenIn.entries.firstOrNull { it.key == a.str("links_open_in") } ?: LinksOpenIn.IN_APP_BROWSER,
            customAds = CustomAds(
                on = custom.bool("on", false),
                list = custom.arr("list").objects().map {
                    CustomAdCard(
                        icon = it.str("icon"),
                        title = it.str("title"),
                        description = it.str("description"),
                        bannerImage = it.str("bannerImage"),
                        buttonText = it.str("buttonText"),
                    )
                },
            ),
            colors = a.obj("colors").let { AdColors(light = palette(it.obj("light")), dark = palette(it.obj("dark"))) },
            network = AdNetwork.entries.firstOrNull { it.key == base.str("ad_network") } ?: AdNetwork.GOOGLE,
            units = AdUnits(
                inter = units.str("inter"),
                interSplash = units.str("inter_splash"),
                interBack = units.str("inter_back"),
                appOpen = units.str("app_open"),
                native = units.str("native"),
                banner = units.str("banner"),
                rewarded = units.str("rewarded"),
            ),
            places = buildMap {
                for (group in AD_GROUPS) {
                    val places = a.obj(group) ?: continue
                    for (name in places.keys()) places.obj(name)?.let { put("$group.$name", place(it)) }
                }
            },
        )
    }

    internal fun fallbackStep(item: String): FallbackStep? = when {
        item == "link" -> FallbackStep.FirstLink
        item.startsWith("https://") || item.startsWith("http://") -> FallbackStep.Url(item)
        else -> AdStep.of(item)?.let { FallbackStep.Ad(it) }
    }

    private fun palette(p: JSONObject?) = AdPalette(
        button = p.hex("button"),
        buttonText = p.hex("button_text"),
        background = p.hex("background"),
        text = p.hex("text"),
    )

    private fun place(p: JSONObject) = AdPlace(
        bottomAd = p.obj("bottom_ad")?.let(::slot),
        listAd = p.obj("list_ad")?.let(::slot),
        exitAd = p.obj("exit_ad")?.let(::exitAd),
        options = p.keys().asSequence().filter { it !in SLOT_KEYS }.associateWith { raw(p.opt(it)) },
    )

    private fun slot(s: JSONObject) = Slot(
        on = s.bool("on", false),
        type = SlotType.of(s.str("type")) ?: SlotType.BANNER,
        onFail = SlotType.of(s.str("on_fail")),
    )

    private fun exitAd(e: JSONObject) = ExitAd(
        on = e.bool("on", false),
        ads = e.arr("ads").strings().mapNotNull(AdStep::of),
        linksCount = e.intOrNull("links_count"),
        links = e.arr("links")?.strings(),
        showAll = e.opt("show_all") as? Boolean,
        every = e.int("every", 1, min = 1),
    )

    private fun onboarding(o: JSONObject?) = OnboardingConfig(
        order = o.arr("order")?.let { it.strings().mapNotNull(OnboardingScreen::of).distinct() } ?: OnboardingConfig().order,
        skipButton = o.bool("skip_button", false),
        defaultHomeSkipButton = o.obj("default_home").bool("skip_button", false),
        defaultHomeAutoOpenSettingsSec = o.obj("default_home").int("auto_open_settings_sec", 3),
    )

    private fun permissions(p: JSONObject?): Map<String, List<AppPermission>> = buildMap {
        p ?: return@buildMap
        for (screen in p.keys()) p.arr(screen)?.let { put(screen, it.strings().mapNotNull(AppPermission::of).distinct()) }
    }

    private fun launcher(l: JSONObject?, adPlaces: JSONObject?) = launcher(l, adPlaces.obj("drawer_sponsored_apps"), adPlaces.obj("app_drawer"))

    private fun launcher(l: JSONObject?, sponsored: JSONObject?, drawer: JSONObject?) = LauncherSettings(
        on = l.bool("on", false),
        osStyle = l.bool("os_style", false),
        defaultHomeBanner = l.bool("default_home_banner", false),
        iconInDrawer = l.obj("our_icon").bool("in_drawer", true),
        iconOnHome = l.obj("our_icon").bool("on_home", true),
        hostAppPanel = l.obj("panels").bool("host_app", true),
        suggestedPanel = l.obj("panels").bool("suggested_panel", false),
        swipeGuide = l.obj("swipe_guide").let {
            SwipeGuide(
                on = it.bool("on", false),
                swipeRightHostApp = it.bool("swipe_right_host_app", true),
                swipeLeftSuggestedPanel = it.bool("swipe_left_suggested_panel", true),
                swipeUpDrawer = it.bool("swipe_up_drawer", true),
            )
        },
        timeWidget = l.bool("time_widget", true),
        searchWidget = l.str("search_widget").takeIf { it == "google" || it == "chrome" } ?: "google",
        sponsoredApps = SponsoredApps(
            on = sponsored.bool("on", false),
            every = sponsored.int("every", 8, min = 1),
            list = sponsored.arr("list").objects()
                .map { SponsoredApp(logo = it.str("logo"), landingUrl = it.str("landing_url"), label = it.str("label")) }
                .filter { it.landingUrl.isNotBlank() },
        ),
        drawerAdRow = drawer.int("bottom_ad_row", 1),
    )

    private fun systemKeys(s: JSONObject?): SystemKeys {
        // Empty value = device default; a missing key stays `next`.
        fun action(key: String, allowed: Set<KeyAction>) =
            KeyAction.entries.firstOrNull { it.key == s.str(key) && it in allowed && (it != KeyAction.DEFAULT || s?.has(key) == true) }
                ?: KeyAction.NEXT
        val all = KeyAction.entries.toSet()
        val sheet = s.obj("bottom_sheet")
        return SystemKeys(
            home = action("home", all),
            back = action("back", setOf(KeyAction.NEXT, KeyAction.BACK, KeyAction.DEFAULT)),
            recents = if (s?.has("recents") == true) action("recents", all) else null,
            sheetOn = sheet.bool("on", false),
            sheetKeys = sheet.arr("keys").strings().mapNotNull { k -> SystemKey.entries.firstOrNull { it.key == k } }.toSet(),
        )
    }

    private fun uninstallFlow(u: JSONObject?) = UninstallFlowConfig(
        premium = u.str("flow") == "premium",
        systemDialog = u.bool("system_dialog", false),
        splashMs = u.int("splash_ms", 3000).toLong(),
        progressMs = u.int("progress_ms", 4000).toLong(),
    )

    // --- JSON helpers: anything of the wrong type reads as missing ---

    private fun JSONObject.mergeIn(over: JSONObject) {
        for (k in over.keys()) {
            val o = over.opt(k)
            val b = opt(k)
            if (o is JSONObject && b is JSONObject) b.mergeIn(o) else put(k, o)
        }
    }

    private fun JSONObject?.obj(k: String) = this?.opt(k) as? JSONObject
    private fun JSONObject?.arr(k: String) = this?.opt(k) as? JSONArray
    private fun JSONObject?.bool(k: String, d: Boolean) = this?.opt(k) as? Boolean ?: d
    private fun JSONObject?.str(k: String) = this?.opt(k) as? String ?: ""
    private fun JSONObject?.hex(k: String) = str(k).takeIf { HEX.matches(it) } ?: ""
    private fun JSONObject?.intOrNull(k: String, min: Int = 0) = (this?.opt(k) as? Number)?.toInt()?.takeIf { it >= min }
    private fun JSONObject?.int(k: String, d: Int, min: Int = 0) = intOrNull(k, min) ?: d

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it) as? String }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it) as? JSONObject }

    private fun raw(v: Any?): Any? = when {
        v == null || v == JSONObject.NULL -> null
        v is JSONObject -> v.keys().asSequence().associateWith { raw(v.opt(it)) }
        v is JSONArray -> (0 until v.length()).map { raw(v.opt(it)) }
        else -> v
    }
}
