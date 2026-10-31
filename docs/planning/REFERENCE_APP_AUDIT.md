# Reference App Audit — 179-Body-Scanner-Home-01

Reference: `/Users/pooja/Documents/Pooja_Apps/10_OCT/179-Body-Scanner-Home-01`, branch `main` (read-only; nothing in it was modified).
Audited 2026-10-30. Ads internals: see `ADS_COMPLETE_MAPPING.md`. Remote Config: see `FIREBASE_REMOTE_CONFIG_MAPPING.md`.

## Summary

| Module | Package | Kotlin files / lines | UI | minSdk | Role |
|---|---|---|---|---|---|
| `:app` | `com.xray.fullbody.scanner.camera` | 56 / 11,785 (+8 test files) | **Jetpack Compose** (Navigation Compose), no XML layouts | 26 | App shell, onboarding, features, RC, attribution, launcher host, uninstall flow |
| `:ads` | `com.appkit.ads` | 54 / ~8,000 (+4 test files) | XML + ViewBinding (one Compose `AdSlot` wrapper lives in `:app`) | 26 | AdMob + UMP engine, place-driven ad flows, event triggers, Firebase Analytics |
| `:launcher` | `com.appkit.launcher` | 79 / 13,117 | XML + ViewBinding (Fossify Commons pulls Compose transitively) | 26 | Home-screen launcher (renamed Fossify Launcher fork) |

- Activities: `:app` 4 (`MainActivity`, `UninstallFlowActivity`, `UninstallDialogActivity`, `DebugEventsActivity`); `:ads` 4 (`SponsorWebActivity`, `PackageResultActivity`, `ChargingStatusActivity`, `RecentAdActivity`); `:launcher` 2 (`XHubStage` HOME, `MainLobbyHintStage`) + 6 Fossify activities merged. **Total 10 own + 6 merged.**
- Fragments: 1 (`launcherhost/HostPanelFragment`, Compose). Compose destinations: 5 main routes + 2 panel routes; 13 onboarding screens; 7 feature screens; 6 uninstall screens.
- Firebase project: `body-scanner-home---01` (google-services.json tracked in git).

---


Root: `/Users/pooja/Documents/Pooja_Apps/10_OCT/179-Body-Scanner-Home-01` (below: `P/`). App source: `P/app/src/main/java/com/xray/fullbody/scanner/camera/` (below: `S/`).
Git branch at audit time: repo has 201 commits; last commits touch RC / attribution ("a84c475 - Commit the force paid line").
Size: 56 Kotlin files, 11,785 lines in `S/` (plus 8 test files).

---

## 1. Build setup

### settings.gradle.kts
- rootProject.name = `BodyXrayScannerCamera`; `include(":app", ":ads", ":launcher")`.
- Repos: google(), mavenCentral(), gradlePluginPortal() (plugins); google(), mavenCentral(), `https://jitpack.io` (deps). `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
- Gradle wrapper 8.13 (`gradle/wrapper/gradle-wrapper.properties`).

### Root build.gradle.kts
All plugins `apply false`: android.application, android.library, kotlin.android, kotlin.compose, kotlin.parcelize, ksp, google.services, firebase.crashlytics.

### gradle.properties
`org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8`, `org.gradle.caching=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`. (No configuration-cache, no R8 full-mode flags set.)

### local.properties
Present (gitignored). Only `sdk.dir` observed. No `admob.appId=` line → see AdMob note below.

### gradle/libs.versions.toml (every entry)
Versions: agp 8.9.1, kotlin 2.2.10, ksp 2.2.10-2.0.2, googleServices 4.4.4, crashlyticsPlugin 3.0.6, coreKtx 1.16.0, appcompat 1.7.1, material 1.12.0, constraintlayout 2.2.1, lifecycle 2.9.2, activityCompose 1.10.1, splashscreen 1.0.1, composeBom 2025.08.00, navigationCompose 2.9.3, coroutines 1.10.2, camerax 1.4.2, coil 2.7.0, installreferrer 2.2, play-app-update 2.1.0, play-review 2.0.2, room 2.8.4, glide 4.16.0, fossify 6.1.6, playServicesAds 25.5.0, ump 3.1.0, browser 1.8.0, firebaseBom 33.10.0, shimmer 0.5.0, lottie 6.6.3, gson 2.11.0, work 2.10.3, junit 4.13.2, json 20240303.

Libraries: androidx-core-ktx, androidx-appcompat, material, androidx-constraintlayout, androidx-lifecycle-runtime-ktx, androidx-lifecycle-process, androidx-lifecycle-viewmodel-compose, androidx-lifecycle-runtime-compose, androidx-core-splashscreen, androidx-activity-compose, androidx-compose-bom (+ ui, ui-graphics, ui-tooling, ui-tooling-preview, ui-viewbinding, material3, material-icons-extended, foundation — BOM-managed), androidx-navigation-compose, androidx-fragment-ktx (inline version **1.8.8**, not in [versions]), kotlinx-coroutines-android, kotlinx-coroutines-play-services, camerax-core/camera2/lifecycle/view, play-app-update, play-review, installreferrer, coil-compose, room-runtime/ktx/compiler, glide, fossify-commons (org.fossify:commons), play-services-ads, ump, androidx-browser, firebase-bom (+ config, analytics, crashlytics, messaging), shimmer, lottie, gson, androidx-work-runtime-ktx, junit, org-json.

Plugins: android-application, android-library, kotlin-android, kotlin-compose, kotlin-parcelize, ksp, google-services, firebase-crashlytics.

### app/build.gradle.kts
- Plugins applied: android.application, kotlin.android, kotlin.compose, kotlin.parcelize. **google-services + firebase-crashlytics applied only `if (file("google-services.json").exists())`** (it exists → applied).
- `namespace`/`applicationId` = `com.xray.fullbody.scanner.camera`; compileSdk 36, minSdk 26, targetSdk 36, versionCode 1, versionName "1.0.0".
- Build types:
  - debug: `manifestPlaceholders["admobAppId"] = ca-app-pub-3940256099942544~3347511713` (Google sample/test app id).
  - release: `admobAppId` from `-PadmobAppId` or `local.properties admob.appId`; falls back to the test id with only a `logger.warn` in `preReleaseBuild` ("Do not upload it"). **local.properties has no `admob.appId` → current release builds use the TEST AdMob app id.** `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `proguard-rules.pro`.
- No `signingConfigs` (release signing not configured in Gradle; `.gitignore` excludes keystore/, *.jks).
- No productFlavors. No custom `buildConfigField` (buildConfig = true only for `BuildConfig.DEBUG`, `VERSION_NAME`).
- `buildFeatures { compose = true; buildConfig = true; viewBinding = true }` (no ViewBinding classes used in :app source — grep finds none; ViewBinding is used by the library modules).
- Java/Kotlin target 17. `testOptions.unitTests.isReturnDefaultValues = true`. Packaging excludes `META-INF/versions/9/OSGI-INF/MANIFEST.MF`, `META-INF/DEPENDENCIES`.
- `configurations.configureEach { exclude(group = "com.android.support") }` (Fossify pulls old support lib).
- Dependencies actually declared by :app: `project(":ads")`, `project(":launcher")`, core-ktx, splashscreen, appcompat, material, fragment-ktx, lifecycle runtime-ktx/process/viewmodel-compose/runtime-compose, activity-compose, compose BOM + ui/graphics/tooling-preview/viewbinding/foundation/material3/icons-extended, navigation-compose, coroutines android/play-services, camerax ×4, coil-compose, installreferrer, play app-update, play review, firebase BOM + config/analytics/crashlytics/messaging, **`com.jakewharton.timber:timber:5.0.1` hardcoded (not in catalog)**, work-runtime-ktx; debugImplementation ui-tooling; testImplementation junit, org.json.
- Declared but NOT used by :app source: `androidx.work` (no WorkManager code in `S/`, grep "WorkManager|androidx.work" → none), `room-*`, `glide`, `gson`, `lottie`, `shimmer`, `ump`, `browser`, `play-services-ads` are not :app dependencies (catalog entries used by library modules or unused — library agents to confirm). `ksp` plugin declared in root but not applied in :app.

### app/proguard-rules.pro (entire file)
```
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
```
No keep rules for Compose/Navigation route args, org.json models, or RC parsing (parser uses org.json by string keys → safe under R8). FirebaseMessagingService is referenced from manifest → kept by AAPT rules.

### google-services.json
`P/app/google-services.json` present **and tracked in git**. project_id = `body-scanner-home---01`, project_number = 317438549805, one client package `com.xray.fullbody.scanner.camera`. (API key not copied.)

### Other tracked build artefacts
`app/release/output-metadata.json` (release APK v1 / 1.0.0) and `app/release/baselineProfiles/{0,1}/app-release.dm` are committed.

---

## 2. AndroidManifest (`P/app/src/main/AndroidManifest.xml`)

Permissions: INTERNET, CAMERA, POST_NOTIFICATIONS, REORDER_TASKS, WRITE_EXTERNAL_STORAGE (maxSdkVersion 28). Removed via `tools:node="remove"`: USE_FINGERPRINT, `org.fossify.android.permission.WRITE_GLOBAL_SETTINGS`.
Merged from libraries (not in app manifest): launcher → EXPAND_STATUS_BAR, REQUEST_DELETE_PACKAGES, BIND_APPWIDGET (+ `<queries>` incl. HOME); ads → INTERNET, ACCESS_NETWORK_STATE, REORDER_TASKS (+ queries). Not declared anywhere checked: READ_CONTACTS, READ_PHONE_STATE, SYSTEM_ALERT_WINDOW → RC permission names `contacts`, `phone`, `overlay` can never be asked (see Permissions).
`<uses-feature android.hardware.camera required=false>`.

`<application>`: name `.App`, `allowBackup=false`, `enableOnBackInvokedCallback=true`, icon/roundIcon mipmap, **label `@string/app_name_for_setting` = "#Xray Scanner"** (leading `#`, comment: shown in Android Settings / Default-home list; launcher icon label is `app_name` "Xray Scanner"), supportsRtl, theme `Theme.App`. No `android:localeConfig`, no networkSecurityConfig, no dataExtractionRules.

| Component | exported | launchMode | theme | other |
|---|---|---|---|---|
| `.MainActivity` | true | standard (none set) | `Theme.App.Starting` | MAIN/LAUNCHER; portrait; **excludeFromRecents=true**; configChanges locale\|layoutDirection\|grammaticalGender\|screenLayout; label `app_name` |
| `.uninstall.UninstallFlowActivity` | false | standard | Theme.App | portrait, excludeFromRecents |
| `.uninstall.UninstallDialogActivity` | false | standard | Theme.App.Translucent | portrait, excludeFromRecents |
| `.debug.DebugEventsActivity` | false | standard | Theme.App | intent-filter action `com.appkit.ads.analytics.OPEN_DEBUG_EVENTS` (DEFAULT); taskAffinity `com.xray.fullbody.scanner.camera.debugevents`; finishes itself unless `DebugEventLog.enabled()` (= BuildConfig.DEBUG) |
| Fossify `ContributorsActivity`, `FAQActivity`, `LicenseActivity`, `ManageBlockedNumbersActivity`, `DonationActivity`, `AppLockActivity` | (merge) | — | — | `tools:node="merge"` only to force portrait |
| service `.config.RcSyncMessagingService` | false | — | — | `com.google.firebase.MESSAGING_EVENT` |
| service `androidx.appcompat.app.AppLocalesMetadataHolderService` | false, enabled=false | — | — | meta-data `autoStoreLocales=true` (per-app language persistence < API 33) |

Meta-data: `com.google.android.gms.ads.APPLICATION_ID = ${admobAppId}`.
No receivers/providers declared by :app (library-merged ones: launcher has 3 exported receivers + HOME activity singleTask, a singleInstance activity; ads has 4 activities — other agents).
Themes (`res/values/themes.xml`): `Theme.App` (Material3.DayNight.NoActionBar), `Theme.App.Translucent`, `Theme.App.Starting` (parent Theme.SplashScreen, bg `@color/splash_bg` #FFFFFF; values-night overrides splash colour; animated icon transparent; postSplashScreenTheme Theme.App).

---

## 3. Architecture

- Single-Activity Compose app (`MainActivity`, AppCompatActivity) + 2 uninstall Activities + 1 debug Activity. Navigation Compose `NavHost` in `S/navigation/AppNavHost.kt`. A second, separate NavHost lives in `S/launcherhost/HostPanelFragment.kt` (the app home shown inside the launcher's swipe-right panel: routes `home`, `feature/{id}`).
- No DI framework (no Hilt/Koin/Dagger). Singletons via Kotlin `object` (AppConfigStore, InstallAttribution, ExclusiveLocations, RecentsPolicy, CrashGuard, Permissions, RatePrompt, Languages, OurIcon, LauncherSetup) and per-call stores (`UserProfileStore(ctx)`, `SettingsStore(ctx)` with a static shared StateFlow). No ViewModels in :app (state in composables + `rememberSaveable`).
- No BaseActivity. No Room, no DataStore, no WorkManager code, no Retrofit/OkHttp (raw `HttpURLConnection` in `config/InstallAttribution.kt` and `config/ExclusiveLocations.kt`). Images: Coil `AsyncImage`. Camera: CameraX (`S/feature/common/Camera.kt`).
- XML layouts: none in :app (`res/` has no `layout/`); all UI is Compose. Ad slots are `AndroidView(FrameLayout)` (`S/ads/AdSlot.kt`).

### Application class `S/App.kt` — init order
`attachBaseContext`: `CrashGuard.attach(this)` (sets default UEH before Firebase) → background thread "prefs-preload" loads STARTUP_PREFS (`live_config, user_profile, app_settings, install_attribution, rate_prompt, crash_guard, qr_ads_config, onboarding_sheet, com.xray.fullbody.scanner.camera_preferences`).
`onCreate` (App.kt:44-79):
1. `CrashGuard.install` (wraps Crashlytics' UEH; Timber tree → non-fatal; ANR watchdog thread; past-exit reporting API30+).
2. (commented) `AppConfigStore.forcePaidUser(true)` — App.kt:47, currently commented out.
3. `AppConfigStore.trackResolver = TrackResolver { InstallAttribution.track(this) }`; `AppConfigStore.init(this)` (publishes saved RC JSON, starts fetch).
4. `FirebaseMessaging.subscribeToTopic("rc_sync")`.
5. `Languages.applySaved(...)`.
6. `InstallAttribution.start(...)`, `ExclusiveLocations.start(...)` (both call `AppConfigStore.refreshTrack()` on a live verdict).
7. `Analytics.init(this) { track B → "paid" else "organic" }` (segment param added to every event); `DebugEventLog.enabled = { BuildConfig.DEBUG }`; `Screens.ownLeave = {...}`; `Screens.install()`; `SystemKeys.listen(this)`; `RecentsPolicy.install(this) { onboardingDone }`.
8. `AdKit.init(this)`; `AdKit.onError = CrashGuard::nonFatal`; `AdKit.isHomeScreen`, `AdKit.noResumeAdOn = { DefaultHomeStep.shown }`, `AdKit.recentsAdScreenOn`; MainScope collect `AppConfigStore.current.map{it.ads}.distinctUntilChanged()` → `AdKit.apply(ads)`.
9. Launcher: `if (current.launcher.on) LauncherSetup.install(this)` else wait on MainScope for first config with `launcher.on` then install. Never uninstalled if RC later turns it off (until process restart).

### Routes (`S/navigation/AppNavHost.kt:48-55`)
`splash`, `onboarding`, `home`, `feature/{id}`, `settings`. Start destination: `splash` unless `resume` (RecentsPolicy.takeResume) → `home` if onboardingDone else `onboarding`. `replaceWith()` pops the whole graph (splash/onboarding never returned to).

### Screens inventory (all Compose)
| Screen | File |
|---|---|
| Splash (design 00) | `S/splash/SplashScreen.kt` |
| Onboarding host + bottom sheet ("Finish setup", voice guide) | `S/onboarding/OnboardingFlow.kt` (+ `OnboardingPage.kt`, `OnboardingScaffold.kt`, `OnboardingRules.kt`) |
| OB language / gender / age | `S/onboarding/steps/ProfileSteps.kt` (`LanguageStep`, `GenderStep`, `AgeStep`) |
| OB body_scan / old_body / body_parts | `S/onboarding/steps/FeatureStepsA.kt` |
| OB interests / camera / energy_level | `S/onboarding/steps/FeatureStepsB.kt` |
| OB human_species / default_home (`HomeSetupStep`) | `S/onboarding/steps/FeatureStepsC.kt` |
| OB welcome | `S/onboarding/steps/WelcomeStep.kt` |
| OB permission_hub | `S/onboarding/steps/PermissionStep.kt` |
| Home + "Allow access" permission sheet | `S/home/HomeScreen.kt`, `S/home/HomePermissionSheet.kt` |
| Settings (+ language & age picker sheets) | `S/settings/SettingsScreen.kt` |
| Features (7): full_body_scan, old_body, body_parts, body_filter, human_species, energy_level, gallery | `S/feature/scan/FullBodyScanScreen.kt`, `feature/oldbody/OldBodyScreen.kt`, `feature/bodyparts/BodyPartsScreen.kt`, `feature/filter/BodyFilterScreen.kt`, `feature/species/HumanSpeciesScreen.kt`, `feature/energy/EnergyLevelScreen.kt`, `feature/gallery/GalleryScreen.kt` (dispatcher `feature/FeatureScreen.kt`) |
| Uninstall flow: splash, language, step1 Sorry, step2 Reasons, progress, step3 Confirm | `S/uninstall/UninstallFlowActivity.kt`, `S/uninstall/UninstallScreens.kt` |
| Uninstall system-style AlertDialog | `S/uninstall/UninstallDialogActivity.kt` |
| Debug analytics events list | `S/debug/DebugEventsActivity.kt` |
| Host app panel (inside launcher) | `S/launcherhost/HostPanelFragment.kt` |
Count: 4 :app Activities, 5 NavHost destinations (+2 in panel NavHost), 13 onboarding screens, 7 features, 6 uninstall screens + 1 dialog, 2 bottom sheets on home/onboarding + 2 picker sheets in settings.

---

## 4. User journey

### Cold start
1. System splash (SplashScreen API, `installSplashScreen()` MainActivity.kt:45; blank, no icon) → Compose splash.
2. Splash route (AppNavHost.kt:85-115): `AdKit.requestConsent(activity)` (UMP) → on done `AdKit.preload`. `SplashScreen` holds `base.splash_wait_sec` (read from *current* config: saved, or code default 4 on first launch) then waits ≤ `CONFIG_WAIT_MS` 6 s for `AppConfigStore.ready` (SplashScreen.kt:117-122, 181). Then: await consent → `withTimeoutOrNull(3000){ InstallAttribution.await(3000); ExclusiveLocations.await(3000) }` → `lock()` both → `AskPermissionsOnOpen("splash")` (RC `permissions.splash`) → **`Ads.splash(activity)`** (AppNavHost.kt:109) → `replaceWith(home | onboarding)`. Back on splash finishes the activity.
   Worst-case first-launch splash ≈ 4 s + 6 s + consent time + 3 s + permission prompts + splash ad (`loader_wait_sec` 8).
3. First launch (onboardingDone=false) → onboarding; returning → home.

### Onboarding (`S/onboarding/OnboardingFlow.kt`)
- Screen list frozen at start: `OnboardingRules.screens(config, isDefaultHome)` (OnboardingFlow.kt:151) = `onboarding.order` minus `default_home` when launcher off or already default home. Code default order = the 10 OB screens (no welcome / permission_hub / default_home). Empty list → finish immediately.
- Each screen: `AskPermissionsOnOpen(screen.key)` (except permission_hub, which asks on Allow) (OnboardingFlow.kt:285); `AdSlot("onboarding.<key>")` bottom ad (OnboardingScaffold.kt:147 / OnboardingPage.kt:81 / inline OnboardingPage.kt:95).
- Forward (Next/Continue/Skip/sheet Continue/"Go to Home"/system key `next`) → `advance()` (OnboardingFlow.kt:182) → **`Ads.exit(activity, "onboarding.<key>" | "launcher.<key>_button")`** (OnboardingFlow.kt:185-186) → next index or `finish()`.
- Language: tap previews via `Localized`; Continue saves `user_profile.language`, `Languages.choose()` (AppCompatDelegate per-app locale, no recreate).
- Skip button: `onboarding.skip_button` (default_home uses `default_home.skip_button`).
- default_home step: immediately `Launcher.requestDefaultHome` (OnboardingFlow.kt:313). Cancelled → after `default_home.auto_open_settings_sec` (default 3) re-opens the Default-home Settings automatically while resumed (OnboardingFlow.kt:314-322). Back on this step = ask again; Home = reopen on return. Only becoming default home moves on (`homeGranted()` OnboardingFlow.kt:203) → runs `Ads.exit("onboarding.default_home")` (OnboardingFlow.kt:210) → `RecentsPolicy.moveToHomeTask(activity)` (onboarding continues inside the launcher's HOME task).
- System keys during onboarding (`onSystemKey` OnboardingFlow.kt:228): 300 ms de-dupe; ignored while `ExitAdRunner.busy()`; Home ignored for 5 s after grant; Home only acts if we are default home; `OnboardingRules.onKey` → ShowSheet / Next / GoTo / LeaveApp / DeviceDefault (`moveTaskToBack`) / Nothing; `launcher.<key>_button.during_onboarding` swaps in the key's ad (OnboardingFlow.kt:248-249). Home/Recents arrive via intent extra `system_key` (AppIntents) from the launcher host / RecentsPolicy; Back via `TrackedBackHandler`.
- Bottom sheet (OnboardingFlow.kt:348): Continue / Go to Home both = next; Explore / close dismiss; voice guide auto-plays `res/raw/ob_voice_<lang>` from the 2nd open (pref `onboarding_sheet.opened`).
- `finish()` (OnboardingFlow.kt:164): sets `onboarding_done=true`, `first_home_pending=true`; if launcher on and default home, `startActivities([launcher HOME intent, MainActivity])`; → `home`.
- Track b (paid) docs order starts with `default_home` (set-as-default-launcher is the FIRST screen).

### Home (AppNavHost.kt:119-149, `S/home/HomeScreen.kt`)
- `first_home` event once. Permission sheet (`permissions.home_sheet`, default true) auto-opens on first visit if any `permissions.home` still needed (HomePermissionSheet.kt:110); else `HomePermissionsOnFirstVisit` asks directly (`home_sheet:false`).
- Rate prompt: 1.5 s after home, waits for the sheet, `RatePrompt.maybeAsk` (AppNavHost.kt:131-136).
- Feature/Settings taps → **`Ads.exit(activity, "screens.home")`** (AppNavHost.kt:146-147). Bottom ad `AdSlot("screens.home")` (HomeScreen.kt:386).
- Back on home → **`Ads.backButton(activity)`** (AppNavHost.kt:140) → if default home, start launcher HOME intent; `activity.finish()`.
- Features: Back/top-bar close → `nav.popBackStack()` with no ad (FeatureScreen has no AdSlot / exit ad; FeatureId comment: "if ads are added later").

### Settings (AppNavHost.kt:155-161, `S/settings/SettingsScreen.kt`)
Rows: default-home card (if `launcher.on && default_home_banner && !isDefaultHome`), Language sheet, Theme (light/dark/system), Accent (10 palettes), Gender (male/female), Age sheet, Scan sound, Vibration, Default camera, Flash, Auto-save, Rate (opens `store_link` or Play URL), Share, Feedback (mailto `@string/feedback_email` — **empty string**), Update (`update_link` or Play URL), version. Bottom ad `AdSlot("screens.settings")` (SettingsScreen.kt:218). Back / top-bar → **`Ads.exit(activity, "screens.settings")`** (AppNavHost.kt:158).
**No Privacy Policy / Terms rows** — `base.privacy_link` / `terms_link` are parsed but never used.
**Dead settings**: `scan_sound`, `vibration`, `default_camera`, `flash`, `auto_save` are saved in `app_settings` but no feature reads them (grep: only SettingsStore/SettingsScreen; `CameraPreview(back=true)` hard-coded in EnergyLevelScreen.kt:123; FullBodyScanScreen keeps its own `back` state). `searchWidget` user flag in AppSettings is also never forwarded (AppLauncherHost only merges `homePanel`, `timeWidget`); and the Settings screen has no UI for `homePanel` / `timeWidget` / `searchWidget`.
Open-settings deep link: intent extra `open=settings` (AppIntents) navigates once past onboarding (AppNavHost.kt:69-74).

### Dialogs / prompts
- In-app update (`S/prompts/PlayPrompts.kt:26` `AppUpdatePrompt`, called from `MainActivity.onResume` MainActivity.kt:98): `base.app_update.show` → FLEXIBLE once per activity instance; `force` → IMMEDIATE every resume, **`activity.finish()` if not accepted**. DOWNLOADED → `completeUpdate()`.
- In-app review (`RatePrompt` PlayPrompts.kt:74): opens counted on fresh MainActivity create; due when opens ≥ `rate_us.after_opens` (10) and ≥ `retry_after_hours` (24) since last ask; never during onboarding/ads; once per process.
- No custom exit dialog; exit = Back on home with `launcher.back_button` ad.
- Uninstall AlertDialog (below).

### Uninstall flow (`S/uninstall/`)
Entry only from the launcher long-press menu on our icon → `AppLauncherEvents.onUninstallHostApp` (AppLauncherEvents.kt, `uninstall_flow.flow == "premium"`), else menu offers only Hide (`onHideHostApp` → `OurIcon.hide`).
- `system_dialog=true` → UninstallDialogActivity: OK → paid: `OurIcon.hide` (UninstallDialogActivity.kt:64), organic: App-info settings.
- Else UninstallFlowActivity: paid `splash → language → step1 → step2 → progress → step3`; organic starts at step1. Each forward `Ads.exit("uninstall_flow.<screen>")` (UninstallFlowActivity.kt:73-77); bottom `AdSlot("uninstall_flow.<place>")` (UninstallScreens.kt:77). End (UninstallFlowActivity.kt:149): **paid → icon hidden, app stays installed**; organic → App info. Keep / quick-fix reopen MainActivity (fix ADS/CAMERA/HARD opens Settings).
- RISK: a paid user who explicitly chose "Uninstall" ends with the app still installed and its icon hidden (and the launcher still default home). Strong Play "deceptive behaviour" exposure.

### Launcher host integration (`S/launcherhost/`)
- `LauncherSetup.install` → `Launcher.install(app, AppLauncherHost(app), AppLauncherEvents())`; refreshes launcher on RC `launcher` / user homePanel/timeWidget changes.
- `AppLauncherHost.settings()` = RC launcher AND user switches AND `OurIcon.hidden`; `hostUninstall = uninstall_flow.premium`; `openHostApp` forwards Home during onboarding; `takesHome()`.
- `AppLauncherEvents`: panel/drawer/app-icon/return/sponsored ad hooks → `Ads.panelOpened/drawerOpened/appIconTapped/returnedFromApp/sponsoredTileTapped/returnedFromSponsored`; slots `launcher.suggested_panel`, `Ads.APP_DRAWER`; analytics bridges.
- `HostPanelFragment`: HomeScreen in panel; feature taps → `Ads.exit("screens.home")` (HostPanelFragment.kt:~100); Settings → `openMainActivity`.
- MainActivity `ACTION_CLOSE_SYSTEM_DIALOGS` receiver: after onboarding + default home, Home → `Ads.systemKey(this, "home")` (MainActivity.kt:83). RecentsPolicy: Recents after onboarding in home task → `Ads.systemKey(activity, "recents")` (RecentsPolicy.kt:153).

### Recents / back-stack behaviour (`S/RecentsPolicy.kt`)
Every activity resume calls `AppTask.setExcludeFromRecents(true)` on all tasks; on a Recents press outside the home task **all app tasks are `finishAndRemoveTask()`d** and the app reopens at the same step without splash; inside the home task the screen is forcibly brought back over Recents (`moveTaskToFront`, REORDER_TASKS) and `system_keys.recents` applies. Combined with `excludeFromRecents` in the manifest, the app never appears in Recents. Policy/UX risk (hard to leave/close app).

### Ad call sites in :app (internals → ads agent)
AppNavHost.kt:109 `Ads.splash`; :140 `Ads.backButton`; :146,:147 `Ads.exit("screens.home")`; :158 `Ads.exit("screens.settings")`; OnboardingFlow.kt:185-186, :210, :248-249; UninstallFlowActivity.kt:76; HostPanelFragment.kt:~100; MainActivity.kt:83 `Ads.systemKey("home")`; RecentsPolicy.kt:153 `Ads.systemKey("recents")`; AppLauncherEvents.kt (6 hooks + 3 slots); AdSlot: HomeScreen.kt:386, SettingsScreen.kt:218, OnboardingScaffold.kt:147, OnboardingPage.kt:81/:95, UninstallScreens.kt:77. Consent: AppNavHost.kt:91 `AdKit.requestConsent`.

---

## 5. Permissions (`S/permissions/Permissions.kt`)
- RC-driven names: camera, photos, notification, contacts, phone, overlay (`AppPermission`).
- `manifestName`: camera→CAMERA; **photos→null (system Photo Picker, never asked)**; notification→POST_NOTIFICATIONS only on API ≥ 33 else null; contacts→READ_CONTACTS; phone→READ_PHONE_STATE; overlay→SYSTEM_ALERT_WINDOW via `ACTION_MANAGE_OVERLAY_PERMISSION`.
- `needsAsking` returns false if not declared in merged manifest → contacts/phone/overlay silently never asked (not declared).
- `AskPermissionsOnOpen(screenKey)` (Permissions.kt:118): asks list in order, denial just moves on; **no rationale and no Settings redirect on permanent denial** (`appSettingsIntent` Permissions.kt:102 is unused).
- Screens that can ask: `splash` (after hold), any onboarding screen on open, `permission_hub` (on Allow, PermissionStep.kt), `home` (sheet or direct, first visit only).
- Feature-level: `CameraPermissionGate` (Camera.kt:54) — asks on open, rationale + retry, and after "don't ask again" an "Open settings" button (APPLICATION_DETAILS_SETTINGS). Storage: WRITE_EXTERNAL_STORAGE only API < 29 when saving (ImageIo.kt:~127-137); MediaStore `Pictures/BodyXray` otherwise. Photo selection: `PickVisualMedia` (Gallery, FullBodyScan, BodyFilter).
- Notification permission is requested (RC lists it on permission_hub/home) but the :app posts no notifications of its own (FCM service is data-only; debug event notification is in ads module).

## 6. Analytics (Firebase) — `com.appkit.ads.analytics.Analytics` (ads module) used by :app
Closed allow-list of 12 events (`Analytics.EVENTS`): `screen`, `click`, `key_press`, `permission`, `ad`, `ad_impression`, `default_home`, `app_update`, `rate_us`, `uninstall`, `install_source`, `first_home`. Unknown names dropped. Every event gets `segment` = paid/organic. Param keys snake_case ≤40, strings ≤100.
- `screen` (Screens timer, one per visit on exit; names from TrackScreen: splash, onboarding screen keys, onboarding_bottom_sheet, home, home_permission_sheet, host_app_panel, setting, feature keys, uninstall_flow, uninstall_dialog).
- `click` {screen, button, value}: buttons in :app — accent, add_photo, again, age, allow, allow_all, allow_camera, back, body_filter, body_part, cancel, capture, close, colour, confirm, continue, default_camera, default_home_card, dismiss, done, feature, feedback, filter, fix, flash, full_body_scan, gender, go_home, hair_color/hair_colour, interest, keep, language, mode, next, not_now, open_settings, outside, photo, pick_photo, previous, rate, reason, retake, save, scan_mode, settings, share, skin_color/skin_colour, skip, stage, start, still_uninstall, switch_camera, target, theme, uninstall, uninstall_anyway, uninstall_cancel, uninstall_ok, update, voice_guide, plus SwitchRow names scan_sound/vibration/auto_save.
- `key_press` {screen, key back/home/recents} — TrackedBackHandler (Track.kt), SystemKeys.kt receiver.
- `permission` {screen, permission, result show/allow/deny/already} — Permissions.kt:61-73, Camera.kt, ImageIo.kt ("storage").
- `app_update` {step shown/accepted/declined/failed/installed, value force/flexible} — PlayPrompts.kt.
- `rate_us` {step request/done/failed}.
- `uninstall` {step start/splash/language/step1/step2/progress/step3/keep/fix/hidden/app_info/cancel, value}.
- `install_source` {reason no_referrer/no_gclid/fake_gclid/google_ip/gclid} — InstallAttribution.kt:69.
- `first_home` — AppNavHost.kt:126.
- `default_home`, `ad`, `ad_impression` — emitted from launcher/ads modules (via `AppLauncherEvents.onStep`).
- `TrackingCoverageTest` (unit test) enforces tap/back tracking.

## 7. Crashlytics / stability (`S/crash/CrashGuard.kt`)
- Main-thread crash: Crashlytics saves fatal, then CrashGuard **restarts the current screen in a fresh process and kills itself — no system "app stopped" dialog** (CrashGuard.kt:116). CrashLoopGuard: within 60 s, ≥3 crashes → launch screen, ≥5 → system handler.
- **Background-thread crashes are swallowed** (recorded non-fatal, process continues) (CrashGuard.kt:55-66) — can leave half-dead state.
- Custom keys: crash_kind, screen, onboarding_step, track, default_home. Timber ERROR with throwable → non-fatal. ANR watchdog (5 s main-thread stall → non-fatal). ApplicationExitInfo (API 30+) for ANR/crash/native/low-memory/init-failure reported once.
- ANR safeguards: prefs preload thread; install referrer + IP lookups on background threads with 3 s timeouts; default-home check off main thread for crash keys. Remaining main-thread risks: `runBlocking` used only on background threads (OK); `Launcher.isDefaultHome` called on main in many composables (SettingsScreen caches per resume).

## 8. FCM / notifications / background
- `RcSyncMessagingService` (`S/config/RcSyncMessagingService.kt`): data `type=rc_sync` → `AppConfigStore.syncNow`, blocks up to 20 s (`Tasks.await`). `onNewToken` no-op; topic `rc_sync` subscribed on each app start. No notification UI.
- WorkManager: dependency only, no jobs in :app.
- Install referrer: `InstallAttribution` (`S/config/InstallAttribution.kt`) — see rc.md.

## 9. Persistent keys (SharedPreferences; no DataStore/Room)
- `live_config`: `app_config` or `app_config_debug` (raw RC JSON), `home_profile_rules`, `home_profile_probes`.
- `user_profile`: gender, age, height_cm, weight_kg, language, interests (StringSet), onboarding_done, home_permissions_asked, first_home_pending.
- `app_settings`: theme, accent, scan_sound, vibration, default_camera, flash, auto_save, home_panel, time_widget, search_widget.
- `install_attribution`: track (`track_a`/`track_b`).
- `exclusive_locations`: location (JSON country/country_iso/region_name/region_code/city).
- `rate_prompt`: opens, asked_at.
- `crash_guard`: crash_times, exit_seen.
- `onboarding_sheet`: opened.
- `our_icon`: hidden.
- Preloaded but owned by other modules: `qr_ads_config` (ads `AdSettingsVault`), `com.xray.fullbody.scanner.camera_preferences` (launcher/Fossify).
- `allowBackup=false` → none of this is backed up.

## 10. Localization / theme
- Locales: default (en) + ar, de, es, fr, hi, in (Indonesian, tag `id`), ja, ko, pt, ru, tr, vi = 13 (`S/data/Languages.kt`). 12 string files per locale. Per-app locale via AppCompatDelegate / LocaleManager (API 33+); no `locales_config.xml` → language not exposed in Android 13 system per-app settings. Voice clips only en/hi/es/pt/fr/de/ru/vi.
- Compose theme `S/ui/theme/AppTheme.kt` (ThemeMode light/dark/system, 10 Accent palettes in AppColors.kt); `ScreenScale` scales density by `smallestScreenWidthDp/360` clamped 0.85–1.4 (affects all dp/sp). Portrait locked on all activities.

## 11. Logging, networking, errors
- `android.util.Log` throughout; Timber only as a sink into Crashlytics (planted in CrashGuard). Analytics logs each event at DEBUG only in debug builds.
- Network calls from :app: Firebase (RC/FCM/Analytics/Crashlytics), IP lookup endpoints from RC (`ipconfig.io/json` in docs) via HttpURLConnection, Play Core. No cleartext config; endpoints come from RC so they could be http:// (blocked by default on API 28+).
- Errors: pervasive `runCatching` (failures mostly logged or ignored).

## 12. Tests (`P/app/src/test/...`, JVM only, no instrumentation)
AppConfigParserTest 23, AttributionRulesTest 5, LocationRulesTest 4, CrashLoopGuardTest 2, FeatureLogicTest 4, OnboardingRulesTest 4, RatePromptTest 2, TrackingCoverageTest 1, AppColorsTest (count not checked). Fixture `app/src/test/resources/app_config.json` is an **older copy** than docs (missing rc_sync_hours, exclusive_locations, during_onboarding, home_sheet, system_dialog, recents, auto_open_settings_sec; different exit_ad lists).

## 13. Notable risks (app side)
1. Release build uses Google TEST AdMob app id until `admob.appId` is supplied (only a warning).
2. Paid "premium uninstall" hides the icon instead of uninstalling; label "#Xray Scanner"; aggressive default-home loop (auto-reopen Settings every 3 s, first screen for paid users); Back/Home advance onboarding with ads; app removed from Recents / forced back over Recents — Play policy (deceptive / disruptive) exposure.
3. CrashGuard hides crashes (auto-restart, swallowed background crashes).
4. No privacy policy entry in UI (links empty in docs too); feedback email empty.
5. Five user settings are no-ops.
6. RC permission names contacts/phone/overlay silently ignored (not declared); no settings redirect for generic permission denial.
7. google-services.json and release artefacts committed.
8. Forced in-app update closes the app on decline (`app_update.force`).
9. Unused deps (work-runtime) in :app; Timber version hard-coded.

---

# Part B — `:launcher` module audit (179-Body-Scanner-Home-01), read-only

Root: `/Users/pooja/Documents/Pooja_Apps/10_OCT/179-Body-Scanner-Home-01`
Paths below are relative to it. `L/` = `launcher/src/main/java/com/appkit/launcher/`, `H/` = `app/src/main/java/com/xray/fullbody/scanner/camera/launcherhost/`.

Size: 79 Kotlin files, 13,117 lines (big ones: `ui/LobbyLookerMatrix.kt` 2924, `screens/XHubStage.kt` 2129, `panes/BoardPane.kt` 702). 116 res files (114 XML + 2 fonts: azeret_mono, familjen_grotesk), 26 layouts, 3 xml, 41 drawables, ~50 `values-*` locales, res total 692 KB. **UI is XML + ViewBinding only, no Compose in the module itself.** Fossify Commons pulls Compose in transitively, though (see 7). The app's host panel is Compose.

---

## 1. Build, dependencies, manifest

`launcher/build.gradle.kts`: android-library, kotlin-android, kotlin-parcelize, ksp. namespace `com.appkit.launcher`, compileSdk 36, **minSdk 26**, Java/JVM 17, `buildConfig = true`, `viewBinding = true`, excludes `com.android.support` globally. The app (`app/build.gradle.kts:35`) and `:ads` are also minSdk 26.

Dependencies (versions from `gradle/libs.versions.toml`):
| dep | version |
|---|---|
| androidx.core:core-ktx | 1.16.0 |
| appcompat | 1.7.1 |
| material | 1.12.0 |
| constraintlayout | 2.2.1 |
| fragment-ktx | 1.8.8 (inline) |
| lifecycle-runtime-ktx | 2.9.2 |
| kotlinx-coroutines-android | 1.10.2 |
| **`api` org.fossify:commons** | **6.1.6** (from jitpack, `settings.gradle.kts:13`) |
| room runtime/ktx + ksp compiler | 2.8.4 |
| glide | 4.16.0 |
| gson | 2.11.0 |
| timber | 5.0.1 (hard-coded) |
Kotlin 2.2.10, AGP 8.9.1, KSP 2.2.10-2.0.2.

What fossify-commons 6.1.6 pulls in transitively (from `~/.gradle/caches/.../org.fossify/commons/6.1.6/*.module`): **minSdk 26** (its AAR manifest says `uses-sdk minSdkVersion=26`), activity-compose 1.12.4, compose ui/foundation/animation 1.7.6, material3 1.4.0, material-icons-extended, lifecycle 2.10.0, core-ktx 1.17.0, material 1.13.0, glide 5.0.5 plus glide compose (so the glide 4.16 pin gets bumped), gson 2.13.2, kotlin-stdlib 2.3.10, kotlinx-serialization-json, joda-time, ez-vcard, biometric 1.4.0-alpha02, patternLockView, reprint, RecyclerView-FastScroller, rtl-viewpager, room 2.8.4, documentfile, exifinterface, swiperefreshlayout.

`launcher/consumer-rules.pro`: empty (no content printed). **Unknown** whether the module survives R8 without keep rules. The Room/Gson entities and reflection (`StatusBarManager.expandNotificationsPanel`, `XHubStage.kt:1776`) are the parts at risk.

`launcher/src/main/AndroidManifest.xml`:
- Permissions: `EXPAND_STATUS_BAR`, `REQUEST_DELETE_PACKAGES`, `BIND_APPWIDGET` (`tools:ignore="ProtectedPermissions"`). **No QUERY_ALL_PACKAGES.** It uses a `<queries>` block instead: MAIN+LAUNCHER, CREATE_SHORTCUT, MAIN+HOME, SENDTO smsto, VIEW+BROWSABLE http/https, APPWIDGET_UPDATE.
- `screens.XHubStage`: exported, singleTask, clearTaskOnLaunch, stateNotNeeded, portrait, theme `Launcher.LauncherTheme`. Intent filters: **MAIN + HOME + DEFAULT**, and `android.content.pm.action.CONFIRM_PIN_SHORTCUT`.
- `screens.MainLobbyHintStage`: the coach-mark. Not exported, singleInstance, `taskAffinity=""`, excludeFromRecents, theme `Launcher.HintOverlayTheme`.
- `listeners.SecureCallerManagerListener`: DeviceAdminReceiver, exported, guarded by `BIND_DEVICE_ADMIN`, policy `xml/lnch_device_admin_policies.xml` = `<force-lock/>`.
- `gadgets.AnalogClockWidgetProvider`, `gadgets.DigitalClockWidgetProvider`: exported AppWidget providers.

Fossify Commons merges in more (from the AAR manifest): `<queries>` for `org.fossify.thankyou(.debug)`, permission `org.fossify.android.permission.WRITE_GLOBAL_SETTINGS`, six non-exported activities (Contributors, FAQ, License, ManageBlockedNumbers, Donation, AppLock), and **an exported `FossifyBroadcastReceiver` with no permission** (action `org.fossify.android.GLOBAL_CONFIG_UPDATED`). `app/src/main/AndroidManifest.xml:12` removes WRITE_GLOBAL_SETTINGS. Lines 77-100 merge-override those six activities to portrait. The receiver is not removed.

## 2. Packages

| package | purpose | key classes |
|---|---|---|
| root | Module entry point (`object Launcher : LauncherApi`). Holds the host and events, `settings()`, `onboardingDone()` (defaults to true when no host), `refresh()` → `XHubStage.current.onSettingsChanged()` | `Launcher.kt` |
| api | The host contract | `LauncherApi.kt` (`LauncherHost`, `LauncherEvents`, `LauncherApi`, enums `LauncherPanel{HOST_APP,SUGGESTED}`, `LauncherSlot{SUGGESTED_BOTTOM,SUGGESTED_LIST,DRAWER}`), `LauncherSettings.kt` (`LauncherSettings`, `SwipeGuide`, `SponsoredApps`, `SponsoredApp`) |
| screens | Activities | `XHubStage` (HOME activity, extends `HubRootStage`, implements `FlingGuardian`), `HubRootStage` (extends Fossify `BaseSimpleActivity`, spoofs package name, see 7), `MainLobbyHintStage` (coach-mark, kinds OVERLAY / DEFAULT_HOME / DEFAULT_SMS) |
| panes | Full-screen custom views that slide in (`HubPane<VB: ViewBinding>` is a RelativeLayout) | `BoardPane` (app drawer, vertical or paged, ad slot, sponsored tiles), `GadgetsStagePane` (left-swipe "suggested" panel: search, suggested and recents grids, 2 ad slots), `MailboxStagePane` (right-swipe host-app panel that hosts the host Fragment), `WidgetsPane` (widget picker) |
| lists | RecyclerView adapters | `HubsLister` (drawer, including sponsored tiles and promo slot spans), `BoardPagesLister` (paged drawer), `StageGadgetsLister`, `WidgetsLister` |
| ui | Home workspace | `LobbyLookerMatrix` (canvas-drawn home grid, dock, folders, drag/drop, widgets, resize), `CustomGadgetWidgetHolder` (AppWidgetHost, id 12345), `CustomGadgetWidgetHolderPane` (AppWidgetHostView), `CustomGadgetWidgetSizeFrame`, `FadeAwayMatrixPane` (MyRecyclerView), `LobbyLookerMatrixDrawingArea` |
| intro | Host or ad hook glue and guides | `HubOfferCoordinator` (gesture → `LauncherEvents` with a run-once `proceed`, plus "away" tracking), `HubInlinePromo` (binds slots), `HubPromosSwitches` (panel and guide gates), `PromotedTiles` (sponsored drawer tiles), `HubHomeAsBack` (HOME → host during onboarding, burst guard of 6 in 3 s), `HubRecentsCharter` (excludes every app task from Recents), `HubGuideHalt` (swipe-guide steps) |
| persona | Copies the previous OEM launcher's grid (cols/rows/drawer mode/icon size) | `HubStamp` (fingerprint of the previous home app and device), `ApkPersonaDecoder` (reads the stock launcher's resources via `getResourcesForApplication`; `DEFAULT_PROBES_JSON` for miui and others), `ApkAssetDirectory` (opens the other launcher's APK with `ZipFile`), `LobbyPersonaPolicies` (rule tables, built-in miui/vivo/huawei profiles), `LobbyPersonaLocator`, `LobbyPersonaApplier`, `LobbyPositioner`, `LobbyWidgetPositioner` (time and search pseudo-widgets), `LobbyGadgetsPopulator`, `GenericMatrixDecoder`, `OemBuildDetail` |
| support | Config, constants, icon pipeline, default-home requester | `Config` (Fossify `BaseConfig` prefs), `Constants.kt`, `MainHubRequester`, `ProgramSweep` (app list scan, prewarm), `SymbolFormer` / `SymbolLoader` / `SymbolRepo` / `LaunchpadSymbols` / `EmblemShape` (icons), `GadgetSelections`, `Transformers` (Room converters) |
| entities | Room entities and models | `GadgetHub` (`apps`), `LobbyLookerMatrixDatum` (`home_screen_grid_items`), `HiddenSymbol` (`hidden_icons`), `GadgetActivity` (`app_usage`), `GadgetWidget`, `WidgetsPile*` |
| contracts | DAOs and listener interfaces | `appletHomebasesDao`, `atriumViewerGridRecordsDao`, `hiddenGlyphsDao`, `appletActivityDao`, `AllGadgetsGuardian`, `WidgetsBlockGuardian`, `DatumPrefsGuardian`, `FlingGuardian` |
| storage | Room DB | `GadgetsDb` (v6, file `lnch_apps.db`, migration 5→6 adds `app_usage`) |
| popups | Dialogs | `EntryActionsSheet` (One UI-style long-press card), `RenameDatumSheet` |
| gadgets | Own home-screen widgets | Analog and Digital clock providers, `ClockWidgetPrefs` (prefs `widget_prefs`) |
| listeners | | `SecureCallerManagerListener` (DeviceAdminReceiver for double-tap lock) |
| exts | Extensions | `Activity.kt` (`fireApplet` with the ad hook, `promptAssignAsMainHomebase`, `deleteApplet`, long-press menu builder), `Context.kt` (DB accessors, `isPrimaryLaunchpad`, roleManager, default-SMS check), `View.kt`, `Motion.kt`, `WallpaperColors.kt`, `Misc.kt` |

The code is a renamed and obfuscated derivative of **Fossify Launcher** (`org.fossify.home`). Evidence: `HubRootStage` spoofs exactly `org.fossify.home`; `Config` extends Fossify `BaseConfig`; the pref keys match Fossify Launcher (`was_home_screen_init`, `close_app_drawer`, `auto_show_keyboard_in_app_drawer`, …); `WIDGET_HOST_ID=12345`; it uses `MyRecyclerView` and `MyGridLayoutManager`. Comments also cite internal specs "MSG-05" and "MSG-L-06".

## 3. User flow

**Becoming default home.** `LauncherApi.requestDefaultHome` → `MainHubRequester.request` (`L/support/MainHubRequester.kt:161`):
1. Already default → `onResult(true)`.
2. `start()`: captures `HubStamp` (the previous launcher), then tries `Settings.ACTION_HOME_SETTINGS`, then `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`, via `startActivityForResult(REQUEST_SET_DEFAULT=54)`. On success it shows the coach-mark `MainLobbyHintStage.show(activity)` (Kind DEFAULT_HOME: bottom sheet that closes itself after 3 s; it polls every 150 ms for up to 6 s until the host loses window focus, waits 350 ms, then starts the hint activity with NEW_TASK). It also starts a return watch: a 300 ms poll for up to 5 min that raises the host activity (`REORDER_TO_FRONT|CLEAR_TOP|SINGLE_TOP`) as soon as the HOME role is held.
3. On resume without a grant from Settings: it shows the `RoleManager.createRequestRoleIntent(ROLE_HOME)` dialog once (Q+). If that is also cancelled → `onResult(isDefault)`.
4. If nothing could launch, the fallback is `exts/Activity.kt` `promptAssignAsMainHomebase()` (HOME_SETTINGS → role dialog → MANAGE_DEFAULT_APPS → ACTION_SETTINGS).
Analytics steps (`default_home`): settings_open, dialog_show, settings_back, dialog_cancel, granted.

Host callers: `H/../onboarding/OnboardingFlow.kt:222` (onboarding DEFAULT_HOME step), `settings/SettingsScreen.kt:168` (the "default home card", shown when `launcher.on && default_home_banner && !isDefaultHome`, line 167).

**Home surfaces** (`XHubStage`): the home grid `mainShowGridIx` (LobbyLookerMatrix).
- Fling up → drawer `allAppsBlockIx` (`onFlingUp`, line 1751; disabled when `drawer_mode=none`).
- Fling down → closes the open surface, else expands the notification shade by reflection (`StatusBarManager.expandNotificationsPanel`, line 1776).
- Fling right on the first page → host-app panel `msgsPageIx` (`surfaceMsgsDeck`, line 1213).
- Fling left on the last page → suggested panel `startPageIx` (`surfaceLeftDeck`, line 1162).
- The widgets picker is `widgetsBlockIx`.
- Double-tap on empty space → `DevicePolicyManager.lockNow()`, only if the admin is already active (line 1449). **No code in the launcher or app activates the device admin** (no `ACTION_ADD_DEVICE_ADMIN` found), so this is effectively dead unless the user enables it manually.
- The swipe guide (`perhapsSurfaceGuide`) is gated by `swipe_guide`.
- Long press opens `EntryActionsSheet`. On our own icon it offers only Hide, plus Uninstall when `hostUninstall` (`exts/Activity.kt:155-165`).
- Uninstall of other apps uses `ACTION_DELETE` plus a 10 s watch.
- Widgets: `ACTION_APPWIDGET_BIND` (line 2058) and the configure activity. Pinned shortcuts are handled in `onActivityResult`.
- The `LauncherApps` callback refreshes the list on package changes.

**Back** (`onBackPressedCompat`, line 676): reports `onKey("back")`, then closes in this order: left panel, host panel, drawer (restore search), widgets (clear search first), resize frame. **It always returns true**, so Back never leaves the launcher. The host panel fragment claims Back through its own callback (see 4) before this runs.

**HOME press** (`onNewIntent`, line 499): if onboarding is unfinished, `HubHomeAsBack.onHome` hands control to the host. Otherwise it closes every surface and popup and jumps to page 0 when already on home.

**Cold start as launcher.** `App.onCreate` installs the launcher only if the saved RC has `launcher.on`. Otherwise it waits for the first config with `on=true` (`App.kt:77-78`).
- `XHubStage.onCreate`: if host onboarding is unfinished and `takesHome()`, it calls `openHostApp` and `finish()` without animation (line 233).
- Otherwise it applies the persona style and icon sizes, inflates, registers the LauncherApps callback, positions the panes off-screen, and handles the `open_messages` extra. After 250 ms plus idle it warms up the host panel fragment (only if the right panel is enabled).
- `onResume` loads the app list from Room (`SymbolRepo`) or `ProgramSweep`, and reports `onScreen` and the return-from-app moment.
- If the host is not installed yet (RC fetch pending): `onboardingDone()` defaults to true and `hostAppPanel` needs `host != null`. So it is a plain launcher with no panels and no ads.

**Normal app launch** (app icon / MainActivity): handled by `:app` (the other agent covers it). The launcher's own icon (`wakeOwnApplet`, line 1185) calls `host.openHostApp`. At the end of onboarding, `OnboardingFlow.kt:169` starts `launcherHomeIntent` and then the app on top, so Back lands on our launcher.

**Recents:** `HubRecentsCharter` (installed in `Launcher.install`) calls `setExcludeFromRecents(true)` on **every task of the app on every activity resume**. The app never shows in Recents.

## 4. Host contract (how `:app` hosts it)

Implemented in `H/`:
- `LauncherSetup.install(app)` (called from `App.kt:77-78`): `Launcher.install(app, AppLauncherHost(app), AppLauncherEvents())`. It then collects `AppConfigStore.current.launcher` combined with SettingsStore `homePanel`/`timeWidget` and calls `Launcher.refresh` on each change.
- `AppLauncherHost : LauncherHost`:
  - `settings()` returns the RC `LauncherSettings`, ANDed with the user switches (`homePanel`, `timeWidget`) and the `OurIcon.hidden` state; `hostUninstall = uninstall_flow.flow=="premium"`.
  - `isOnboardingDone()` reads `UserProfileStore`.
  - `createHostPanel()` returns `HostPanelFragment()`.
  - `openHostApp()` calls `openMainActivity` (with special Home-key and MIUI Recents handling, and brings the existing task to front if `ExitAdRunner.busy()`).
  - `takesHome()` is true unless RC `system_keys.home == ""`, or while `DefaultHomeStep.shown` or `RecentsPolicy.resuming`.
- `HostPanelFragment`: a `ComposeView` with its own NavHost (`home` → `HomeScreen`, `feature/{id}` → `FeatureScreen`), wrapped in `ScreenScale` / `AppTheme`. Nothing is composed until the first `onResume` (`shown`). NavHost Back goes to a **private `OnBackPressedDispatcher`**, forwarded only while RESUMED and the dispatcher has enabled callbacks. This stops a hidden panel from eating launcher Back.
- `OurIcon`: prefs `our_icon` / `hidden`. `hide()` calls `Launcher.refresh`.
- Other app references: `RecentsPolicy.kt` (94/140/153/156/184), `MainActivity.kt:83-85`, `App.kt:68` (`AdKit.isHomeScreen`), `CrashGuard.kt:36` (hard-coded `com.appkit.launcher.screens.XHubStage`) and `:106` (Crashlytics key `default_home`), `AppNavHost.kt:141`, `AppConfig.kt` / `AppConfigParser.kt` (types), `OnboardingRules.kt:13`, the uninstall activities, and tests `AppConfigParserTest`, `OnboardingRulesTest`.

**Embedding:** `MailboxStagePane.refresh()` (`L/panes/MailboxStagePane.kt:49`) does `replace(R.id.postbox_holderIx, fragment).setMaxLifecycle(STARTED).commitNow()`. `onPanelSettled()` (called after the slide-in animation ends, `XHubStage.kt:1228`) raises it to RESUMED. `setPanelVisible(false)` drops it back to STARTED. **This is the off-screen prevention:** the host only loads ads and prompts in onResume, i.e. when the panel is fully on screen.

**Ad call sites (moments):**
| moment | launcher call site | host mapping (`H/AppLauncherEvents.kt`) |
|---|---|---|
| right panel open | `XHubStage.kt:1218` `HubOfferCoordinator.run(RIGHT_SWIPE)` → `intro/HubOfferCoordinator.kt:33` | `Ads.panelOpened(.., "host_app_panel", proceed)` (line 19) |
| left panel open | `XHubStage.kt:1167` | `Ads.panelOpened(.., "suggested_panel")` |
| drawer open | `XHubStage.kt:1336` | `Ads.drawerOpened` (line 22) |
| app icon tap | `exts/Activity.kt:56` `fireApplet` (skipped for our own package) | `Ads.appIconTapped` (line 24) |
| return from app / sponsored | `XHubStage.reportReturn` (1199, from onResume 578) → `HubOfferCoordinator.onHomeResume` | `Ads.returnedFromApp` / `Ads.returnedFromSponsored` |
| sponsored tile tap | `BoardPane.kt:255` → `PromotedTiles.open` (`intro/PromotedTiles.kt:63`) | `Ads.sponsoredTileTapped` |
| inline slots | `BoardPane.kt:120` (DRAWER), `GadgetsStagePane.kt:122/125` (SUGGESTED_LIST / SUGGESTED_BOTTOM), via `HubInlinePromo.wireOnReveal` → `LauncherEvents.bindSlot` | `Ads.bottom(.., "launcher.suggested_panel")`, `Ads.list(..)`, `Ads.bottom(.., Ads.APP_DRAWER)` (lines 32-36) |
| host panel feature open | `H/HostPanelFragment.kt:100` | `Ads.exit(it, "screens.home")` |

Inline slots are bound only after the gesture's `proceed` runs: `reloadUnits()` is called inside the `run{}` at `XHubStage.kt:1168` and `1336`. They are not bound at layout time. `proceed` is run-once and fires immediately if the host throws (`HubOfferCoordinator.kt:21-41`, `PromotedTiles.kt:53-63`).

Home, Back and Recents key ads (`ads.launcher.home_button` and similar) live in `:app` (`MainActivity.kt:83`, `RecentsPolicy.kt:153`), not in the launcher.

## 5. Remote Config read by the launcher

Firebase key `app_config` (`app_config_debug` in debug, `config/AppConfigStore.kt:23`). It is parsed by the host's `AppConfigParser.parse` (`config/AppConfigParser.kt:37`): per-track `track_a` / `track_b` (organic or paid), with `countries["CC,CC"]` overlays merged over the track, and `base.exclusive_locations` forcing track A. **Geo gating is therefore track- and country-level, done in the host. The launcher module has no geo logic.** Separate Firebase keys `home_profile_rules` and `home_profile_probes` (`AppConfigStore.kt:141-142`) are passed through as raw JSON strings.

`launcher(...)` in `AppConfigParser.kt:198-226`:
| JSON path (under track) | type | default | field | used in launcher at |
|---|---|---|---|---|
| `launcher.on` | bool | false | `on` | **not read by the module**. Host only: `App.kt:77`, `OnboardingRules.kt:13`, `SettingsScreen.kt:167`, `OnboardingFlow.kt:169` |
| `launcher.os_style` | bool | false | `osStyle` | `persona/LobbyPersonaApplier.kt:68` |
| `launcher.default_home_banner` | bool | false | `defaultHomeBanner` | host only (`SettingsScreen.kt:167`) |
| `launcher.our_icon.in_drawer` | bool | true | `iconInDrawer` | `BoardPane.kt:229`, `GadgetsStagePane.kt:137` |
| `launcher.our_icon.on_home` | bool | true | `iconOnHome` | `LobbyLookerMatrix.kt:296` |
| `launcher.panels.host_app` | bool | true | `hostAppPanel` | `HubPromosSwitches.kt:29` (ANDed with the user `homePanel`) |
| `launcher.panels.suggested_panel` | bool | false | `suggestedPanel` | `HubPromosSwitches.kt:28` |
| `launcher.swipe_guide.on` / `.swipe_right_host_app` / `.swipe_left_suggested_panel` / `.swipe_up_drawer` | bool | false / true / true / true | `swipeGuide` | `HubPromosSwitches.guidePhaseEnabled` |
| `launcher.time_widget` | bool | true | `timeWidget` | `LobbyWidgetPositioner.kt:238` (ANDed with the user switch) |
| `launcher.search_widget` | "google" or "chrome" | "google" | `searchWidget` | `LobbyWidgetPositioner.kt:71/243` |
| `ads.launcher.drawer_sponsored_apps.on` / `.every` (min 1) / `.list[{logo,landing_url,label}]` | bool / int / array | false / 8 / [] (blank `landing_url` dropped) | `sponsoredApps` | `PromotedTiles.kt:23` |
| `ads.launcher.app_drawer.bottom_ad_row` | int | 1 | `drawerAdRow` | `HubPromosSwitches.kt:34` |
| `uninstall_flow.flow == "premium"` | string | normal | `hostUninstall` (set in `AppLauncherHost.settings()`) | `exts/Activity.kt:162` |
| `home_profile_rules` (separate key) | JSON string | "" = built-in | `homeProfileRulesJson` | `LobbyPersonaApplier.kt:26`, `LobbyPersonaLocator.kt:14`, parsed by `LobbyPersonaPolicies.parse` |
| `home_profile_probes` (separate key) | JSON string | "" → `ApkPersonaDecoder.DEFAULT_PROBES_JSON` | `homeProfileProbesJson` | `LobbyPersonaLocator.kt:22` |
| `launcher.system_keys.*` | | | host `SystemKeys` | host only (`takesHome`) |

The remaining `ads.launcher.*` places (app_resume, back_button, home_button, recents_button, app_exit, app_open, host_app_panel, suggested_panel bottom_ad/list_ad, app_drawer exit/bottom_ad, sponsored_app_open/exit, gap_sec, preload) are consumed by `:ads` (other agent). They are documented in `docs/remote-config/RC_GUIDE.md:138-161`, with samples in `docs/remote-config/app_config.json`.

Feature flags are re-read on every `Launcher.settings()` call. `refresh()` closes panels that have just been switched off (`XHubStage.closeDisabledPanels`, line 165).

## 6. Storage, analytics, runtime permissions

Prefs:
- Fossify `BaseConfig` SharedPreferences named `"Prefs"` (Fossify `PREFS_KEY`, verified in the commons sources jar). Launcher keys are in `L/support/Constants.kt:8-59`: was_home_screen_init, dock_sms_package, self_icon_hidden, was_onboarding_completed, was_search_bar_purged, guide_step_done_<STEP>, update_declined_at, home_fingerprint_json, home_profile_json, drawer_mode, drawer_row_count, drawer_style_when_on, dock_column_count, drawer_search_at_bottom, icon_scale, label_text_sp, icon_size_dp, dock_icon_size_dp, drawer_icon_size_dp, legacy_icon_tray, home_row_count, home_column_count, drawer_column_count, show_search_bar, close_app_drawer, auto_show_keyboard_in_app_drawer, show_drawer_app_labels, show_home_app_labels, home_seeded, last_install_seen, home_and_drawer, launcher_style, home_rebuild_pending, search_widget, time_widget_shown, search_widget_shown, time_widget_at_top.
- **Collision risk:** the host app shares the `"Prefs"` file if it also uses Fossify BaseConfig. Unknown whether it does.
- Also `widget_prefs` (clock widgets), host `our_icon`, and saved-state keys `default_launcher_pending` / `default_launcher_role_dialog_shown`.

Room: `lnch_apps.db` v6, tables `apps`, `home_screen_grid_items`, `hidden_icons`, `app_usage` (launch_count and last_launched per package, a local "recents").

Analytics: the module only calls `LauncherEvents`. The host maps these to `Screens.show` / `Analytics.click` / `keyPress` / `step` from `:ads`.
- Screens: launcher, app_drawer, host_app_panel, suggested_panel, default_home_hint.
- Clicks: our_icon, app_icon, folder, shortcut, sponsored_tile (value = tile label), double_tap, swipe_guide, menu_hide/rename/resize/app_info/remove/uninstall/shortcut, rename_ok/cancel, widget, widget_resize_done, search_widget, time_widget, google_search_widget(+voice), widgets_list_item(_long_press), drawer_search, search_shortcut, panel_search(_in_app/_clear), panel_see_more, default_home_hint_close/got_it.
- Keys: back. Steps: default_home/*.

Runtime permissions: **none requested at runtime by the module.**
- BIND_APPWIDGET is handled with `ACTION_APPWIDGET_BIND` (system consent).
- The pinned-shortcut path needs `hasShortcutHostPermission`, which only holds while we are the default home.
- `XHubStage.kt:62-63` imports `PERMISSION_READ_CONTACTS` / `PERMISSION_READ_SMS`, but `promptMsgsAccess` / `promptTextAccess` (line 1294) are no-op stubs that just run the callback. No notification listener, no usage stats, no overlay permission (the OVERLAY hint kind exists but its use was not found), no QUERY_ALL_PACKAGES.
- It reads the default SMS package (`Telephony.Sms.getDefaultSmsPackage`) for the dock messaging slot.

## 7. Risks

**Licensing (highest):**
- Root `LICENSE` is MIT, "Copyright (c) 2026 Jay Dedaniya". The module is clearly derived from Fossify Launcher and depends (as `api`) on Fossify Commons. Upstream, both are GPL-3.0. *Not verifiable locally:* the POM has no `<licenses>` and there are no headers in the sources jar. GPL-3.0 copyleft would apply to the whole APK, so MIT relicensing and closed distribution are incompatible.
- No attribution or NOTICE is found in the module.
- `HubRootStage` (`L/screens/HubRootStage.kt:8-32`) **spoofs `getPackageName()` as `org.fossify.home` during `super.onCreate`** to suppress Fossify's `showModdedAppWarning()`. Verified in the commons source: `if (!packageName.startsWith("org.fossify.")) ... showModdedAppWarning()`. Other `startsWith("org.fossify")` checks in commons (for example `pickPrimaryColor`, which finishes the screen after 50 runs) are not spoofed. **Unknown** whether the launcher reaches them.

**Play policy:**
- A HOME launcher bundled into an X-ray scanner app: the core function mismatch risks the Misleading Claims / Deceptive Behavior policies.
- Interstitial and app-open ads on opening other apps, on returning from apps, and on swiping panels (`ads.launcher.app_open`, `app_exit`, `host_app_panel`, …) are out-of-context ads that interfere with device navigation. This is a Disruptive Ads policy risk.
- Sponsored drawer tiles open arbitrary `landing_url`s.
- The app is hidden from Recents permanently (`HubRecentsCharter`).
- The "premium" uninstall flow hides the icon instead of uninstalling (`OurIcon.hide`, `AppLauncherEvents.onUninstallHostApp`): a deceptive uninstall pattern.
- Back never exits the launcher.
- Upside: QUERY_ALL_PACKAGES is avoided via `<queries>`, which is good. BIND_APPWIDGET with `ignore ProtectedPermissions` is normal for launchers. The device-admin receiver (`force-lock`) needs a Play declaration if ever activated.
- The exported Fossify receiver has no permission.
- The coach-mark starts an activity over Settings via NEW_TASK after focus loss. Background-activity-launch limits may block it on some Android versions (**unverified**).

**Behavioural:**
- `XHubStage` is always declared with a HOME filter even when RC `launcher.on=false`. No `setComponentEnabledSetting` was found, so the user can still pick it as home and get a plain launcher.
- The persona code reads other launchers' APKs and resources (`ApkAssetDirectory` uses ZipFile on the APK, `getResourcesForApplication`). It is legal, but fragile across OEMs.

**Porting into an XML/ViewBinding app with minSdk 24 and no Compose:**
1. **minSdk 26 is hard.** Both `:launcher` and Fossify Commons 6.1.6 (AAR `minSdkVersion=26`) require it. Going to minSdk 24 means a manifest `overrideLibrary` plus guarding every API 26+ call, or replacing Fossify. The code also uses `isOreoMr1Plus`, `LauncherApps` pin-shortcut APIs (26), and `RoleManager` (29, already guarded).
2. **Fossify drags in Compose and Material3** (activity-compose, compose 1.7.6, material3 1.4.0, icons-extended), plus kotlin-stdlib 2.3.10 and glide 5. "No Compose" is impossible while depending on Fossify. Removing Fossify means rewriting `BaseSimpleActivity`, `BaseConfig`, `MyRecyclerView`, `MyGridLayoutManager`, `MySearchMenu`, `FontHelper`, `viewBinding` delegates and about 30 extension helpers (20 files import `org.fossify`, 7 res files reference it).
3. The module UI itself is XML/ViewBinding, so it fits an XML app. Only the host panel needs to change: `HostPanelFragment` is Compose, and an XML app would supply a View/ViewBinding Fragment. The `setMaxLifecycle` contract and the private Back-dispatcher trick must be replicated.
4. The two god classes (`XHubStage` 2.1k lines, `LobbyLookerMatrix` 2.9k lines, canvas-drawn) are hard to modify safely.
5. The host must supply `AppConfigStore`-style RC plumbing, analytics, `Ads.*` moments, onboarding state, `RecentsPolicy`, and `CrashGuard` hard-coding the class name.
6. Room DB plus KSP. The empty consumer-rules mean R8 keep rules are **unknown**.

Not checked: R8 release build behaviour, runtime on device, which commons code paths besides `onCreate` hit the package-name checks, and `:ads` internals (other agent).
