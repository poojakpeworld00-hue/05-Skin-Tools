# Remote Config guide — FFF (Skin Emotes)

Firebase project `skin-emotes-7823f`. The app reads **one JSON string parameter** per build:

| Parameter | Used by | Notes |
|---|---|---|
| `app_config` | release builds | ships with `ads.on: false` and empty ad unit ids |
| `app_config_debug` | debug builds | Google test ad units; fetched on every launch |
| `home_profile_rules`, `home_profile_probes` | launcher (optional) | grid/icon profile tables; empty = launcher built-ins |

Parsed by `AppConfigParser` (`app/src/main/java/com/fffemotes/skintools/master/data/remote/config/`). Missing key or wrong type → the code default; unknown names are dropped; invalid JSON keeps the previous config.

## Editing and publishing

1. Edit **`tools/gen_remote_config.py`** (not the JSON files).
2. Run `python3 tools/gen_remote_config.py`. It writes `docs/remote-config/app_config.json` and `app_config_debug.json`. The app bundles no copy: as the reference, it runs on its code defaults (ads and launcher off) until Firebase has served a live value once; after that the last live value is saved on the device.
3. Firebase console → Remote Config → add/update the String parameters `app_config` / `app_config_debug` with the generated JSON → Publish.
4. Devices pick it up on the next sync (`base.rc_sync_hours`, release; every launch in debug), or at once with a silent FCM push to topic `rc_sync` with data `type: rc_sync`.

Before a release: real AdMob unit ids in `base.ad_units`, `ads.on: true` for the tracks that should see ads, and `admob.appId` in `local.properties`.

## Shape

```
{ "schema": 1,
  "base":    { …shared settings… },
  "track_a": { …organic users… },
  "track_b": { …paid users… } }
```

Paid / organic: `InstallAttribution` (install referrer gclid + Google-IP check) picks `track_b` or `track_a`; `base.exclusive_locations` forces `track_a` for listed places. Inside a track, `countries` overlays (`"IN,PK": { … }`, by SIM/network/locale country) merge over the track.

## `base`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `privacy_link`, `terms_link`, `store_link`, `update_link` | string | "" | Settings rows (fallback to the app's strings / Play URL) |
| `app_update.show` / `.force` | bool | false | Play in-app update: flexible / immediate (declined force → explanation, then close) |
| `splash_wait_sec` | int | 3 (template: 4) | splash hold before the config wait (≤ 6 s) |
| `no_internet_dialog` | bool | true | offline: the app's screens (not the launcher home or ad screens) show a "No internet" dialog with Retry / Open settings; it closes by itself once back online, and the splash waits for it |
| `rc_sync_hours` | int | 1 | min hours between fetches; 0 = every start |
| `attribution.*` | object | — | `gclid_min_length`, `fake_gclids`, `google_ip_check {on, endpoint, field, match}` |
| `exclusive_locations` | object | off | `{on, endpoint, list}`: IP country/state/city names forced organic |
| `ad_network` | google / custom | google | |
| `ad_units.{inter, inter_splash, inter_back, app_open, native, banner, rewarded}` | string | "" | `inter_splash` / `inter_back` fall back to `inter` |

## `track_x.ads`

Global: `on`, `loader_wait_sec` (8), `loader_on_miss` (a place can set its own `loader_on_miss` to override it, e.g. `onboarding.default_home`: true; true: an exit_ad ad that is not cached yet is loaded behind "Please wait", as the reference; false: that step is skipped; the splash always loads), `preload.{inter,native,app_open,rewarded}`, `formats_on.{inter,native,banner,native_banner,app_open,rewarded}`, `if_ad_not_ready` (list of steps / `link` / URL), `links`, `links_count`, `show_all`, `links_open_in` (`in_app_browser` / `webview` / `browser`), `custom_ads {on, list[{icon,title,description,bannerImage,buttonText}]}`, `colors.{light,dark}.{button,button_text,background,text}`.

Places: `track_x.ads.<group>.<name>` with
- `bottom_ad {on, type}` — type: `banner`, `banner_inline`, `banner_collapsible`, `native_big`, `native_mid`, `native_mid2`, `native_banner`, `native_full`
- `on_fail` (banner slots) — the type shown when the banner fails to load, e.g. `native_banner`; needs that format on in `formats_on`. Leave it out for no fallback (the house ad, when `custom_ads.on`)
- `list_ad {on, type}` — onboarding steps: the ad between the options; launcher: the suggested panel; `screens.home` / `launcher.host_app_panel`: the ad inside the Home list
- `list_ad_after` (number) — list screens: the `list_ad` row goes after this many items (0 = top). `list_ad_every` (number, grids) repeats it after every this many items; leave it out or 0 for one ad. Places: `screens.list_<characters|bundles|pets|vehicles|emotes|weapons|maps>`, `screens.list_<emotes|weapons|maps>_items` (inside a category), `screens.tips`, `screens.diamond_tips`, `screens.calculator`. `screens.calc` puts its `list_ad` above the Calculate button; `screens.diamond_article` pins its `bottom_ad` under the text
- `list_ad_position` (`screens.home`, `launcher.host_app_panel`) — `after_header` | `after_tiles` (above "Tools & guides", default) | `after_tools`
- `exit_ad {on, ads[], every, links_count, links, show_all}` — ads: `inter`, `rewarded`, `app_open`, `full_native`, `custom`, `link`. `every` counts **per place**: on a place visited once (each onboarding step) anything above 1 means it never shows.
- options: `gap_sec`, `preload`, and per event place `on`, `start_after_hours`, `gap_minutes`, `max_per_day`, `window_sec`, `show_ad_screen`, `during_onboarding`

| Group | Places (code caller) |
|---|---|
| `screens` | `splash`, `home`, `list`, `detail`, `articles`, `article`, `calculator`, `calc`, `settings`, `language` |
| `onboarding` | one per step key: `OB_1` … `OB_13`, `welcome`, `permission_hub`, `default_home` |
| `launcher` | `app_resume`, `back_button`, `home_button`, `recents_button`, `app_open`, `app_exit`, `host_app_panel`, `suggested_panel`, `app_drawer` (+ `bottom_ad_row`), `drawer_sponsored_apps {on, every, list[{logo, landing_url, label}]}`, `sponsored_app_open`, `sponsored_app_exit`, `phone_unlock`, `charger_connected`, `charger_disconnected`, `app_installed`, `app_uninstalled`, `play_store_return` |
| `uninstall_flow` | `splash`, `language`, `step1`, `step2`, `progress`, `step3` |

## Onboarding step keys

| Key | Screen | Key | Screen |
|---|---|---|---|
| `OB_1` | Language | `OB_8` | Goal |
| `OB_2` | Gender | `OB_9` | Character |
| `OB_3` | Age | `OB_10` | Tips |
| `OB_4` | Play style | `OB_11` | Room |
| `OB_5` | Favourites | `OB_12` | Map |
| `OB_6` | Game mode | `OB_13` | Daily |
| `OB_7` | Weapon | | |

`OB_1`–`OB_10` follow the reference config; `OB_11`–`OB_13` are FFF's extra questions and copy `OB_10`'s ads. Extra steps: `welcome`, `permission_hub`, `default_home`. The bottom sheet's voice guide plays `res/raw/ob_voice_<lang>` from the sheet's second opening.

## Other track keys

| Key | Default | Meaning |
|---|---|---|
| `onboarding.order` | `OB_1` … `OB_13` | step keys in order; `welcome`, `permission_hub`, `default_home` only when listed. `default_home` is dropped when the launcher is off or already the default home |
| `onboarding.skip_button` / `default_home.skip_button` | true / false | Skip on steps / on the launcher step |
| `onboarding.default_home.auto_open_settings_sec` | 3 | re-open the Default-home page after a cancel; 0 = never |
| `permissions.<screen>` | {} | `notification` (others need a manifest entry). Screens: `splash`, any step key, `permission_hub` (on Allow), `home` (first visit) |
| `permissions.home_sheet` | true | Home asks with the "Allow access" sheet, else directly |
| `launcher.on` | false | installs the home-screen launcher |
| `launcher.{os_style, default_home_banner, our_icon.{in_drawer,on_home}, panels.{host_app,suggested_panel}, swipe_guide.*, time_widget, search_widget}` | see parser | launcher look and panels; `default_home_banner` = Settings "Set as default home" row |
| `launcher.system_keys.{home, back, recents}` | next / next / none | during onboarding: `next`, `back`, `reset`, `""` (device default) |
| `launcher.system_keys.bottom_sheet {on, keys[]}` | off | listed keys open the "Almost done" sheet first |
| `uninstall_flow.{flow, system_dialog, splash_ms, progress_ms}` | normal | `premium` = Uninstall on our icon opens the uninstall screens (ends in Android's uninstall) |
| `prompts.rate_us.{after_opens, retry_after_hours}` | 10 / 24 | in-app review on Home |
| `settings.{share_app, rate_app, feedback, update_app}` | true | show/hide the Settings > About rows; per track (organic vs paid) and per `countries` overlay |
| `countries` | {} | per-country overlays, see Shape |
