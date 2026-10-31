# Reference vs Target Comparison

Reference = `179-Body-Scanner-Home-01` (`R/`). Target = FFF `SkinHubSkinsBundles` (`T/` = `app/src/main/java/com/fffemotes/skintools/master/`).
Every module below lists: 1 Reference · 2 FFF today · 3 Required change · 4 Create · 5 Modify · 6 Preserve · 7 Dependencies · 8 RC · 9 Ads · 10 Tests.
Status tags: **PORT** (bring over), **ADAPT** (rebuild in XML / FFF terms), **DECIDE** (needs your call), **SKIP** (Body-Scanner-only).

## Global incompatibilities (affect every module)

| Topic | Reference | FFF | Consequence |
|---|---|---|---|
| UI toolkit | Compose + Navigation Compose in `:app` | XML + ViewBinding, one Activity per screen, no Compose allowed | Every reference screen/flow is **re-expressed in XML**; only plain-Kotlin logic (config, rules, ads engine) is ported as code. |
| minSdk | 26 (all modules, Fossify AAR forces 26) | 24 | `:ads` can drop to 24 (GMA 25.5.0 and UMP 3.1.0 declare minSdk 24/21; only `DebugEventLog` has an O+ branch, already guarded). `:launcher` cannot without replacing Fossify. |
| AGP / Kotlin | AGP 8.9.1, Kotlin plugin 2.2.10, Java 17 | AGP 9.4.1 (built-in Kotlin), Java 11 | FFF moves to Java 17; google-services / crashlytics Gradle plugins must be verified on AGP 9 (risk R4). |
| DI | Kotlin `object` singletons | `AppContainer` manual DI + ViewModels | Ported singletons stay `object`s inside `:ads`; app-side config/attribution are exposed through `AppContainer` repositories. |

---

## M1. Project architecture — ADAPT
1. 3 modules `:app` `:ads` `:launcher`; single Compose Activity; `object` singletons; no BaseActivity.
2. 1 module, clean-architecture packages (`data/domain/presentation/di`), 11 Activities, ViewModels.
3. Add `:ads` library module; keep FFF layering; add `data/remote/` (config) and `data/attribution/` packages; add an `AdActivity` helper rather than a BaseActivity (FFF has none — decision D7).
4. `ads/` module (see file plan); `T/data/remote/*`, `T/data/attribution/*`, `T/core/crash/*`.
5. `settings.gradle.kts` (include `:ads`, jitpack not needed), `T/di/AppContainer.kt`.
6. All `T/domain/*`, `T/data/local/*`, `T/presentation/*` screens.
7. none new at this level.
8. —  9. — 10. Build both modules; existing `CalculateUseCaseTest` still passes.

## M2. Gradle & dependencies — PORT
1. libs.versions.toml (see REFERENCE_APP_AUDIT §1): firebase-bom 33.10.0 (config, analytics, crashlytics, messaging), play-services-ads 25.5.0, ump 3.1.0, browser 1.8.0, shimmer 0.5.0, gson 2.11.0, glide 4.16.0, play app-update 2.1.0, play review 2.0.2, installreferrer 2.2, coroutines-play-services 1.10.2, lifecycle-process, google-services 4.4.4, crashlytics plugin 3.0.6; release `admobAppId` placeholder logic.
2. No Firebase / ads / play-core / referrer deps; no google-services plugin; no proguard file; no signing.
3. Add the subset actually used by ported code. **Do not add**: Compose BOM/libraries, navigation-compose, camerax, coil, room, fossify-commons, lottie (unused even in reference), work-runtime (unused in reference app).
4. `app/proguard-rules.pro`, `ads/consumer-rules.pro`.
5. `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts` (Java 17, `buildConfig = true`, manifest placeholder `admobAppId`, release app-id check → **fail** instead of warn), `gradle.properties` if needed.
6. sdp/ssp, material, lifecycle versions (align, don't downgrade: FFF already newer than reference for core-ktx/material/lifecycle).
7. As listed in 1, minus exclusions.
8. — 9. AdMob app id placeholder. 10. `assembleRelease` fails without `admob.appId`; debug uses test id.

## M3. Application init — ADAPT
1. `App.kt`: CrashGuard attach → prefs preload thread → Crashlytics guard → `AppConfigStore.init` (track resolver) → FCM topic `rc_sync` → `Languages.applySaved` → `InstallAttribution.start` / `ExclusiveLocations.start` → `Analytics.init(segment)` → `Screens.install` → `SystemKeys.listen` → `RecentsPolicy.install` → `AdKit.init` + hooks + config collector → `LauncherSetup.install` if `launcher.on`.
2. `App.kt` only builds `AppContainer`.
3. Same order minus SystemKeys / RecentsPolicy / launcher (unless D1/D3 approve). Language already handled by AppCompat in FFF.
4. — (logic lives in new packages) 5. `T/App.kt`, `T/di/AppContainer.kt`. 6. `AppContainer` repositories.
7. firebase-*, ads module. 8. reads `app_config[_debug]`. 9. `AdKit.init`, `AdKit.apply` on config change, `AdKit.onError`. 10. Robolectric not present → JVM tests for parser; manual cold-start check.

## M4. Splash & startup — ADAPT
1. Compose splash: UMP → preload → `splash_wait_sec` hold → ≤6 s config wait → ≤3 s attribution → permissions `splash` → `Ads.splash` → home/onboarding. SplashScreen API (`Theme.App.Starting`).
2. `SplashActivity` fixed 2.8 s animation then route (`SplashViewModel.destination()`).
3. Keep FFF splash visuals/animations; replace the fixed timer with the reference sequence; progress bar tracks the wait. Back on splash finishes.
4. `T/presentation/splash/SplashSequence.kt` (coroutine sequence, testable) — or inside `SplashViewModel`.
5. `SplashActivity.kt`, `SplashViewModel.kt`. 6. `activity_splash.xml` design, animations.
7. coroutines. 8. `base.splash_wait_sec`, `permissions.splash`, `ads.screens.splash`. 9. `Ads.exit(this, "screens.splash")`, `AdKit.requestConsent`, `AdKit.preload`. 10. First launch offline (defaults), returning user (saved config), consent form shown in EEA (UMP debug geography).

## M5. Language & onboarding — ADAPT
1. 13 Compose screens; order from `onboarding.order`; per-screen permissions, bottom ad, exit ad on every forward action; `skip_button`; bottom sheet; voice guide; default_home step; profile saved to `user_profile`.
2. 13 XML steps (`OnboardingSteps.kt`), language applies locale, Skip → Home, answers kept only in SavedStateHandle.
3. Filter FFF steps by RC `onboarding.order` (FFF keys); RC `skip_button` controls Skip visibility; add bottom ad slot + inline native slot; run `Ads.exit("onboarding.<key>")` before advancing; **persist answers** (new prefs `user_profile`) — reference persists, FFF currently does not.
4. `T/data/local/UserProfileStore.kt` (or extend `PreferencesDataSource`), `res/layout/view_ad_slot.xml` (shared slot container).
5. `OnboardingActivity.kt`, `OnboardingViewModel.kt`, `OnboardingSteps.kt` (add `key`), `OptionAdapter.kt` (inline ad view type), `activity_onboarding.xml`.
6. All 13 FFF questions, art, strings, design, language behaviour.
7. — 8. `onboarding.order`, `onboarding.skip_button`, `permissions.<step>`, `ads.onboarding.<step>`. 9. bottom/inline/exit per step. 10. Unit test: order filtering (unknown keys dropped, empty → finish), exit-ad called once per Continue, Skip path.
- **SKIP**: bottom sheet / voice guide, default_home step, body-scanner screens.

## M6. Navigation — ADAPT
1. NavHost routes `splash/onboarding/home/feature/{id}/settings`; `replaceWith` clears graph; deep link extra `open=settings`.
2. Explicit Intents per Activity; Home double-back exit.
3. Keep Activities; wrap every forward/back navigation that has an RC place in `Ads.exit(...) { startActivity / finish }`. Add `open=settings` extra handling on Home if needed (only used by launcher in reference → likely SKIP).
4. `T/presentation/common/AdNavigation.kt` (`Activity.navigateWithAd(place) {}`, `onBackWithAd(place)`). 5. every Activity with a place (see ADS mapping B2). 6. Intent factories (`ListActivity.intent`, `DetailActivity.intent`, …).
7. — 8. — 9. exit places. 10. Back from every screen still works with ads off / offline / no-fill.

## M7. Advertisement module — PORT (`:ads`)
1. `com.appkit.ads` engine (AdKit, Ads, ExitAdRunner, ExitAdFlow, BottomAdLoader, AdsConfigApplier, renderers, UMP, analytics); ~1/3 dead legacy code; 11 known bugs/risks (ADS mapping §8).
2. none.
3. Port module into FFF as `:ads` with minSdk 24, **namespace kept or renamed (decision D8)**. Remove dead legacy paths and `lottie`; fix: rewarded show-failure → `onMiss`, ad expiry (app-open 4 h, inter/rewarded 1 h), destroy natives, in-flight preload de-dupe, Privacy options hook. Keep Analytics allow-list but with FFF event set.
4. whole `ads/` tree (see file plan). 5. — 6. —
7. play-services-ads, ump, browser, firebase-analytics, shimmer, gson, glide, sdp/ssp, lifecycle-process, coroutines. 8. consumes `AdsConfig` only. 9. all formats. 10. Port `ExitAdFlowTest`, `AnalyticsRulesTest`, `ScreenVisitsTest`, `AdEventsTest`; add tests for the fixes.

## M8. Ad placements — ADAPT
1. 39 placements (20 app, 10 launcher, 9 module-internal).
2. none.
3. 15 FFF placements (ADS mapping B2). Event/launcher placements per D1–D3.
4. `res/layout/view_ad_slot.xml`. 5. Activities + layouts in B2. 6. layouts' design. 7. — 8. `ads.*` places. 9. see B2. 10. Each place: on/off/no-fill/offline/`gap_sec`/`every`.

## M9. Firebase Remote Config — PORT + ADAPT
1. `AppConfigStore` (fetch/activate, `rc_sync_hours`, saved JSON in `live_config`, `ready` flow, FCM forced sync), `AppConfigParser` (track_a/track_b, countries overlay, exclusive locations), `AppConfig` models; no Firebase defaults.
2. none.
3. Port store + parser (plain Kotlin/org.json), drop launcher/uninstall/system_keys parts unless approved, add FFF onboarding keys, add `setDefaultsAsync` with a generated defaults XML, guard duplicate fetch on push-started process.
4. `T/data/remote/config/{AppConfigStore,AppConfigParser,AppConfig,CountryResolver}.kt`, `res/xml/remote_config_defaults.xml`, `docs/remote-config/app_config.json` (FFF template), `RcSyncMessagingService.kt`.
5. `App.kt`, `AppContainer.kt`, manifest (FCM service). 6. — 7. firebase-config, firebase-messaging. 8. see RC mapping Part C. 9. feeds `AdKit.apply`. 10. Port `AppConfigParserTest` (23 tests) with FFF fixture; test defaults == fixture.

## M10. Install attribution & geo (organic/paid tracks) — DECIDE (D4)
1. `InstallAttribution` (Install Referrer gclid rules + Google-IP check via `ipconfig.io`) and `ExclusiveLocations` (IP geo) choose `track_a` / `track_b`; verdict permanent.
2. none.
3. If kept: port as-is with fixes (wait for RC before deciding on first install; no permanent paid verdict without check). If not: single track.
4. `T/data/attribution/*`. 5. `App.kt`, splash. 6. — 7. installreferrer. 8. `base.attribution.*`, `base.exclusive_locations.*`, `countries`. 9. segment param. 10. Port `AttributionRulesTest`, `LocationRulesTest`.

## M11. Permissions — ADAPT
1. RC lists per screen (`camera, photos, notification, contacts, phone, overlay`); no rationale / no Settings redirect (except camera gate).
2. Only WRITE_EXTERNAL_STORAGE (< API 29) on Detail download, with launcher contract.
3. FFF needs only `notification` (if notifications are added — FFF posts none today; asking without use is discouraged — D6) and existing storage. Add rationale + Settings redirect on permanent denial.
4. `T/presentation/common/Permissions.kt`. 5. manifest (`POST_NOTIFICATIONS` only if D6), Splash/Onboarding/Home. 6. Detail storage flow. 7. — 8. `permissions.<screen>`, `home_sheet`. 9. — 10. Deny / don't-ask-again / API 24, 29, 33 paths.

## M12. Dialogs & prompts — PORT (logic) + ADAPT (UI)
1. In-app update (flexible / force → finish on decline), in-app review (`rate_us.after_opens`, `retry_after_hours`), home permission sheet, onboarding bottom sheet, uninstall dialog.
2. Settings "Rate us" opens Play; no update/review flow.
3. Port `AppUpdatePrompt` and `RatePrompt` (Play Core, no UI of their own). Force update: show a blocking dialog instead of silently finishing (risk). Home permission sheet → Material BottomSheetDialog XML only if D6.
4. `T/presentation/prompts/PlayPrompts.kt`. 5. `HomeActivity`, `SettingsActivity`, an `onResume` hook. 6. Settings rows. 7. play app-update, play review. 8. `base.app_update.*`, `prompts.rate_us.*`, `base.store_link`, `update_link`. 9. never during an ad (`ExitAdRunner.busy()`). 10. Internal app sharing test for update; review is no-op in debug.

## M13. Analytics — PORT
1. `com.appkit.ads.analytics.Analytics` (12-event allow-list, `segment` param, `Screens` visit timer, `DebugEventLog` + `DebugEventsActivity`).
2. none.
3. Reuse from `:ads`; FFF screen names (`splash, onboarding_<key>, home, list, detail, articles, article, calculator, calc, settings, language`) and click names; drop `default_home`, `uninstall`, `first_home`? (keep `first_home`). Avoid double `ad_impression` (decide whether AdMob↔Firebase link is on — D9).
4. `T/presentation/common/Track.kt` (click/back helpers), optional `DebugEventsActivity` (debug only). 5. every Activity (screen + clicks). 6. — 7. firebase-analytics. 8. — 9. `ad`, `ad_impression`. 10. Port `TrackingCoverageTest` idea for XML (every clickable has a tracked listener) — optional.

## M14. Crashlytics — ADAPT
1. `CrashGuard`: auto-restart without system dialog, swallowed background crashes, ANR watchdog, exit-info reporting, Timber → non-fatal, custom keys.
2. none.
3. Plain Crashlytics + custom keys + Timber non-fatal sink + exit-info reporting. **Do not port** auto-restart / swallowed background crashes (hides crashes; risk R6) unless you require it (D10).
4. `T/core/crash/CrashReporting.kt`. 5. `App.kt`. 6. — 7. firebase-crashlytics, timber. 8. — 9. `AdKit.onError` → non-fatal. 10. Force-crash in debug build appears in console.

## M15. FCM / background — PORT
1. `RcSyncMessagingService` (data `type=rc_sync` → forced fetch), topic `rc_sync`; no notifications; WorkManager unused.
2. none.
3. Port service; avoid double fetch when process starts for the push.
4. `T/data/remote/config/RcSyncMessagingService.kt`. 5. manifest. 6. — 7. firebase-messaging. 8. forced sync. 9. — 10. Send a test data message.

## M16. Launcher (`:launcher`) — DECIDE (D1), recommendation SKIP
1. Fossify Launcher fork as HOME app; host panel; panels/drawer/sponsored tiles; ads on every gesture; Recents hiding.
2. none.
3. Porting requires minSdk 26 + Compose (Fossify) — conflicts with your "no Compose" rule — plus GPL-3.0 licensing exposure and Play Deceptive/Disruptive policy risk. Recommended: not ported.
4–10. If approved: separate plan.

## M17. Uninstall flow — DECIDE (D2), recommendation SKIP
1. Launcher long-press → "premium" flow that hides the icon instead of uninstalling.
2. none. 3. Only reachable from the launcher; deceptive pattern. Recommended: not ported.

## M18. System keys / Recents policy — DECIDE (D3), recommendation SKIP
1. `SystemKeys`, `RecentsPolicy` (app removed from Recents, forced back over Recents), Recents → Play Store jump, Home-key ads.
2. none. 3. Policy risk; depends on deprecated `ACTION_CLOSE_SYSTEM_DIALOGS`. Recommended: not ported.

## M19. Event screens (charging, package, unlock, Recents ad) — DECIDE (D3)
Ported inside `:ads` but gated by RC places defaulting off, or removed from the module. Lock-screen charging ad is the highest risk.

## M20. Localization & theme — PRESERVE
1. 13 locales, Compose theme with 10 accents, light/dark.
2. 9 locales, dark-only rose theme, Sora.
3. Keep FFF. Ads module brings its own strings in 13 locales → keep FFF's 9 + `en` fallback; restyle ad layouts with FFF tokens (`sh_*`) and Sora.
5. `ads/src/main/res/values*/` (prune to FFF locales), ad layouts colours. 8. `ads.colors.dark.*` used for native palette. 10. Ads render legibly on dark theme.

## M21. FFF content — PRESERVE
`Content.kt`, 377 images, catalog/detail/articles/calculator logic, MediaRepository, strings, design: unchanged except for added ad slots, exit-ad wrappers and analytics calls.

---

## Decisions needed (pending questions)

| ID | Question | Recommendation |
|---|---|---|
| D1 | Port the home-screen launcher (`:launcher`)? | No (Compose/minSdk/GPL/Play policy). |
| D2 | Port the uninstall flow? | No. |
| D3 | Port Recents/Home-key ads, Play Store jump, phone-unlock, charging and package event screens? | No; at most keep code with RC defaults off. |
| D4 | Keep organic/paid tracks (install referrer + IP checks)? | Your call — affects RC shape and privacy disclosure. |
| D5 | FFF Firebase project, `google-services.json`, AdMob app id, real unit ids, `ads.links`, privacy/terms/store URLs? | Required before ads/RC work can be tested for real. |
| D6 | Ask notification permission even though FFF sends no notifications? | No, unless notifications are added. |
| D7 | Introduce a `BaseActivity`? | No — small `AdNavigation` helpers instead. |
| D8 | Keep package `com.appkit.ads` for the module or rename to `com.fffemotes.skintools.ads`? | Rename (avoid shipping another app's namespace). |
| D9 | Is AdMob linked to Firebase (auto `ad_impression`)? | Decides whether manual `ad_impression` stays. |
| D10 | Keep CrashGuard auto-restart? | No. |
| D11 | Home Back: keep double-back toast, or reference behaviour (back ad then finish)? | Back ad on first press, then existing double-back rule. |
| D12 | App name: "FFF – Skins & Bundles" (this brief) or "FFF Emotes & Skin Tools" (current rebrand)? | Confirm. |

## Risks

| ID | Risk |
|---|---|
| R1 | No real ad ids anywhere (reference live RC uses test ids) — revenue setup must be supplied by you. |
| R2 | Reference live links point to third-party game sites; must not be copied. |
| R3 | Launcher / uninstall / Recents / lock-screen features carry Play Deceptive & Disruptive Ads policy exposure. |
| R4 | AGP 9.4.1 compatibility of google-services 4.4.4 and crashlytics 3.0.6 Gradle plugins — verify first; fallback is newer plugin versions. |
| R5 | `ACTION_CLOSE_SYSTEM_DIALOGS` deprecated since API 31; key detection unreliable. |
| R6 | CrashGuard hides crashes from users and Play vitals. |
| R7 | Fossify-derived code is likely GPL-3.0 while the repo LICENSE says MIT. |
| R8 | Reference docs / cache / fixture / live RC disagree (v20 vs v52) — FFF needs one generated source of truth. |
| R9 | Aggressive exit-ad chains (link + app_open + inter on every onboarding step) hurt UX and AdMob policy compliance; FFF defaults should start conservative. |
