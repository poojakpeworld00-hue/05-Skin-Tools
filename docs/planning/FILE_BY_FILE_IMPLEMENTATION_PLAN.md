# File-by-File Implementation Plan

Proposal for review — **nothing is implemented**. Assumes the recommendations in `REFERENCE_VS_TARGET_COMPARISON.md` (D1–D3 = skip launcher, uninstall, system-key/event features; D8 = rename ads package). Rows marked *(if Dn)* depend on that decision.

Abbreviations: `R:` = reference `179-Body-Scanner-Home-01/`, `RA:` = `R:app/src/main/java/com/xray/fullbody/scanner/camera/`, `RD:` = `R:ads/src/main/java/com/appkit/ads/`, `T:` = FFF `app/src/main/java/com/fffemotes/skintools/master/`.
Actions: **CREATE** (new, written for FFF) · **PORT** (copy then adapt package/strings) · **MODIFY** (existing FFF file) · **PRESERVE** (untouched) · **DROP** (reference file not brought over).

## Implementation sequence

| Step | Area | Depends on |
|---|---|---|
| 1 | Project architecture: `:ads` module skeleton | — |
| 2 | Gradle & dependencies | 1, D5 (google-services.json) |
| 3 | Application initialization | 2 |
| 4 | Remote Config store + parser + defaults (needed by everything after) | 3 |
| 5 | Ads module port + fixes | 2, 4 |
| 6 | Splash & startup flow | 4, 5 |
| 7 | Language & onboarding flow | 4, 5 |
| 8 | Navigation helpers (`AdNavigation`) | 5 |
| 9 | All ad placements | 6–8 |
| 10 | JSON configuration + defaults XML + FFF RC template | 4, 9 |
| 11 | Permission management | 4 |
| 12 | Dialogs & prompts (update, review, privacy options) | 4, 5 |
| 13 | Analytics & Crashlytics | 2, 5 |
| 14 | Remaining reusable modules (FCM sync, attribution *(if D4)*) | 4 |
| 15 | FFF content integration check (no content changes) | 9 |
| 16 | Testing & validation | all |
| 17 | Release build verification | all, D5 |

## 1. Project architecture

| Action | File | Source / notes |
|---|---|---|
| MODIFY | `settings.gradle.kts` | `include(":app", ":ads")` |
| CREATE | `ads/build.gradle.kts` | from `R:ads/build.gradle.kts`; namespace `com.fffemotes.skintools.ads` (D8), minSdk 24, compileSdk 37, Java 17, viewBinding, buildConfig |
| CREATE | `ads/src/main/AndroidManifest.xml` | from reference: INTERNET, ACCESS_NETWORK_STATE; `SponsorWebActivity`. REORDER_TASKS + event activities only *(if D3)* |
| CREATE | `ads/consumer-rules.pro` | from reference (HouseAdCard, Gson TypeToken) |

## 2. Gradle & dependencies

| Action | File | Notes |
|---|---|---|
| MODIFY | `gradle/libs.versions.toml` | add firebase-bom 33.10.0 (config, analytics, crashlytics, messaging), play-services-ads 25.5.0, ump 3.1.0, browser 1.8.0, shimmer 0.5.0, gson 2.11.0, glide 4.16.0, play app-update 2.1.0, play review 2.0.2, installreferrer 2.2 *(if D4)*, coroutines-android + play-services 1.10.2, lifecycle-process, timber 5.0.1; plugins google-services 4.4.4, firebase-crashlytics 3.0.6 (verify on AGP 9 — R4) |
| MODIFY | `build.gradle.kts` (root) | declare library + Firebase plugins `apply false` |
| MODIFY | `app/build.gradle.kts` | `implementation(project(":ads"))` + deps; Java 17; `buildConfig = true`; `manifestPlaceholders["admobAppId"]` (debug = Google test id; release = `-PadmobAppId` / `local.properties admob.appId`, **fail release build if missing**); apply Firebase plugins; `proguardFiles(..., "proguard-rules.pro")` |
| CREATE | `app/proguard-rules.pro` | `-keepattributes SourceFile,LineNumberTable` + reference rules |
| CREATE | `app/google-services.json` | **from you (D5)**; recommend not committing it, or confirm |

## 3. Application initialization

| Action | File | Notes |
|---|---|---|
| MODIFY | `T:App.kt` | order from `RA:App.kt`: crash reporting → `AppConfigStore.init` → FCM topic → attribution *(if D4)* → `Analytics.init` → `Screens.install` → `AdKit.init` + hooks (`isHomeScreen` = HomeActivity, `noResumeAdOn` = splash) → collect config → `AdKit.apply` |
| MODIFY | `T:di/AppContainer.kt` | expose `configRepository` (wraps `AppConfigStore`), `userProfileStore` |
| PRESERVE | `T:data/repository/*`, `T:domain/*` | |

## 4. Remote Config

| Action | File | Source |
|---|---|---|
| PORT | `T:data/remote/config/AppConfigStore.kt` | `RA:config/AppConfigStore.kt` + `setDefaultsAsync(R.xml.remote_config_defaults)` + no double fetch for push-started process |
| PORT | `T:data/remote/config/AppConfigParser.kt` | `RA:config/AppConfigParser.kt` minus `launcher()`, `system_keys`, `uninstall_flow` *(D1–D3)*; FFF onboarding keys |
| PORT | `T:data/remote/config/AppConfig.kt` | `RA:config/AppConfig.kt` minus launcher/uninstall types; `OnboardingScreen` → FFF keys enum |
| PORT | `T:data/remote/config/CountryResolver.kt` | `RA:config/Resolvers.kt` |
| PORT | `T:data/remote/config/RcSyncMessagingService.kt` | `RA:config/RcSyncMessagingService.kt` |
| CREATE | `T:domain/repository/ConfigRepository.kt` + `T:data/repository/ConfigRepositoryImpl.kt` | FFF layering wrapper (StateFlow of `AppConfig`) |
| CREATE | `app/src/main/res/xml/remote_config_defaults.xml` | generated from `docs/remote-config/app_config.json` |
| CREATE | `docs/remote-config/app_config.json`, `app_config_debug.json`, `RC_GUIDE.md` | FFF template (ads off by default, test unit ids only in debug) |
| PORT *(if D4)* | `T:data/attribution/InstallAttribution.kt`, `ExclusiveLocations.kt` | `RA:config/InstallAttribution.kt`, `ExclusiveLocations.kt` (+ fix: decide only after RC is ready) |

## 5. Advertisement module (`ads/src/main/java/com/fffemotes/skintools/ads/`)

| Action | File(s) | Notes |
|---|---|---|
| PORT | `AdKit.kt`, `Ads.kt`, `AdsConfigApplier.kt`, `BottomAdLoader.kt`, `ExitAdFlow.kt`, `ExitAdRunner.kt`, `config/AdsConfig.kt` | core façade; remove launcher-only entry points if D1/D3 skip (`panelOpened`, `drawerOpened`, `appIconTapped`, `returnedFromApp`, `sponsored*`, `phoneUnlocked`, `chargerChanged`, `packageChanged`, `playStoreReturn`, `systemKey`, `keyAdDuringOnboarding`, `recentsAdOn`) |
| PORT | `analytics/Analytics.kt`, `analytics/Screens.kt`, `analytics/DebugEventLog.kt` | FFF event allow-list |
| PORT | `entity/AdSlotKind.kt`, `entity/HouseAdCard.kt` | |
| PORT | `host/AdEvents.kt`, `host/AdLifecycle.kt`, `host/AdLog.kt`, `host/AdPreload.kt`, `host/AppResumeWatcher.kt`, `host/WindowFocus.kt` | |
| PORT | `policy/AdLoadMode.kt`, `AdSettingsVault.kt`, `AdUnitIds.kt`, `AdWait.kt`, `ConsentFlowGate.kt`, `SharedLoad.kt` | |
| PORT (trimmed) | `policy/ScreenAdPlanner.kt` | keep only `nativeUnitId()` (or inline it) |
| DROP | `policy/ShowFrequencyGovernor.kt` | counters never written (ADS §5.3) |
| PORT | `render/AdColors.kt`, `AdFrameStyling.kt`, `AdSlotGate.kt`, `AdViewKit.kt`, `BannerRenderer.kt`, `CollapsibleBackdrop.kt`, `HouseAdDirector.kt`, `NativeAdRenderer.kt`, `NativeStripRenderer.kt`, `ResumeAdDirector.kt`, `RewardAdRenderer.kt`, `SponsorWebActivity.kt` | fixes: rewarded show-failure → `onMiss`; ad age expiry; destroy displayed natives; remove dead methods |
| PORT | `render/interstitial/ChainInterstitial.kt`, `InterstitialPreloader.kt` | in-flight preload de-dupe |
| PORT (trimmed) | `render/interstitial/NavigationInterstitial.kt` → keep `openSponsorLink`, `onReturnFromSettings`, `onAppCovered`, `fetchInterstitials`, `takePreloadedGoogle`; `InterstitialBackupChain.kt` → keep `fullNative()`, `custom()` | |
| DROP | `render/interstitial/PlacementFlow.kt` | unreachable |
| PORT *(if D3)* / DROP | `events/**` (9 files), `launcher/UnlockAdWatcher.kt` | event triggers |
| PORT | `res/layout/ad_gms_native_compact.xml`, `ad_gms_native_large.xml`, `ad_gms_native_medium.xml`, `ad_gms_native_medium_alt.xml`, `ad_gms_native_fullpage.xml`, `house_ad_card_a.xml`, `house_ad_card_b.xml`, `house_ad_strip.xml`, `house_ad_fullpage.xml`, `part_fullscreen.xml` | restyle with FFF dark tokens + Sora |
| DROP | `res/layout/ad_gms_native_large_top.xml` | never used |
| DROP *(unless D3)* | `activity_charging_status.xml`, `screen_package_result.xml`, `row_pkgres_check.xml`, `screen_recent_ad.xml` | |
| PORT | `res/values/adk_module.xml`, `adk_ported.xml`, locales `ar,de,fr,hi,in,ja,tr,vi` | drop es, ko, pt, ru (FFF doesn't ship them) |
| DROP | `qureka1.gif`, `adk_banner.webp`, figtree/nunito fonts | placeholder art / other fonts |
| PORT | `ads/src/test/**` (4 files) | + new tests for fixes |

## 6. Splash & startup

| Action | File | Notes |
|---|---|---|
| MODIFY | `T:presentation/splash/SplashActivity.kt` | consent → preload → hold `splash_wait_sec` → ≤6 s config → permissions `splash` → `Ads.exit("screens.splash")` → route; keep current animations |
| MODIFY | `T:presentation/splash/SplashViewModel.kt` | sequence as a suspend function (unit-testable) |
| PRESERVE | `res/layout/activity_splash.xml` | |

## 7. Language & onboarding

| Action | File | Notes |
|---|---|---|
| MODIFY | `T:presentation/onboarding/OnboardingSteps.kt` | add stable `key` per step |
| MODIFY | `T:presentation/onboarding/OnboardingViewModel.kt` | filter by `onboarding.order`; `skip_button`; persist answers; `next()` emits an "exit ad then advance" event |
| MODIFY | `T:presentation/onboarding/OnboardingActivity.kt` | bottom slot, inline slot, `Ads.exit("onboarding.<key>")` before advance |
| MODIFY | `T:presentation/onboarding/OptionAdapter.kt` | inline ad view type after option 2 (design `adAfter`) |
| MODIFY | `res/layout/activity_onboarding.xml` | `adSlot` container under the CTA |
| CREATE | `T:data/local/UserProfileStore.kt` | `user_profile` prefs: answers + `onboarding_done` (migrate existing `onboarded` key) |
| MODIFY | `T:data/local/PreferencesDataSource.kt`, `T:data/repository/SettingsRepositoryImpl.kt` | read legacy `onboarded` for returning users |
| PRESERVE | `T:presentation/settings/LanguageActivity.kt` (adds slot only, step 9) | |

## 8. Navigation

| Action | File | Notes |
|---|---|---|
| CREATE | `T:presentation/common/AdNavigation.kt` | `Activity.openWithAd(place, intent)`, `Activity.backWithAd(place)`, slot binding helper `bindAdSlot(container, place)` |
| CREATE | `res/layout/view_ad_slot.xml` | shared slot `FrameLayout` |

## 9. Ad placements (see `ADS_COMPLETE_MAPPING.md` B2)

| Action | File |
|---|---|
| MODIFY | `T:presentation/home/HomeActivity.kt`, `res/layout/activity_home.xml` |
| MODIFY | `T:presentation/catalog/ListActivity.kt`, `res/layout/activity_list.xml` |
| MODIFY | `T:presentation/catalog/DetailActivity.kt`, `res/layout/activity_detail.xml` |
| MODIFY | `T:presentation/articles/ArticlesActivity.kt`, `res/layout/activity_articles.xml`, `activity_article.xml` |
| MODIFY | `T:presentation/calculator/CalculatorActivity.kt`, `res/layout/activity_calc.xml` |
| MODIFY | `T:presentation/settings/SettingsActivity.kt`, `res/layout/activity_settings.xml` |
| MODIFY | `T:presentation/settings/LanguageActivity.kt`, `res/layout/activity_language.xml` |

## 10. JSON configuration & defaults — see step 4 rows (`docs/remote-config/*`, `remote_config_defaults.xml`, parser test fixture `app/src/test/resources/app_config.json`).

## 11. Permissions

| Action | File | Notes |
|---|---|---|
| PORT | `T:presentation/common/Permissions.kt` | from `RA:permissions/Permissions.kt`; FFF names only; add rationale + Settings redirect |
| MODIFY *(if D6)* | `app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS` |
| PRESERVE | `DetailActivity` storage permission flow | |

## 12. Dialogs & prompts

| Action | File | Notes |
|---|---|---|
| PORT | `T:presentation/prompts/PlayPrompts.kt` | from `RA:prompts/PlayPrompts.kt` (`AppUpdatePrompt`, `RatePrompt`); force update shows a dialog, not a silent finish |
| MODIFY | `T:presentation/settings/SettingsActivity.kt` | "Privacy options" row (UMP) when required; privacy/terms rows read `base.privacy_link/terms_link` (fallback to existing strings) |
| MODIFY | `res/values*/strings.xml` | new strings in all 9 locales |

## 13. Analytics & Crashlytics

| Action | File | Notes |
|---|---|---|
| CREATE | `T:presentation/common/Track.kt` | screen/click/back helpers over `Analytics` |
| MODIFY | every Activity | screen + click events |
| CREATE | `T:core/crash/CrashReporting.kt` | Crashlytics custom keys, Timber tree, exit-info (no auto-restart, D10) |
| CREATE (debug only, optional) | `T:presentation/debug/DebugEventsActivity.kt` | from `RA:debug/DebugEventsActivity.kt`, XML |

## 14. Remaining reusable modules
FCM sync (step 4); attribution *(if D4)*. Launcher / uninstall / system keys / Recents: DROP unless D1–D3 change.

## 15. FFF content — PRESERVE
`T:data/local/Content.kt`, `SupportedLanguages.kt`, `T:domain/**`, `T:presentation/catalog/{TileAdapter,TileShape,ListViewModel,DetailViewModel}.kt`, `T:presentation/articles/ArticlesViewModel.kt`, `T:presentation/calculator/{CalcViewModel,CalculatorUi}.kt`, `T:presentation/common/{Motion,UiText,Insets,ViewModels}.kt`, all `res/drawable-nodpi`, fonts, theme, strings content.

## 16–17. Testing & release — see `IMPLEMENTATION_CHECKLIST.md`.

## Reference files deliberately not used (Body-Scanner-only or rejected)
`RA:feature/**` (7 features + camera), `RA:onboarding/steps/**` (screens rebuilt as FFF steps), `RA:home/**`, `RA:settings/SettingsScreen.kt`, `RA:ui/**` (Compose theme), `RA:navigation/AppNavHost.kt`, `RA:MainActivity.kt`, `RA:launcherhost/**`, `RA:uninstall/**`, `RA:RecentsPolicy.kt`, `RA:analytics/SystemKeys.kt`, `RA:ads/AdSlot.kt` (Compose), `RA:crash/CrashGuard.kt` restart logic, `R:launcher/**`, all body-scanner images/strings/voice clips.
