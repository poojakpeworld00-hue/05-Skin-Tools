# Implementation Checklist

Tick in order. Nothing below has been started. Items marked 🔒 are blocked on your input.

## 0. Before any code
- [ ] 🔒 Decisions D1–D12 answered (`REFERENCE_VS_TARGET_COMPARISON.md`)
- [ ] 🔒 Firebase project for `com.fffemotes.skintools.master` + `google-services.json`
- [ ] 🔒 AdMob app id + real unit ids (inter, inter_splash, inter_back, app_open, native, banner, rewarded — only those you want)
- [ ] 🔒 Privacy policy, terms, store links; `ads.links` list (or none)
- [ ] Work on a new branch from `v_1.0.0`; keep `docs/planning/` as the spec

## 1. Architecture & Gradle
- [ ] `:ads` module created, builds empty
- [ ] Java 17, `buildConfig`, AdMob placeholder, release fails without real app id
- [ ] Firebase plugins work on AGP 9.4.1 (else bump plugin versions)
- [ ] No Compose dependency anywhere (`./gradlew :app:dependencies | grep compose` → empty)
- [ ] minSdk 24 kept

## 2. Init & Remote Config
- [ ] `App` init order matches plan
- [ ] `AppConfigStore` fetch/activate, `rc_sync_hours`, saved JSON, `ready` flow
- [ ] `remote_config_defaults.xml` generated from FFF `app_config.json`; parser test proves defaults == fixture
- [ ] FCM `rc_sync` topic + service, no double fetch
- [ ] Parser tests ported (FFF fixture) and green

## 3. Ads module
- [ ] Core façade + renderers ported, package renamed (D8)
- [ ] Dead legacy code removed; `lottie` not added
- [ ] Fixes: rewarded show-failure fallback · ad expiry · native destroy · preload de-dupe · `markOwnLaunch` used for share/rate/links
- [ ] UMP consent on splash; Privacy options row in Settings when required
- [ ] Ad layouts restyled to FFF dark theme
- [ ] Ported ads tests green + new tests for fixes

## 4. Flows
- [ ] Splash: consent → preload → hold → config wait → permissions → splash exit ad → route
- [ ] Onboarding: RC order filter, skip_button, bottom + inline + exit ads, answers persisted, legacy `onboarded` migrated
- [ ] Home: bottom slot, exit ad on every destination, back ad + exit rule (D11)
- [ ] List / Detail / Articles / Article / Calculator / Calc / Settings / Language places wired
- [ ] App-resume ad skips own launches and splash

## 5. Permissions, prompts, analytics, crash
- [ ] Permissions helper with rationale + Settings redirect; only FFF-relevant names
- [ ] In-app update (flexible / force dialog) and review prompt per RC
- [ ] Analytics: FFF screen + click events, segment param, no double `ad_impression` (D9)
- [ ] Crashlytics custom keys + Timber sink; no auto-restart (D10)

## 6. Validation (debug, test ids)
- [ ] Ads off (default config): app behaves exactly like today
- [ ] Each place on/off from RC (debug key `app_config_debug`) without rebuild
- [ ] No-fill, offline (airplane mode), slow network (loader timeout = `loader_wait_sec`)
- [ ] `gap_sec`, `every`, `if_ad_not_ready`, `show_all`, `links_count` behave as specified
- [ ] Only one full-screen ad at a time; navigation always continues
- [ ] Back during an ad / during loader
- [ ] Process death on every screen; language change mid-onboarding keeps step + answers
- [ ] Rotation not applicable (portrait) — verify no leaks with LeakCanary or StrictMode
- [ ] UMP: EEA debug geography shows form; Privacy options reachable
- [ ] API 24, 29, 33, 35+ devices/emulators
- [ ] All 9 locales incl. RTL (Arabic) render ad slots correctly
- [ ] `./gradlew test` green

## 7. Release verification
- [ ] Real AdMob app id in release manifest (inspect merged manifest)
- [ ] Release RC (`app_config`) has real unit ids; debug key keeps test ids
- [ ] R8 release build runs: ads load, RC parses, Gson house ads deserialize
- [ ] Crashlytics mapping upload works
- [ ] Data safety form updated (advertising id, analytics, crash data)
- [ ] Play policy self-check: no lock-screen ads, no system-key hijack, no deceptive uninstall, ads clearly separated from content
- [ ] Version code/name bumped; signing configured outside the repo
