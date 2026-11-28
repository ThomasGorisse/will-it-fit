# Car boot / trunk 3D mode

Implemented on `feat/car-trunk-3d`. No version bump, release, Play action or paid API.

## What changed

From **Size**, enter dimensions or pick an existing object, tap **Through the door?**, then
**Boot**. The existing screen now offers twenty vehicle profiles and five generic presets. **Change object or sizes** returns
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

The searchable, independently scrolling catalogue contains the requested **20 vehicle profiles
plus five generic presets**: city car, compact hatchback, estate, SUV and cargo van. The old
measured Peugeot preset remains `BootPreset.ESTATE`, preserving saved/default behaviour; the
generic estate is a separate entry. The catalogue is the requested EU/US selection, not a
claim that these exact variants are the current twenty sales leaders. Names include the tested
year/variant; data for a UK/EU car must be checked against the actual US trim.

All values below are centimetres. Opening means clear width at the bottom × vertical height;
floor width is the narrowest width between wheel arches. Interior height is usable load height,
not exterior height. **Every RiDC profile has measured opening/floor dimensions and an estimated
interior height**. This distinction is visible in every list row, beneath the selected preset,
and on the PNG and shared caption. Source links were checked on 2026-11-28.

| Vehicle / tested variant and source | Opening W × H | Floor L × W | Usable H | Folded L | Estimated fields |
| --- | --- | --- | --- | --- | --- |
| [Peugeot 308 SW · PureTech 130 Allure (2014)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/peugeot-308sw-puretech-130-stop-start-allure-5dr) | 107.4 × 72.9 | 105.5 × 104.3 | 68 | 183.6 | height |
| [Peugeot 2008 · 1.2 (2020)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/2008-12-5dr-saloon-2020) | 102 × 77.5 | 78 × 95.5 | 65 | 155 | height |
| [Peugeot 3008 · 1.5 (2021)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/3008-15-5dr-saloon-2021) | 105 × 82.8 | 92.5 × 105 | 70 | 168 | height |
| [Renault Clio · TCe (2020)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/clio-tce-5dr-hatch-2020) | 101.2 × 74 | 64.5 × 97.4 | 60 | 129.5 | height |
| [Renault Captur · TCe (2020)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/captur-tce-5dr-saloon-2020-0) | 93.3 × 81.8 | 66.5 × 101.5 | 65 | 144 | height |
| [Renault Kangoo · E-Tech EV45 (2023)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/kangoo-e-tech-electric-ev45-5dr-saloon-2023) | 119.6 × 111.1 | 102 × 119.4 | 100 | 176 | height |
| [VW Golf · 1.5 eTSI (2020)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/golf-15-etsi-5dr-hatch-2020) | 103.5 × 77.5 | 75.5 × 101 | 60 | 145 | height |
| [VW Tiguan · 2.0 TDI (2021)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/tiguan-20-tdi-5dr-saloon-2021-0) | 115 × 82.8 | 90.5 × 100.5 | 70 | 169 | height |
| [Tesla Model Y · Maximum Range AWD (2021)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/model-y-maximum-range-awd-5dr-saloon-2021) | 100.5 × 69 | 111 × 95 | 65 | 193.5 | height |
| [Tesla Model 3 (2024)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/model-3-5dr-hatch-2024) | 99.5 × 51.5 | 106.5 × 94.5 | 50 | 186.5 | height |
| [Dacia Duster · Hybrid 140 (2024)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/duster-hybrid-140-5dr-saloon-2024) | 98.2 × 84.5 | 93 × 99.5 | 70 | 162 | height |
| [Toyota Yaris · Hybrid 130 (2025)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/yaris-15-hybrid-130-5dr-hatch-2025) | 89 × 77.2 | 63.5 × 90 | 55 | 138 | height |
| [Toyota RAV4 (2019)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/rav4-5dr-saloon-2019) | 104 × 86.5 | 101 × 100 | 75 | 180.5 | height |
| [Citroën Berlingo · M (2019)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/berlingo-m-5dr-saloon-2019) | 119.8 × 100 | 99 × 119.8 | 95 | 175.5 | height |
| [BMW X1 · sDrive18i M Sport DKG (2023)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/x1-sdrive18i-m-sportpaket-steptronic-dkg-5dr-saloon) | 106.8 × 72.1 | 90 × 100.2 | 65 | 162.5 | height |
| [Skoda Octavia Combi · 1.5 TSI (2021)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/octavia-combi-15-tsi-5dr-estate-2021) | 102.5 × 77.6 | 109 × 100 | 65 | 186 | height |
| [Honda CR-V · e:HEV Advance (2024)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/cr-v-20-i-mmd-ehev-advance-5dr-saloon-2024) | 111.3 × 83 | 88 × 100.5 | 70 | 186 | height |
| [Hyundai Tucson · 1.6 48V (2021)](https://ridc.org.uk/features-reviews/out-and-about/choosing-car/car/tucson-16-48v-mildhybrid-5dr-saloon-2021) | 102 × 72.7 | 91 × 104 | 65 | 172.5 | height |
| [Ford F-150 · 5.5 ft bed (2024)](https://content.fordpro.com/content/dam/fordpro/us/en-us/pdf/showroom/trucks/f-150/2024/2024_Ford_F-150_Tech_Specs.pdf) | 128.524 × 54.356 | 170.434 × 128.524 | 54.356 | — | opening width, opening height |
| [6 m³ van · Renault Trafic L1H1 (5.8 m³)](https://cdn.group.renault.com/ren/gb/transversal-assets/brochures/van-ebrochures/TRAFIC-eBrochure.pdf.asset.pdf/13742c53ef.pdf) | 139.1 × 132 | 253.7 × 126.8 | 138.7 | — | opening height |

The original 308 SW RiDC figures are unchanged: opening 107.4 × 72.9, floor 105.5 × 104.3,
folded length 183.6; its 68 cm usable height remains an estimate. The other RiDC interior
heights are deliberately authored planning estimates, not inferred from boot volume, an opening
height, a seat headroom figure or an external vehicle dimension. There is no claim that their
full interior profile has been measured. Floor positions, trim and seat settings can differ.

Ford's 2024 specification gives the **5.5 ft Styleside** bed floor length 67.1 in, width between
wheelhouses 50.6 in and bed depth 21.4 in. These are converted using exactly 2.54 cm/in. Its
"opening" width/height are conservative **estimates** reusing the wheelhouse width and bed
wall depth, not measured tailgate aperture dimensions. This profile checks a box entirely below
the bed rails with the tailgate closed. It does not validate load restraint, weight or overhang.
The UI and card explain that restriction; the 3D scene remains a schematic cargo corridor.

The **6 m³ van category** uses Renault Trafic L1H1, whose manufacturer volume is actually
**5.8 m³**, shown in its name. The brochure publishes rear width 139.1, floor length 253.7,
width between arches 126.8 and internal height 138.7. Rear opening height **132 cm is an
estimate**; the source's drawing/table does not unambiguously establish that measurement.
No volume-to-dimensions conversion or load-through-bulkhead length is used. There is no folding
extension for the cargo van or pickup.

Generic presets are entirely **estimated**, with no named-car measurement claim:

| Generic preset | Opening W × H | Floor L × W × H | Folded L |
| --- | --- | --- | --- |
| City car | 90 × 70 | 55 × 95 × 65 | 120 |
| Compact hatchback | 100 × 75 | 75 × 100 × 70 | 150 |
| Estate car | 108 × 80 | 103 × 104 × 68 | 180 |
| SUV | 110 × 85 | 95 × 105 × 80 | 175 |
| Cargo van | 140 × 125 | 240 × 120 × 130 | — |

`BootPreset.sourceUrl` and `estimated: Set<BootMeasure>` keep provenance next to the numbers.
The review fixture in `tools/car-boot/sources.json` records the references and field status.
Editing any field makes the selection custom, so a modified car is never shared under the
original car's name or measured label. Presets are recovered from exact saved dimensions;
existing links retain folding/unit support and custom checks use the localized "Your boot".

## Translations and share card

All catalogue, folding, diagonal, provenance and share strings have explicit **en, de, es, fr,
it and pt-BR** resources, including the fifteen strings previously falling back to English.
Product/model/trim names remain proper names; vehicle categories and bed/van labels are localized.
`BootTranslationsTest` discovers shipped locale folders and checks all feature strings for
presence, nonempty values and matching indexed format arguments. Language-neutral dimension
punctuation (`door_object`, marked `translatable="false"`) intentionally uses the shared resource.

One tap on the existing boot share button draws a **PNG** with Android Canvas/StaticLayout in
Kotlin, on a background dispatcher. It needs no scene capture, GPU readback, storage permission,
network call or new dependency. Text wraps and the bitmap grows to fit long translations.
It names the exact car, fit/miss/diagonal/seats-down/combined verdict, clearance, object and
boot sizes, carry/angle, provenance, measurement reminder and localized "Checked with Will It
Fit" signature. Tight and zero-clearance results use amber; misses use red. The image is stored
in `cache/photos/boot-<UUID>.png`, with failure cleanup and seven-day retention. Existing JPEG
cleanup does not delete PNG cards. The existing FileProvider grants only temporary read access;
sharing sets `image/png`, `EXTRA_STREAM`, ClipData, text and the existing reproducible check link.
Other spaces keep their existing photo flow. Save failures use the existing localized retry UI.
`BootShareTextTest` exercises the actual localized resource templates, both units, decimal
separators, zero clearance, negative excess, and all five verdict combinations.

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

**Compiled: yes. Seen on screen: no.** The actual Android app and tests compiled against
this checkout's configured dependencies; no APK assembly, installation, emulator or device was
used. PNG appearance, chooser handoff and layout are still unverified visually.

Disk was checked before both build attempts: initially 11 GiB, then 8.09 GiB for the final
incremental run (8.1 GiB in `df -h /`). All Gradle execution was protected by
`lockf -k /tmp/mac-build.lock`; a brief occupied-lock attempt exited without starting Gradle,
then a bounded wait acquired the lock. No other session's process was stopped.

Final command (with a second free-space guard inside the acquired lock):

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
ANDROID_HOME=/Users/thomasgorisse/Library/Android/sdk \
GRADLE_USER_HOME=/Users/thomasgorisse/.gradle-codex \
lockf -k -t 180 /tmp/mac-build.lock /bin/zsh -c '
  python3 -c "import shutil, sys; sys.exit(0 if shutil.disk_usage(\"/\").free >= 8 * 2**30 else 1)" &&
  ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --offline \
    --console=plain --no-daemon --max-workers=2 \
    -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m
'
```

Real final Gradle output:

```text
Free GiB: 8.09
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 12s
27 actionable tasks: 2 executed, 25 up-to-date
```

The initial run executed `:app:compileDebugKotlin` successfully, including the new Compose
picker and PNG renderer. It ran 186 JVM tests with one failing translation check: the test had
incorrectly required a locale override for the punctuation-only `door_object` resource marked
`translatable="false"`. Correcting the test's selection resolved that failure; no translatable
feature resource was excluded. The first compile used Kotlin's compiler fallback after its
daemon failed; the final run explicitly used in-process compilation. Existing AGP deprecation
and FSEvents warnings did not prevent compilation or tests.

Final JUnit XML report totals, read from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:

```text
{'tests': 186, 'failures': 0, 'errors': 0, 'skipped': 0}
BootCatalogueTest 4 tests, 0 failures
BootShareTextTest 6 tests, 0 failures
CarBootCheckTest 11 tests, 0 failures
BootTranslationsTest 1 tests, 0 failures
```

The full existing suite ran, including shared-link compatibility and the independent
corner-projection grid now exercised across all 25 profiles. Additional checks passed:

```text
values-de: 99/99 feature translations; format arguments match
values-es: 99/99 feature translations; format arguments match
values-fr: 99/99 feature translations; format arguments match
values-it: 99/99 feature translations; format arguments match
values-pt-rBR: 99/99 feature translations; format arguments match
All resource XML parses; boot resource references resolve; git diff --check passed.
```

Full local Gradle logs: `/tmp/wif-car-trunk-gradle.log` (initial compile/run) and
`/tmp/wif-car-trunk-gradle-final.log` (successful final run). HTML test report:
`app/build/reports/tests/testDebugUnitTest/index.html`.

## What remains

- No device actions are authorized. Android display, large font/landscape layout, exported PNG
  appearance, and the receiving share-sheet app must be visually checked in a later session.
- The separate shared-check website and older recipients still need their own changes to show
  diagonal/folding verdicts; Android shared links remain backward compatible.
- This is still a conservative rectangular planning check, not a physical loading simulation.

No commit, push, version bump, paid API call, deploy or store action was performed. No ADB,
emulator, simulator, package install/uninstall, launch, device start or device stop was used.
