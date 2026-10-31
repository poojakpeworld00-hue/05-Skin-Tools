"""Generates the FFF Remote Config (docs/remote-config/*.json) to publish in Firebase. The app bundles no copy: as the
reference, it runs on its code defaults until a live value arrives.
The values are the reference app's config as given (onboarding screens OB_1…), with FFF's own extras:
OB_11–OB_13 (FFF has 13 questions; they copy OB_10) and the FFF-only screens list/detail/articles/article/calculator/calc/language.
Run from the repo root: python3 tools/gen_remote_config.py"""
import copy, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPONSORED = json.load(open(f"{ROOT}/tools/sponsored_apps.json"))
TEST_UNITS = {  # Google's public test ad units.
    "inter": "ca-app-pub-3940256099942544/1033173712", "inter_splash": "ca-app-pub-3940256099942544/1033173712",
    "inter_back": "ca-app-pub-3940256099942544/1033173712", "app_open": "ca-app-pub-3940256099942544/9257395921",
    "native": "ca-app-pub-3940256099942544/2247696110", "banner": "ca-app-pub-3940256099942544/9214589741",
    "rewarded": "ca-app-pub-3940256099942544/5224354917",
}
OB = [f"OB_{i}" for i in range(1, 14)]  # OB_1 language … OB_13 daily (OnboardingScreen in AppConfig.kt)


def slot(on, kind):
    s = {"on": on, "type": kind}
    if kind.startswith("banner"): s["on_fail"] = "native_banner"  # a failed banner shows a native banner instead
    return s


def ex(on, ads, links_count=None):
    e = {"on": on, "ads": ads}
    if links_count is not None: e["links_count"] = links_count
    return e


def place(bottom=None, exit=None, **opts):
    p = {}
    if bottom: p["bottom_ad"] = bottom
    if exit: p["exit_ad"] = exit
    p.update(opts)
    return p


def colors(button, button_text):
    # FFF is dark-only, so the phone's light mode gets the same dark card as dark mode: app card #14161D, app text.
    palette = {"button": button, "button_text": button_text, "background": "#14161D", "text": "#F4F5F7"}
    return {"light": dict(palette), "dark": dict(palette)}


def launcher_settings(host_app, sheet_on):
    return {"on": True, "os_style": False, "default_home_banner": True, "our_icon": {"in_drawer": True, "on_home": True},
            "panels": {"host_app": host_app, "suggested_panel": False},
            "swipe_guide": {"on": False, "swipe_right_host_app": True, "swipe_left_suggested_panel": True, "swipe_up_drawer": True},
            "time_widget": True, "search_widget": "google",
            "system_keys": {"home": "next", "back": "next", "recents": "next", "bottom_sheet": {"on": sheet_on, "keys": ["back"]}}}


def launcher_ads(b):
    """track_a (b=False) / track_b (b=True) `ads.launcher` places."""
    lc = (lambda n: n) if b else (lambda n: None)  # track_b spells links_count out
    return {
        # Reference values: organic app_resume / back_button on, paid off.
        "app_resume": place(exit=ex(not b, ["app_open"] if not b else [], lc(0))),
        "back_button": {"during_onboarding": False, "exit_ad": ex(not b, ["inter"] if not b else [], lc(0))},
        "home_button": {"during_onboarding": False, "exit_ad": ex(False, [], lc(0))},
        "recents_button": {"during_onboarding": False, "exit_ad": ex(False, [], lc(0))},
        "app_exit": place(exit=ex(True, ["link", "rewarded", "inter"], 1) if b else ex(False, [])),
        "app_open": place(exit=ex(True, ["app_open"], 0) if b else ex(False, [])),
        "host_app_panel": place(bottom=slot(True, "banner"), exit=ex(True, ["inter"], lc(0)),
                                list_ad=slot(True, "native_big"), list_ad_position="after_tiles"),
        "suggested_panel": {"exit_ad": ex(False, [], lc(0)), "bottom_ad": slot(not b, "native_mid2"), "list_ad": slot(not b, "native_banner")},
        "app_drawer": {"exit_ad": ex(False, [], lc(0)), "bottom_ad": slot(False, "native_mid"), "bottom_ad_row": 1},
        "drawer_sponsored_apps": {"on": b, "every": 8, "list": SPONSORED},
        "sponsored_app_open": place(exit=ex(True, ["link", "app_open"], 1) if b else ex(False, [])),
        "sponsored_app_exit": place(exit=ex(True, ["rewarded", "link"], 2) if b else ex(True, ["rewarded"])),
        # As the reference: paid shows inter then rewarded after an unlock (from 24 h after install), organic none.
        "phone_unlock": {"on": b, "exit_ad": ex(True, ["inter", "rewarded"] if b else ["inter"], lc(0)), "start_after_hours": 24, "gap_minutes": 30, "max_per_day": 5},
        "charger_connected": {"on": False, "bottom_ad": slot(True, "native_big"), "exit_ad": ex(True, ["inter"], lc(0)), "gap_sec": 1800},
        "charger_disconnected": {"on": False, "bottom_ad": slot(True, "native_big"), "exit_ad": ex(True, ["full_native"], lc(0)), "gap_sec": 1800},
        "app_installed": {"on": False, "bottom_ad": slot(True, "native_mid2"), "exit_ad": ex(False, [], lc(0)), "gap_sec": 0},
        "app_uninstalled": {"on": False, "bottom_ad": slot(True, "native_mid2"), "exit_ad": ex(False, [], lc(0)), "gap_sec": 0},
        "play_store_return": {"on": b, "window_sec": 6000, "show_ad_screen": False, "bottom_ad": slot(not b, "native_big"),
                              "exit_ad": ex(not b, ["inter"] if not b else [], lc(0)), "gap_sec": 120},
    }


def uninstall_ads(b):
    lc = 0 if b else None
    return {"splash": place(exit=ex(True, ["app_open"], lc)), "step1": place(bottom=slot(True, "native_mid")),
            "step2": place(bottom=slot(True, "native_big")), "step3": place(bottom=slot(True, "native_big")),
            "language": place(bottom=slot(True, "native_big"), exit=ex(True, ["inter"], lc)),
            "progress": place(bottom=slot(True, "native_big"))}


def fff_screens(b):
    """FFF-only screens (the reference app has no such screens)."""
    return {"list": place(bottom=slot(True, "banner"), exit={"on": True, "ads": ["inter"], "every": 1}),
            "detail": place(bottom=slot(True, "native_mid")), "articles": place(bottom=slot(True, "banner")),
            "article": place(bottom=slot(True, "native_mid")), "calculator": place(bottom=slot(True, "banner")),
            "calc": place(bottom=slot(True, "native_mid")),
            "language": place(bottom=slot(True, "native_banner"), exit=ex(True, ["inter"])),
            **(fff_list_ads() if b else {})}


def fff_list_ads():
    """track_b: the ad inside each list (`list_ad` after `list_ad_after` items) and the calculator / diamond-tip pages."""
    def lst(kind, after, every=0):
        d = {"list_ad": slot(True, kind), "list_ad_after": after}
        if every: d["list_ad_every"] = every  # repeat: again after every this many items
        return d
    big = lambda after, every=0: lst("native_big", after, every)
    o = {f"list_{t}": big(6, 6) for t in ["characters", "bundles", "pets", "vehicles"]}
    for t in ["emotes", "weapons"]:
        o[f"list_{t}"] = big(2)              # the categories
        o[f"list_{t}_items"] = big(6, 6)     # the items inside one category
    o["list_maps"] = lst("native_mid", 0)   # top of the maps
    o["list_maps_items"] = lst("native_mid", 2)
    o["tips"] = big(2)
    o["diamond_tips"] = big(2)
    o["diamond_article"] = place(bottom=slot(True, "native_mid"))  # pinned under the text
    o["settings"] = place(list_ad=slot(True, "native_big"), exit=ex(False, [], 0))  # under the Language row; replaces the bottom native
    o["calculator"] = place(bottom=slot(True, "banner"), **lst("native_mid", 0))  # mid native on top of the calculators
    o["calc"] = place(bottom=slot(True, "banner"), list_ad=slot(True, "native_big"))  # big native above Calculate
    return o


def onboarding_ads(b):
    o = onboarding_exit_ads(b)
    # Every step with options: big native between the options (list_ad), native banner pinned under the button (bottom_ad).
    for k, p in o.items():
        if k != "default_home":
            p["bottom_ad"] = slot(True, "native_banner")
            p["list_ad"] = slot(True, "native_big")
    # Language (OB_1): one big native pinned at the bottom, none between the languages.
    o["OB_1"]["bottom_ad"] = slot(True, "native_big")
    o["OB_1"]["list_ad"] = slot(False, "native_big")
    return o


def onboarding_exit_ads(b):
    # Reference: every step's exit ad is link → inter → app_open, both tracks.
    ref = ["link", "inter", "app_open"]
    o = {k: place(bottom=slot(True, "native_mid"), exit=ex(True, ref)) for k in OB + ["welcome", "permission_hub"]}
    # loader_on_miss: right after the default-home grant nothing may be preloaded yet, so these ads load behind the loader.
    o["default_home"] = place(bottom=slot(not b, "native_mid"), exit=ex(True, ref), loader_on_miss=True)
    return o


def track(b):
    lc = 0 if b else None
    screens = {"splash": place(exit=ex(False, [], 0) if b else ex(True, ["inter"])),
               "home": place(bottom=slot(True, "banner"), exit=ex(True, ["inter"], lc),
                             list_ad=slot(True, "native_mid"), list_ad_position="after_tiles"),
               "settings": place(bottom=slot(True, "native_mid"), exit=ex(False, [], lc)),
               **fff_screens(b)}
    return {
        "ads": {
            "on": b, "loader_wait_sec": 8, "loader_on_miss": False,
            "preload": {"inter": True, "native": True, "app_open": True, "rewarded": True},
            "formats_on": {k: True for k in ["inter", "native", "banner", "native_banner", "app_open", "rewarded"]},
            "if_ad_not_ready": ["rewarded"],
            "links": ["https://game20.nextgen7play.com/explore?utm_source=app"],
            "links_count": 2, "show_all": True, "links_open_in": "in_app_browser",
            "custom_ads": {"on": False, "list": []},
            "colors": colors("#FF3B5C", "#FFFFFF") if b else colors("#FFC233", "#0B0C10"),
            "screens": screens, "onboarding": onboarding_ads(b), "launcher": launcher_ads(b), "uninstall_flow": uninstall_ads(b),
        },
        "onboarding": {"order": ["default_home"] + OB if b else OB + ["default_home"], "skip_button": True,
                       "default_home": {"skip_button": True, "auto_open_settings_sec": 3}},
        "permissions": {"permission_hub": ["camera", "photos", "notification"], "home": ["notification"], "home_sheet": True},
        "launcher": launcher_settings(host_app=not b, sheet_on=b),
        "uninstall_flow": {"flow": "premium" if b else "normal", "system_dialog": False, "splash_ms": 3000, "progress_ms": 4000},
        "prompts": {"rate_us": {"after_opens": 10, "retry_after_hours": 24}},
        "settings": {"share_app": True, "rate_app": True, "feedback": True, "update_app": True},  # Settings > About rows
        "countries": {"IN,PK": {"permissions": {"permission_hub": ["camera"], "home": ["photos", "notification"]}}} if b else {},
    }


def config(release):
    return {
        "schema": 1,
        "base": {
            "privacy_link": "", "terms_link": "", "store_link": "", "update_link": "",
            "app_update": {"show": True, "force": False}, "splash_wait_sec": 4, "no_internet_dialog": True, "rc_sync_hours": 1,
            "attribution": {"gclid_min_length": 20, "fake_gclids": ["123456789"],
                            "google_ip_check": {"on": True, "endpoint": "https://ipconfig.io/json", "field": "asn_org", "match": ["Google LLC"]}},
            "exclusive_locations": {"on": False, "endpoint": "https://ipconfig.io/json", "list": []},
            "ad_network": "google",
            # Release unit ids come from the FFF AdMob account: empty = no ad loads until they are published.
            "ad_units": {k: "" for k in TEST_UNITS} if release else dict(TEST_UNITS),
        },
        "track_a": track(False),
        "track_b": track(True),
    }


def debug_testing(cfg):
    """app_config_debug only: unlock and charger moments fire at once so they can be checked on a test phone."""
    for t in ("track_a", "track_b"):
        l = cfg[t]["ads"]["launcher"]
        l["phone_unlock"].update({"start_after_hours": 0, "gap_minutes": 0})
        for k in ("charger_connected", "charger_disconnected"):
            l[k].update({"on": True, "gap_sec": 0})
    return cfg


out = {"app_config": config(True), "app_config_debug": debug_testing(config(False))}
os.makedirs(f"{ROOT}/docs/remote-config", exist_ok=True)
for name, cfg in out.items():
    with open(f"{ROOT}/docs/remote-config/{name}.json", "w") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False); f.write("\n")
print("written")
