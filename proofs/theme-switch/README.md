# Light ↔ dark while the app is open (emulator API 36, 26/11 ~21 h 40)

**Before** (main, SceneView 4.76.0 debug; the Play 3.0.10 on 4.72.0 does the same or worse):
the activity is recreated, the 3D engine takes 4–9 s to drain on the emulator, and the room stays
black, without the sofa, for 10 s and more (`before-1-dark-10s`, `before-2-light-10s`).

**After** (`configChanges="uiMode"`): nothing is recreated, no model reloads (no "ready in" line in
logcat). The sofa and its dimensions stay on screen the whole time; the room goes from day to
evening in 6–10 s on the emulator (`after-dark-*`), and back to light is immediate
(`after-back-to-light-2s`). Not yet seen on a real phone.
