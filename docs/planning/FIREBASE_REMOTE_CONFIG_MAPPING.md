# Firebase Remote Config Mapping — reference audit, live values, FFF proposal

Part A = reference code/docs audit. Part B = live values fetched 2026-10-30 (read-only client fetch, template v52). Part C = proposed FFF schema (for review).

## Part A — Firebase Remote Config audit — 179-Body-Scanner-Home-01 (all modules, READ-ONLY)

Paths: `P/` = `/Users/pooja/Documents/Pooja_Apps/10_OCT/179-Body-Scanner-Home-01`, `S/` = `P/app/src/main/java/com/xray/fullbody/scanner/camera`.

### 0. Firebase project
`P/app/google-services.json` (tracked in git): project_id **`body-scanner-home---01`**, project_number 317438549805, package `com.xray.fullbody.scanner.camera` (single client). Keys not recorded.
Dependency: `firebase-config` via BOM 33.10.0 in :app only. `:ads` has firebase-analytics only; `:launcher` has no Firebase dependency. **The only class that touches `FirebaseRemoteConfig` in the whole project is `S/config/AppConfigStore.kt`** (grep `RemoteConfig` across all modules).

### 1. Sources of truth found
| Source | Path | Notes |
|---|---|---|
| Guide | `P/docs/remote-config/RC_GUIDE.md` | 212 lines, "draft for review" |
| Release JSON | `P/docs/remote-config/app_config.json` | identical byte-for-byte to the debug file (`diff` → same) |
| Debug JSON | `P/docs/remote-config/app_config_debug.json` | same as above |
| Template cache | `P/.remote-config-template.cache.json` | tracked in git; `{entries:{app_config, app_config_debug}, state:"UPDATE", templateVersion:"20"}`; provenance unknown (looks like a snapshot of the published Firebase template); **differs from docs** (see §6) |
| Test fixture | `P/app/src/test/resources/app_config.json` | older copy of the docs JSON (see §6) |
| In-app defaults | **none in Firebase sense**: no `setDefaultsAsync`, no `remote_config_defaults.xml`, no defaults map (grep `setDefaults` → none). Defaults are Kotlin data-class defaults in `S/config/AppConfig.kt` + parser fallbacks in `S/config/AppConfigParser.kt`, `P/launcher/.../api/LauncherSettings.kt`, `P/ads/.../config/AdsConfig.kt`. |

### 2. Firebase RC keys (top-level parameters)
| RC key | Type | In-app Firebase default | Read by | When |
|---|---|---|---|---|
| `app_config` | String (JSON) | none → `""` | `AppConfigStore.sync` `rc.getString(rcKey)` (AppConfigStore.kt:90) | release builds (`rcKey = if (BuildConfig.DEBUG) "app_config_debug" else "app_config"`, AppConfigStore.kt:23) |
| `app_config_debug` | String (JSON) | none → `""` | same | debug builds |
| `home_profile_rules` | String (JSON) | none → `""` (= launcher built-in table) | AppConfigStore.kt:92 → `LauncherSettings.homeProfileRulesJson` → `LobbyPersonaLocator.resolve` / `LobbyPersonaApplier` (launcher/persona) | same key for debug and release |
| `home_profile_probes` | String (JSON) | none → `""` (= `ApkPersonaDecoder.DEFAULT_PROBES_JSON`) | AppConfigStore.kt:93 → `homeProfileProbesJson` → `LobbyPersonaLocator.resolve` | same |
Total: **4 RC parameters** (2 used per build). `home_profile_rules` / `home_profile_probes` are **not present in the cached template** (only app_config / app_config_debug) and not in docs JSON → currently unpublished → built-ins used. Note `LobbyPersonaLocator.kt:9` comment says they are "Served from app_config ... -> launcher.home_profile_rules" — inaccurate; code reads them as separate top-level keys (matches RC_GUIDE line 8).
Also: rules/probes are only saved when `app_config` is non-blank (AppConfigStore.kt:91-97) and published together.

### 3. Fetch / activate logic (`S/config/AppConfigStore.kt`)
- `init()` (App.onCreate, App.kt:49): resolves country (`CountryResolver`: SIM → network → `Locale.getDefault().country`), publishes saved JSON from prefs `live_config` immediately and sets `ready=true`; then `fetchRemote` (skips + ready=true if no FirebaseApp).
- `sync()` (AppConfigStore.kt:80): `minimumFetchIntervalInSeconds = 0` in debug, `rc_sync_hours × 3600` in release (hours from the config currently in memory: saved or code default 1). `setConfigSettingsAsync(...).continueWithTask { fetchAndActivate() }`. On complete (success **or failure**): `getString(rcKey)` (activated value, so a failed fetch re-publishes the last activated cache) → `publish()` → saved to prefs only if parse succeeds → `ready=true` → `scheduleNextSync`.
- Periodic: `Handler.postDelayed(sync, hours × 3_600_000)` on main looper while the process lives (launcher keeps it alive); `rc_sync_hours <= 0` → no in-process timer.
- Forced: silent FCM topic `rc_sync`, data `type=rc_sync` → `RcSyncMessagingService.onMessageReceived` → `syncNow()` → `rc.fetch(0).onSuccessTask { activate() }`, awaited ≤20 s (RcSyncMessagingService.kt:16). Subscribed every launch (App.kt:51).
- **No real-time listener** (`addOnConfigUpdateListener` absent). No timeout on the fetch itself; the splash bounds it (below).
- Splash wait: hold `base.splash_wait_sec` (from current config; first launch = default 4) then `withTimeoutOrNull(6_000) { ready.first { it } }` (SplashScreen.kt:117-121, `CONFIG_WAIT_MS`). Returning users: `ready` already true (saved JSON) → never wait for the fresh fetch; the new values apply live when the fetch lands (flows collected by AdKit/launcher/onboarding config). Onboarding freezes its screen list at start (OnboardingFlow.kt:151) so a mid-flow refresh doesn't reshuffle.
- Background threads `InstallAttribution` and `ExclusiveLocations` wait ≤5 s for `ready` before reading their specs (InstallAttribution.kt:60, ExclusiveLocations.kt:50). The splash then waits ≤3 s for both (AppNavHost.kt:101-104) and `lock()`s them; later verdicts apply next launch.
- Caching: Firebase's own activated cache + app prefs `live_config` (`app_config[_debug]`, `home_profile_rules`, `home_profile_probes`). Invalid JSON → keeps previous config (publish returns false). Blank/missing key → nothing published, saved copy kept indefinitely.
- `refreshTrack()` re-parses the last JSON when the organic/paid verdict or location changes.
- Duplicate / extra fetches:
  1. A process started only for the FCM push runs `App.onCreate` → `init` → normal sync, **and** `syncNow` → `fetch(0)`: two fetches back to back.
  2. `fetch(0)` in release bypasses the min interval; topic pushes to all installs at once = thundering herd and possible `FirebaseRemoteConfigFetchThrottledException` (handled: completion listener still runs).
  3. Each `sync` re-applies `setConfigSettingsAsync`; harmless.
  4. `syncNow` cancels/reschedules the hours timer (scheduleNextSync removes previous callback) — fine. No guard against overlapping syncs.

### 4. `app_config` JSON — every parameter
Merge rule (AppConfigParser.kt:38-69): pick `track_a` (organic) or `track_b` (paid); if `base.exclusive_locations.on` and the saved IP location matches `list` → force `track_a`; then merge `track_x.countries[KEY]` where KEY is a comma list containing the device country (objects merged key-by-key, lists/scalars replaced; all matching keys merged in iteration order); `base` read only from root. Wrong type → default; unknown enum names dropped.
Track choice: `InstallAttribution.track()` = saved verdict else `track_a`; `forcePaidUser(true)` (App.kt:47, commented out) forces `track_b`.

Legend: Default = in-app default when key missing / wrong type. Docs = value in `docs/remote-config/app_config.json` (A = track_a, B = track_b).

### 4.1 Root
| Key | Type | Default | Docs | Parser | Consumers |
|---|---|---|---|---|---|
| `schema` | int | — | 1 | **never read** | none |
| `base` | object | {} | — | `base()` | — |
| `track_a`, `track_b` | object | {} | — | `parse()` | — |

### 4.2 `base` (BaseConfig, AppConfig.kt:25)
| Key | Type | Default | Docs | Parser | Consumer(s) |
|---|---|---|---|---|---|
| `privacy_link` | string | "" | "" | AppConfigParser.kt:72 | **none** (grep `privacyLink` → only AppConfig/Parser) |
| `terms_link` | string | "" | "" | :73 | **none** |
| `store_link` | string | "" | "" | :74 | SettingsScreen.kt:152 (Rate / Share link; fallback Play URL) |
| `update_link` | string | "" | "" | :75 | SettingsScreen.kt:209 (Update row; fallback store link) |
| `app_update.show` | bool | false | true | :76 | PlayPrompts.kt:44 (`AppUpdatePrompt.onResume`, from MainActivity.kt:98) |
| `app_update.force` | bool | false | false | :77 | PlayPrompts.kt:45 (IMMEDIATE + finish on decline) |
| `splash_wait_sec` | int ≥0 | 4 | 4 | :78 | SplashScreen.kt:118; (UninstallFlowActivity reuses SplashScreen, bar uses this not splash_ms) |
| `rc_sync_hours` | int ≥0 | 1 | 1 | :79 | AppConfigStore.kt:81, :120 |
| `attribution.gclid_min_length` | int ≥1 | 20 | 20 | :83 | `AttributionRules.paidGclid` (InstallAttribution.kt:128) |
| `attribution.fake_gclids` | string[] | [] | ["123456789"] | :84 | same (exact match). Docs value has 9 chars (< 20) so it is redundant |
| `attribution.google_ip_check.on` | bool | false | true | :85 | InstallAttribution.kt:63 |
| `attribution.google_ip_check.endpoint` | string | "" | "https://ipconfig.io/json" | :86 | `onGoogleNetwork` (HttpURLConnection, 3 s timeouts) |
| `attribution.google_ip_check.field` | string | "asn_org" | "asn_org" | :87 | `AttributionRules.googleNetwork` |
| `attribution.google_ip_check.match` | string[] | [] | ["Google LLC"] | :88 | same (case-insensitive) |
| `exclusive_locations.on` | bool | false | false | :92 and :43 | `ExclusiveLocations.start` (ExclusiveLocations.kt:52), parser :43-44 |
| `exclusive_locations.endpoint` | string | "" | "https://ipconfig.io/json" | :92 | ExclusiveLocations.lookup (fields country, country_iso, region_name, region_code, city) |
| `exclusive_locations.list` | string[] | [] | [] | :92, :43 | `LocationRules.matches` (country/code/state/state code/city, case-insensitive) |
| `ad_network` | enum google/custom | GOOGLE | "google" | :136 | ads: AdsConfigApplier.kt:31, ExitAdFlow.kt:111, BottomAdLoader.kt:44 |
| `ad_units.inter/inter_splash/inter_back/app_open/native/banner/rewarded` | string | "" | Google **test** unit ids (ca-app-pub-3940256099942544/…) | :137-145 | ads: AdsConfigApplier.kt:46-52, ExitAdRunner.kt:118-123 |

### 4.3 `track_x.ads` (AdsConfig, `P/ads/src/main/java/com/appkit/ads/config/AdsConfig.kt`; parser AppConfigParser.kt:96-153; consumers in ads module — internals for the ads agent)
| Key | Type | Default | Docs A / B | Consumer |
|---|---|---|---|---|
| `on` | bool | false | **A false** / B true | AdsConfigApplier.kt:30 (`IsAdsON`), Ads.kt |
| `loader_wait_sec` | int | 8 | 8 / 8 | AdsConfigApplier.kt:32 |
| `preload.inter/native/app_open/rewarded` | bool | true each | true / true | AdsConfigApplier.kt:41-44 |
| `formats_on.inter/native/banner/native_banner/app_open/rewarded` | bool | true each | true / true | ExitAdFlow.kt:113-115, BottomAdLoader.kt:40-41, AdsConfigApplier.kt:36-37 |
| `if_ad_not_ready` | string[] (ad step / "link" / URL) | [] | ["rewarded"] / ["rewarded"] | ExitAdFlow.kt:84 |
| `links` | string[] | [] | ["https://www.examples.com"] (placeholder) | ExitAdFlow.kt:90, AdsConfigApplier.kt:58 |
| `links_count` | int | 0 | 2 / 2 | `AdsConfig.linksFor` (AdsConfig.kt:28-30) |
| `show_all` | bool | true | true / true | `showAllFor` (AdsConfig.kt:35), ExitAdFlow.kt:77 |
| `links_open_in` | enum in_app_browser/webview/browser | IN_APP_BROWSER | in_app_browser | AdsConfigApplier.kt:59, AdViewKit.kt |
| `custom_ads.on` | bool | false | false | ExitAdFlow.kt:110, AdsConfigApplier.kt:54 |
| `custom_ads.list[]` {icon, title, description, bannerImage, buttonText} | object[] of strings | [] | [] | AdsConfigApplier.kt:55 (Gson) |
| `colors.light/dark.{button, button_text, background, text}` | `#RRGGBB` (else "") | "" | A #1A6CF2…, B #016770… | AdsConfigApplier.kt:72-74 |
| `screens.<place>` / `onboarding.<place>` / `launcher.<place>` / `uninstall_flow.<place>` | object | absent = no ad | see below | `places` map key `"<group>.<name>"` |

Place object (AppConfigParser.kt:168-184): `bottom_ad {on bool=false, type enum=banner}`, `list_ad {same}`, `exit_ad {on bool=false, ads [inter|rewarded|app_open|full_native|custom|link], links_count int?, links string[]?, show_all bool?, every int≥1 =1}`, every other key kept raw in `options` (read via `AdPlace.bool/int`): `during_onboarding` (Ads.kt:62), `gap_sec` (ExitAdRunner.kt:52, ChargeEventWatcher.kt:88, PackageEventWatcher.kt:116, RecentAdWatcher.kt:115), `on` (Ads.kt:167 — event places), `start_after_hours`=24 / `gap_minutes`=30 / `max_per_day`=5 (UnlockAdWatcher.kt:86-90), `window_sec` (RecentAdWatcher.kt:153, **code default 180 vs docs 6000**), `show_ad_screen` (RecentAdWatcher.kt:112), `preload` (AdsConfigApplier.kt:64). Slot types: banner, banner_inline, banner_collapsible, native_big, native_mid, native_mid2, native_banner, native_full.
Places used by :app call sites: `screens.splash|home|settings`; `onboarding.<13 screen keys>`; `launcher.app_resume, back_button, home_button, recents_button, app_exit, app_open, host_app_panel, suggested_panel (+list_ad), app_drawer (+bottom_ad_row), drawer_sponsored_apps, sponsored_app_open, sponsored_app_exit, phone_unlock, charger_connected, charger_disconnected, app_installed, app_uninstalled, play_store_return`; `uninstall_flow.splash|language|step1|step2|step3|progress`.
Special parse: `ads.launcher.drawer_sponsored_apps {on=false, every int≥1 =8, list[{logo, landing_url, label}] (blank landing_url dropped)}` → `LauncherSettings.sponsoredApps` (AppConfigParser.kt:218-224; consumer launcher `PromotedTiles.kt:23`); `ads.launcher.app_drawer.bottom_ad_row` int=1 → `drawerAdRow` (launcher `HubPromosSwitches.kt:34`).
Feature places `screens.<feature key>` are not used (FeatureId comment: "if ads are added later").

### 4.4 `track_x.onboarding` (AppConfigParser.kt:186-191)
| Key | Type | Default | Docs A / B | Consumer |
|---|---|---|---|---|
| `order` | string[] of OnboardingScreen keys (language, gender_select, age_select, body_scan, old_body, body_parts, interests, camera, energy_level, human_species, welcome, permission_hub, default_home) | the 10 OB screens | A: 10 + default_home last; B: **default_home first** + 10 | OnboardingRules.kt:13, OnboardingFlow.kt:151 |
| `skip_button` | bool | false | true / true | OnboardingFlow.kt:273 |
| `default_home.skip_button` | bool | false | true / true | same |
| `default_home.auto_open_settings_sec` | int ≥0 | 3 | 3 / 3 | OnboardingFlow.kt:318 |

### 4.5 `track_x.permissions` (AppConfigParser.kt:193-196, :61)
| Key | Type | Default | Docs A / B | Consumer |
|---|---|---|---|---|
| `<screen>` (splash, any onboarding key, permission_hub, home) | string[] of camera/photos/notification/contacts/phone/overlay | {} (nothing asked) | permission_hub & home: [camera, photos, notification]; B `countries."IN,PK"`: permission_hub [camera], home [photos, notification] | `AskPermissionsOnOpen` (Permissions.kt:118 via AppNavHost.kt:95, OnboardingFlow.kt:285), PermissionStep.kt, HomePermissionSheet.kt:116 |
| `home_sheet` | bool | true | true / true | HomeScreen.kt:142 |
Note: the parser treats every key of `permissions` as a screen; `home_sheet` is a boolean so `arr()` returns null and it is skipped — OK. `photos` never maps to a runtime permission; `contacts/phone/overlay` never asked (not declared in manifest).

### 4.6 `track_x.launcher` (LauncherSettings, AppConfigParser.kt:200-226)
| Key | Type | Default | Docs A / B | Consumer |
|---|---|---|---|---|
| `on` | bool | false | true / true | App.kt:77-78 (installs launcher), OnboardingRules.kt:13, OnboardingFlow.kt:169, SettingsScreen.kt:167 |
| `os_style` | bool | false | false | launcher `LobbyPersonaApplier.kt:64-68` |
| `default_home_banner` | bool | false | true | SettingsScreen.kt:167 |
| `our_icon.in_drawer` / `on_home` | bool | true | true | AppLauncherHost.settings (AND !OurIcon.hidden) → launcher |
| `panels.host_app` | bool | true | A true / **B false** | AppLauncherHost (AND user homePanel) → launcher |
| `panels.suggested_panel` | bool | false | false | launcher `HubPromosSwitches.kt:28` |
| `swipe_guide.on / swipe_right_host_app / swipe_left_suggested_panel / swipe_up_drawer` | bool | false / true / true / true | false/true/true/true | launcher `HubPromosSwitches.kt:18` |
| `time_widget` | bool | true | true | AppLauncherHost (AND user timeWidget) |
| `search_widget` | "google"/"chrome" | "google" | "google" | launcher (`LauncherSettings.searchWidget`) |
| `system_keys.home` | next/back/reset/"" | next | next | OnboardingRules.onKey, AppLauncherHost.takesHome |
| `system_keys.back` | next/back/"" (reset not allowed) | next | next | OnboardingRules.onKey |
| `system_keys.recents` | next/back/reset/"" ; missing = null (nothing) | null | next / next | OnboardingRules.onKey, RecentsPolicy.kt:155-158 |
| `system_keys.bottom_sheet.on` | bool | false | A false / B true | `SystemKeys.opensSheet` |
| `system_keys.bottom_sheet.keys` | string[] home/back/recents | [] | ["back"] | same |
(`system_keys` is parsed from `launcher.system_keys`, AppConfigParser.kt:63.)

### 4.7 `track_x.uninstall_flow` (AppConfigParser.kt:244-249)
| Key | Type | Default | Docs A / B | Consumer |
|---|---|---|---|---|
| `flow` | "premium" else normal | normal | A normal / **B premium** | AppLauncherHost (hostUninstall), AppLauncherEvents.onUninstallHostApp, UninstallFlowActivity.kt:53, UninstallDialogActivity.kt:28 |
| `system_dialog` | bool | false | false | AppLauncherEvents, UninstallDialogActivity |
| `splash_ms` | int | 3000 | 3000 | UninstallFlowActivity.kt:88 |
| `progress_ms` | int | 4000 | 4000 | UninstallFlowActivity.kt:117 |

### 4.8 `track_x.prompts.rate_us`
`after_opens` int default 10 (docs 10), `retry_after_hours` int default 24 (docs 24) → `RatePrompt.due` (PlayPrompts.kt:105) via AppNavHost.kt:135.

### 4.9 `track_x.countries`
Object; key = comma-separated ISO-3166 alpha-2 list; value = partial audience block. Docs: A `{}`; B `{"IN,PK": {permissions...}}`. Country from SIM/network/locale (NOT IP) — `CountryResolver` (Resolvers.kt:18-24). Empty country → no overlay.

### 5. `home_profile_rules` / `home_profile_probes` schemas (launcher)
- `home_profile_rules`: either a JSON array of rules or `{ "disabled": bool, "rules": [...] }` (`LobbyPersonaPolicies.parse`, launcher/persona/LobbyPersonaPolicies.kt:105). Rule = `{ "match": {...}, "profile": {...} }`.
  - match keys (string or string[] for package/manufacturer/model/device/product; numbers otherwise): package, manufacturer, model_prefix, version_code_min/max, sdk_min/max, sw_dp_min/max, model, device, product, os_version_min/max, os_version_prefix, density_min/max, width_dp_min/max, height_dp_min/max, nav_mode.
  - profile keys (`LobbyPersona.fromJson`): id, home_cols, home_rows, dock_size, drawer_mode (e.g. paged/none), drawer_cols, drawer_rows, drawer_search, home_labels, drawer_labels, drawer_search_position, icon_scale, label_sp, icon_dp, dock_icon_dp, drawer_icon_dp, icon_width_fraction, icon_width_fraction_by_dpi {dpi: fraction}, dock_icon_width_fraction, drawer_icon_width_fraction, icon_inset, legacy_icon_tray (e.g. "white").
  - `disabled: true` → persona resolution off. Parse error → null → built-in `DEFAULT_RULES_JSON`.
- `home_profile_probes`: object keyed by launcher package → `{ "<profile field>": ["<resource name>", ...], "_verified": "note" }` (e.g. `com.miui.home: {home_cols:["config_cell_count_x"], home_rows:["config_cell_count_y"]}`); default `ApkPersonaDecoder.DEFAULT_PROBES_JSON`. Detailed semantics → launcher agent.

### 6. Drift between sources
- docs `app_config.json` == `app_config_debug.json` (identical, incl. Google test ad units) — docs line 41 acknowledges; real units must go into `app_config` before release.
- Cached template (v20) vs docs: cache lacks `base.rc_sync_hours`, `base.exclusive_locations`, all `during_onboarding`, `system_keys.recents`, `auto_open_settings_sec`, `home_sheet`, `system_dialog`; cache **track_a.ads.on = true** (docs false) but nearly every track_a slot/exit `on=false` and preload all false, `screens.home.bottom_ad.type = banner`; cache **track_a.onboarding.order = ["language"] only**; cache track_a has extra `permissions.language = ["notification"]`; track_b cache `onboarding.default_home.exit_ad.on` and `screens.home.exit_ad.on` false (docs true). Keys missing from the cache fall back to code defaults; these equal the docs values except `system_keys.recents` (code default null = nothing, docs "next"). If the cache reflects the live template, organic users currently see only the language screen in onboarding and almost no ads.
- Test fixture (`app/src/test/resources/app_config.json`) is older than docs (lacks rc_sync_hours, exclusive_locations, during_onboarding, home_sheet, system_dialog, recents, auto_open_settings_sec; track_a links [] / links_count 0; different exit ad lists, adds `rewarded` steps).

### 7. Flags
### Declared but never read (grep evidence)
- `schema` — grep `"schema"` in app/ads/launcher → only an unrelated XML namespace in launcher `ApkPersonaDecoder.kt:13`.
- `base.privacy_link`, `base.terms_link` — `grep -rn privacyLink|termsLink` → only `config/AppConfig.kt` & `AppConfigParser.kt`. (No privacy policy shown anywhere in-app.)
- Parsed-into-map but unused places: `screens.<feature>` none defined; all docs places have consumers.
### Read with no Firebase default
All 4 RC keys (`getString` with no `setDefaultsAsync`); first launch offline → `""` → code defaults (`AppConfig()`): ads off, launcher off, onboarding = 10 screens, no permissions asked, organic.
### Code defaults that differ from docs / behaviour
`play_store_return.window_sec` default 180 (docs 6000); `system_keys.recents` missing = null (docs sets "next"); `ads.on` default false; `launcher.on` default false (first offline launch = plain app, launcher installed later when config arrives — App.kt:78).
### Hardcoded values that look like they should be remote
Splash config wait 6 000 ms (SplashScreen.kt:181); attribution+location splash wait 3 000 ms (AppNavHost.kt:101); RC-ready wait 5 000 ms (InstallAttribution.kt:60, ExclusiveLocations.kt:50); IP lookup timeouts 3 000 ms; FCM sync await 20 s; rate prompt delay 1 500 ms (AppNavHost.kt:132); CrashLoopGuard 60 s / 3 / 5; ANR 5 s; key de-dupe 300 ms; default-home Home debounce 5 s; Play fallback URL `https://play.google.com/store/apps/details?id=<pkg>`; `feedback_email` string resource (empty); AdMob app id via build property (test id fallback).
### Country / geo
- `countries` overlay by SIM/network/locale country.
- `base.exclusive_locations` by IP lookup (country/state/city names or codes) → forces organic. Saved location persists; failed lookup keeps last; ambiguous codes (e.g. IN = India / Indiana) noted in docs.
- `attribution.google_ip_check` by IP ASN org (`Google LLC`) → organic.
### Permission-related config
`permissions.<screen>` lists, `permissions.home_sheet`, country overrides (B IN,PK). Names contacts/phone/overlay accepted by parser but cannot be requested (not in manifest).
### Activity / screen exclusion lists
No RC-driven activity exclusion list in :app. Ad suppression is code-driven: `AdKit.noResumeAdOn = { DefaultHomeStep.shown }`, `Screens.ownLeave` (App.kt:60), `AdKit.recentsAdScreenOn = { onboardingDone }` (App.kt:70); `AdKit.isHomeScreen`. (Ads module may have its own lists — ads agent.)
### Other risks
1. Attribution decided before RC loads on first install falls back to defaults after 5 s: `google_ip_check.on` default false and `fake_gclids` [] → a Google review device with a gclid would be classed **paid** permanently (verdict never re-evaluated once saved).
2. Paid track (`track_b`) drives the most aggressive behaviour: default_home first, premium uninstall (icon hide), bottom-sheet on Back, phone_unlock ads on, play_store_return on.
3. Release still has Google test ad units in both RC JSONs and the test AdMob app id in the build.
4. `fetch(0)` per push + duplicate fetch on push-started process; no real-time listener; returning users see new values only after the next sync (≥1 h by default) or a push.
5. Docs/cache/test fixture out of sync; no single generated defaults file.
6. Saved `live_config` never expires; deleting `app_config` in the console leaves devices on their last copy forever.

---

## Part B — Live values (fetched 2026-10-30)

Fetched read-only through the public Remote Config client endpoint with the reference app's own `google-services.json` (project `body-scanner-home---01`, country `US`, language `en-US`). API key not recorded. Raw copies kept outside the repo.

- Template **version 52**, `state: UPDATE`. Only `app_config` and `app_config_debug` are published; **`home_profile_rules` / `home_profile_probes` are not published** (launcher built-ins apply).
- The repo's `.remote-config-template.cache.json` is version 20 and `docs/remote-config/*.json` differ from live in **114** (release) / **112** (debug) leaf values. Live `app_config` ≠ live `app_config_debug` (debug adds `base.exclusive_locations.list = ["GJ"]`; release adds real privacy/terms links).
- **All `base.ad_units.*` (inter, inter_splash, inter_back, app_open, native, banner, rewarded) are Google TEST ids in both live keys.** No real unit ids exist anywhere.

| Path | Docs | Live (`app_config`) |
|---|---|---|
| `base.rc_sync_hours` | 1 | **24** |
| `base.privacy_link` / `terms_link` | "" | set (privacypolicyzone.com URLs for the reference package) |
| `track_a.ads.on` | false | **true** |
| `track_a.ads.preload.*` | true | **false** (all four) |
| `track_a.ads.links` | `["https://www.examples.com"]` | two third-party game-site URLs |
| `track_a` places | most `on: true` | **almost all `on: false`** (splash, home exit, settings bottom, every onboarding bottom/exit, all launcher places, uninstall places) |
| `track_a.ads.screens.home.bottom_ad.type` | native_banner | **banner** |
| `track_a.onboarding.order` | 10 screens + default_home | **`["language"]` only** |
| `track_a.permissions.splash` | — | `["notification"]` (new key) |
| `track_a/b.permissions.home_sheet` | true | false |
| `track_a/b.permissions.home` | camera, photos, notification | `["notification"]` |
| `track_a/b.uninstall_flow.system_dialog` | false | true |
| `track_b.ads.on` | true | **false** |
| `track_b` onboarding exit ad order | link, inter, app_open | link, **app_open, inter** |
| `track_b.ads.launcher.phone_unlock` | 24 h / 30 min / 5 per day | **0 / 0 / 1000** (with ads off) |
| `track_b.launcher.system_keys.bottom_sheet.keys` | ["back"] | ["recents"] |
| `*.ads.launcher.drawer_sponsored_apps` | every 8, list [] | every 6, list of external apps |
| `track_b.onboarding.order` | default_home first | default_home first + 10 (no welcome / permission_hub) |

Not invented: everything above is copied from the fetched JSON; values not shown were equal to the docs.

---

## Part C — Proposed FFF Remote Config (for review; nothing published)

### C1. Parameters
| RC key | Type | Purpose | FFF default (in code + `res/xml/remote_config_defaults.xml`) |
|---|---|---|---|
| `app_config` | JSON string | release config (same root shape: `schema`, `base`, `track_a`, `track_b`) | parsed from defaults below |
| `app_config_debug` | JSON string | debug builds | same |
| `home_profile_rules`, `home_profile_probes` | JSON string | launcher only | **only if the launcher is approved** |

Unlike the reference, FFF ships Firebase in-app defaults (`setDefaultsAsync`) so the first offline launch is deterministic.

### C2. Kept from the reference (same keys, same code defaults)
`base.{privacy_link, terms_link, store_link, update_link, app_update.{show,force}, splash_wait_sec(4), rc_sync_hours(1), ad_network, ad_units.*, attribution.*, exclusive_locations.*}`; `track_x.ads.*` (every key in Part A §4.3 / ADS mapping §5.2); `track_x.onboarding.{order, skip_button}`; `track_x.permissions.{<screen>, home_sheet}`; `track_x.prompts.rate_us.{after_opens(10), retry_after_hours(24)}`; `track_x.countries`.

### C3. FFF-specific values (not in the reference)
| Path | Type | Default | Consumer (to create) |
|---|---|---|---|
| `track_x.onboarding.order` | string[] | `["language","gender","age","style","fav","mode","weapon","goal","char","tips","room","map","daily"]` (current FFF order) | `OnboardingViewModel` (filters `onboardingSteps()`) |
| `track_x.ads.onboarding.<fff step>` | place | absent → no ad | `OnboardingActivity` |
| `track_x.ads.screens.{list,detail,articles,article,calculator,calc,language}` | place | absent → no ad | respective Activities |
| `track_x.permissions.<screen>` names | string[] | `{}` | only `notification` is meaningful for FFF (no camera/photos features) |

### C4. Dropped (Body-Scanner-only)
Onboarding keys `gender_select, age_select, body_scan, old_body, body_parts, interests, camera, energy_level, human_species, welcome, permission_hub` (FFF keys replace them); `launcher.*`, `system_keys.*`, `uninstall_flow.*`, `home_profile_*` unless the launcher is approved.

### C5. Values that must come from you (not invented)
Firebase project + `google-services.json` for `com.fffemotes.skintools.master`; AdMob app id; real unit ids for every format used; `ads.links` (or `[]`); privacy / terms / store links; which track is paid vs organic for FFF and whether attribution (gclid / Google-IP check) is wanted.
