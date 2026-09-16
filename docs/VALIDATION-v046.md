# Version 0.4.6 — remember and display the selected matte

Validated 2026-09-16.

The selected matte is saved in measurement preferences and restored on app
startup, with white as the fallback for missing or invalid values. The camera
screen displays a white or blue 40 dp circle beside the flashlight, inside a
48 dp layout box. It has an accessible selected-matte label; selection remains
in Calibration.

## Checks

- Signed release assembly and lint passed in 1m 40s; zero lint errors,
  30 warnings. Log: `.build-logs/gradle-20260916-124952-738-22916.log`.
- Installed the minified release on Pixel_6a API 33 x86_64. The initial matte
  without a saved preference was white.
- Selected blue in Calibration, returned to Camera, force-stopped the app and
  relaunched it: the matte and indicator remained blue. Repeated for white,
  with the same successful result.
- UI-tree checks confirmed the selected-matte labels and non-clickable indicator.
  Visual inspection of both restarted screenshots confirmed matching flashlight
  circle size and no clipping or overlap.
- Evidence: `.build-logs/qa/matte-v046.txt`, `v046-restarted-blue.png`,
  `v046-restarted-white.png`, and their XML snapshots.
- Runtime logs showed one camera binding per process, with no extra binding
  when changing matte or display mode, and no LeafArea or AndroidRuntime errors.
  Log: `.build-logs/qa/runtime-v046.txt`.
- No full regression suite was rerun or new repository tests added for this
  small settings/UI change. Physical-device behavior was not tested.

## Release artifact

`output/apk/LI6800-Area-0.4.6-release-universal.apk`, versionCode 10,
149,606,041 bytes; minified, resource-shrunk, non-debuggable, four ABIs.

- SHA-256: `edebd48ff66f0c7a24b837af2fe37c2c460ce590f2323bbb7094ba7b3361b40d`.
- Signature and 16 KB ZIP alignment passed. Signer matches 0.4.5:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- Assets are unchanged V2 PDF/STEP/JSON and baseline profiles. No demo or V1 assets.
- Delivered to the local Dropbox root with matching SHA-256; cloud sync unverified.
