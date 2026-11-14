Google Play listing assets for Will It Fit 3.0, prepared for upload; nothing uploaded.
Locales: en-US, fr-FR, de-DE, es-ES, it-IT and pt-BR; see locales.json.
Each locale contains a title, short description, plain-text full description and changelogs/default.txt.
Run python3 store/play/check.py: character limits checked at 30 / 80 / 4000 / 500, including line breaks.
feature-graphic.png is 1024×500 RGB; made with the built-in image generation tool; prompts in feature-graphic-prompt.txt.

Phone screenshots (`screenshots/phone/<locale>/1-sofa.jpg`…`5-dark.jpg`, 1280×2560, 2:1, cm): one
series per locale with the app UI in that language (en-US uses the en-GB UI, for centimetres), taken
on the Pixel 7a emulator on 2026-11-14, status bar in demo mode (9:41): the room shots (1, 2, 3, 5)
from build `85dee7d`, the door shot (4) from `caabb3e` (the door scene has not changed since). Still missing: an AR shot on a real phone.

### Framed screenshots (local only; do not upload)

After replacing the phone captures, run `python3 store/play/frames/make_frames.py`.
If Pillow is missing: `python3 -m venv store/play/frames/.venv`, then
`store/play/frames/.venv/bin/python3 -m pip install pillow`; use that interpreter to run the script.
Copy: `frames/captions.json`; font: Avenir Next Bold / Medium (sans fallbacks included).
Outputs: `frames/out/<locale>/1.jpg`–`6.jpg`, 1242×2208 RGB, plus 1572×465
`frames/out/contact-<locale>.png`. Optional: `--locale en-US --locale fr-FR`.
All caption glyphs and text bounds are checked. Each locale frames its own captures.
Slide 5 is the evening view; slide 6 reuses `frames/free-offline-art.png`, staged artwork
made with built-in imagegen (prompt: `frames/free-offline-art-prompt.txt`). No upload occurs.
