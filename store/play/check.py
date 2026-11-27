#!/usr/bin/env python3
"""Check every configured Play listing and the feature graphic using only the standard library."""

import json
from pathlib import Path
import struct
import sys
import unicodedata


ROOT = Path(__file__).resolve().parent
REQUIRED = [
    "en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR",
    "en-GB", "es-419", "nl-NL", "pl-PL", "sv-SE", "tr-TR", "ja-JP", "ko-KR", "id", "ru-RU",
]
LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
    "changelogs/default.txt": 500,
}


def main():
    errors = []
    try:
        locales = json.loads((ROOT / "locales.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"FAIL locales.json: {exc}")
        return 1
    if not isinstance(locales, list) or not all(isinstance(locale, str) and locale for locale in locales):
        print("FAIL locales.json: expected a list of non-empty locale codes")
        return 1
    if len(locales) != len(set(locales)):
        errors.append("locales.json must not contain duplicate locales")
    missing = [locale for locale in REQUIRED if locale not in locales]
    if missing:
        errors.append(f"locales.json is missing required locales: {', '.join(missing)}")

    print("Characters (title / short / full / what's new; limits 30 / 80 / 4000 / 500):")
    checked = 0
    for locale in locales:
        counts = []
        for name, limit in LIMITS.items():
            path = ROOT / locale / name
            try:
                text = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError) as exc:
                errors.append(f"{locale}/{name}: {exc}")
                counts.append("missing")
                continue
            # Count every code point, including all newlines and the final LF.
            count = len(text)
            counts.append(f"{count}/{limit}")
            checked += 1
            if not text.strip() or count > limit:
                errors.append(f"{locale}/{name}: {count} characters, limit {limit}")
            if text != unicodedata.normalize("NFC", text):
                errors.append(f"{locale}/{name}: text must use NFC Unicode normalization")
            if "\ufeff" in text or any(ord(c) < 32 and c != "\n" for c in text):
                errors.append(f"{locale}/{name}: unexpected control character or BOM")
            if name in ("title.txt", "short_description.txt") and "\n" in text.rstrip("\n"):
                errors.append(f"{locale}/{name}: must be a single line")
            if name == "title.txt" and not text.startswith("Will It Fit: "):
                errors.append(f"{locale}/{name}: missing brand and localized subtitle")
        print(f"{locale}: {' / '.join(counts)}")

    try:
        header = (ROOT / "feature-graphic.png").read_bytes()[:33]
        if len(header) != 33 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
            raise ValueError("not a PNG with an IHDR header")
        width, height, depth, color = struct.unpack(">IIBB", header[16:26])
        if (width, height, depth, color) != (1024, 500, 8, 2):
            raise ValueError(f"expected 1024x500, 8-bit RGB; got {width}x{height}, depth {depth}, color {color}")
        print("feature-graphic.png: 1024x500, 8-bit RGB, no alpha")
    except (OSError, ValueError, struct.error) as exc:
        errors.append(f"feature-graphic.png: {exc}")

    try:
        if not (ROOT / "README.md").read_text(encoding="utf-8").strip():
            errors.append("README.md is empty")
    except (OSError, UnicodeError) as exc:
        errors.append(f"README.md: {exc}")

    if errors:
        for error in errors:
            print(f"FAIL {error}")
        return 1
    print(f"PASS: {checked} text files across {len(locales)} locales; all character limits checked.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
