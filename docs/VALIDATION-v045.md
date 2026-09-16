# Version 0.4.5 — keep live measurements awake

Validated 2026-09-11.

The activity sets `FLAG_KEEP_SCREEN_ON` while `InputMode.CAMERA` is active,
independently of Camera/Calibration display mode and marker tracking. A Compose
disposal effect clears the flag when live capture ends or the screen is disposed.
Android stops honoring the flag when the activity is backgrounded. No permission
or CPU wake lock is needed.

## Checks

- Final signed release assembly and lint passed in 1m 14s; zero lint errors,
  30 warnings. Log: `.build-logs/gradle-20260911-130139-803-16732.log`.
- Installed the minified release on Pixel_6a API 33 x86_64, with screen timeout
  temporarily set to 15 seconds and charging stay-awake disabled.
- Camera and Calibration remained awake without touches after 20 seconds each,
  while searching for markers. Window service identified this app as the window
  keeping the display awake.
- Open photo stops live capture; canceling returns to the stopped screen with
  the Live button visible. Its keep-awake window cleared. The emulator was still
  awake at the first 20-second check, then a longer check confirmed Asleep with
  no keep-awake window.
- Resuming Live restored the keep-awake window. Background timeout was checked
  separately. Original emulator timeout/charging settings were restored.
- Evidence: `.build-logs/qa/v045-final-camera.txt`, `v045-final-calibration.txt`,
  `v045-final-stopped-full-power.txt`, and `v045-final-background.txt`.
- An initial view-property implementation failed the stopped-mode cleanup check
  and was replaced with explicit window flag handling before final handoff.
  The final APK checksum below identifies the corrected build.
- No new repository tests were added or full regression suite rerun for this
  small patch. Physical-device behavior was not tested.

Reference: [Android screen-awake behavior](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).

## Release artifact

`output/apk/LI6800-Area-0.4.5-release-universal.apk`, versionCode 9,
149,605,629 bytes; minified, resource-shrunk, non-debuggable, four ABIs.

- SHA-256: `d5174ed4a26ef9fd777df7db4c3f0bc5e8b829e8798d96754e883a08c7686f6e`.
- Signature and 16 KB ZIP alignment passed. Signer matches 0.4.4:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- Assets are unchanged V2 PDF/STEP/JSON and baseline profiles. No demo or V1 assets.
- Delivered to the local Dropbox root with matching SHA-256; cloud sync unverified.
