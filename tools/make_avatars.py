"""Crop head-and-shoulders avatars (av_*.webp) for the gender/age steps from the character renders.

Usage: python3 tools/make_avatars.py "<path to 'Hello! Penguin Gamer Welcome Banner.png'>"
"""
import sys
from pathlib import Path
from PIL import Image

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
SIZE = 320


def portrait(src: Image.Image) -> Image.Image:
    """Square crop around the head of a full-body cutout, using its alpha channel."""
    a = src.getchannel("A")
    x0, y0, x1, y1 = a.getbbox()
    h = y1 - y0
    band = a.crop((x0, y0, x1, y0 + int(h * 0.16))).load()
    bw, bh = x1 - x0, int(h * 0.16)
    xs = [x for x in range(bw) for y in range(0, bh, 2) if band[x, y] > 128]
    cx = x0 + sum(xs) / len(xs)  # horizontal centre of the head/hair
    side = int(h * 0.36)
    top = max(0, y0 - int(h * 0.02))
    left = int(cx - side / 2)
    return src.crop((left, top, left + side, top + side)).resize((SIZE, SIZE), Image.LANCZOS)


def main():
    for out, name in {"av_male": "alok", "av_female": "kelly", "av_age1": "caroline", "av_age2": "leon",
                      "av_age3": "dasha", "av_age4": "chrono", "av_age5": "joseph"}.items():
        portrait(Image.open(RES / f"{name}.webp").convert("RGBA")).save(RES / f"{out}.webp", "WEBP", quality=90)
    # Penguin mascot from the FFF "Hello!" banner: a square around its head and goggles.
    hello = Image.open(sys.argv[1]).convert("RGB")
    w, h = hello.size
    side, left = int(h * 0.81), int(w * 0.26)
    hello.crop((left, 0, left + side, side)) \
        .resize((SIZE, SIZE), Image.LANCZOS).save(RES / "av_other.webp", "WEBP", quality=90)


if __name__ == "__main__":
    main()
