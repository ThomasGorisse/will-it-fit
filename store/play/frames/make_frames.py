#!/usr/bin/env python3
"""Rebuild six localized Play slides from five captures and one fixed staged image.

Python 3 + Pillow. Paths are relative to this file, so any working directory works.
Run: store/play/frames/.venv/bin/python3 store/play/frames/make_frames.py
Optional: --locale en-US --locale fr-FR
Only writes inside frames/out/. No network, build, upload, or source-image edits.
"""

import argparse
from functools import lru_cache
import json
from pathlib import Path
import sys
import unicodedata

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps
except ImportError:
    sys.exit(
        "Pillow is required. Use python3 -m pip install --user pillow, or create "
        "store/play/frames/.venv and install pillow there."
    )


ROOT = Path(__file__).resolve().parent
CAPTURES = ROOT.parent / "screenshots" / "phone"
OUT = ROOT / "out"
LOCALES = ("en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR")
SLIDES = ("1-sofa", "2-bed", "3-wardrobe", "4-door", "5-dark", "6-free")
SIZE = WIDTH, HEIGHT = 1242, 2208
MARGIN = 104
TEXT_WIDTH = WIDTH - 2 * MARGIN
HEADLINE_TOP = 174
HEADLINE_STEP = 118
SUBLINE_TOP = 442
PHONE_TOP = 552
PHONE_WIDTH = 860
BEZEL = 12
SCREEN_WIDTH = PHONE_WIDTH - 2 * BEZEL
SCREEN_HEIGHT = 2 * SCREEN_WIDTH
PHONE_HEIGHT = SCREEN_HEIGHT + 2 * BEZEL
PHONE_RADIUS = round(PHONE_WIDTH * 0.07)
TEAL = "#1F6F78"
DARK_TEAL = "#0E4A50"
IVORY = "#F7F3EE"
INK = "#1C1B1F"


def pick_fonts():
    """TTC face indices are explicit: Avenir Next Bold / Medium on macOS."""
    candidates = (
        ("/System/Library/Fonts/Avenir Next.ttc", 0,
         "/System/Library/Fonts/Avenir Next.ttc", 5),
        ("/System/Library/Fonts/HelveticaNeue.ttc", 1,
         "/System/Library/Fonts/HelveticaNeue.ttc", 0),
        ("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 0,
         "/System/Library/Fonts/Supplemental/Arial.ttf", 0),
        ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 0,
         "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 0),
    )
    for bold, bold_index, regular, regular_index in candidates:
        if Path(bold).is_file() and Path(regular).is_file():
            return {"bold": (bold, bold_index), "regular": (regular, regular_index)}
    raise ValueError("No supported sans font found; add a font pair in pick_fonts().")


@lru_cache(maxsize=None)
def font(style, size):
    path, index = FONT_FILES[style]
    return ImageFont.truetype(path, size=size, index=index)


def validate_captions(captions):
    if tuple(captions) != LOCALES:
        raise ValueError("captions.json must contain the six locales in listing order")
    glyphs = set("çãßñéœüêÀÇ¿¡’")
    for locale, slides in captions.items():
        if tuple(slides) != SLIDES:
            raise ValueError(f"{locale}: expected exactly {', '.join(SLIDES)}")
        for slide, copy in slides.items():
            headline = copy["headline"]
            subline = copy.get("subline", "")
            if not 1 <= len(headline.splitlines()) <= 2:
                raise ValueError(f"{locale}/{slide}: headline must use one or two lines")
            if len(headline.replace("\n", " ")) > 32:
                raise ValueError(f"{locale}/{slide}: shorten headline to about 28 characters")
            if "\n" in subline:
                raise ValueError(f"{locale}/{slide}: subline must be a single line")
            for text in (headline, subline):
                if text != unicodedata.normalize("NFC", text):
                    raise ValueError(f"{locale}/{slide}: use NFC Unicode")
                glyphs.update(text)
    # Pillow renders unsupported characters with the same .notdef glyph.
    # Check every used character in both faces, plus language-specific probes.
    for style in FONT_FILES:
        face = font(style, 64)
        missing = face.getmask("\U0010ffff")
        missing_signature = (missing.size, bytes(missing))
        for char in sorted(glyphs):
            if char.isspace():
                continue
            mask = face.getmask(char)
            if not mask.getbbox() or (mask.size, bytes(mask)) == missing_signature:
                raise ValueError(f"{style}: missing glyph {char!r} (U+{ord(char):04X})")
    print("Glyphs: all caption characters and ç ã ß ñ é œ ü ê À Ç ¿ ¡ ’ present in both faces")


def gradient(top, bottom):
    ramp = Image.linear_gradient("L").resize(SIZE)
    return ImageOps.colorize(ramp, top, bottom).convert("RGB")


def rounded_mask(size, radius):
    # Supersampling keeps the small bezel and screen corners smooth.
    scale = 3
    mask = Image.new("L", (size[0] * scale, size[1] * scale))
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, mask.width - 1, mask.height - 1), radius * scale, fill=255
    )
    return mask.resize(size, Image.Resampling.LANCZOS)


def phone(capture):
    with Image.open(capture) as source:
        source = ImageOps.exif_transpose(source).convert("RGB")
        if source.size != (1280, 2560):
            raise ValueError(f"{capture.name}: expected 1280x2560, got {source.size}")
        screen = source.resize((SCREEN_WIDTH, SCREEN_HEIGHT), Image.Resampling.LANCZOS)
    body = Image.new("RGBA", (PHONE_WIDTH, PHONE_HEIGHT), "#151C1E")
    body.putalpha(rounded_mask(body.size, PHONE_RADIUS))
    # A fine metallic edge, deliberately no artificial notch or camera cutout.
    ImageDraw.Draw(body).rounded_rectangle(
        (1, 1, PHONE_WIDTH - 2, PHONE_HEIGHT - 2), PHONE_RADIUS,
        outline="#566365", width=2
    )
    body.paste(screen, (BEZEL, BEZEL), rounded_mask(screen.size, PHONE_RADIUS - BEZEL))
    return body


def phone_slide(index, device):
    if index == 5:
        canvas = gradient(DARK_TEAL, "#082E33")
    elif index in (2, 4):
        canvas = gradient(IVORY, "#EDE6DC")
    else:
        canvas = gradient(TEAL, "#15575E")
    left = (WIDTH - PHONE_WIDTH) // 2
    shadow = Image.new("RGBA", SIZE)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (left - 2, PHONE_TOP + 20, left + PHONE_WIDTH + 2, PHONE_TOP + PHONE_HEIGHT + 20),
        PHONE_RADIUS, fill=(5, 22, 24, 65)
    )
    canvas = Image.alpha_composite(canvas.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(26)))
    # The bottom 40 px of the device bleed beyond the canvas; all controls remain visible.
    canvas.alpha_composite(device, (left, PHONE_TOP))
    return canvas.convert("RGB")


def art_slide():
    with Image.open(ROOT / "free-offline-art.png") as art:
        canvas = ImageOps.fit(art.convert("RGB"), SIZE, Image.Resampling.LANCZOS)
    # Full bleed, with a soft ivory scrim for headline contrast. No phone frame.
    overlay = Image.new("RGBA", (1, HEIGHT))
    overlay.putdata([
        (247, 243, 238, round(220 * max(0, 1 - y / 850) ** 0.65))
        for y in range(HEIGHT)
    ])
    return Image.alpha_composite(canvas.convert("RGBA"), overlay.resize(SIZE)).convert("RGB")


def fitted_font(texts, style, preferred, minimum):
    for size in range(preferred, minimum - 1, -1):
        candidate = font(style, size)
        if all(candidate.getlength(text) <= TEXT_WIDTH for text in texts):
            return candidate
    raise ValueError(f"Copy is too wide; shorten {texts!r}")


def add_copy(canvas, copy, index):
    light = index in (1, 3, 5)
    foreground = IVORY if light else INK
    secondary = "#D9E8E6" if light else "#40585A"
    draw = ImageDraw.Draw(canvas)
    draw.text((MARGIN, 77), "Will It Fit", font=font("bold", 32), fill=foreground, anchor="lt")
    draw.text((WIDTH - MARGIN, 80), f"{index:02d} / 06", font=font("regular", 26),
              fill=secondary, anchor="rt")
    draw.rounded_rectangle((MARGIN, 130, MARGIN + 56, 135), 2, fill=foreground)
    lines = copy["headline"].splitlines()
    title_font = fitted_font(lines, "bold", 100, 86)
    for line_index, line in enumerate(lines):
        position = (MARGIN, HEADLINE_TOP + line_index * HEADLINE_STEP)
        box = draw.textbbox(position, line, font=title_font, anchor="lt")
        if box[0] < MARGIN - 4 or box[2] > WIDTH - MARGIN or box[3] > SUBLINE_TOP - 24:
            raise ValueError(f"Headline would overflow: {line!r}, {box}")
        draw.text(position, line, font=title_font, fill=foreground, anchor="lt")
    if copy.get("subline"):
        sub_font = fitted_font([copy["subline"]], "regular", 40, 34)
        box = draw.textbbox((MARGIN, SUBLINE_TOP), copy["subline"], font=sub_font, anchor="lt")
        if box[2] > WIDTH - MARGIN or box[3] > PHONE_TOP - 40:
            raise ValueError(f"Subline would overflow: {copy['subline']!r}, {box}")
        draw.text((MARGIN, SUBLINE_TOP), copy["subline"], font=sub_font, fill=secondary, anchor="lt")


def save_rgb(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Slides as JPEG (Play takes it; a sixth of the PNG weight in the repo), sheets as PNG.
    if path.suffix == ".jpg":
        image.convert("RGB").save(path, format="JPEG", quality=92, optimize=True, progressive=True)
    else:
        image.convert("RGB").save(path, format="PNG", optimize=True)
    # Reopen the actual deliverable, rather than only checking the in-memory image.
    with Image.open(path) as saved:
        saved.verify()


def contact_sheet(locale):
    thumb_size = (248, 441)
    gutter = 12
    sheet = Image.new("RGB", (6 * thumb_size[0] + 7 * gutter, thumb_size[1] + 2 * gutter), "#E4DED6")
    for index in range(1, 7):
        with Image.open(OUT / locale / f"{index}.jpg") as slide:
            thumb = slide.resize(thumb_size, Image.Resampling.LANCZOS)
        sheet.paste(thumb, (gutter + (index - 1) * (thumb_size[0] + gutter), gutter))
    save_rgb(sheet, OUT / f"contact-{locale}.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--locale", action="append", choices=LOCALES, help="Repeat to rebuild selected locales only")
    args = parser.parse_args()
    captions = json.loads((ROOT / "captions.json").read_text(encoding="utf-8"))
    validate_captions(captions)
    print("Fonts:", font("bold", 100).getname(), "/", font("regular", 40).getname())
    # Each locale frames its own captures, so the phone speaks the caption's language.
    # Source captures and generated art stay untouched.
    art = art_slide()
    locales = tuple(dict.fromkeys(args.locale or LOCALES))
    for locale in locales:
        bases = [phone_slide(i, phone(CAPTURES / locale / f"{slide}.jpg"))
                 for i, slide in enumerate(SLIDES[:5], 1)] + [art]
        for index, (slide, base) in enumerate(zip(SLIDES, bases), 1):
            canvas = base.copy()
            add_copy(canvas, captions[locale][slide], index)
            save_rgb(canvas, OUT / locale / f"{index}.jpg")
        contact_sheet(locale)
        print(f"{locale}: 6 JPEGs, 1242x2208 RGB; contact-{locale}.png, 1572x465")
    print(f"Done: {6 * len(locales)} slides + {len(locales)} contact sheets in {OUT}")


if __name__ == "__main__":
    try:
        FONT_FILES = pick_fonts()
        main()
    except (OSError, ValueError, KeyError) as exc:
        sys.exit(f"FAIL: {exc}")
