# Light ↔ dark while the app is open (emulator API 36, 26/11 ~21 h 40)

**Before** (main, SceneView 4.76.0 debug; the Play 3.0.10 on 4.72.0 does the same or worse):
the activity is recreated, the 3D engine takes 4–9 s to drain on the emulator, and the room stays
black, without the sofa, for 10 s and more (`before-1-dark-10s`, `before-2-light-10s`).

**After** (`configChanges="uiMode"`): nothing is recreated, no model reloads (no "ready in" line in
logcat). The sofa and its dimensions stay on screen the whole time; the room goes from day to
evening in 6–10 s on the emulator (`after-dark-*`), and back to light is immediate
(`after-back-to-light-2s`). Not yet seen on a real phone.

**Round trip, after the Codex fix** (#56 built on SceneView 4.76.0 for this check only; on 4.72.0
the emulator stalls frames, sceneview#4969): cold start in light, dark, back to light without
relaunch. `roundtrip-1` and `roundtrip-3` match: the day sun is back to SceneView's default colour,
not the evening's warm one. One model load in logcat, no engine torn down.
