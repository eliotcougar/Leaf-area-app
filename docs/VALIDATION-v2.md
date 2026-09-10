# V2 marker layout / app 0.2.0 - 2026-09-10

The user's field-use screenshot showed the V1 app reporting 3.357 cm² with all
six markers detected and 0.08 mm marker fit residual. This demonstrates one
real-world use case; it supplies no independent ground-truth leaf area.

## Geometry changes

- Ten 8 mm ArUco `DICT_4X4_50` markers, IDs 20-29, replace the six 10 mm V1 markers.
- Eight side markers: x = -42, -18, +8, +24 mm, y = -25 and +25 mm.
- Two markers behind the opening: (-42, 0) and (-24, 0) mm.
- Side wings extend from x=17 to x=31 mm, an increase of 14 mm. Overall dimensions
  are 111 x 68 mm. The central front 90-degree R20 arc, its tip at x=20, and
  the 6 cm² opening remain unchanged. STEP backing thickness stays 0.4 mm.
- Labels and a HOLD HERE area occupy the rear section, away from the opening.

Four or more detected markers are required. Their centers must surround the
opening center, with markers above, below, ahead of, and behind it. A rear-center
marker can replace an obscured side marker. The existing 24-pixel minimum marker
edge, 110-pixel minimum opening span, and fit/sharpness gates are retained.
Smaller markers reduce pixel coverage at a given distance; the app may request
moving closer. Their physical reliability is not yet established.

V1 and V2 use disjoint IDs. The app automatically selects the correct dimensions,
rejects mixed V1/V2 frames, and carries the actual template with each measurement
through resegmentation and archive saving. The status uses the correct total of
6 or 10 markers. The Print cutout action exports V2. V2 prints require app 0.2.0
or newer; V1 prints remain usable. All original PDFs, V1 artifacts, and the V1
app APK are preserved.

## Build and Android checks

`tools/build.ps1` ran assembleDebug, assembleDebugAndroidTest, testDebugUnitTest,
and lintDebug successfully in 1m 2s. Log:
`.build-logs/gradle-20260910-155331-528-20628.log`.
Two JVM tests passed. Lint reported 0 errors, 11 warnings: five version suggestions,
four KTX style suggestions, the existing backup configuration suggestion, and a
plural suggestion for the marker total (the total is always 6 or 10).

An in-place update installed on the single task-owned Pixel_6a API33 x86_64 AVD.
Direct instrumentation returned `OK (5 tests)`:

1. Actual native OpenCV detection, calibration, and segmentation on V2 fixtures.
2. Three visible markers invalidate the result.
3. Four markers (new front pair plus rear-center pair) and six remaining markers
   after rear occlusion calibrate; a four-marker one-sided row is rejected.
4. V1 keeps its 10 mm marker scale, even after the engine processes V2; its saved
   JSON and template archive still identify V1. Mixed V1/V2 images are rejected.
5. V2 saved ZIPs retain source, mask, measured area, template ID, and safe CSV cells.

Evidence: `.build-logs/qa/instrumentation-v2.txt` and `measurement-v2.txt`.

| Synthetic V2 fixture | Measured mm² |
| --- | ---: |
| Empty | 0 |
| Half | 299.55625 |
| Full | 599.1275 |
| Leaf with hole | 415.3575 |
| Perspective half | 299.4125 |
| Yellow full coverage | 599.6275 |

The hole fixture reference is 415.5 mm²; half/full targets are 300/600 mm².
All six above detect ten markers. Fit residuals were 0.006705 mm on flat fixtures
and 0.016799 mm on the perspective fixture. These are software checks and marker
fit residuals, not a physical accuracy specification.

The updated UI displayed the hole sample as 4.154 cm² and `10 of 10 markers`.
Screenshots and fresh UI trees are in `.build-logs/qa/v2-*`. An unrelated emulator
wireless-debugging prompt was canceled before UI testing; wireless access was
not enabled.

The Print cutout action saved V2 through Android's Create Document picker. The
7248-byte exported PDF matches the app asset and desktop PDF byte for byte.
Final app/runtime error log was empty. The task-owned emulator was shut down
after QA, leaving the shared ADB server available.

## PDF and STEP verification

The rendered A4 PDF decoded all 20 markers (ten on each of two identical cutouts).
Visual layout inspection passed. The PDF and STEP use the same source outline.
Independent PDF-vector comparison found under 0.000030 mm maximum vertex
deviation, due to decimal rounding in PDF coordinates. The STEP was reimported:
one valid closed solid, 111 x 68 x 0.4 mm, exact circular through-hole diameter
27.639531957706 mm, volume 2589.6510900373164 mm³. Boolean comparisons against
the pre-export solid found no differing volume above the 1e-7 mm³ threshold.

Audit: `output/cad/mask-step-v2-verification.json`. CAD and PDF previews were
visually checked. Print at Actual size / 100%, and independently measure the
50 mm ruler. The physical print, instrument clearance, and new backing fit still
need to be checked on the user's equipment.

## Artifacts

- APK: `output/apk/LI6800-Area-0.2.0-debug-universal.apk`, 161607202 bytes.
  SHA-256: `ea489846d7cf169a34520554ca19f5e9d1570a752953defd026c0ba8f2423207`.
- PDF: `output/pdf/LI6800-6cm2-marker-cutout-v2.pdf`.
  SHA-256: `461b4167ae184221be85c148073b5b7788857da28cec530d9340e2755a636ce0`.
- STEP: `output/cad/LI6800-6cm2-mask-v2-0.4mm.step`.
  SHA-256: `9b8c95f6ef25b24693bc6c5f3106db76846f8ab23c2f71950c35149c41085bc8`.

APK signature and 16 KB ZIP alignment verification passed. Signer certificate
SHA-256 is unchanged:
`597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
This is the project's debug-signed evaluation build, with all four original ABIs.
The APK was copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.2.0-debug-universal.apk`;
source and destination SHA-256 match. Dropbox cloud sync was not independently checked.
Physical V2 accuracy, continuous tracking of a real V2 print, and performance on
the user's phone remain unverified. No segmentation or manual-edit workflow was
added or changed.
