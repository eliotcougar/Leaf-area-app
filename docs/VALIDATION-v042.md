# Version 0.4.2 — flashlight control

Validated 2026-09-10. Camera-selection and measurement behavior from
[0.4.1](VALIDATION-v041.md) is retained.

## Behavior

- A flashlight toggle sits immediately beside the camera picker in Camera view.
  Its highlighted state follows CameraX's reported torch state.
- The button is disabled when the selected camera has no flash or while a torch
  request is pending. A failed request reports a short message without changing
  the selected camera or resetting calibration.
- Circle preview retains the camera and light. Unbinding for a camera switch,
  freeze, or leaving live capture turns the light off. Torch state is not saved.
- Observers are removed on disposal; completions from old controls are ignored.

## Verification

- Release/debug APK assembly, Android test APK assembly, two JVM tests, and
  release lint passed. Lint: zero errors, 30 warnings.
  Build log: `.build-logs/gradle-20260910-192722-077-8068.log` (1m 24s).
- Pixel_6a API 33 x86_64 instrumentation: **OK (10 tests)**.
  The rear camera has no flash; its disabled toggle was checked along with
  existing calibration, layout, camera selection and persistence regressions.
  Log: `.build-logs/qa/instrumentation-v042.txt`.
- Installed and exercised the signed minified release. The emulator's front
  camera supports a simulated torch: the toggle changed to ON, stayed ON through
  Circle & sensitivity and back, and changed to OFF on request.
- Switched from front camera with torch ON to rear camera (disabled toggle),
  then back to front: torch was OFF. Binding logs show only the initial binding
  and the two deliberate camera switches, with no rebind from torch/display changes.
- Inspected release screenshot: flashlight and lens picker are adjacent below
  the top overlay, with a yellow background for torch ON.
  Evidence: `.build-logs/qa/v042-flashlight-on.png`, `v042-flashlight-off.xml`,
  `v042-flashlight-retained.xml`, `v042-switch-resets-torch.xml`, and
  `torch-binding-v042.txt`.
- Release LeafArea/AndroidRuntime error-log query returned no entries. The
  task-owned emulator was shut down after validation.

Physical illumination and phone-specific flash behavior were not tested. Freeze
shutdown uses the same unbind cleanup; it was not separately exercised with the
simulated torch ON. Torch-request failure handling was reviewed, not fault-injected.

## Delivered APK

`output/apk/LI6800-Area-0.4.2-release-universal.apk`, versionCode 6,
149,605,069 bytes. R8 minified, resource-shrunk, non-debuggable; ARM64, ARMv7,
x86 and x86_64. Signature and 16 KB ZIP alignment checks passed.

- SHA-256: `f447e5d60850a27a60389034a002f2081eebe17085c66f11948da769bc3be398`.
- Signer matches 0.4.1:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- Assets contain only the unchanged V2 PDF/STEP/JSON and generated baseline
  profiles; no demo assets or V1 template data.
- Copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.4.2-release-universal.apk`;
  source/copy SHA-256 verified. Cloud sync was not checked.
