# Version 0.4.0 release validation — 2026-09-10

Workspace: `E:\programming\li6800-area-app`. Package `org.li6800.area`, versionCode 4.

## Changes

- V2-only calibration (IDs 20–29). V1 input produces no measurement; the V1
  definition and PDF are absent from the APK. Historical saved records remain readable.
- Removed the mm²/coverage row from the measurement header in both display modes.
  cm² remains visible at the top; stored/exported measurements retain metric detail.
- Added a camera-switch icon in the live camera view. It cycles exposed cameras,
  rear first, including physical lenses behind logical cameras when Android exposes
  them. A switch starts a new calibration and rejects frames from the previous
  camera session. Unavailable binding choices fall back to another exposed camera.
- Leaf sensitivity persists in private preferences, with range/finite-value checks.
- Menu entries export the V2 PDF and 0.4 mm STEP using Android's document picker.
  Files are bundled and work offline.
- Removed demo UI and loading code from production. Synthetic fixtures now live
  in `app/src/androidTest/assets/`, only in the separate test APK.
- Release uses R8 code minification and resource shrinking with the optimized
  Android default rules. No blanket keep rule disables application optimization.

## Build and automated checks

Final build log: `.build-logs/gradle-20260910-182509-811-13088.log`.
Ran assembleRelease, assembleDebug, assembleDebugAndroidTest, testDebugUnitTest,
and lintRelease using the required unsandboxed local build helper. Passed in 1m 18s.

- JVM: 2 tests, zero failures or errors.
- Instrumentation on the final source's debug variant, Pixel_6a API 33 x86_64:
  **OK (9 tests)**. `.build-logs/qa/instrumentation-v4.txt`.
- Tests cover native OpenCV area/calibration, perspective, missing/occluded markers,
  one-sided rejection, V1 rejection, archive read-back, detached rim filtering,
  retained readings/reacquisition, sensitivity persistence, fixed layouts, live
  updates in both modes, and camera switching.
- Release lint: zero errors, 29 warnings (dependency version notices, unused
  resources, KTX suggestions, plural guidance, and backup configuration).
- Native libraries could not be stripped by the local build tools and were
  packaged as supplied. Most universal APK size is native OpenCV across four ABIs.

## Actual minified-release runtime checks

Installed the signed release over the existing package without uninstalling.
All of the following were checked through the ordinary release UI:

- Native full-screen camera opens. The switch button moved from device 0 to
  device 1; dumpsys confirmed device 0 closed and device 1 open. The front emulator
  camera shows its generated scene. No app camera-processing errors were logged.
- Selected sensitivity 74% using the slider. Force-stopped the process, relaunched,
  and confirmed 74% was restored in Circle & sensitivity.
- Imported an external V2 half-circle PNG through the system document picker.
  Result: **2.997 cm²**, ten markers, 0.01 mm fit error at the restored sensitivity.
  This exercises native detection, homography, segmentation, and rendering after R8.
- Menu contains Open photo, Saved, Cutout PDF, Mask STEP · 0.4 mm, and How to measure;
  there is no Samples/demo entry.
- Exported both files to emulator Downloads and pulled them back. Their SHA-256
  hashes match the source files and bundled APK assets:
  - PDF: `461b4167ae184221be85c148073b5b7788857da28cec530d9340e2755a636ce0`.
  - STEP: `9b8c95f6ef25b24693bc6c5f3106db76846f8ab23c2f71950c35149c41085bc8`.
- Final LeafArea/AndroidRuntime error log was empty: `.build-logs/qa/runtime-v4.txt`.

Screenshots/UI trees: `.build-logs/qa/v4-release-camera`, `v4-release-front`,
`v4-sensitivity-restored`, `v4-release-menu`, and `v4-release-half-circle`.
The screen test also records fixed bounds under `output/screenshots/v4-*`.

## Delivered APK

`output/apk/LI6800-Area-0.4.0-release-universal.apk`, 149,582,173 bytes.

- SHA-256: `a5c82b067264458575449fc9555763d57ae576e1a4daddc517458f72971cf84e`.
- apksigner verification and 16 KB ZIP alignment check passed.
- Preserved certificate SHA-256:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
  The certificate retains its Android Debug name to update previously delivered
  installations; the APK itself is the non-debuggable release variant.
- AAPT confirms version 0.4.0/code 4, min SDK 26, target SDK 37, all four ABIs
  (arm64-v8a, armeabi-v7a, x86, x86_64), and no application-debuggable flag.
- R8 mapping confirms renamed app classes. Mapping, configuration, seeds, usage,
  and resource reports are retained under `app/build/outputs/mapping/release/`.
- APK assets consist only of V2 JSON, PDF, STEP, and generated baseline profiles.
  No demo image assets or V1 template data are present.
- Copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.4.0-release-universal.apk`;
  destination size and SHA-256 match. Cloud synchronization was not checked.

## Limits and references

Physical multi-lens phones, physical measurement accuracy, lens distortion,
landscape, and enlarged font settings were not validated. Android/OEM exposure
determines which auxiliary lenses the app can select. The rim-filter boundaries
and physical accuracy limitations from V3 still apply. No cloud sync is inferred
from a local Dropbox copy.

Implementation API references: [CameraSelector physical lens selection](https://developer.android.com/reference/androidx/camera/core/CameraSelector.Builder#setPhysicalCameraId(java.lang.String)),
[CameraInfo physical cameras](https://developer.android.com/reference/androidx/camera/core/CameraInfo#getPhysicalCameraInfos()),
and [R8 release optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
