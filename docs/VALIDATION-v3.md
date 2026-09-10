# Version 0.3.0 validation — 2026-09-10

Workspace: `E:\programming\li6800-area-app`. Package `org.li6800.area`, versionCode 3.

## Implemented behavior

- Fixed, non-scrollable main screen. CameraX PreviewView fills the background;
  area, coverage, tracking status, and controls have fixed positions.
- Exactly two live modes: Camera; Circle & sensitivity. The second has a square
  rectified circle preview, area above, and backing/sensitivity controls below.
  Switching display modes does not remove or rebind the camera use cases.
- Marker loss keeps the last valid area, source image, circle image, and timestamp.
  The amber Last reading label identifies retained data. Reacquisition replaces it.
  A new input clears old measurements. Saving retained data preserves the accepted
  frame and records `heldWhenSaved` in metadata.
- Sensitivity resegments the accepted frame and applies to subsequent live frames.
- Detached low-color connected components confined to the outer 1.5 mm of the
  aperture are excluded. Components with colored pixels or interior tissue remain.
  The aperture itself is still 600 mm²; there is no blanket erosion or largest-leaf rule.
- V1/V2 calibration and template geometry remain unchanged. The V2 PDF bundled in
  the APK matches `output/pdf/LI6800-6cm2-marker-cutout-v2.pdf`:
  SHA-256 `461b4167ae184221be85c148073b5b7788857da28cec530d9340e2755a636ce0`.

## Build and tests

`tools/build.ps1` ran unsandboxed using the documented local JDK, SDK, signing key,
and shared dependency cache. The final production build ran assembleDebug,
assembleDebugAndroidTest, testDebugUnitTest, and lintDebug successfully in 1m 8s.
Log: `.build-logs/gradle-20260910-161655-957-25408.log`.

- JVM: 2 tests, zero failures/errors (circle integration and holes).
- Lint: zero errors, 29 warnings. Warnings include unused resources, dependency
  version notices, KTX suggestions, backup configuration, and plural guidance.
- Direct instrumentation on Pixel_6a, API 33, x86_64: **OK (8 tests)**.
  Log: `.build-logs/qa/instrumentation-v3.txt`.
- Existing native OpenCV tests cover synthetic areas, perspective, missing and
  occluded markers, one-sided rejection, V1 compatibility, mixed-layout rejection,
  and saved archive read-back/provenance.
- New segmentation tests verify a detached gray rim counts as zero, adding that
  rim does not change a central green leaf's area, interior gray tissue survives,
  and small colored edge tips remain measurable.
- New state tests drive actual ViewModel analysis: valid frame, repeated tracking
  loss, sensitivity adjustment of retained data, reacquisition, then a new input.
  Source identity and original timestamp remain intact while held.
- The screen test runs the real activity and virtual camera, injecting calibrated
  fixtures through the analyzer executor. It checks retained area/status, unchanged
  text/button bounds, no scrollable main nodes, square circle preview, and a fresh
  full-circle measurement in the second mode before switching back to Camera.
  UI lookup explicitly refreshes Compose accessibility nodes to avoid cached text.

Visual inspection of final screenshots confirmed readable controls and no overlap
on the tested portrait 1080 × 2400 display. Square preview bounds were 961 × 961 px.
Evidence: `output/screenshots/v3-held.png`, `v3-circle-sensitivity.png`, and
`v3-bounds.txt`. The camera screenshot shows the emulator's virtual room; the held
area and rectified circle come from injected synthetic fixtures, not that room.

## APK

`output/apk/LI6800-Area-0.3.0-debug-universal.apk`, 165,050,339 bytes.

- SHA-256: `f8df9d6601cf06e3a77cfe49b1c84d4cdb0b8dcda3ac4b28021914f04e5595ff`.
- `apksigner verify --print-certs`: passed, same project signer as earlier versions.
- Certificate SHA-256: `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- `zipalign -c -P 16 -v 4`: passed.
- AAPT verified version 0.3.0/code 3, min SDK 26, target SDK 37, and all four ABIs:
  arm64-v8a, armeabi-v7a, x86, x86_64. There is no INTERNET permission.
- This remains a debug-build APK signed with the project's preserved debug key.
- Copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.3.0-debug-universal.apk`;
  destination size and SHA-256 match the source. Cloud sync was not checked.
- Final LeafArea/AndroidRuntime error log was empty after the passing tests.

## Limits

These are software and emulator checks. Physical camera accuracy, lighting,
printer scaling, and smaller-marker detection reliability have not been measured.
The live UI tests use injected synthetic frames for known-area assertions; native
preview binding is exercised with the emulator camera. Landscape and enlarged
system font layouts were not validated in this iteration.

Shadows touching tissue or extending more than 1.5 mm inward can remain included.
Thin gray tissue entirely confined to the rim band may be excluded. Color/backing
segmentation is not a trained leaf classifier. No physical accuracy specification
is claimed. Dropbox local copy verification does not prove cloud synchronization.
