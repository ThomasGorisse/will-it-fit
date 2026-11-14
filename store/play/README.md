Google Play listing assets for Will It Fit 3.0, prepared for upload; nothing uploaded.
Locales: en-US, fr-FR, de-DE, es-ES, it-IT and pt-BR; see locales.json.
Each locale contains a title, short description, plain-text full description and changelogs/default.txt.
Run python3 store/play/check.py: character limits checked at 30 / 80 / 4000 / 500, including line breaks.
feature-graphic.png is 1024×500 RGB; made with the built-in image generation tool; prompts in feature-graphic-prompt.txt.

Phone screenshots (`screenshots/phone/`, 1280×2560, 2:1, English UI in cm): taken on the
Pixel 7a emulator on 2026-11-14 from build `82f26eb`. Still missing: an AR shot on a real phone,
and a clean status bar (demo mode).

### Framed screenshots (local only; do not upload)

After replacing the five phone captures, run `python3 store/play/frames/make_frames.py`.
If Pillow is missing: `python3 -m venv store/play/frames/.venv`, then
`store/play/frames/.venv/bin/python3 -m pip install pillow`; use that interpreter to run the script.
Copy: `frames/captions.json`; font: Avenir Next Bold / Medium (sans fallbacks included).
Outputs: `frames/out/<locale>/1.png`–`6.png`, 1242×2208 RGB, plus 1572×465
`frames/out/contact-<locale>.png`. Optional: `--locale en-US --locale fr-FR`.
All caption glyphs and text bounds are checked. Phone UI remains English.
Slide 5 is the evening view; slide 6 reuses `frames/free-offline-art.png`, staged artwork
made with built-in imagegen (prompt: `frames/free-offline-art-prompt.txt`). No upload occurs.
Known check limitation: `check.py` requires a five-line README, so the appended documentation fails that check.
