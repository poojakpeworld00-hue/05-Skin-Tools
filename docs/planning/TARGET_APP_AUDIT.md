# Target App Audit — FFF (Skin Emotes)

Audited: `/Users/pooja/Documents/Pooja_Apps/10_OCT/SkinHubSkinsBundles`, branch `v_1.0.0`, HEAD `10cbc11` (2026-10-30).
Read-only audit: no source file was changed to produce this document.

## 1. Identity

| Item | Value | Where |
|---|---|---|
| applicationId / namespace | `com.fffemotes.skintools.master` | `app/build.gradle.kts` |
| Store name | FFF Emotes & Skin Tools | `res/values/strings.xml` → `store_name` |
| Name in Android Settings | #Emote Skin Tools | `app_name` |
| Launcher label | Skin Emotes | `launcher_name` (set on `SplashActivity`) |
| Root project name | `SkinHub – Skins & Bundles` | `settings.gradle.kts` |
| versionCode / versionName | 1 / 1.0 | `app/build.gradle.kts` |

> The Phase 1 brief calls the target "FFF – Skins & Bundles". The current rebrand (requested earlier in this session) is "FFF Emotes & Skin Tools / Skin Emotes". See *Pending questions*.

## 2. Project architecture

- **Modules:** one — `:app`. No library modules.
- **Language/UI:** Kotlin, XML layouts, ViewBinding. **No Compose**, no Fragments, no Navigation component.
- **Layers** (package `com.fffemotes.skintools.master`):
  - `App.kt` — `Application`; creates `AppContainer`.
  - `di/AppContainer.kt` — manual DI (one instance per app): `catalogRepository`, `settingsRepository`, `mediaRepository`, `calculate`.
  - `data/local/` — `Content.kt` (static catalog, 290 `Item`s + articles), `SupportedLanguages.kt` (9 languages), `PreferencesDataSource.kt` (SharedPreferences `skinhub`).
  - `data/repository/` — `CatalogRepositoryImpl`, `SettingsRepositoryImpl`, `MediaRepositoryImpl`.
  - `domain/model/` — `Item`, `Group`, `Section`, `Article`, `CatalogType` (CHARACTERS, BUNDLES, EMOTES, WEAPONS, PETS, VEHICLES, MAPS), `CalculatorType`, `CalcResult`, `Language`.
  - `domain/repository/` — interfaces above. `domain/usecase/CalculateUseCase.kt`.
  - `presentation/` — one Activity per screen + ViewModel (StateFlow), helpers in `presentation/common/` (`appViewModel {}` factory, `collectWhenStarted`, `padForSystemBars`, `Motion.kt` glow/shine/pop, `UiText`).
- **No BaseActivity / BaseFragment.** Each Activity calls `enableEdgeToEdge()` + `padForSystemBars()` itself.
- **Tests:** `app/src/test/.../domain/usecase/CalculateUseCaseTest.kt` (JUnit 4). No instrumented tests.

## 3. Gradle

| Item | Value |
|---|---|
| AGP | 9.4.1 (built-in Kotlin, no separate kotlin plugin) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 |
| Java | 11 |
| Release | `optimization { enable = true; packageScope = androidx/kotlin/kotlinx }` (AGP 9 R8 DSL). **No `proguard-rules.pro`**, **no signing config**. |
| Build types / flavors | default debug + release; no flavors; no `buildConfig` fields |
| Repos | google, mavenCentral (no jitpack) |
| Plugins | `com.android.application` only — **no google-services, no Crashlytics plugin** |

Dependencies (`gradle/libs.versions.toml`): core-ktx 1.19.1, appcompat 1.8.0, material 1.14.0, activity-ktx 1.13.0, constraintlayout 2.2.2, lifecycle viewmodel/runtime 2.10.0, sdp/ssp 1.1.1; tests junit 4.13.2, androidx junit 1.3.0, espresso 3.7.0.

**Absent:** Firebase (any), AdMob/GMA, UMP consent, Play Billing, Play In-App Update/Review, Install Referrer, WorkManager, Timber, coroutines-play-services, RecyclerView declared explicitly (comes transitively).

## 4. Manifest (`app/src/main/AndroidManifest.xml`)

- Permissions: `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) only. No INTERNET, no POST_NOTIFICATIONS, no AD_ID.
- Application: `.App`, `allowBackup`, backup/data-extraction rules, `localeConfig`, RTL, theme `Theme.SkinHubSkinsBundles`.
- Activities (all `screenOrientation=portrait`, only Splash exported):
  `SplashActivity` (MAIN/LAUNCHER, splash theme), `OnboardingActivity`, `HomeActivity`, `ListActivity`, `DetailActivity`, `ArticlesActivity`, `ArticleActivity`, `CalculatorActivity`, `CalcActivity`, `SettingsActivity`, `LanguageActivity` — **11 activities**, 0 fragments.
- Service: `AppLocalesMetadataHolderService` (autoStoreLocales for per-app language < API 33).

## 5. Screens and flow

| Screen | Class | Layout | Notes |
|---|---|---|---|
| Splash | `presentation/splash/SplashActivity` + `SplashViewModel` | `activity_splash.xml` | Fixed 2.8 s, then `isOnboarded()` → Home else Onboarding |
| Onboarding (13 steps) | `presentation/onboarding/OnboardingActivity`, `OnboardingViewModel`, `OnboardingSteps`, `OptionAdapter` | `activity_onboarding.xml`, `item_option_row.xml`, `item_option_grid.xml` | Step 1 = language (applies locale, recreates). Skip → Home. Answers kept only in `SavedStateHandle` (**not persisted**). |
| Home | `presentation/home/HomeActivity` | `activity_home.xml`, `item_tool_tile.xml` | 6 category tiles + 4 tools; double-back-to-exit (2 s) |
| Category list | `presentation/catalog/ListActivity`, `ListViewModel`, `TileAdapter`, `TileShape` | `activity_list.xml`, `item_tile.xml` | Opens groups (sub-list, itself) or Detail |
| Detail | `presentation/catalog/DetailActivity`, `DetailViewModel` | `activity_detail.xml`, `item_stat.xml` | "Download" → `MediaRepository.saveToGallery` (runtime WRITE permission < Q) |
| Articles list / article | `ArticlesActivity` / `ArticleActivity` (same file) | `activity_articles.xml`, `activity_article.xml` | Tips & tricks / diamond guides |
| Calculator chooser / result | `CalculatorActivity` / `CalcActivity` (same file), `CalcViewModel`, `CalculatorUi` | `activity_calc.xml` | Uses `CalculateUseCase` |
| Settings | `SettingsActivity`, `SettingsViewModel` | `activity_settings.xml`, `item_tool.xml` | Language, share, rate (market://), privacy, terms |
| Language (from Settings) | `LanguageActivity`, `LanguageViewModel` | `activity_language.xml` | Reuses `languageStep` + `OptionAdapter` |

Flow: `Splash → (first run) Onboarding ×13 → Home` / `Splash → (returning) Home`. Home → List → (List) → Detail; Home → Articles → Article; Home → Calculator → Calc; Home → Settings → Language. Back: system back everywhere; Home double-back to exit; Onboarding back goes to previous step, finishes on step 1.

## 6. Storage, config, other systems

| System | State |
|---|---|
| SharedPreferences | file `skinhub`; key `onboarded` (Boolean). Language is AppCompat per-app locale (persisted by AppCompat). |
| DataStore / Room | none |
| Network / API | none — all content is bundled (`Content.kt`, 377 images, 13 MB `drawable-nodpi`) |
| Remote Config / Analytics / Crashlytics / FCM | none |
| Ads | none |
| Localization | 9 locales: en, hi, in(id), tr, vi, ar, fr, de, ja (`res/values-*`, `res/xml/locales_config.xml`) |
| Theme | Dark only, `Theme.Material3.Dark.NoActionBar`, tokens `sh_*` in `res/values/colors.xml`, Sora font, sdp/ssp sizing |
| Privacy / terms URLs | `privacy_policy_url`, `terms_url` strings — **empty** → "link unavailable" toast |
| Logging | none |
| Error handling | `runCatching` in media save; toasts |

## 7. Retain / adapt / connect

| Keep as-is (FFF content) | Adapt | Connect to reference infrastructure |
|---|---|---|
| `Content.kt`, all `drawable-nodpi` art, `domain/*`, catalog/detail/articles/calculator screens, strings & 9 locales, theme/tokens, Sora font | `SplashActivity` (RC fetch + ad gating instead of fixed 2.8 s), `OnboardingActivity` (ad slots per step, persist answers), `HomeActivity` (native/banner slots, exit flow), `SettingsActivity` (consent/privacy options) | `App` (Firebase/ads init), every Activity (interstitial/native placements), manifest (permissions, AdMob app id), Gradle (Firebase, GMA, UMP) |
