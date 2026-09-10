# Version 0.4.4 — bottom controls

Validated 2026-09-10.

The circle/sensitivity mode is now labeled **Calibration**, including help and
the public README. Save moved from the top overlay to a 52 dp circular icon
between the equally sized Camera and Calibration buttons. It remains available
in both modes and preserves the existing save dialog and valid-reading checks.

## Verification

- Release/debug/test APK assembly and release lint passed (1m 14s).
  Log: `.build-logs/gradle-20260910-195951-661-19060.log`.
  Lint: zero errors, 30 warnings.
- Existing `MeasurementScreenTest` passed on Pixel_6a API 33 x86_64 (23.37s).
  This covers fixed layout, held readings, both live modes and camera selection.
  No new tests were added for this layout change; the full suite was not rerun.
- Visually inspected screenshots with a valid held reading in both modes. Save
  is centered between the two mode buttons; Calibration fits without clipping.
  Screenshots: `.build-logs/qa/v044-camera.png` and `v044-circle-sensitivity.png`.
- Installed and launched the signed minified release; its UI tree confirms the
  new bottom row and no top Save action. No LeafArea/runtime errors appeared.
  Evidence: `.build-logs/qa/v044-release.xml` and `runtime-v044.txt`.
- The emulator was shut down afterward. Physical-device checks were not run.

## Release

`output/apk/LI6800-Area-0.4.4-release-universal.apk`, versionCode 8,
149,605,629 bytes. Minified, resource-shrunk, non-debuggable; four ABIs.
Signature and 16 KB ZIP alignment verification passed.

- SHA-256: `8b5256dfcdb918057ff20f58f9be63d4d0a9728b34f8953d9e7b020557f7bd1f`.
- Signer matches 0.4.3:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- Assets contain only unchanged V2 PDF/STEP/JSON and baseline profiles, with no
  demo or V1 assets.
- Copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.4.4-release-universal.apk`;
  source and copy hashes match. Cloud sync was not checked.
