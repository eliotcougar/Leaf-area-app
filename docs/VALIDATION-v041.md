# Version 0.4.1 — explicit camera selection

Validated 2026-09-10 in `E:\programming\li6800-area-app`.

## Behavior and cause addressed

The 0.4.0 binding-error handler removed the failed choice and automatically bound
another camera. A missing selected ID also resolved to the first camera. Both
paths could undo an explicit selection. These paths are removed: failure retains
the requested key, reports Camera unavailable, and leaves the picker accessible.
The user's exact hardware failure was not reproduced on their physical phone.

The button now opens a radio-button list with facing, camera/lens ID, focal length
when available, and an automatic label for logical multi-lens cameras. Physical
lenses remain listed even if their ID is also exposed as a direct logical camera;
those selectors have different behavior. The chosen key is saved in private
preferences and used after activity recreation and process restarts.

CameraX state errors are handled without fallback, and obsolete session callbacks
cannot stop a newer camera. A successful switch starts fresh calibration. Camera
and Circle & sensitivity display changes retain the selected camera and session.

## Checks

- Final build: `tools/build.ps1` with assembleRelease, assembleDebug,
  assembleDebugAndroidTest, testDebugUnitTest, and lintRelease. Passed in 1m 10s.
  Log: `.build-logs/gradle-20260910-191406-916-25084.log`.
- JVM: two tests passed. Release lint: zero errors, 30 warnings.
- Direct instrumentation: **OK (10 tests)** on Pixel_6a API 33 x86_64.
  `.build-logs/qa/instrumentation-v041.txt`.
- Regression coverage includes opening the list without switching, selected radio
  state, explicit front selection, display changes, activity recreation, missing
  camera failure without fallback, recovery through the same picker, preference
  restoration, and ignoring old-session errors. Existing measurement tests pass.
- One initial UI run was interrupted by the emulator's unrelated wireless-debugging
  system dialog. It was canceled, then the complete suite passed.
- Installed the signed minified release over the previous package. The picker
  showed rear camera 0, automatic front camera 1, and physical front lenses 3/4.
- Selected front lens 3 (key `1/3`), force-stopped and relaunched the release.
  Logs confirm `Bound selected camera 1/3` in both processes, and the refreshed UI
  tree confirms Front lens 3 remained checked. No unexpected switch was observed.
- Final release LeafArea/AndroidRuntime error log is empty. This is separate from
  the deliberate missing-camera error exercised by the debug instrumentation test.
- Evidence: `.build-logs/qa/v041-release-picker.png`, `v041-release-restored.xml`,
  `v041-release-binding.txt`, and `runtime-v041.txt`.

## Release artifact

`output/apk/LI6800-Area-0.4.1-release-universal.apk`, 149,585,897 bytes.

- SHA-256: `eecac05776cfed4c5d401039cfad517b9fdaa54640d92d2bc474c99b175b83df`.
- Signature and 16 KB ZIP alignment verification passed. Certificate unchanged:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- Minified/resource-shrunk, non-debuggable release, versionCode 5; ARM64, ARMv7,
  x86_64 and x86. The preserved certificate is named Android Debug, as in V4.
- Asset audit confirms only V2 JSON/PDF/STEP and generated baseline profiles.
  No demo assets or V1 template data. Generated deliverables remain ignored by Git.
- Copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.4.1-release-universal.apk`;
  copied SHA-256 verified. Cloud sync was not checked.

## Limits

The emulator exposes a simulated logical/physical multi-camera configuration;
real phone lens availability, HAL behavior, and optical performance need physical
validation. Entries marked automatic still intentionally allow device lens choice.
An explicit physical selector requests that lens; unsupported configurations are
reported rather than replaced. Previous segmentation/accuracy limits still apply.

API references: [physical camera selection](https://developer.android.com/reference/androidx/camera/core/CameraSelector.Builder#setPhysicalCameraId(java.lang.String))
and [physical camera enumeration](https://developer.android.com/reference/androidx/camera/core/CameraInfo#getPhysicalCameraInfos()).
