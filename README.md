# LI-6800 Leaf Area - 0.4.0

An offline Android app for the projected leaf area inside a **6.000 cm²** paper
opening. The V2 cutout uses ten printed ArUco markers to calibrate each image and requires
at least four suitably distributed markers before reporting a measurement.

## Repository contents

This repository contains source, build/generation scripts, documentation, bundled
app assets, and test fixtures. APKs, generated output, screenshots, logs, caches,
and signing keys are not tracked. The PDF and STEP under `app/src/main/assets/`
are required by the app's offline export menu. Test images are included only in
the separate test APK. Original reference photos/PDFs and the historical toolchain
smoke project stay local; historical documentation can refer to those local files.

Build a fresh APK using the instructions below. Paths under `output/` in the
documentation describe local generated files, not downloads in this repository.

## Use it

1. Install the APK from `output/apk/` on Android 8.0 or newer.
2. Print `output/pdf/LI6800-6cm2-marker-cutout-v2.pdf` at **Actual size / 100% on A4**.
   Disable Fit to page. Verify the 50 mm ruler. More > Cutout PDF can
   also save the same PDF to your phone.
3. Cut out one of the two templates and remove its center circle. Keep the square
   markers intact. Use only one cutout in the image.
4. Place the leaf flat on a matte white backing and put the cutout on top. Keep the
   markers and leaf nearly in the same plane. Avoid strong shadows and glare.
5. The camera opens automatically after permission is granted. Hold it close enough
   to read the markers, nearly overhead. More contains Open photo, Saved, Cutout PDF, Mask STEP, and help.
   The camera-switch icon cycles through cameras/lenses exposed by Android, rear
   cameras first. Each switch starts a fresh calibration.
6. Use the two bottom buttons: **Camera** for a full-screen live view, or
   **Circle & sensitivity** for a square preview of the measuring circle with the
   slider below it. Both modes update live and show the area at the top. Choose
   White paper or Blue mat to match the backing. Leaf sensitivity is remembered
   across app restarts. The main screen does not scroll; the area header shows cm².
7. Freeze to hold a result, or Save to freeze it and enter a sample ID. Saved lists local results and can
   share each as a ZIP containing a CSV, images, mask, and calibration metadata.
8. Enter the result in **cm²** as `S` in LI-6800 **Constants > Gas Exchange**. The
   hardware chamber aperture stays at 6 cm². Ensure the photographed region is the
   same region enclosed by the instrument.

This iteration is meant for evaluation. It has software and emulator checks, not
an established physical accuracy specification. Check the overlay before saving.
Green, yellow, and brown tissue all count; holes and tissue outside the circle do
not. Curled leaves cannot be corrected by a flat paper reference.

If tracking disappears, the last calibrated area and circle image remain visible,
labelled **Last reading**. Camera video continues; new valid tracking updates the
result automatically. Saving a held result stores its original calibrated frame
and timestamp, not the current uncalibrated view. Changing to another input starts
a new measurement. The controls do not move when tracking changes.

Detached low-color components confined to the outer 1.5 mm of the opening are
removed as likely rim shadows. The filter preserves colored leaf tips, interior
dark tissue, holes, and separate fragments; it does not shrink the aperture.
Shadows connected to tissue or extending farther inward remain ambiguous and can
still be included. Thin gray tissue confined to that rim band may be excluded.

## V2 cutout geometry

- Template ID: `LI6800-6-V2`; OpenCV dictionary `DICT_4X4_50`; IDs 20-29.
- Opening radius: `sqrt(600 / pi)` mm; diameter 27.639531958 mm.
- Marker black square sides: 8 mm, with at least 3 mm clearance to the outer edge.
- Eight side markers have centers at x = -42, -18, +8, +24 mm and y = -25, +25 mm.
  Two rear-center markers are at (-42, 0) and (-24, 0) mm.
  The JSON coordinate system is x right, y down.
- Overall dimensions: 111 x 68 mm. The side wings extend 14 mm beyond V1's
  shoulder edge (x=17 to x=31) to fit the extra pair. Width stays at 68 mm.
- The front 90-degree arc retains the original 20 mm radius, including the tip at
  x = +20 mm. The side wings extend forward outside that protected central arc.
- The PDF and app load geometry from the same generator; the original supplied
  PDFs remain unchanged. The older drawing's slightly smaller circle is not used
  as the app's measurement target.

The front arc interpretation and source measurements are documented in
`docs/MEASUREMENT-DESIGN.md` and `docs/reference-geometry.json`.

Version 0.4.0 supports only V2 (ten 8 mm markers, IDs 20-29). V1 printouts
are no longer calibrated. Previously saved V1 records remain readable and shareable.
Four markers must surround the center, with markers on both sides and in front
of/behind the opening. The middle markers can replace obscured side markers;
a one-sided cluster is rejected. Print V2 for new measurements.

### 0.4 mm printed backing

`output/cad/LI6800-6cm2-mask-v2-0.4mm.step` is a single solid backing for attaching
the V2 paper printout. Overall dimensions are 111 x 68 x 0.4 mm, with the same
27.639531958 mm circular through-opening and flat top and bottom. Units are mm;
the bottom is at z=0. No adhesive or printer-shrinkage offset is included.

The outer profile is taken directly from `tools/generate-template.py:outline`,
including the fine segments at the front tip. Its vertices match both printed
PDF outlines within 0.000030 mm (PDF coordinate rounding). The opening is an
exact CAD circle corresponding to the PDF's nominal circle dimensions.
Reimporting the STEP confirmed a valid closed solid, dimensions, hole radius,
volume, and geometric equivalence. The audit and preview are in `output/cad/`.
Regenerate using the installed FreeCAD interpreter:

```powershell
& 'C:\Program Files\FreeCAD 1.1\bin\python.exe' tools/generate-mask-step.py
```

More > Mask STEP saves the same 0.4 mm backing file offline.

The original V1 STEP/PDF remain available under their existing filenames.
Their frozen generators are `tools/generate-template-v1.py` and
`tools/generate-mask-step-v1.py`.

## Build and verify

Read `BUILDING.md` and `AGENTS.md` for this PC's required build environment. Run
Gradle commands unsandboxed through the helper; do not use Windows UAC.

```powershell
.\tools\build.ps1 -Tasks @(':app:assembleRelease', ':app:testDebugUnitTest', ':app:lintRelease')
.\tools\build.ps1 -Tasks @(':app:assembleDebugAndroidTest')
```

The release APK is minified and resource-shrunk with R8, is non-debuggable, and
has no demo mode, demo images, or V1 template assets. Source APK:
`app/build/outputs/apk/release/app-release.apk`. It includes ARM64, ARMv7, x86_64,
and x86. Native OpenCV libraries account for most of the universal APK's size.

Release signing deliberately uses this project's preserved
`.android-user-home/debug.keystore` identity so it updates previous installations.
Despite the certificate's Android Debug name, this is the optimized **release**
build, not the debug variant. Preserve the ignored key for subsequent updates.
R8 mapping files are under `app/build/outputs/mapping/release/`.

For emulator tests, check ownership and run at most one emulator on this PC. The
direct instrumentation runner is useful because the installed AGP test task can
report BUILD SUCCESSFUL even when emulator installation fails:

```powershell
$adbTool = 'C:\Users\eliot\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adbTool -s emulator-5554 install --no-streaming -r app\build\outputs\apk\debug\app-debug.apk
& $adbTool -s emulator-5554 install --no-streaming -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adbTool -s emulator-5554 shell am instrument -w org.li6800.area.test/androidx.test.runner.AndroidJUnitRunner
```

Require the instrumentation output to say `OK (9 tests)`; the shell exit code
alone is not proof. The test uses the same native OpenCV implementation as the
app, including perspective correction, segmentation, missing-marker rejection,
occluded and one-sided layouts, V1 rejection, saved sensitivity, and
saved ZIP read-back. `tools/android-qa.py` drives UI controls by labels from
fresh UI trees and records screenshots under `.build-logs/qa/`.
Additional checks cover detached rim shadows, retained readings/reacquisition,
fixed on-screen control bounds, camera switching, and live updates in both display modes.

`tools/generate-template.py` regenerates the V2 PDF, `app/src/main/assets/template-v2.json`,
and synthetic test images under `app/src/androidTest/assets/`. Dependencies: reportlab, numpy, OpenCV 4.13, pypdfium2.
The local generator searches `tmp/python-modules` for the verified OpenCV wheel;
an ordinary Python environment can instead install `opencv-python-headless==4.13.0.92`.
The generator renders the actual PDF and verifies all 20 printed markers by decoding
their IDs. Source geometry inspection remains in `tools/inspect-reference-geometry.py`.

## Implementation and current boundaries

Kotlin/Compose, CameraX 1.6.1, OpenCV 4.13.0, AGP 9.4.0, Gradle 9.6.0, compile/target
SDK 37, min SDK 26. These versions deliberately reuse this PC's verified toolchain.

`MeasurementEngine.kt` owns marker detection, quality gates, image-to-mm homography,
the rectified 10 px/mm raster, and segmentation. `AreaRaster` integrates the selected
pixels only inside the aperture, with 4x4 subpixel circle-edge coverage. The current
engine does not estimate camera lens distortion: use a near-normal, centered view.
RANSAC and fit error can reject inconsistent points but cannot prove an undistorted,
flat physical scene. No old Petiole marker layout is assumed to be compatible.

Segmentation is automatic, adjustable color/background separation,
not a trained leaf recognition model. Wood grain and uncontrolled backgrounds are
outside the intended first-version setup. A correctly decoded print can still be
the wrong physical scale; verify the printed ruler independently.

All processing is offline; there is no INTERNET permission. Measurement archives
are in app-private storage and are excluded from backup/device transfer. Saved
files persist across app restarts but are removed by uninstalling or clearing app
data, so export important records. Sharing is user initiated. The app has no demo mode. Test fixtures are packaged only in the separate test APK.
Historical synthetic archives retain their label and export metadata.

See `docs/VALIDATION-v4.md` for current validation; earlier reports retain the earlier
releases' checks. Smaller markers have less pixel coverage at the same
distance; move closer when requested. Their physical reliability is still to be assessed.
