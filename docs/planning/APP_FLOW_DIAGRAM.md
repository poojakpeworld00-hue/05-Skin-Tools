# App Flow Diagrams

Mermaid diagrams (render in GitHub / Android Studio Markdown preview). Place keys in `quotes` are Remote Config ad places (see `ADS_COMPLETE_MAPPING.md`).

## 1. Reference app — cold start to Home (`:app`, Compose single Activity)

```mermaid
flowchart TD
    A[Launcher icon] --> B[MainActivity<br/>installSplashScreen]
    B --> C{RecentsPolicy.takeResume?}
    C -- resume --> H0{onboardingDone?}
    C -- normal --> S[Compose splash route]
    S --> S1[AdKit.requestConsent UMP] --> S2[AdKit.preload all formats]
    S2 --> S3[hold base.splash_wait_sec 4s<br/>then wait ≤6s for AppConfigStore.ready]
    S3 --> S4[await InstallAttribution + ExclusiveLocations ≤3s → lock track]
    S4 --> S5[AskPermissionsOnOpen 'splash']
    S5 --> S6[Ads.splash → exit_ad 'screens.splash']
    S6 --> H0
    H0 -- no --> O[Onboarding<br/>order = track.onboarding.order]
    H0 -- yes --> HM[Home]
    O --> O1[each screen: AskPermissionsOnOpen + bottom_ad 'onboarding.KEY']
    O1 --> O2[Next/Continue/Skip/system key 'next'<br/>→ Ads.exit 'onboarding.KEY']
    O2 -->|more| O1
    O2 -->|default_home step| DH[MainHubRequester: Home settings → coach-mark → role dialog<br/>reopen every auto_open_settings_sec]
    DH -->|granted| O2
    O2 -->|last| F[onboarding_done=true → launcher HOME + MainActivity]
    F --> HM
    HM --> HP[first visit: permission sheet → rate prompt 1.5s]
    HM -->|feature / settings| X1[Ads.exit 'screens.home'] --> FE[Feature / Settings]
    FE -->|Settings back| X2[Ads.exit 'screens.settings'] --> HM
    HM -->|Back| X3[Ads.backButton 'launcher.back_button'] --> EXIT[launcher HOME or finish]
```

## 2. Reference app — background triggers

```mermaid
flowchart LR
    BG[App to background] -->|AppResumeWatcher| R[Ads.appResume 'launcher.app_resume'<br/>skipped: own launch, ad activities, busy]
    HK[Home key ACTION_CLOSE_SYSTEM_DIALOGS] --> HK1[Ads.systemKey 'launcher.home_button']
    RK[Recents key] --> RK1{play_store_return due?} -- yes --> PS[Play Store home jump]
    RK1 -- no --> RK2[Ads.systemKey 'launcher.recents_button' / RecentAdActivity]
    UN[ACTION_USER_PRESENT] --> UN1[on launcher home → 'launcher.phone_unlock']
    CH[Power connected/disconnected] --> CH1[ChargingStatusActivity over lock screen<br/>'launcher.charger_*']
    PK[Package added/removed] --> PK1[PackageResultActivity 'launcher.app_installed/uninstalled']
    LZ[Launcher gestures] --> LZ1[panel / drawer / app icon / return / sponsored tile<br/>'launcher.host_app_panel' … 'sponsored_app_exit']
    LI[Launcher long-press our icon → Uninstall] --> UF[UninstallFlowActivity / UninstallDialogActivity<br/>premium: icon hidden, app stays]
```

## 3. FFF today

```mermaid
flowchart TD
    A[Launcher icon] --> SP[SplashActivity fixed 2.8s]
    SP -->|onboarded| HM[HomeActivity]
    SP -->|first run| OB[OnboardingActivity 13 steps]
    OB -->|Get Started / Skip| HM
    HM --> L[ListActivity] --> L2[ListActivity group] --> D[DetailActivity → Download]
    L --> D
    HM --> AR[ArticlesActivity] --> AR2[ArticleActivity]
    HM --> CA[CalculatorActivity] --> CA2[CalcActivity]
    HM --> ST[SettingsActivity] --> LG[LanguageActivity]
    HM -->|Back ×2 in 2s| EXIT[finish]
```

## 4. FFF target (proposed — infrastructure from the reference, FFF screens kept)

```mermaid
flowchart TD
    A[Launcher icon] --> SP[SplashActivity]
    SP --> S1[UMP consent → AdKit.preload]
    S1 --> S2[hold base.splash_wait_sec, wait ≤6s for AppConfigStore.ready]
    S2 --> S3[optional: attribution/location ≤3s → track]
    S3 --> S4[AskPermissionsOnOpen 'splash' — only names valid for FFF]
    S4 --> S5[Ads.exit 'screens.splash']
    S5 -->|onboarded| HM[HomeActivity]
    S5 -->|first run| OB[OnboardingActivity<br/>steps filtered by track.onboarding.order]
    OB --> OB1[bottom_ad 'onboarding.STEP' + inline native on language/gender/age]
    OB1 -->|Continue / Skip| OB2[Ads.exit 'onboarding.STEP'] -->|next| OB1
    OB2 -->|last| HM
    HM --> HM1[bottom_ad 'screens.home'; first visit: rate prompt]
    HM -->|tile / tool / settings| X1[Ads.exit 'screens.home'] --> DEST[List / Articles / Calculator / Settings]
    DEST -->|item| X2[Ads.exit 'screens.list' …] --> DET[Detail / Article / Calc]
    DET -->|Back| X3[Ads.exit 'screens.detail' …] --> DEST
    HM -->|Back| X4[Ads.exit 'launcher.back_button'] --> EXIT[double-back rule or finish — decision]
    BG[App resume from background] --> R[Ads.appResume 'launcher.app_resume']
```

Launcher, uninstall flow, Recents/Home-key ads and event screens are **not** in the FFF target unless approved (see Pending questions in `REFERENCE_VS_TARGET_COMPARISON.md`).
