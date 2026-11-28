# Will It Fit

**Answer "will it fit?" in under thirty seconds: type three sizes or tap a typical object, see it at real size in your room or carried through your door, and send the photo.**

Free Android app, no account, no ads, no purchases, works offline. Play listing:
`com.gorisse.thomas.willitfit` ("Will It Fit: AR Furniture Size").

## The screens

1. **Size** (home). A live 3D room from the first frame: the object stands against the wall at
   its size, dimensions written on the floor. Width, depth, height (cm or inches) and typical
   objects: sofas, beds, wardrobe, fridge, washer, TVs, bike, stroller.
2. **In my room** (AR). The object lands on the floor at real size by itself; drag to slide,
   twist to turn, size locked. Photo button.
3. **Result**. The photo with the dimensions written on it; share or retake.
4. **Through the door?** No camera. Door opening → yes or no, the margin, and which way to
   carry it, acted out in 3D.
5. **Car boot** (the Boot tab in the door check). Pick a city car, compact, estate, SUV or van,
   adjust its measurements, and see the object under an open hatch. Checks the opening and cargo
   space, including a floor diagonal or folding the rear seats. Data and limitations:
   [CAR_TRUNK_MODE.md](CAR_TRUNK_MODE.md).
6. **Settings**. Units, privacy, licences.
7. **AR tape** (from the ruler on home, or "Measure the door with AR"). Aim the dot at one end,
   tap +, aim at the other: the length shows on the line, then goes into a side of the object or
   of the door.

## Built for what comes next

- `fit/Dimensions.kt` is the one dimensions type, with its `DimensionSource` (typed, preset,
  and later measured).
- `ui/MeasureScreen.kt` is the two-point AR tape; its maths (`fit/Tape.kt`) is unit-tested.
- A room scan will come from SceneView's room engine, into the same AR screen.

## Build

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Kotlin, Jetpack Compose, Material 3 (stable, Compose BOM 2026.09.00), SceneView 4.69.0
(`arsceneview`), AGP 9.5, JDK 21, compileSdk 37, minSdk 28.

The debug build installs as `com.gorisse.thomas.willitfit.debug`, next to the Play version.
Release signing and Play upload are not in this repository.
