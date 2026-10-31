# Ads Complete Mapping — reference `:ads` module and FFF target placements

Part A is the reference audit (read-only). Part B maps it onto FFF and is a **proposal for review**.

## Part A — Ads module audit — `179-Body-Scanner-Home-01` (`:ads`, package `com.appkit.ads`)

Read-only audit. Paths are relative to `/Users/pooja/Documents/Pooja_Apps/10_OCT/179-Body-Scanner-Home-01`.
Ads sources: `ads/src/main/java/com/appkit/ads/...` (abbreviated `ads:` below). App sources: `app/src/main/java/com/xray/fullbody/scanner/camera/...` (abbreviated `app:`).

---

### A1. Module overview

- 126 files in `ads/` (excluding `build/`): 54 Kotlin main files (8,022 lines incl. tests), 4 unit-test files, 65 resource files, `AndroidManifest.xml`, `build.gradle.kts`, `consumer-rules.pro`.
- Two layers:
  1. **New façade (used)**: `AdKit` (init / config / UMP), `Ads` (the only entry point screens call), `ExitAdRunner` + `ExitAdFlow` (the `exit_ad` sequence engine), `BottomAdLoader` (`bottom_ad` / `list_ad` slots), `AdsConfigApplier` (writes the typed `AdsConfig` into a SharedPreferences "vault"), `config/AdsConfig.kt` (typed RC model).
  2. **Ported "MSG-06 / QR" engine** (renderers that read the vault, not `AdsConfig`): `render/*`, `render/interstitial/*`, `policy/*`. A large part of it is legacy and unreachable (see §8).
- Data path: Firebase RC string key `app_config` (release) / `app_config_debug` (debug) → `app:config/AppConfigStore.kt` → `app:config/AppConfigParser.ads()` builds `AdsConfig` → `app:App.kt:71-75` collects `AppConfigStore.current.map { it.ads }.distinctUntilChanged()` and calls `AdKit.apply(ads)` → `AdsConfigApplier.apply()` rewrites vault prefs `qr_ads_config` → renderers read vault keys (`IsAdsON`, `googleInter`, …). Façade code (`Ads`, `ExitAdRunner`, `BottomAdLoader`, watchers) reads `AdKit.config` directly.
- Network: Google AdMob only (`AdNetwork.GOOGLE`) plus "custom" house ads (`custom_ads`) and direct links. No mediation adapters, no Facebook (`entity/AdSlotKind.kt` comment: "Facebook Audience Network is not part of this module").
- Ad unit IDs: never hard-coded in code (`grep -rn "ca-app-pub" ads app/src --include='*.kt'` → nothing). They come only from RC `base.ad_units.*`. The shipped `docs/remote-config/app_config.json` contains **Google sample test unit IDs** for every format.

---

### A2. File inventory

### A2.1 Kotlin (main)

| File | Role | Public API / key members |
|---|---|---|
| `AdKit.kt` | Module setup singleton | `config: AdsConfig` (default = ads off), hooks `isHomeScreen`, `noResumeAdOn`, `recentsAdScreenOn`, `onError`; `init(app)` registers `AdLifecycle`, `AppResumeWatcher`, `UnlockAdWatcher`, `ChargeEventWatcher`, `PackageEventWatcher`, `RecentAdWatcher`, starts MobileAds if UMP `canRequestAds()`, applies config; `apply(config)`; `requestConsent(activity,onDone)`; `privacyOptionsRequired()`; `showPrivacyOptions()`; `preload(activity)`; internal `adsOn()` = `config.on && consentAllows()`; private `consentAllows()` (no ads only when consent REQUIRED and not given); `startMobileAds()` (`MobileAds.initialize` on a new single-thread executor, once). |
| `Ads.kt` | Single entry point for every ad kind | Place-key consts (`SPLASH="screens.splash"`, `BACK_BUTTON`, `APP_RESUME`, `APP_OPEN`, `APP_EXIT`, `APP_DRAWER`, `SPONSORED_APP_OPEN/EXIT`, `PHONE_UNLOCK`, `CHARGER_CONNECTED/DISCONNECTED`, `APP_INSTALLED/UNINSTALLED`, `PLAY_STORE_RETURN`); `exit()`, `bottom()`, `list()`, `splash()`, `backButton()`, `keyAdDuringOnboarding()`, `recentsAdOn()`, `systemKey()`, `preload()`, `appResume()`, `markOwnLaunch()`, `clearOwnLaunch()`, `panelOpened()`, `drawerOpened()`, `appIconTapped()`, `returnedFromApp()`, `sponsoredTileTapped()`, `returnedFromSponsored()`, `phoneUnlocked()`, `chargerChanged()`, `packageChanged()`, `playStoreReturn()`; internal `exitOn()`, `placeOn()`; `RETURN_HANDLED_MS=3000`. |
| `AdsConfigApplier.kt` | AdsConfig → vault prefs | `apply(context, config, adsAllowed)` (clears managed keys and rewrites in one editor; see §5.3), `writePalette()` (light/dark native colours). |
| `ExitAdRunner.kt` | Runs a place's `exit_ad` on the engine | `busy()` (flow active OR inter showing OR app-open showing OR full native showing OR loader dialog up), `run(activity, placeKey, onDone)`; `SCREEN_GAP_PLACES`; per-place `gap_sec` persisted in prefs `adkit_exit_gap`; inner `FinishWatcher` (ends flow if activity destroyed) and `EngineSource` (maps `AdStep` → renderer; splash deadline; links). |
| `ExitAdFlow.kt` | Pure sequencing rules (unit-tested) | `interface ExitAdSource`, `class EveryCounter` (in-memory, per process), `class ExitAdFlow` (links/`link` step, ads in order, `if_ad_not_ready`, `show_all`), `formatOn()`, `object PlaceRules` (`exitRuns`, `gapPassed`, `countToday`, `unlockAllowed`, `withinWindow`). |
| `BottomAdLoader.kt` | `bottom_ad` / `list_ad` slot | `load(activity, container, slot, placeKey)`: shimmer, `AdWaits` timeout → custom card or collapse; dispatch to Banner / NativeStrip / Native renderers. |
| `config/AdsConfig.kt` | Typed RC model | `AdsConfig`, `FormatFlags`, `FormatsOn`, `LinksOpenIn`, `AdNetwork`, `AdUnits` (`interForSplash()`, `interForBack()` fall back to `inter`), `CustomAds`, `CustomAdCard`, `AdColors`, `AdPalette`, `AdPlace` (`bool()`, `int()`), `Slot`, `SlotType`, `ExitAd`, `AdStep`, `FallbackStep`; `linksFor()`, `showAllFor()`. |
| `analytics/Analytics.kt` | Firebase Analytics logger | 12 allowed events (`EVENTS`), `init()`, `log()`, `click()`, `keyPress()`, `permission()`, `step()`, `segment`; `EventRules.clean()`. |
| `analytics/DebugEventLog.kt` | Debug in-memory event list + ongoing notification | `enabled`, `ACTION_OPEN`, `revision`, `all()`, `clear()`, `add()`. |
| `analytics/Screens.kt` | Screen-visit timer (`screen` event) | `ownLeave`, `current`, `show()`, `exitBy()`, `install()` (uses `ProcessLifecycleOwner`); `ScreenVisits` pure class. |
| `entity/AdSlotKind.kt` | Network enum from vault `IsAdType` | `GOOGLE/CUSTOM/UNKNOWN`, `fromString()`. |
| `entity/HouseAdCard.kt` | Gson model of custom ad card | kept by `consumer-rules.pro`. |
| `events/FeatureFrameActivity.kt` | Base AppCompat activity for event screens | back → `Analytics.keyPress("back")` + `performBack()`; `Screens.show(screenName)` on resume. |
| `events/SystemDialogHelper.kt` | Lifecycle observer: closes event screen on Recents (`ACTION_CLOSE_SYSTEM_DIALOGS` reason `recentapps`) | used by Charging + PackageResult activities. |
| `events/charging/ChargeEventWatcher.kt` | Dynamic receiver `ACTION_POWER_CONNECTED/DISCONNECTED` → `ChargingStatusActivity` | gap per place in prefs `charge_event_watcher` (`last_<place>`), pending queue TTL 10 min. |
| `events/charging/ChargingEnergyView.kt` | Custom ring view (UI only) | — |
| `events/charging/ChargingStatusActivity.kt` | Battery screen (shows over lock, turns screen on) | `bottom_ad` at `R.id.chargingAdSlot` (line 149), `exit_ad` on close/back (line 156), screen `charging_event`. |
| `events/pkgresult/PackageEventWatcher.kt` | Dynamic receiver `PACKAGE_ADDED/REMOVED` → `PackageResultActivity` | shared `last_shown_at` gap for both places, pending TTL 10 min, `seedIfNeeded()`. |
| `events/pkgresult/PackageMetadataCache.kt` | Caches labels/icons of installed apps (for uninstall screen) | `remember/read/seedAll/forget`. |
| `events/pkgresult/PackageResultActivity.kt` | Install/uninstall result screen | `bottom_ad` (line 289), `exit_ad` on close (line 298), screen `package_event`. |
| `events/recent/RecentAdWatcher.kt` | Recents press (`ACTION_CLOSE_SYSTEM_DIALOGS`) → Play Store jump or ad screen | `claimsPress()`, `playStoreDue()`, `disarm()`; prefs keys `__recent_ad_last_shown`, `__recent_playstore_fired`. |
| `events/recent/RecentAdActivity.kt` | Ad screen after Recents | `bottom_ad` (line 27), `exit_ad` on Continue/Back (line 47), screen `recents_event`. |
| `host/AdEvents.kt` | `ad` and `ad_impression` analytics | `send()`, `loadFailed()`, `failStep()` (code 3 → `no_fill`), `paid()`, `standardFormat()`, `formatOf()`, `Listener`, `FullScreen`; `place` (current exit place). |
| `host/AdLifecycle.kt` | App-wide ActivityLifecycleCallbacks | on resume: `NavigationInterstitial.onReturnFromSettings()` (link return) + `keepNativeWarm()`; on stop: `NavigationInterstitial.onAppCovered()`. |
| `host/AdLog.kt` | Debug-only log (`AdEngine` tag) | `read()` and `dumpVault()` are no-ops. |
| `host/AdPreload.kt` | Warm every format | `all(activity)`: app open, global inter, back inter (if `IsBack`), rewarded, native, native strip. |
| `host/AppResumeWatcher.kt` | background→foreground detector (started-activity counter, ignores config changes) | posts `Ads.appResume(activity)`. Does NOT use `ProcessLifecycleOwner`. |
| `host/WindowFocus.kt` | `Activity.onceWindowFocused {}` | — |
| `launcher/UnlockAdWatcher.kt` | `ACTION_USER_PRESENT` → `launcher.phone_unlock` exit ad on home screen | counters `__unlock_last_ms`, `__unlock_day`, `__unlock_day_count` in vault. |
| `policy/AdLoadMode.kt` | Preload vs on-load per format (+ per place for inter) | `Format` enum (vault keys `is_preload_*`), `preload()`, `decide()`, `interPreloadPlaces()`. |
| `policy/AdSettingsVault.kt` | SharedPreferences `qr_ads_config` wrapper | `getInstance`, typed get/put, `sponsorLinks()`, `parseSponsorLinks()`, `clearManagedIn()` (keeps `OnMaketing`, `__cfg_audience_split`, `geo_country_code`, `GET_DATA_RAW`, `__*`). |
| `policy/AdUnitIds.kt` | Per-placement override lookup in vault `placements` JSON or `<place>_<key>` | `own()`, `resolve()`, `pickFullNative()`, `slotEnabled()`, `placementNames()`. |
| `policy/AdWait.kt` | The single loader timeout (`inter_load_timeout_sec`, fallback 8 s) | `AdWait`, `AdWaits.waitMs/main`, `guardSlot()`. |
| `policy/ConsentFlowGate.kt` | UMP wrapper | `canRequestAds()`, `isPrivacyOptionsRequired`, `askConsent()` (`requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`), `openPrivacyOptions()`. |
| `policy/ScreenAdPlanner.kt` | Legacy "ScreenAds" planner | Only `nativeUnitId()` is used (by `NativeAdRenderer`); `showAd/presentBanner/presentNative/resolve` unused. |
| `policy/SharedLoad.kt` | One in-flight load shared by many waiters | used by `NativeAdRenderer`. |
| `policy/ShowFrequencyGovernor.kt` | Static counters (`interCounter`, `bannerCounter`, …) | counters compared to vault `*Counter` keys that are never written (§5.3). |
| `render/AdColors.kt` | Hex parsing, defaults | — |
| `render/AdFrameStyling.kt` | Native/house styling helpers, banner strip frame, `sdp()` | — |
| `render/AdSlotGate.kt` | Slot open/closed tags, one-load-per-slot (30 s stale), `closeWithScreen()` (pause/resume/destroy banners with activity) | — |
| `render/AdViewKit.kt` | `hasNetwork()`, `linksOpenIn()`, `openInBrowserApp()`, `pushSponsorLink()` (house-ad CTA) | — |
| `render/BannerRenderer.kt` | Adaptive / inline / normal / collapsible banners; Ad Manager units (`/`-prefixed) | `presentBanner()`; `BannerSizing`, `BannerStyle`, `BannerLifecycleHook`. |
| `render/CollapsibleBackdrop.kt` | White view under Google's expanded collapsible popup (API 29+, `WindowInspector`) | — |
| `render/HouseAdDirector.kt` | Custom (house) ad cards from `CUSTOM_ADS` JSON | `fetchHouseAd(context, container, style, …, onFail)`, `HouseAdStyle` (BIG/MID/BANNER/FULLSCREEN), `clearCache()`. |
| `render/NativeAdRenderer.kt` | Native big / medium / medium-alt / full-page; single shared cache | `fetchNativeAds()`, `presentLargeNative()`, `presentMediumNative()`, `presentMediumNativeAlt()`, `presentFullNative()`, `presentFullNativeWithUnit()`, `presentWithUnit()`, `holdsCachedAd()`, `isLoading()`, `isFullNativeShowing`. |
| `render/NativeStripRenderer.kt` | Native banner (compact) with own cache | `fetchNativeStripAds()`, `presentNativeStrip()`. |
| `render/ResumeAdDirector.kt` | App-open ad | `loadAd()`, `showLoaded()` (used), `presentAdIfReady()` (legacy, unreachable), flags `isShowingAd`, `skipNextAppOpenAd`, `isAdAvailable`; unused `callbackshow`, `isOpenAppDismiss`, `flagReturnAd()/takeReturnAdFlag()`. |
| `render/RewardAdRenderer.kt` | Rewarded ad | companion `preload()`, `presentIfLoaded()`, `presentOrLoad()` (used); instance `show(requireEarned…)` (unused). |
| `render/SponsorWebActivity.kt` | In-app WebView for links (`links_open_in: webview`) | `intentFor(context,url)`, extra `sponsored_url`. |
| `render/interstitial/ChainInterstitial.kt` | Interstitial step of exit_ad chains, per-unit cache | `preload()`, `hasReady()`, `show(..., loadIfMissing)`. |
| `render/interstitial/InterstitialBackupChain.kt` | Legacy chain steps; `fullNative()` and `custom()` are used by `ExitAdRunner` | `run()`, `parse()`, `runSteps()` (legacy). |
| `render/interstitial/InterstitialPreloader.kt` | Full-screen "Please Wait…" loader dialog (`part_fullscreen.xml`) | `show()`, `hide()`, `isShowing`. |
| `render/interstitial/NavigationInterstitial.kt` | Legacy navigation interstitial + **sponsor-link opener (used)** | companion: `isInterShow`, `takePreloadedGoogle()`, `hasPreloadedGoogle`, `openSponsorLink()` (Custom Tabs / webview / browser; 3 s "never covered" guard; 1 s session wait), `onReturnFromSettings()`, `onAppCovered()`; instance `fetchInterstitials()` (used by preload); `showInterstitial()`/`runLinkFirst()` unused. |
| `render/interstitial/PlacementFlow.kt` | Legacy `ad_flow` per placement | unreachable (`ad_flow` never written). |

### A2.2 Tests (`ads/src/test/java/com/appkit/ads/`)

| File | Covers |
|---|---|
| `ExitAdFlowTest.kt` | 23 tests: `linksFor`, `every`, `exitRuns`, link order / `link` step, `if_ad_not_ready`, `show_all`, `formats_on`, network=custom, done-once, activity gone, `gapPassed`, unlock limits, `countToday`, Play Store window. |
| `analytics/AnalyticsRulesTest.kt` | event-name validity, `EventRules.clean`. |
| `analytics/ScreenVisitsTest.kt` | 5 visit-timer tests. |
| `host/AdEventsTest.kt` | `failStep`, `standardFormat`, `formatOf`. |

### A2.3 Resources (`ads/src/main/res/`)

| Group | Files | Use |
|---|---|---|
| Native layouts | `ad_gms_native_compact.xml` (native banner), `ad_gms_native_large.xml`, `ad_gms_native_large_top.xml` (only if `isButtonTop`, never passed true), `ad_gms_native_medium.xml`, `ad_gms_native_medium_alt.xml`, `ad_gms_native_fullpage.xml` | via ViewBinding |
| House-ad layouts | `house_ad_card_a.xml` (big), `house_ad_card_b.xml` (mid), `house_ad_strip.xml` (banner), `house_ad_fullpage.xml` | `HouseAdDirector` |
| Event screens | `activity_charging_status.xml`, `screen_package_result.xml`, `row_pkgres_check.xml`, `screen_recent_ad.xml` | event activities |
| Loader | `part_fullscreen.xml` | `InterstitialPreloader` |
| Drawables | 27 files incl. `qureka1.gif` (284 KB) and `adk_banner.webp` (67 KB) used as placeholders in house-ad layouts | — |
| Fonts | `figtree_bold/medium.ttf`, `nunito_bold/extrabold.ttf` | layouts |
| Values | `values/adk_module.xml` (ids `tag_ad_slot_closed/loading/watched`, `tag_collapsible_backdrop`; themes `Theme.AdKit.Web/PackageResult/Charging`), `values/adk_ported.xml` (65 strings + colors/dimens), 13 locale overrides `values-{ar,de,es,fr,hi,in,ja,ko,pt,ru,tr,vi}/` + `values-night/` | — |

### A2.4 Build / manifest

- `ads/build.gradle.kts`: `com.android.library`, namespace `com.appkit.ads`, compileSdk 36, minSdk 26, Java/JVM 17, `buildConfig` + `viewBinding` on, `unitTests.isReturnDefaultValues = true`.
- `ads/consumer-rules.pro`: keeps `HouseAdCard`, `Signature`, Gson `TypeToken`.
- `ads/src/main/AndroidManifest.xml`: see §7.

---

### A3. Ad formats

Common to every full-screen format:
- **Show gate**: everything shown from a place goes through `ExitAdRunner.run()` → `PlaceRules.exitRuns(adsOn, exit, every, place)` (ads on, block exists and `on`, `every` counter) → not finishing/destroyed → `!busy()` → `gap_sec` (non-event places) → `ExitAdFlow`.
- **Duplicate prevention**: `ExitAdRunner.active` (one flow at a time) + `busy()`; flags `NavigationInterstitial.isInterShow` (set *before* `show()`), `ResumeAdDirector.isShowingAd`, `NativeAdRenderer.isFullNativeShowing`, `InterstitialPreloader.isShowing`. Cached ad objects are removed from the cache before `show()`. `onDone` guarded to run once; `FinishWatcher` ends the flow if the activity is destroyed.
- **Load timeout**: `ads.loader_wait_sec` → vault `inter_load_timeout_sec` → `AdWaits.waitMs()` (≤0 → 8 s). On-load shows `InterstitialPreloader` dialog ("Please Wait…").
- **`loadIfMissing = true`** for every exit_ad step (`ExitAdRunner.EngineSource.show`): with no cached ad the step loads behind the loader even in preload mode. So `ads.preload.*` effectively only controls background warming, not whether an exit ad can show.
- **Failure / no-fill**: load failure, timeout or show failure → `onNotReady` → `if_ad_not_ready` fallback list → next ad in list. `ad` event step `failed` / `no_fill` (code 3) / `timeout` / `show_failed`.
- **Offline**: no explicit network check for inter / rewarded / app-open / full native (unit) loads; the SDK load fails and the step moves on. Banners, native slots and native strip check `hasNetwork()` first and hide/collapse.
- **Ad expiry**: no 1 h (inter/rewarded) or 4 h (app open) age check on cached ads anywhere (`grep -rn "SystemClock\|expire" render/` shows none for ad age).
- **Test vs real IDs**: unit IDs come only from RC; app id from `manifestPlaceholders` (§7).

### A3.1 App Open — `render/ResumeAdDirector.kt`
- Load: `loadAd(context, force)` → `AppOpenAd.load(context, vault googleAppopen, AdRequest)`; needs vault `AppopenAds` and `IsAdType == Google`; skipped unless `is_preload_app_open` or `force`. Guard `isLoadingAd`.
- Show (used path): `showLoaded(activity, onShown, onMiss, loadIfMissing)` — no ad → `loadThenShow()` (loader + `AdWait`); dismiss → `onShown()` + reload; show fail → `onMiss()` + reload. Paid label `app_open`.
- Preload: `AdPreload.all()` → `ResumeAdDirector.loadAd(activity)`; reload after every show.
- Used as an `exit_ad` step `app_open` anywhere (resume, onboarding chains, uninstall splash, sponsored open).
- **Resume trigger** (`launcher.app_resume`): `host/AppResumeWatcher` counts started activities; first `onActivityStarted` after all stopped (not a config change) posts `Ads.appResume(activity)`. `Ads.appResume` (`Ads.kt:79-96`) skips when: `ResumeAdDirector.skipNextAppOpenAd` (own launch / links) with no pending launcher return; within 3 s of a handled `app_exit`/`sponsored_app_exit`; module screens (`com.appkit.ads.*`) or SDK ad activities (`com.google.android.gms.ads.*`); `AdKit.noResumeAdOn(activity)` (app sets `DefaultHomeStep.shown`, `app:App.kt:69`); `ExitAdRunner.busy()`. Waits for window focus. A pending launcher return runs `launcher.app_exit` / `launcher.sponsored_app_exit` instead (one ad per return).
- **Cold start**: `wentToBackground` starts false, so a cold start never gets the resume ad; the splash `exit_ad` covers cold start.
- Legacy `presentAdIfReady()` (vault `AppopenCounter`, `PlacementFlow "appOpen"`) is only called from `NavigationInterstitial.afterLinkTasks` (unreachable) — dead.
- `Ads.markOwnLaunch()/clearOwnLaunch()` exist but the app never calls them (`grep -rn "markOwnLaunch" app/src/main` → none); only module links and `RecentAdWatcher.launchPlayStoreFrom` set `skipNextAppOpenAd`.

### A3.2 Interstitial — `render/interstitial/ChainInterstitial.kt` (+ preload in `NavigationInterstitial.fetchInterstitials`)
- Units: `screens.splash` → `ad_units.inter_splash` (blank → `inter`); `launcher.back_button` → `ad_units.inter_back` (blank → `inter`); every other place → `ad_units.inter` (`ExitAdRunner.kt:121-125`).
- Preload: `AdPreload.all()` → `NavigationInterstitial().fetchInterstitials()` loads global `googleInter` into `googleInterAd` (if `IsAdsON`, `InterAds`, `is_preload_ads`, Google), plus per-place `preload: true` places (`AdLoadMode.interPreloadPlaces`, which resolve to the global unit as no per-place unit is ever written); back inter preloaded into `ChainInterstitial.ready` when `IsBack` (back_button exit_ad on). After dismiss / show failure `ChainInterstitial` re-preloads its unit.
- Show: `ChainInterstitial.show()` — `busy` check, take `ready[unit]` or (global unit) `NavigationInterstitial.takePreloadedGoogle()`, else on-load behind loader with `AdWait`; a late ad is cached for the next step. `isInterShow` flag set before `show()`. Paid label `inter_chain`.
- Per-unit `loading` set prevents two loads of one unit from the chain path; `ChainInterstitial.preload` skips the global unit only if `NavigationInterstitial.hasPreloadedGoogle` (not while that load is in flight → possible duplicate request).
- Frequency: `every`, `gap_sec`, `ShowFrequencyGovernor.interCounter`/vault `InterCounter` only in the dead `presentAdCore` path.
- Legacy `NavigationInterstitial.showInterstitial()/presentAdCore()/presentNetworkInterstitial()/fetchAndShowGmsOnDemand()/onGmsFailure()` and `runLinkFirst()`: no callers (`grep -rn "showInterstitial\|runLinkFirst" ads app` → definitions only) — dead.

### A3.3 Rewarded — `render/RewardAdRenderer.kt`
- Load: `preload(context, ignoreMasterSwitch, force, onResult)` → `RewardedAd.load(vault googleRewarded)`; requires `IsAdsON`, `RewardedAds`; skipped unless `is_preload_rewarded` or forced.
- Show (used): `presentOrLoad(activity, onDone, onMiss, loadIfMissing)` → `presentIfLoaded()`; on-load via `loadNow()` (loader + `AdWait`). **Show failure in `presentIfLoaded` calls `onDone()` (counted as shown), not `onMiss`** — fallback does not run on show failure. Reward callback ignored (`ad.show(activity) {}`); paid label `rewarded_fallback`.
- The reward-gated `RewardAdRenderer().show(requireEarned, onRewarded, …)` with DirectLink fallback has no callers — no real "watch ad to unlock" placement exists.
- Used in RC as an exit step (`launcher.app_exit`, `sponsored_app_exit`, `phone_unlock` in track_b) and as global `if_ad_not_ready: ["rewarded"]`.

### A3.4 Rewarded Interstitial
- **Not present.** `grep -rniE "RewardedInterstitial" ads app/src/main` → no matches.

### A3.5 Native (big / mid / mid2) — `render/NativeAdRenderer.kt`
- One shared static cache `nativeAd` (unit = `ScreenAdPlanner.nativeUnitId()` = vault `googleNative`, since `screen_wise_ad` is never written). Load via `fetchNativeAds()` with `SharedLoad` (one request in flight, many waiters). Only when `is_preload_native` true; otherwise slots use `presentWithUnit()` to load their own.
- Show: `presentLargeNative()` (`native_big`, `ad_gms_native_large.xml`), `presentMediumNative()` (`native_mid`, `ad_gms_native_medium.xml`), `presentMediumNativeAlt()` (`native_mid2`, `ad_gms_native_medium_alt.xml`). Each checks `hasNetwork`, `IsAdsON`, `NativeAd`, counter vault `NativeCounter` (big) / `MidNativeCounter` (mid/mid2) — counters never written → always 0 → show every time. Cached ad is consumed then `fetchNativeAds()` refills (preload). Failure: house ad (`HouseAdDirector`) else collapse.
- Warm-keeping: `AdLifecycle.keepNativeWarm()` on every activity resume (if no cached ad); `AdPreload.all()`.
- Palette from vault `NativeBgColor`, `NativebtnColor`, `NativetxtColor`, `NativebtntxtColor` (RC `ads.colors.light/dark`, rewritten before each slot by `writePalette`).
- Displayed `NativeAd` objects are never `destroy()`ed after display (only the cache's previous ad on a new load, and the full-page ones on close).
- Paid labels `native_big`, `native_medium`, `native_unit`.

### A3.6 Native Banner (strip) — `render/NativeStripRenderer.kt`
- Own static cache `nativeAdBanner`, unit `googleNative` (same unit as native), layout `ad_gms_native_compact.xml`. Gate `NativeBannerPresenter` (`formats_on.native_banner`), counter `MidNativeCounter` (shares the name with mid native; never written).
- Preload via `AdPreload` when `is_preload_native`; on-load `fetchNativeStripAds(now = true)`. Failure → house banner card → else hidden. Paid label `native_strip`. Refills after each show.
- Most-used slot type in RC (`screens.home`, several onboarding steps, `suggested_panel.list_ad`).

### A3.7 Banner (adaptive / inline / collapsible) — `render/BannerRenderer.kt`
- Slot types: `banner` → anchored adaptive (`getCurrentOrientationAnchoredAdaptiveBannerAdSize`), `banner_inline` → inline adaptive, `banner_collapsible` → anchored adaptive + extras `collapsible=bottom` on **every** load (no app-side limit). `BannerSizing.NORMAL` (320×50) only reachable from dead `ScreenAdPlanner`.
- Unit vault `googleBanner`; Ad Manager units (start with `/`) use `AdManagerAdView`/`AdManagerAdRequest`.
- One load per slot (`AdSlotGate.beginLoad`, 30 s stale), attach only after window focus (so a collapsible's popup doesn't show behind a full-screen ad), `CollapsibleBackdrop` white view under the expanded popup, `AdSlotGate.closeWithScreen()` pauses/resumes and destroys with the activity.
- Refresh: no app-side refresh timer; AdMob console refresh only. A new `BannerRenderer` per slot load.
- Failure: house banner (`HouseAdDirector`), else hidden. Counter vault `BannerCounter` (never written). Paid label `banner`.
- No RC place uses a banner type in `docs/remote-config/app_config.json` (all slots are native types).

### A3.8 Fullscreen Native — `NativeAdRenderer.presentFullNative()` / `presentFullNativeWithUnit()`; `InterstitialBackupChain.fullNative()`
- As exit step `full_native`: cached native → black overlay on `android.R.id.content` + `ad_gms_native_fullpage.xml` with close button (`adFullClose`, `Analytics.click("native_ad_close")`); else loads `AdUnitIds.pickFullNative()` (= `googleNative`, `googleFullNative` never written) behind black overlay with `AdWait`. Sets `isFullNativeShowing`. Paid labels `native_full`, `native_full_unit`.
- As slot type `native_full` (`BottomAdLoader`): `presentFullNative` inside the container; close collapses it.
- Used in RC: `launcher.charger_disconnected.exit_ad: ["full_native"]`.

### A3.9 Custom (house) ads — `render/HouseAdDirector.kt`
- Source: `ads.custom_ads.list` → vault `CUSTOM_ADS` (Gson JSON) when `custom_ads.on` (`IsCustomADS`). Random card per show; Glide image loading; only the CTA is clickable → `pushSponsorLink()` opens the **first** `ads.links` URL (`links_open_in`).
- Uses: exit step `custom` (`InterstitialBackupChain.custom`, full-screen with close button), fallback for failed/timeout/off slots (`BottomAdLoader.showCustom`), banner/native failure fallback.
- RC ships `custom_ads.on: false`, `list: []` in both tracks.

### A3.10 Direct links — `ExitAdFlow.openLinks` → `ExitAdRunner.EngineSource.openLinks` → `NavigationInterstitial.openSponsorLink()`
- `ads.links` (+ per-block `links`) repeated to `links_count` (per-block override); opened at the `link` step (or first if no `link` step), all stacked in reverse order so the first is on top.
- `links_open_in`: `in_app_browser` (Chrome Custom Tabs, binds `com.android.chrome`, 1 s session wait), `webview` (`SponsorWebActivity`), `browser` (system browser app).
- Return detection: `AdLifecycle.onActivityStopped` → `onAppCovered()`, then `onActivityResumed` → `onReturnFromSettings()` fires `onClosed`. Guard: if the link never covered the app in 3 s, treated as closed. `skipNextAppOpenAd` set during links and cleared 1 s after return. `ad` event `custom/link_open`.
- Fallback items `link` (first link) or a literal URL in `if_ad_not_ready`.
- Shipped RC: `links: ["https://www.examples.com"]`, `links_count: 2` (global, both tracks).

### A3.11 "Events" triggers (module-owned dynamic receivers, registered in `AdKit.init`)
| Event | Receiver | Gates | Screen / ad |
|---|---|---|---|
| Charging | `ChargeEventWatcher` (`ACTION_POWER_CONNECTED/DISCONNECTED`, `RECEIVER_NOT_EXPORTED`) | `Ads.placeOn(place)` (place `on` + ads on), `gap_sec` per place (prefs `charge_event_watcher`) | Starts `ChargingStatusActivity` over the resumed activity; if none resumed, queued (TTL 10 min) and shown on the next resume. `bottom_ad` + `exit_ad` on close. |
| Package (pkgresult) | `PackageEventWatcher` (`PACKAGE_ADDED/REMOVED`, scheme `package`, ignores `EXTRA_REPLACING` and own package) | `placeOn`, `gap_sec` (one shared `last_shown_at` for both places) | `PackageResultActivity` (fake privacy/optimize check), `bottom_ad` + `exit_ad`. Seeds `PackageMetadataCache` only when `app_uninstalled` on. |
| Recents | `RecentAdWatcher` (`ACTION_CLOSE_SYSTEM_DIALOGS` reason `recentapps`/`recent_apps`; `homekey` disarms) | Play Store jump: place `on`, not fired yet, within `window_sec` (default 180) of first install; ad screen: `AdKit.adsOn()`, `show_ad_screen`, `gap_sec`, `AdKit.recentsAdScreenOn` (app: onboarding done) | Jump: `moveTaskToFront` then Play Store home (`com.android.vending` launcher intent, fallback web), one-shot. Ad screen: `RecentAdActivity` on pause of armed activity. |
| Phone unlock | `UnlockAdWatcher` (`ACTION_USER_PRESENT`, `RECEIVER_EXPORTED`) | `placeOn` + `exitOn`, `start_after_hours` (24), `gap_minutes` (30), `max_per_day` (5), front activity must pass `AdKit.isHomeScreen` (app: onboarding done AND default home) | `Ads.exit(activity, PHONE_UNLOCK)` 400 ms after resume (if unlocked while not home, waits up to 10 s for a home resume). |

All receivers are dynamic (only while the process lives); none are declared in a manifest. Background events are queued and only shown when the user next resumes one of the app's activities.

### A3.12 Consent (UMP) — `policy/ConsentFlowGate.kt`, `AdKit.requestConsent`
- `AdKit.init` starts MobileAds immediately when `canRequestAds()` is already true (returning users).
- Splash (`app:navigation/AppNavHost.kt:91`) calls `AdKit.requestConsent(activity) { AdKit.preload(activity); … }` every launch → `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`; on done/error starts MobileAds and re-applies config with the consent gate. `ConsentDebugSettings` built with no test device / geography.
- Gate: ads blocked only when status `REQUIRED` and `canRequestAds()` false (`AdKit.consentAllows`).
- `AdKit.privacyOptionsRequired()` / `showPrivacyOptions()` exist but the app never calls them (`grep -rn "privacyOptions\|showPrivacyOptions" app/src/main` → none) — no Privacy options entry in Settings.

---

### A4. Placements

RC JSON path for a place key `<group>.<name>` is `track_x.ads.<group>.<name>` (see §5). "exit" = `exit_ad`, "bottom" = `bottom_ad`. Values cited are from `docs/remote-config/app_config.json` (A = `track_a` organic, B = `track_b` paid). Fallback for every exit = `if_ad_not_ready` then next step, then `onDone` (navigation proceeds); for every slot = custom card if `custom_ads.on`, else collapse.

### A4.1 App (Compose) placements

| # | File:line | Screen / route | Trigger | Format | Place key | RC (A / B) |
|---|---|---|---|---|---|---|
| 1 | `app:navigation/AppNavHost.kt:91` | Splash | UMP consent then `AdKit.preload` | preload all | — | — |
| 2 | `app:navigation/AppNavHost.kt:109` | Splash → Home/Onboarding | after hold + attribution + permissions | exit (inter, unit `inter_splash`; splash deadline = loader_wait_sec from first step) | `screens.splash` | A on `[inter]` (ads.on false) / B off |
| 3 | `app:home/HomeScreen.kt:386` (`BottomAdSpace`) | Home (also inside launcher host panel via `HostPanelFragment`) | composition | bottom | `screens.home` | A/B `native_banner` |
| 4 | `app:navigation/AppNavHost.kt:146` | Home | open a feature card | exit | `screens.home` | A/B `[inter]` (B `links_count:0`) |
| 5 | `app:navigation/AppNavHost.kt:147` | Home | open Settings | exit | `screens.home` | same as #4 |
| 6 | `app:navigation/AppNavHost.kt:140` | Home | Back (leaves to launcher home / finishes) | exit (inter, unit `inter_back`) | `launcher.back_button` | A on `[inter]` / B off |
| 7 | `app:settings/SettingsScreen.kt:218` | Settings | composition | bottom | `screens.settings` | A/B `native_mid` |
| 8 | `app:navigation/AppNavHost.kt:158` | Settings | Back / close | exit | `screens.settings` | A/B off |
| 9 | `app:launcherhost/HostPanelFragment.kt:100` | Launcher host-app panel Home | open feature | exit | `screens.home` | as #4 |
| 10 | `app:onboarding/OnboardingScaffold.kt:147` and `app:onboarding/OnboardingPage.kt:81` | Every onboarding step (`LocalOnboardingPlace = "onboarding.<screen>"`, `OnboardingFlow.kt:284`) | composition (bottom, unless inline) | bottom | `onboarding.<screen>` | see below |
| 11 | `app:onboarding/OnboardingPage.kt:91-95`, used at `app:onboarding/steps/ProfileSteps.kt:314` (gender), `:434` (age), `app:onboarding/steps/FeatureStepsB.kt:157` (interests) | Gender / Age / Interests | inline native between hero and options when `bottom_ad.type` starts with `native` | bottom (inline) | `onboarding.gender_select/age_select/interests` | A `native_banner`, B `native_big` |
| 12 | `app:onboarding/OnboardingFlow.kt:185-186` (`advance()`) | Every onboarding step | Next / Continue / Skip / sheet Continue / system key `next` | exit; replaced by `launcher.<key>_button` when `during_onboarding` true | `onboarding.<screen>` | all steps A/B `[link, inter, app_open]` with global `links_count: 2` |
| 13 | `app:onboarding/OnboardingFlow.kt:210` (`homeGranted()`) | default_home step | user set us as default home | exit (runs over the next step) | `onboarding.default_home` | A/B `[link, inter, app_open]`; bottom A on / B off |
| 14 | `app:onboarding/OnboardingFlow.kt:248-249` (`keyAd`) | Onboarding | Back/Home/Recents key mapped to GoTo / LeaveApp | exit | `launcher.{back,home,recents}_button` (only if `during_onboarding`) | all `during_onboarding:false` → never |
| 15 | `app:MainActivity.kt:83` | Any MainActivity screen after onboarding | Home key (`ACTION_CLOSE_SYSTEM_DIALOGS reason homekey`) while default home | exit | `launcher.home_button` | A/B off |
| 16 | `app:RecentsPolicy.kt:153` | After onboarding, in home task, default home | Recents key | exit | `launcher.recents_button` | A/B off |
| 17 | `app:RecentsPolicy.kt:133` | — | Recents | defers to `RecentAdWatcher.claimsPress` (Play Store jump) | `launcher.play_store_return` | — |
| 18 | `app:uninstall/UninstallScreens.kt:77` (`Page()` used at :86 language, :128 step1, :156 step2, :195 progress, :227 step3) | Uninstall flow | composition | bottom | `uninstall_flow.<screen>` | step1 `native_mid`, others `native_big` |
| 19 | `app:uninstall/UninstallFlowActivity.kt:76` (`leave()`), triggers :88 (splash auto after `splash_ms`), :94 (language pick), :105 (step1 → step2), :112 (uninstall anyway → progress), :117 (progress done), :123 (step3 end) | Uninstall flow | leaving the screen | exit | `uninstall_flow.<screen>` | `splash [app_open]`, `language [inter]`; step1/step2/step3/progress have no `exit_ad` → pass-through |
| 20 | `app:ads/AdSlot.kt:21` | Compose wrapper | `AndroidView` factory → `Ads.bottom`/`Ads.list` | slot | caller's key | — |

### A4.2 Launcher placements (launcher module calls `LauncherEvents`; implemented in `app:launcherhost/AppLauncherEvents.kt`, installed `app:launcherhost/LauncherSetup.kt:21`). The launcher module itself has no dependency on `:ads` (`grep -rn "com.appkit.ads" launcher/src` → none).

| # | Launcher call site | App bridge (file:line) | Trigger | Format | Place key | RC (A / B) |
|---|---|---|---|---|---|---|
| 21 | `launcher/.../intro/HubOfferCoordinator.kt:33` | `AppLauncherEvents.kt:20` | swipe right → host app panel | exit | `launcher.host_app_panel` | A/B `[inter]` |
| 22 | `HubOfferCoordinator.kt:32` | `AppLauncherEvents.kt:20` | swipe left → suggested panel | exit | `launcher.suggested_panel` | A/B off |
| 23 | `launcher/.../intro/HubInlinePromo.kt:17,23` | `AppLauncherEvents.kt:33` | suggested panel bottom slot | bottom | `launcher.suggested_panel` | A `native_mid2` on / B off |
| 24 | `HubInlinePromo.kt:18,23` | `AppLauncherEvents.kt:34` | suggested panel list slot | list_ad | `launcher.suggested_panel` | A `native_banner` on / B off |
| 25 | `HubOfferCoordinator.kt:34` | `AppLauncherEvents.kt:22` | app drawer opened | exit | `launcher.app_drawer` | A/B off |
| 26 | `HubInlinePromo.kt:19,23` | `AppLauncherEvents.kt:35` | drawer slot (row from `ads.launcher.app_drawer.bottom_ad_row`, parsed by app) | bottom | `launcher.app_drawer` | A/B off |
| 27 | `HubOfferCoordinator.kt:35` | `AppLauncherEvents.kt:24` | app icon tapped (before opening it) | exit | `launcher.app_open` | A off / B `[app_open]` |
| 28 | `HubOfferCoordinator.kt:49` + `Ads.appResume` pending return | `AppLauncherEvents.kt:26` | back from that app | exit | `launcher.app_exit` | A off / B `[link, rewarded, inter]`, `links_count:1` |
| 29 | `launcher/.../intro/PromotedTiles.kt:63` | `AppLauncherEvents.kt:28` | sponsored drawer tile tapped | exit | `launcher.sponsored_app_open` | A off / B `[link, app_open]`, `links_count:1` |
| 30 | `HubOfferCoordinator.kt:50` | `AppLauncherEvents.kt:30` | back from sponsored tile | exit | `launcher.sponsored_app_exit` | A `[rewarded]` / B `[rewarded, link]`, `links_count:2` |

### A4.3 Module-internal placements

| # | File:line | Trigger | Format | Place key | RC (A / B) |
|---|---|---|---|---|---|
| 31 | `ads:host/AppResumeWatcher.kt:31` → `ads:Ads.kt:79-96` | app background → foreground | exit (`app_open`) | `launcher.app_resume` | A `[app_open]` / B off |
| 32 | `ads:launcher/UnlockAdWatcher.kt:74` | phone unlocked on home | exit | `launcher.phone_unlock` | A off / B on `[inter, rewarded]` |
| 33 | `ads:events/charging/ChargingStatusActivity.kt:149` / `:156` | charger connected screen / close | bottom / exit | `launcher.charger_connected` | A/B place off (`native_big`, `[inter]`) |
| 34 | same file, place from `EXTRA_PLACE` | charger disconnected | bottom / exit | `launcher.charger_disconnected` | A/B place off (`native_big`, `[full_native]`) |
| 35 | `ads:events/pkgresult/PackageResultActivity.kt:289` / `:298` | app installed result / close | bottom / exit | `launcher.app_installed` | A/B place off |
| 36 | same | app uninstalled | bottom / exit | `launcher.app_uninstalled` | A/B place off |
| 37 | `ads:events/recent/RecentAdWatcher.kt:192-216` | first Recents press within `window_sec` | Play Store home jump (not an ad) | `launcher.play_store_return` (`on`) | A off / B on, `window_sec: 6000` |
| 38 | `ads:events/recent/RecentAdActivity.kt:27` / `:47` | Recents → ad screen (`show_ad_screen`) / Continue or Back | bottom / exit | `launcher.play_store_return` | `show_ad_screen:false` in A and B → never |
| 39 | `ads:host/AdLifecycle.kt:24` | every activity resume | native warm-up | — | — |

Places defined in RC with no code caller: `launcher.drawer_sponsored_apps` (read by the app parser for `LauncherSettings`, not an ad place). Every place key used in code exists in the shipped RC.

---

### A5. Remote Config keys consumed by ads

### A5.1 Transport
- RC parameter (string JSON): `app_config` (release) or `app_config_debug` (debug) — `app:config/AppConfigStore.kt:23`. Fetched with `fetchAndActivate` at most every `base.rc_sync_hours` (or forced via FCM topic `rc_sync`); saved JSON is re-published at startup.
- Parse: `app:config/AppConfigParser.parse()` → picks `track_a` / `track_b` (`base.exclusive_locations` can force A) → merges `countries` overlays (`"IN,PK"` in B only overrides permissions) → `ads(t.obj("ads"), base)` at `AppConfigParser.kt:96-152`.
- Into ads: `app:App.kt:65` `AdKit.init(this)`, `:71-75` `AdKit.apply(ads)` on every distinct change.

### A5.2 Keys (parser: `AppConfigParser.ads()`; defaults are parser defaults, which equal `AdsConfig` defaults except where noted)

| JSON path | Type | Default | Model field | Consumer |
|---|---|---|---|---|
| `base.ad_network` | `"google"`/`"custom"` | google | `network` | vault `IsAdType`; `ExitAdFlow.formatOn`; `BottomAdLoader` |
| `base.ad_units.inter` | string | "" | `units.inter` | vault `googleInter` |
| `base.ad_units.inter_splash` | string | "" → inter | `units.interSplash` | `ExitAdRunner` splash; vault `googleS_Inter` (written, never read) |
| `base.ad_units.inter_back` | string | "" → inter | `units.interBack` | `ExitAdRunner` back; vault `googleBackInter` (AdPreload) |
| `base.ad_units.app_open` | string | "" | `units.appOpen` | vault `googleAppopen` |
| `base.ad_units.native` | string | "" | `units.native` | vault `googleNative` (native, strip, full native) |
| `base.ad_units.banner` | string | "" | `units.banner` | vault `googleBanner` |
| `base.ad_units.rewarded` | string | "" | `units.rewarded` | vault `googleRewarded` |
| `ads.on` | bool | false | `on` | `AdKit.adsOn`; vault `IsAdsON` (&& consent) |
| `ads.loader_wait_sec` | int | 8 | `loaderWaitSec` | vault `inter_load_timeout_sec` → `AdWaits` |
| `ads.preload.{inter,native,app_open,rewarded}` | bool object | true each | `preload` | vault `is_preload_ads/_native/_app_open/_rewarded`. Note: `AdLoadMode` docs say inter defaults to on-load and accept a single boolean; the parser only accepts the object and defaults inter to **true**. |
| `ads.formats_on.{inter,native,banner,native_banner,app_open,rewarded}` | bool object | true each | `formatsOn` | vault `InterAds`, `NativeAd`, `BannerAdPresenter`, `NativeBannerPresenter`, `AppopenAds`, `RewardedAds`; `ExitAdFlow.formatOn`; `BottomAdLoader` |
| `ads.if_ad_not_ready` | string array (`inter`/`rewarded`/`app_open`/`full_native`/`custom`, `link`, or URL) | [] | `ifAdNotReady` | `ExitAdFlow.fallback` (`AppConfigParser.fallbackStep`) |
| `ads.links` | string array | [] | `links` | `ExitAdFlow`; vault `DirectLink` (house-ad CTA) |
| `ads.links_count` | int | 0 | `linksCount` | `AdsConfig.linksFor` |
| `ads.show_all` | bool | true | `showAll` | `ExitAdFlow` |
| `ads.links_open_in` | `webview`/`in_app_browser`/`browser` | in_app_browser | `linksOpenIn` | vault `link_open_in` |
| `ads.custom_ads.on` | bool | false | `customAds.on` | vault `IsCustomADS` |
| `ads.custom_ads.list[]` `{icon,title,description,bannerImage,buttonText}` | objects | [] | `customAds.list` | vault `CUSTOM_ADS` |
| `ads.colors.{light,dark}.{button,button_text,background,text}` | `#RRGGBB` (else "") | "" | `colors` | vault `NativebtnColor`, `NativebtntxtColor`, `NativeBgColor`, `NativetxtColor` |
| `ads.{screens,onboarding,launcher,uninstall_flow}.<name>` | object | — | `places["<group>.<name>"]` | see below |
| `…<place>.bottom_ad` / `.list_ad` `{on, type}` | type ∈ `banner, banner_inline, banner_collapsible, native_big, native_mid, native_mid2, native_banner, native_full` | on false, type banner (unknown → banner) | `Slot` | `BottomAdLoader` |
| `…<place>.exit_ad.on` | bool | false | `ExitAd.on` | `PlaceRules.exitRuns` |
| `…<place>.exit_ad.ads` | array of `inter, rewarded, app_open, full_native, custom, link` (unknown dropped) | [] | `ExitAd.ads` | `ExitAdFlow` |
| `…<place>.exit_ad.links_count` | int? | null → global | `ExitAd.linksCount` | `linksFor` |
| `…<place>.exit_ad.links` | array? | null → global | `ExitAd.links` | `linksFor`, fallback `link` |
| `…<place>.exit_ad.show_all` | bool? | null → global | `ExitAd.showAll` | `showAllFor` |
| `…<place>.exit_ad.every` | int ≥1 | 1 | `ExitAd.every` | `EveryCounter` (in memory) |
| place option `on` | bool | false | `options` | `Ads.placeOn` (phone_unlock, charger_*, app_installed/uninstalled, play_store_return) |
| place option `gap_sec` | int | 0 | `options` | `ExitAdRunner` (any non-event place), `ChargeEventWatcher`, `PackageEventWatcher`, `RecentAdWatcher` (ad screen) |
| place option `preload` | bool | absent | `options` | `AdsConfigApplier` → vault `placements` JSON → `AdLoadMode` (inter warm-up per place) |
| `launcher.back_button/home_button/recents_button.during_onboarding` | bool | false | `options` | `Ads.keyAdDuringOnboarding` |
| `launcher.phone_unlock.start_after_hours / gap_minutes / max_per_day` | int | 24 / 30 / 5 (code defaults in `UnlockAdWatcher.eligible`) | `options` | `PlaceRules.unlockAllowed` |
| `launcher.play_store_return.window_sec` | int | 180 (code default) | `options` | `RecentAdWatcher.playStoreDue` |
| `launcher.play_store_return.show_ad_screen` | bool | false | `options` | `RecentAdWatcher.armIfAllowed` |
| `launcher.app_drawer.bottom_ad_row`, `launcher.drawer_sponsored_apps.{on,every,list}` | — | 1 / false, 8, [] | parsed by app into `LauncherSettings` (`AppConfigParser.kt:198-225`), not by ads | launcher module |

### A5.3 Vault keys read by the engine but never written by `AdsConfigApplier` (legacy, always default)
`InterCounter`, `AppopenCounter`, `BannerCounter`, `NativeCounter`, `MidNativeCounter` (→ 0, i.e. no counter skipping), `ScreenAds`, `screen_wise_ad`, `screen_wise_default`, `link_first_then`, `link_first_show_all`, `ad_flow`, `ad_flow_show_all`, `inter_fallback` (code default `full_native,custom`), `googleFullNative`, `<place>_ads_on`, `<prefix>link_first_then` in `fetchInterstitials`. Derived from: `grep -rhoE 'get(Boolean|String|Int|Long)\("…"' ads/src/main/java` vs `grep -hoE 'put…\("…"' AdsConfigApplier.kt`. Written but never read: `googleS_Inter`.

---

### A6. Analytics (`ads/analytics`, `ads/host/AdEvents.kt`)

- `Analytics.init(context, segment)` from `app:App.kt:56` (segment `paid` if `track == B`, else `organic`). Only events in `Analytics.EVENTS` are sent (12 names, the comments say "11"): `screen`, `click`, `key_press`, `permission`, `ad`, `ad_impression`, `default_home`, `app_update`, `rate_us`, `uninstall`, `install_source`, `first_home`. Every event gets `segment`. Param rules (`EventRules.clean`): keys `^[a-z][a-z0-9_]{0,39}$`, strings ≤100 chars, nulls dropped, Int→Long, Float→Double. Debug builds also log to Firebase and to `DebugEventLog` (in-memory, ongoing notification → host `DebugEventsActivity`).
- **`ad`** (`AdEvents.send`): params `screen` (`Screens.current`), `ad_place` (RC place key; omitted for warm-up preloads), `format` (`banner|native|inter|rewarded|app_open|custom`), `step` (`request`, `loaded`, `failed`, `no_fill` (code 3), `timeout`, `show`, `show_failed`, `impression`, `click`, `close`, `link_open`), `error_code`.
- **`ad_impression`** (`AdEvents.paid`, from every `setOnPaidEventListener`): `ad_platform="admob"`, `ad_source` (adapter name or `unknown`), `ad_format` (`standardFormat`: app_open/rewarded/interstitial/native/banner), `ad_unit_name`, `value` (micros/1e6), `currency` (default USD), `ad_placement` (engine label: `inter_chain`, `inter_navigation`*, `app_open`, `app_open_resume`*, `rewarded`, `rewarded_fallback`, `banner`, `native_strip`, `native_big`, `native_medium`, `native_full`, `native_unit`, `native_full_unit`; * = dead path), `ad_screen`, `user_segment`, `ad_place`.
- Clicks from ads module: `custom_ad_close`, `native_ad_close`, `house_ad_cta`, `continue`, `close`, `done`, `privacy_review`; `key_press back`. Screens: `charging_event`, `package_event`, `recents_event`.
- `Screens.install()` uses `ProcessLifecycleOwner` (only place it is used); `Screens.ownLeave` set by app (`App.kt:60`) to `ExitAdRunner.busy() || skipNextAppOpenAd || …` so ad/link departures don't end a visit.
- No other revenue logging (no Adjust/AppsFlyer/Firebase `ad_revenue` custom). Unit tests cover naming rules and `failStep`/`standardFormat`/`formatOf`.

---

### A7. Dependencies & manifest

### A7.1 `ads/build.gradle.kts` dependencies (versions from `gradle/libs.versions.toml`)
| Alias | Coordinates | Version |
|---|---|---|
| `androidx.core.ktx` | androidx.core:core-ktx | 1.16.0 |
| `androidx.appcompat` | androidx.appcompat:appcompat | 1.7.1 |
| `material` | com.google.android.material:material | 1.12.0 |
| `androidx.constraintlayout` | androidx.constraintlayout:constraintlayout | 2.2.1 |
| `androidx.lifecycle.runtime.ktx` / `.process` | androidx.lifecycle | 2.9.2 |
| `kotlinx.coroutines.android` | org.jetbrains.kotlinx | 1.10.2 |
| `play.services.ads` (**api**) | com.google.android.gms:play-services-ads | 25.5.0 |
| `ump` | com.google.android.ump:user-messaging-platform | 3.1.0 |
| `androidx.browser` | androidx.browser:browser | 1.8.0 |
| `firebase.bom` + `firebase.analytics` | com.google.firebase | BOM 33.10.0 |
| `shimmer` | com.facebook.shimmer:shimmer | 0.5.0 |
| `lottie` | com.airbnb.android:lottie | 6.6.3 (**unused**: no `lottie` reference in ads sources/res) |
| `gson` | com.google.code.gson:gson | 2.11.0 |
| `glide` | com.github.bumptech.glide:glide | 4.16.0 |
| hard-coded | com.intuit.sdp:sdp-android / com.intuit.ssp:ssp-android | 1.1.1 (not in version catalog) |
| test | junit 4.13.2, org.json 20240303 | |
Plugins: AGP 8.9.1, Kotlin 2.2.10.

### A7.2 Manifests
- `ads/src/main/AndroidManifest.xml`: permissions `INTERNET`, `ACCESS_NETWORK_STATE`, `REORDER_TASKS` (Play Store jump `moveTaskToFront`); `<queries>` for `MAIN/LAUNCHER` intents (package-result labels); activities `render.SponsorWebActivity` (portrait, excludeFromRecents, not exported), `events.pkgresult.PackageResultActivity` (singleTask, `taskAffinity=""`, excludeFromRecents), `events.charging.ChargingStatusActivity` (singleTask, `taskAffinity=""`, `showWhenLocked`, `turnScreenOn`), `events.recent.RecentAdActivity` (singleTop). **No receivers or services** — all event receivers are registered at runtime in `AdKit.init` (`USER_PRESENT` and `CLOSE_SYSTEM_DIALOGS` as `RECEIVER_EXPORTED`; power and package as `RECEIVER_NOT_EXPORTED`).
- AdMob app id: `app/src/main/AndroidManifest.xml:103-105` `com.google.android.gms.ads.APPLICATION_ID = ${admobAppId}`. `app/build.gradle.kts:13-47`: debug = Google test id `ca-app-pub-3940256099942544~3347511713`; release = `-PadmobAppId` or `local.properties admob.appId`, else falls back to the test id with a `preReleaseBuild` warning. `local.properties` currently has no `admob.appId` line (`grep -n admob local.properties` → none), so a release build today uses the test app id.

---

### A8. Risks, oddities, blockers

1. **Shipped RC uses Google test unit IDs** for every format (`docs/remote-config/app_config.json` `base.ad_units`), and release builds fall back to the test AdMob app id (no `admob.appId` set). Real IDs must be supplied before release.
2. **Placeholder links**: `ads.links: ["https://www.examples.com"]`, `links_count: 2` (both tracks). In `track_b` (`ads.on: true`) every onboarding step's `exit_ad` `[link, inter, app_open]` opens 2 tabs of this URL before the inter, on all 13 onboarding steps; `if_ad_not_ready: ["rewarded"]` adds a rewarded attempt when inter/app_open miss. Very aggressive, policy-risky (AdMob "unexpected ads"/interstitial after link-outs) and the URL is a placeholder.
3. **Large dead legacy engine**: `NavigationInterstitial.showInterstitial/presentAdCore/runLinkFirst/afterLinkTasks/presentNetworkInterstitial/fetchAndShowGmsOnDemand/onGmsFailure`, `InterstitialBackupChain.run/runSteps/step/link/rewarded`, `PlacementFlow`, `ResumeAdDirector.presentAdIfReady` (+ `callbackshow`, `isOpenAppDismiss`, `flagReturnAd`), `RewardAdRenderer.show()`, `ScreenAdPlanner.showAd/presentBanner/presentNative/resolve/adaptiveBannerHeightPx`, `guardSlot` (only from dead planner), `ShowFrequencyGovernor` counters, `AdSettingsVault.lastResultAdShape`, `AdUnitIds.slotEnabled`, `AdLog.read/dumpVault`, `lottie` dependency, 14 vault keys never written (§5.3). Grep evidence: `grep -rnF "showInterstitial" ads/src/main app/src/main` → 1 hit (definition); `grep -rnF "RewardAdRenderer()" …` → 0.
4. **Rewarded show failure counts as shown** (`RewardAdRenderer.presentIfLoaded` calls `onDone` on `onAdFailedToShowFullScreenContent`), so `if_ad_not_ready` and later steps with `show_all:false` are skipped.
5. **No ad-age expiry** for cached app-open (Google: 4 h) or inter/rewarded ads.
6. **No UMP privacy-options entry point** in the app (`AdKit.showPrivacyOptions` unused) — required when `privacyOptionsRequirementStatus == REQUIRED` (EEA). `ConsentDebugSettings` has no test devices.
7. **App-open on own returns**: `Ads.markOwnLaunch()` is never called by the app, so returning from the app's own share sheet, gallery picker, browser links in Settings (`SettingsScreen.kt:202-209`), etc. triggers `launcher.app_resume` (on in track_a's place config; track_a has `ads.on:false`, track_b has the place off — latent).
8. **Compose slot lifecycle**: `app:ads/AdSlot.kt` has no `onRelease`/`DisposableEffect`; banners are destroyed only when the *activity* finishes (`AdSlotGate.closeWithScreen`), so NavHost route changes inside `MainActivity` leave `AdView`s alive off-screen. Displayed `NativeAd`s are never destroyed.
9. **`ad_impression` duplicates**: the module logs Firebase `ad_impression` manually from paid events; if AdMob is linked to Firebase, AdMob's automatic `ad_impression` collection will double revenue in GA4.
10. **Init applies the default (ads off) config first** (`AdKit.init` → `AdsConfigApplier.apply(app, AdsConfig())`), clearing vault unit IDs until the collector applies the saved config on the next main-loop turn; and `AdKit.preload` runs once after consent — if the live config arrives later (first launch), nothing is re-preloaded on `apply` (exit ads still load on demand because `loadIfMissing = true`).
11. **`ads.preload` semantics**: exit steps always load on demand (`loadIfMissing = true`), so `preload` only affects warm-ups; `AdLoadMode` doc ("interstitials on-load by default", "single boolean accepted") contradicts the parser (object only, default true).
12. **Event triggers depend on a live process**: all receivers are runtime-registered; events while the app is killed are lost, and background events only show when the user next resumes an app activity (pending TTL 10 min). `ACTION_CLOSE_SYSTEM_DIALOGS` (Recents/Home detection in `RecentAdWatcher`, `SystemDialogHelper`, app `MainActivity`/`RecentsPolicy`) is deprecated since API 31; delivery to third-party apps on recent Android versions was not verified here — treat the Recents/Home key placements as unverified on API 31+.
13. **Charging screen** shows over the lock screen and turns the screen on with an ad (`showWhenLocked`, `turnScreenOn`) — Google Play / AdMob policy risk ("ads on lock screen" / disruptive ads) if `charger_*` places are turned on. Package and charging screens show unrelated "privacy/optimize check" content around ads (`PackageResultActivity`).
14. **Play Store jump** (`play_store_return.on: true` in track_b, `window_sec: 6000`): the first Recents press within 100 min of install opens the Play Store home — hijacks a system key; policy risk.
15. **Phone unlock interstitial** (track_b on, `[inter, rewarded]`, up to 5/day) on the launcher home — out-of-app style interstitial, policy risk.
16. `app_installed`/`app_uninstalled` share one `gap_sec` timestamp; `ExitAdRunner` marks `gap_sec` as used even when no ad ends up showing; `EveryCounter` resets every process start.
17. Collapsible banners request the expanded format on every load (no frequency limit); no RC slot uses banners today.
18. House ad layouts embed `qureka1.gif` (284 KB) and `adk_banner.webp` placeholders; the house CTA always opens the first `ads.links` URL regardless of the card.
19. Possible duplicate interstitial requests: `ChainInterstitial.preload` of the global unit only checks `NavigationInterstitial.hasPreloadedGoogle`, not an in-flight preload.
20. Not checked: runtime behaviour on device, the `launcher` module internals beyond its four `LauncherEvents` call sites, and the live Firebase RC values (only the repo's `docs/remote-config/*.json`, which are identical between `app_config.json` and `app_config_debug.json` per `diff`).

---

## Part B — FFF target ad mapping (proposal, not implemented)

FFF has **no ad code today** (see `TARGET_APP_AUDIT.md`). The plan reuses the reference engine's model unchanged: every ad is a **place** (`<group>.<name>`) whose `bottom_ad` / `list_ad` / `exit_ad` blocks come from Remote Config; code only calls `Ads.exit(activity, place) { proceed }` before navigating and `Ads.bottom(activity, container, place)` for slots. No placement is hard-coded on or off: an absent RC block means no ad (reference parser behaviour).

### B1. Formats carried over

| Format | Carried over | Notes for FFF |
|---|---|---|
| App Open (splash exit step + `launcher.app_resume`) | Yes | Resume ad via `AppResumeWatcher`; FFF must call `Ads.markOwnLaunch()` before share / rate / privacy links (reference bug A3.1). |
| Interstitial (exit step, `inter_splash`, `inter_back`) | Yes | |
| Rewarded (exit step / fallback) | Yes | Fix: show failure must call `onMiss` (reference bug §8.4). No reward-gated unlock exists in the reference, so none is proposed. |
| Native big / mid / mid2, Native banner, Fullscreen native | Yes | XML slots; destroy `NativeAd` on Activity destroy (reference bug §8.8). |
| Banner (adaptive / inline / collapsible) | Yes | |
| Custom house ads | Yes | Placeholder drawables (`qureka1.gif`, `adk_banner.webp`) are **not** copied; FFF-styled placeholder needed. |
| Direct links (`link` step) | Yes, RC-driven | Live reference values point to third-party game sites — FFF needs its own list or `[]`. |
| Rewarded Interstitial | Not present in reference → not added | |
| Event triggers: charging screen, package result, phone unlock, Recents → Play Store jump | **Decision required** | Lock-screen / system-key ads are Play & AdMob policy risks (§8.13–8.15) and unlock/Recents ads assume a launcher. Proposal: port the code paths behind RC places that default **off**, or drop them — see Pending questions. |

### B2. FFF placement table (proposed place keys)

| # | FFF file (to change) | Trigger | Kind | Place key | Reference equivalent |
|---|---|---|---|---|---|
| 1 | `presentation/splash/SplashActivity.kt` | After consent + RC ready + hold | preload all | — | AppNavHost.kt:91 |
| 2 | `SplashActivity.kt` (replaces fixed 2.8 s route) | Leaving splash | exit | `screens.splash` (unit `inter_splash`) | AppNavHost.kt:109 |
| 3 | `presentation/onboarding/OnboardingActivity.kt` + `activity_onboarding.xml` (new `adSlot` under CTA) | Each step shown | bottom | `onboarding.<step>` | OnboardingScaffold.kt:147 |
| 4 | `OnboardingActivity.kt` / `OptionAdapter.kt` (inline slot after option 2, as the design's `adAfter`) | Language / gender / age steps | bottom (inline native) | `onboarding.language/gender/age` | OnboardingPage.kt:91-95 |
| 5 | `OnboardingViewModel.next()` caller in `OnboardingActivity` | Continue / Skip | exit | `onboarding.<step>` | OnboardingFlow.kt:185 |
| 6 | `presentation/home/HomeActivity.kt` + `activity_home.xml` (bottom slot) | Home shown | bottom | `screens.home` | HomeScreen.kt:386 |
| 7 | `HomeActivity.kt` (tiles, tools, settings) | Open any destination | exit | `screens.home` | AppNavHost.kt:146-147 |
| 8 | `HomeActivity.kt` back callback (replaces double-back toast or runs before it — decision) | Back on Home | exit (unit `inter_back`) | `launcher.back_button` | AppNavHost.kt:140 |
| 9 | `presentation/catalog/ListActivity.kt` + `activity_list.xml` | List shown / item tap | bottom / exit | `screens.list` | new FFF place (reference parser accepts any `screens.<name>`) |
| 10 | `presentation/catalog/DetailActivity.kt` + `activity_detail.xml` | Detail shown / back | bottom / exit | `screens.detail` | new |
| 11 | `presentation/articles/ArticlesActivity.kt` (both classes) | Shown / open / back | bottom / exit | `screens.articles`, `screens.article` | new |
| 12 | `presentation/calculator/CalculatorActivity.kt` (both classes) | Shown / calculate / back | bottom / exit | `screens.calculator`, `screens.calc` | new |
| 13 | `presentation/settings/SettingsActivity.kt` | Shown / back | bottom / exit | `screens.settings` | SettingsScreen.kt:218, AppNavHost.kt:158 |
| 14 | `presentation/settings/LanguageActivity.kt` | Shown / Apply | bottom / exit | `screens.language` | new |
| 15 | `:ads` `AppResumeWatcher` (no FFF code) | Background → foreground | exit (`app_open`) | `launcher.app_resume` | AppResumeWatcher.kt:31 |

FFF onboarding step keys (stable, used in RC and analytics): `language, gender, age, style, fav, mode, weapon, goal, char, tips, room, map, daily` (same names as the `step_*` strings).

Not carried over unless the launcher is approved: `launcher.host_app_panel, suggested_panel, app_drawer, app_open, app_exit, sponsored_app_open/exit, home_button, recents_button, phone_unlock, play_store_return`, `uninstall_flow.*`.

### B3. Rules the FFF integration must keep
- One ad flow at a time (`ExitAdRunner.busy()`); navigation always continues in `onDone` even on no-fill / offline / timeout.
- `gap_sec`, `every`, `if_ad_not_ready`, `show_all`, `links_count` honoured from RC; nothing hard-coded.
- Consent (UMP) before any ad request; Settings gets a **Privacy options** row when `AdKit.privacyOptionsRequired()`.
- Release build must fail (not warn) without a real AdMob app id; RC must carry real unit ids before release.
