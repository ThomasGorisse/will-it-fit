# Car boot / trunk 3D mode

Implemented on `feat/car-trunk-3d`. No version bump, release, Play action or paid API.

## What changed

From **Size**, enter dimensions or pick an existing object, tap **Through the door?**, then
**Boot**. The existing screen now offers five boot presets. **Change object or sizes** returns
to the same object input/catalog; the selected boot is kept. Every boot dimension remains
editable, in the existing cm/inch units. Choosing a preset replaces all its measurements.

The verdict distinguishes a straight fit, a miss, a diagonal fit and a fit requiring the rear
seats down. If both folding and a diagonal are necessary, the headline names folding and the
detail names the diagonal angle. The smallest clearance and carrying orientation remain visible,
including zero-clearance/tight fits. The sheet scrolls while leaving space for the 3D view.

The existing SceneView boot scene now has a raised hatch and translucent glass. It draws the
same object model, carry orientation, yaw and floor length used by the verdict. Seat backs fold
when that is the recommendation. A miss leaves the object outside the boot, coloured as a miss.
These are schematic cargo spaces, not manufacturer vehicle models.

## Presets and data sources

All values below are centimetres. Opening means clear width × height; floor width is the
narrow corridor between wheel arches. Height is usable load height, not vehicle exterior height.

| Preset | Opening | Floor length, seats up | Floor width | Usable height | Length, seats down |
| --- | --- | --- | --- | --- | --- |
| City car | 90 × 70 | 55 | 95 | 65 | 120 |
| Compact hatchback | 100 × 75 | 75 | 100 | 70 | 150 |
| Estate: Peugeot 308 SW (2014) | 107.4 × 72.9 | 105.5 | 104.3 | 68 | 183.6 |
| SUV | 110 × 85 | 95 | 105 | 80 | 175 |
| Cargo van | 140 × 125 | 240 | 120 | 130 | Not applicable |

**City, compact, SUV and cargo van are typical planning values**, authored for this feature.
They are not measurements or specifications of a named car. The UI explicitly says so.

For the estate, the opening, both floor lengths and narrowest floor width come from
[RiDC's test of the Peugeot 308SW PureTech 130 STOP START Allure 5dr estate, 2014](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/peugeot-308sw-puretech-130-stop-start-allure-5dr),
checked 2026-11-28. Millimetres were converted to centimetres without rounding. RiDC measures
opening width at the bottom, rather than an entire opening profile. **The 68 cm usable interior
height is an estimate**, not a RiDC measurement; the UI identifies that estimate. Other model
years, trims, floor settings and seat positions can differ. Measure the actual car.

## Calculation and architecture decisions

- Reuse `Dimensions`, all six `Carry` orientations, `Boot`, `BootVerdict`, the existing fields,
  palette, object catalog/models, navigation, AR tape and share flow. No separate product flow
  or rendering engine was introduced.
- `checkBoot` checks the seats-up space. Straight carries retain the existing largest-minimum-gap
  ordering and win over diagonal carries. A diagonal reuses `liftFloor`'s numerical search
  (0.1° sweep with local refinement). For each candidate angle, the full projected box width must
  clear **both** the hatch and the wheel arches; projected depth must clear the floor length,
  and height must clear the opening and interior. It is not enough for an object's longest side
  to be shorter than the floor diagonal.
- `checkCarBoot` first tries every seats-up placement, then compares the optional folded length.
  Folding is recommended only if it changes a miss into a fit. A total miss retains the seats-up
  limiting dimension. The moving-day checklist continues to check seats up with `checkBoot`,
  avoiding a silent conditional “yes” in its simple pass/fail rows.
- This is a conservative rectangular corridor with fixed yaw during loading. It assumes the
  object can be carried in the chosen orientation outside the car. It does not simulate roof
  taper, hatch closure, curved seals, sill clearance, folding-seat steps, front seat position,
  pivoting manoeuvres, flexibility or weight. A numerical search can miss an extremely narrow
  near-touching angular solution. Check the physical vehicle before loading.
- Folded length is optional and saved with the existing preferences. Existing saved five-number
  boots retain their dimensions and no folded length is invented for them. A fresh installation
  uses the new estate preset; the existing estate reset also selects it.
- Shared links keep the existing five-number `boot=` payload and add optional `bootDown=`.
  New app recipients recompute the same configuration. Old links still parse, and malformed
  optional folded lengths are ignored. Older apps/the external web fallback need their own
  update to understand folding and the new diagonal result; this branch only changes Android.

## Validation

`CarBootCheckTest` covers all five presets, the four outcomes, combined folding + diagonal,
opening/height failures, preference for seats-up placements, invalid/nonfinite values, and an
independent corner-projection check across a grid of object dimensions. `SharedCheckTest` covers
folded-length round trips and backwards compatibility. Existing boot tests remain in place.

Targeted Gradle command for a machine with the project dependencies available:

```sh
lockf -k /tmp/mac-build.lock ./gradlew :app:testDebugUnitTest \
  --tests 'com.gorisse.thomas.willitfit.fit.*Boot*Test' \
  --tests 'com.gorisse.thomas.willitfit.fit.SharedCheckTest'
```

Local results: **103 fit-package JUnit tests passed**, including the new car-boot and sharing
cases. The unchanged production sources and tests were compiled directly with cached Kotlin
2.4.21 and run with JUnit 4.13.2 (compiler heap capped at 256 MB, tests at 128 MB). No stubs or
copies of the algorithms were used. `Dimensions.toSize` resolved against the cached SceneView
4.69.4 math alias/kotlin-math dependency; this does not validate the app's SceneView 4.72 UI.

The Gradle attempt did not acquire the occupied shared build lock, so no Android build started.
No `assemble` was run. All resource XML parses, new boot resource references resolve, and
`git diff --check` passes. Full Android compilation and visual verification remain outstanding.

## Screenshots to take

Use a CI debug APK on an available bench **after coordinating ownership**. Do not replace the
Play app or start/stop any emulator or ADB server. No device was used for this implementation.

1. Light and dark: small custom box **50 × 40 × 30**, estate preset, positive verdict and open
   hatch; also capture the five-entry preset menu.
2. **160 × 40 × 50**, estate preset: “Fits with rear seats down”, extended floor and folded backs.
3. Custom object **140 × 10 × 60**; opening **110 × 70**, interior length **120**, width **110**,
   height **70**: “Fits diagonally”, visible yaw and angle. Remove/reduce folded length if needed.
4. With the same diagonal case, opening width **40**: negative result and object outside.
5. **205 × 10 × 60**; opening **110 × 70**, floor **100 × 110**, height **70**, folded length
   **190**: both folding and diagonal required.
6. Inches, large font, landscape: scroll every field, check the preview remains visible; clear
   a length and confirm the verdict/share action is hidden until valid. Reopen the screen/app
   and open a shared folded-length link to check persistence and captions.

## What remains

- Android build and on-device visual verification: until completed, this is an unverified UI
  prototype. No screenshots or device validation are claimed.
- Translate the new default resources into **de, es, fr, it, pt-rBR**. No translation generator
  exists in this checkout; new strings currently fall back to English using Android resources.
- Update the separate shared-check website/older consumers if they must show identical results.
- Vehicle-specific profiles beyond the one sourced estate, or physical manoeuvre simulation,
  require additional measured data and are outside this feature.
