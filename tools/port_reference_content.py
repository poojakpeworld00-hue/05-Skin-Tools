"""Generate Content.kt and copy images from the reference app (Skin_Hub_Bundle_Tools).

The reference app hardcodes all content in Java; this parses those literals so the data is
ported verbatim instead of retyped. Usage: python3 tools/port_reference_content.py <reference app root>
"""
import re
import shutil
import sys
from pathlib import Path

REF = Path(sys.argv[1])
J = REF / "app/src/main/java/eliteskin/toolbox/skinzone/booster/skinarena/tools/legendskin"
APP = Path(__file__).resolve().parent.parent / "app/src/main"
TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|R\.drawable\.\w+|-?\d+')
images = set()


def src(rel):
    return (J / rel).read_text()


def parse_args(text, start):
    """Parse a Java argument list starting just after '(' — string literals (with + concatenation),
    R.drawable refs and ints — up to the matching ')'."""
    args, i, depth, pending_concat = [], start, 0, False
    while True:
        while text[i] in " \t\r\n,":
            i += 1
        if text[i] == ")":
            return args, i
        if text[i] == "+":
            pending_concat = True
            i += 1
            continue
        m = TOKEN.match(text, i)
        if not m:
            raise ValueError(f"unparsed at {text[i:i+60]!r}")
        tok = m.group(0)
        if tok.startswith('"'):
            val = tok[1:-1]
            if pending_concat and args and isinstance(args[-1], Lit):
                args[-1] = Lit(args[-1].s + val)
            else:
                args.append(Lit(val))
        elif tok.startswith("R.drawable."):
            args.append(Img(tok[len("R.drawable."):]))
        else:
            args.append(int(tok))
        pending_concat = False
        i = m.end()


class Lit:
    def __init__(self, s): self.s = s
    def kt(self): return '"' + self.s.replace("$", "\\$") + '"'


class Img:
    def __init__(self, n): self.n = n; images.add(n)
    def kt(self): return f"R.drawable.{self.n}"


def calls(text, ctor):
    out = []
    for m in re.finditer(re.escape(ctor) + r"\(", text):
        args, _ = parse_args(text, m.end())
        if args and isinstance(args[0], Lit) and args[0].s == "":
            continue  # `default: return new Info("", ...)` fallbacks
        out.append(args)
    return out


def case_returns(text, fn):
    """id -> literal for `case N: return "..."` inside method `fn`."""
    body = text[text.index(f" {fn}("):]
    body = body[: body.index("\n    }\n")]
    out = {}
    for m in re.finditer(r"case (\d+):\s*return\s*", body):
        lit = TOKEN.match(body, m.end()).group(0)
        # Handle "a" + "b" concatenation after return.
        j, s = m.end() + len(lit), lit[1:-1]
        while True:
            k = re.match(r'\s*\+\s*("(?:\\.|[^"\\])*")', body[j:])
            if not k: break
            s += k.group(1)[1:-1]; j += k.end()
        out[int(m.group(1))] = Lit(s)
    return out


def kt_item(name, image, desc, stats=(), thumb=None, title=None):
    st = ", ".join(f"R.string.{k} to {v.kt()}" for k, v in stats)
    parts = [name.kt(), image.kt(), desc.kt()]
    if stats: parts.append(f"listOf({st})")
    if thumb: parts.append(f"thumb = {thumb.kt()}")
    if title: parts.append(f"title = {title.kt()}")
    return f"Item({', '.join(parts)})"


def no_zero(stats):
    return [] if all(v.s.strip("0") == "" for _, v in stats) else stats


sections = {}

# Characters: list thumbnails come from CharactersActivity, details (with b_ portraits) from CharacterData.
thumbs = [Img(n) for n in re.findall(r"R\.drawable\.(\w+)", src("skinhub/skinchar/CharactersActivity.java").split("int[] images")[1].split("};")[0])]
chars = calls(src("skinhub/skinchar/CharacterData.java"), "new Info")
sections["CHARACTERS"] = ("R.string.cat_characters", [("null", None, [
    kt_item(a[0], a[6], a[5], [("stat_age", a[1]), ("stat_birthday", a[2]), ("stat_occupation", a[3]), ("stat_hobby", a[4])], thumb=thumbs[i])
    for i, a in enumerate(chars)])])

# Bundles
bd = src("skinhub/skinbundle/BundleDetailData.java")
titles, names, descs = case_returns(bd, "titleFor"), case_returns(bd, "nameFor"), case_returns(bd, "descFor")
blist = calls(src("intro/IntroDesignTenScreen.java"), "new BundleItem")
sections["BUNDLES"] = ("R.string.cat_bundles", [("null", None, [
    kt_item(names[a[0]], a[2], descs[a[0]], title=titles[a[0]]) for a in blist])])

# Emotes: 4 categories by id range.
edesc = case_returns(src("skinhub/skinemotes/EmoteDetailData.java"), "descFor")
emotes = calls(src("intro/IntroDesignTwelveScreen.java"), "new BundleItem")
ranges = [("Normal Emotes", "em1", 1, 28), ("Rare Emotes", "em2", 29, 69), ("Elite Pass Emotes", "em3", 70, 85), ("Super Rare Emotes", "em4", 86, 99)]
sections["EMOTES"] = ("R.string.cat_emotes", [(Lit(t).kt(), Img(im).kt(), [
    kt_item(a[1], a[2], edesc.get(a[0], Lit(""))) for a in emotes if lo <= a[0] <= hi]) for t, im, lo, hi in ranges])

# Weapons: 10 categories; WeaponCategoryActivity lists which ids each holds.
winfo = {i + 1: a for i, a in enumerate(calls(src("skinhub/skinweapon/WeaponData.java"), "new Info"))}
wcat_src = src("skinhub/skinweapon/WeaponCategoryActivity.java")
build = wcat_src[wcat_src.index("List<Object> buildList("):]
build = build[: build.index("\n    }\n")]
cat_ids = {}
for m in re.finditer(r"case (\d+):(.*?)break;", build, re.S):
    cat_ids[int(m.group(1))] = [int(x) for x in re.findall(r"add\(d,\s*(\d+)\)", m.group(2))]
wcats = calls(src("skinhub/skinweapon/WeaponsActivity.java"), "new BundleItem")
def weapon(a):
    return kt_item(a[0], a[1], a[8], no_zero([("stat_accuracy", a[2]), ("stat_damage", a[3]), ("stat_fire_rate", a[4]),
                                             ("stat_range", a[5]), ("stat_magazine", a[6]), ("stat_movement", a[7])]))
sections["WEAPONS"] = ("R.string.cat_weapons", [(Lit(re.sub(r"\s*\\n\s*", " ", c[1].s)).kt(), c[2].kt(),
    [weapon(winfo[i]) for i in cat_ids[c[0]]]) for c in wcats])

# Pets, Vehicles
pets = calls(src("skinhub/skinpets/PetData.java"), "new Info")
sections["PETS"] = ("R.string.cat_pets", [("null", None, [kt_item(a[0], a[3], a[2], [("stat_ability", a[1])]) for a in pets])])
veh = calls(src("skinhub/skinvehicle/VehicleData.java"), "new Info")
sections["VEHICLES"] = ("R.string.cat_vehicles", [("null", None, [
    kt_item(a[0], a[1], a[6], [("stat_hp", a[2]), ("stat_acceleration", a[3]), ("stat_control", a[4]), ("stat_speed", a[5])]) for a in veh])])

# Maps: 6 maps, ids numbered consecutively across them.
md = src("skinhub/skinmaps/MapDetailData.java")
mnames, mdescs = case_returns(md, "nameFor"), case_returns(md, "descFor")
mc = src("skinhub/skinmaps/MapCategoryActivity.java")
imgs_src = mc[mc.index("int[] imagesFor("):]
imgs_by_cat = {}
for m in re.finditer(r"(case (\d+)|default):\s*return new int\[\]\{(.*?)\};", imgs_src, re.S):
    imgs_by_cat[int(m.group(2)) if m.group(2) else 1] = [Img(n) for n in re.findall(r"R\.drawable\.(\w+)", m.group(3))]
maps = [("Next Tera", "nextera", 1), ("Alpine", "alpine", 14), ("Bermuda", "bermuda", 20), ("Bermuda Remastered", "bermuda2", 29), ("Kalahari", "kalahari", 36), ("Purgatory", "purgatory", 44)]
sections["MAPS"] = ("R.string.tool_maps", [(Lit(t).kt(), Img(im).kt(), [
    kt_item(mnames[start + k], img, mdescs[start + k]) for k, img in enumerate(imgs_by_cat[c + 1])]) for c, (t, im, start) in enumerate(maps)])

# Tips and diamond tips.
tips = calls(src("skinhub/tips/TipsActivity.java"), "new Tip")
dt = src("skinhub/tips/DiamondTipsData.java")
dt_titles = dict(re.findall(r"case CAT_(\w+):\s*return \"([^\"]+)\"", dt))
dt_methods = dict(re.findall(r"case CAT_(\w+):\s*return (\w+)\(\);", dt))
guides = []
for cat, method in dt_methods.items():
    body = dt[dt.index(f"List<String> {method}()"):]
    args, _ = parse_args(body, body.index("Arrays.asList(") + len("Arrays.asList("))
    guides.append(f'Article({Lit(dt_titles[cat]).kt()}, "", {Lit(chr(92) + "n" + chr(92) + "n").kt()}.let {{ sep -> listOf({", ".join(a.kt() for a in args)}).joinToString(sep) }})')

# Copy every referenced image from the reference app (drawable or density folders) into drawable-nodpi.
res = REF / "app/src/main/res"
out_dir = APP / "res/drawable-nodpi"
missing = []
for n in sorted(images):
    found = next((p for p in res.glob(f"drawable*/{n}.*") if p.suffix in (".webp", ".png", ".jpg")), None)
    if not found:
        missing.append(n); continue
    shutil.copy(found, out_dir / f"{n}{found.suffix}")
for n in ("cal1", "cal5", "cal6", "cal7"):
    p = next(res.glob(f"drawable*/{n}.*"))
    shutil.copy(p, out_dir / f"{n}{p.suffix}")

lines = ["// GENERATED by tools/port_reference_content.py from the reference app's data. Do not edit by hand.",
         "package com.fffemotes.skintools.master.data.local", "",
         "import com.fffemotes.skintools.master.R",
         "import com.fffemotes.skintools.master.domain.model.Article",
         "import com.fffemotes.skintools.master.domain.model.Group",
         "import com.fffemotes.skintools.master.domain.model.Item",
         "import com.fffemotes.skintools.master.domain.model.Section", "",
         "@Suppress(\"LargeClass\")", "object Content {"]
for key, (title, groups) in sections.items():
    lines.append(f"    val {key}: Section by lazy {{")
    lines.append(f"        Section({title}, listOf(")
    for gname, gimg, items in groups:
        lines.append(f"            Group({gname}, {gimg or 0}, listOf(")
        lines += [f"                {it}," for it in items]
        lines.append("            )),")
    lines.append("        ))")
    lines.append("    }")
lines.append("    val TIPS: List<Article> by lazy {")
lines.append("        listOf(")
lines += [f"            Article({a[1].kt()}, {a[2].kt()}, {a[3].kt()})," for a in tips]
lines.append("        )\n    }")
lines.append("    val DIAMOND_TIPS: List<Article> by lazy {")
lines.append("        listOf(")
lines += [f"            {g}," for g in guides]
lines.append("        )\n    }\n}")
(APP / "java/com/fffemotes/skintools/master/data/local/Content.kt").write_text("\n".join(lines) + "\n")
for key, (_, groups) in sections.items():
    print(key, [len(g[2]) for g in groups])
print("tips", len(tips), "guides", len(guides), "images", len(images), "missing", missing)
