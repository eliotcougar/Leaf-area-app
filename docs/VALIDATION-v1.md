# First iteration validation - 2026-09-10

Workspace: `E:\programming\li6800-area-app`. Package `org.li6800.area`, version
`0.1.0` (1), Android 8.0/API 26 or newer. This is a project-debug-signed evaluation
APK. The user excluded add/erase editing; the final app uses automatic segmentation
with backing and sensitivity controls.

## Delivered artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `output/apk/LI6800-Area-0.1.0-debug-universal.apk` | 161325893 | `8d0e180178d96a24475f25a6fc01606fc6b2304fde4be98f2b53489ebd6a0178` |
| `output/pdf/LI6800-6cm2-marker-cutout-v1.pdf` | 6167 | `45b124f366d46935ccfebe9235ab7bfaf7ccadb30064a3c0497c9e9c3a6293ab` |

The APK includes ARM64, ARMv7, x86, and x86_64. `apksigner verify --verbose
--print-certs` passed with v2 signing. Certificate SHA-256:
`597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
`zipalign -c -P 16 -v 4` passed. Every ARM64 ELF LOAD segment has 16384-byte
alignment; this is a packaging check, not execution on a 16 KB-page device.

The PDF has one A4 page with two identical templates. Independent PDF vector
read-back measured both opening bounds as 27.639539 mm in each axis (rounding in
PDF serialization); the source formula is `2 * sqrt(600/pi)` mm. The 50 mm ruler
was also verified from the PDF. Rendering and visual inspection passed; OpenCV
decoded all 12 markers, with IDs 10-15 appearing twice. The six markers on each
cutout are 10 mm squares. The original front 90-degree arc of radius 20 mm stays
in place while the sides widen to 68 mm. This arc is the implementation's
interpretation of the protected front of the curved tip.

The PDF embedded in the APK, the desktop output, and the PDF exported through
Android's Create Document picker have identical bytes. Print at Actual size/100%
and check the ruler independently; software read-back cannot verify a printer.

## Build and automated checks

Command: `tools/build.ps1 -Tasks @(':app:assembleDebug',
':app:assembleDebugAndroidTest', ':app:testDebugUnitTest', ':app:lintDebug')`.
Passed in 58 seconds. Log: `.build-logs/gradle-20260910-143226-107-21316.log`.
The helper uses the installed Android Studio JDK 21, SDK 37, shared v2rayNG Gradle
dependency cache, and this project's own caches and signing key.

- Two JVM tests passed: analytic empty/full/half area, exclusion outside the
  aperture, and exclusion of a known central circular hole.
- Lint: 0 errors, 10 warnings. Five dependency-version suggestions, four KTX style
  suggestions, and one pre-Android-12 backup configuration suggestion. Backup is
  disabled in the manifest; Android 12+ extraction rules exclude measurement files.
- Three Android instrumentation tests passed through the direct runner, with
  explicit `OK (3 tests)`: native ArUco detection and measurement, rejection with
  only three markers, and saved archive read-back including CSV formula handling.

Earlier, the Gradle connected-test task reported BUILD SUCCESSFUL despite an
emulator install failure during boot. That run was not counted. The final APK and
test APK installed successfully with `adb install --no-streaming`, then the direct
instrumentation runner passed. Evidence: `.build-logs/qa/instrumentation-final.txt`.

## Numerical checks on Android

These are synthetic images processed by the app's actual OpenCV implementation
on the x86_64 emulator, using white backing and sensitivity 0.5. They establish
software behavior, not an accuracy specification for real leaves.

| Fixture | Expected (mm²) | Measured (mm²) |
| --- | ---: | ---: |
| Empty | 0 | 0 |
| Half circle | 300 | 299.55625 |
| Full circle | 600 | 599.14 |
| Leaf shape with hole | 415.5, fixture raster reference | 415.3575 |
| Half circle under perspective | 300 | 299.43125 |
| Yellow full coverage | 600 | 599.59125 |
| Only three markers | No result | No result |

All six valid fixtures detected six markers. Homography RMS residual was
0.006641 mm for the flat fixtures and 0.016226 mm for perspective. This residual
measures marker fit only, and must not be interpreted as leaf area accuracy.

## Emulator UI checks

One task-owned `Pixel_6a` AVD, API 33 x86_64, `emulator-5554`, 1080x2400. No physical
phone was attached. Checks used fresh UI trees, screenshots, and logcat.

- Cold launch and synthetic sample selection succeeded. The hole sample displayed
  4.154 cm² and its automatic overlay retained the interior hole.
- Camera permission denied: explanatory recovery controls appeared. Granting
  permission started actual CameraX frames from the emulator virtual scene.
  Frames without markers showed no area; both Freeze controls were disabled.
- Switching from the camera to Open photo stopped the camera flow. Selecting the
  task's half-circle PNG from Downloads measured 2.996 cm² through the URI import
  path. This is a synthetic QA file imported as a photo; only built-in samples
  receive the app's synthetic flag automatically.
- Review has the automatic overlay, backing selection, sensitivity, sample ID,
  and save controls. Add, erase, undo, reset, and the editing dialog are absent.
- Saving `QA-auto-half` and force-stopping/restarting the app preserved the record
  in Saved. Share opened Android's chooser with the ZIP URI; no external sharing
  destination was selected.
- Read-back of that app-private ZIP verified 299.55625 mm², calibration metadata,
  CSV, source image, rectified image, overlay, and tissue mask. Manual-edit fields
  are absent from both JSON and CSV. Archive writes use a temporary file followed
  by rename.
- Print cutout opened Create Document and successfully saved the matching PDF
  in Downloads. The first immediate read preceded asynchronous completion; the
  completed 6167-byte export was subsequently checked by hash.
- Final LeafArea/AndroidRuntime error log contained no errors.

Evidence lives under `.build-logs/qa/`, including `artifact-audit.json`,
`QA-auto-half.zip`, UI XML/PNG files, and the exported PDF. Reusable QA helpers:
`tools/android-qa.py` and `tools/verify-artifacts.py` (the latter expects the named
QA record and PDF export on the emulator).

## Remaining physical validation

No printed template, actual LI-6800 head fit, real leaf measurement, or physical
phone camera has been tested. Live camera transport and static marker processing
were checked separately; continuous tracking of a physical marked cutout remains
untested. Blue-backing behavior has not received numerical fixture validation.
No claim is made about performance across phones or production release readiness.

The app has no camera lens-distortion model, automatic cut-edge verification, or
leaf recognition model. Shadows, reflections, an incorrect backing setting,
printer scaling, badly cut paper, paper curvature, and leaf/marker plane separation
can bias results even when marker fit is good. Use a flat matte backing, a centered
near-normal view, and inspect the automatic overlay. Establish accuracy with
independently measured shapes, repeated prints, real leaves, and target phones
before relying on results for scientific reporting.
