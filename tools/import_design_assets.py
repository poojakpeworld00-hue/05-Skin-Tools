"""Copy the Claude Design "Skin Hub" image assets into app/src/main/res/drawable-nodpi.

Usage: python3 tools/import_design_assets.py <exported design folder> [--placeholders]
PNGs are re-encoded to WebP (max 1080px wide). --placeholders writes a flat tile for any asset
still missing so the app builds; re-run without it once the real export is available.
"""
import sys
from pathlib import Path
from PIL import Image

NAMES = (
    "splash_bg gc1 gc2 gc3 gc4 gc5 gc6 "
    "fc_characters fc_bundles2 fc_weapons2 fc_pets2 fc_emotes fc_vehicles gm1 gm2 gm3 gm4 "
    "w_smg w_sni w_sg ch_alok ch_chrono ch_k ch_kelly map_bermuda map_purgatory map_kalahari map_alpine "
    "q_mode q_weapon q_goal q_char q_guide q_room q_map q_tips"
).split()
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"


def main():
    src = Path(sys.argv[1])
    placeholders = "--placeholders" in sys.argv
    found = {p.stem: p for p in src.rglob("*") if p.suffix.lower() in (".webp", ".png", ".jpg", ".jpeg")}
    OUT.mkdir(parents=True, exist_ok=True)
    missing = []
    for name in NAMES:
        dst = OUT / f"{name}.webp"
        if name in found:
            p = found[name]
            if p.suffix.lower() == ".webp":
                dst.write_bytes(p.read_bytes())
            else:
                img = Image.open(p)
                img = img.convert("RGBA" if "A" in img.getbands() else "RGB")
                if img.width > 1080:
                    img = img.resize((1080, round(img.height * 1080 / img.width)), Image.LANCZOS)
                img.save(dst, "WEBP", quality=85)
        elif not dst.exists() or dst.stat().st_size < 200:  # tiny file = placeholder from an earlier run
            missing.append(name)
            if placeholders and not dst.exists():
                Image.new("RGB", (64, 64), (124, 58, 237)).save(dst, "WEBP")
    assert all((OUT / f"{n}.webp").exists() for n in NAMES) or not placeholders
    print("missing:", " ".join(missing) or "none")


if __name__ == "__main__":
    main()
