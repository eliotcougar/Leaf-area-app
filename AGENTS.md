# Local Android development instructions

This folder contains the LI-6800 Leaf Area Android app. Read `BUILDING.md` before building.
The two original PDFs are reference inputs; preserve them unchanged.
For measurement work, read `docs/MEASUREMENT-DESIGN.md`. The user specified a
6.000 cm² circular opening, true-scale printing without resizing, and at least
four markers around the opening for live calibration. Count only leaf tissue
inside that circle. Use automatic segmentation with backing/sensitivity controls;
the user explicitly does not want an add/erase editing workflow. Keep original PDF measurements separate from the intended
600 mm² template geometry; do not silently rescale measurements to hide a mismatch.

Current app is 0.4.4. The V2 PDF/STEP has ten 8 mm markers (IDs 20-29), two
rear-center markers, and forward side wings. The user removed V1 support in 0.4.0;
only V2 is calibrated. Never reuse IDs across scales. Keep demo workflows out of
production and synthetic images under androidTest/assets, not main/assets.
V2 backing dimensions are 111 x 68 x 0.4 mm. Preserve the central front R20 arc
and 6 cm² opening. Read `docs/VALIDATION-v044.md` for current checks.
The main measurement screen is fixed and non-scrollable, with two live display
modes: full-screen camera, and square circle preview with sensitivity below.
Both show the area at the top. Keep the same camera session across display and
tracking changes. The bottom row is Camera, a circular Save icon, and Calibration.
Save is available in both modes, with no duplicate Save action in the top overlay.
Marker loss retains the last valid area, source, and timestamp
with an explicit held-reading label. Remove detached low-color rim shadows without
shrinking the 6 cm² aperture; preserve interior tissue and colored leaf tips.
The top overlay shows cm² without a mm²/coverage row. Remember leaf sensitivity.
The camera button opens a list of exposed cameras/physical lenses and marks the
remembered selection. Do not silently fall back on binding or runtime failure.
Distinguish automatic logical cameras from fixed physical lenses. Keep failed
choices selectable for retry and ignore error callbacks from old sessions.
Changing cameras clears the previous calibration. Menu exports PDF and STEP offline.
CameraX 1.6.1 single-camera binding does not propagate selector physical IDs to
streams. Keep CameraChoice.configureOutputs applying Camera2Interop physical IDs
to BOTH Preview and ImageAnalysis builders. Verify resolved session/output IDs
and camera-service streams, not just the remembered picker key. Keep autofocus.
The flashlight toggle beside the camera picker uses the bound CameraX camera and
observed torch state. Disable it when flash is unavailable. Keep torch on across
display changes; turn it off when unbinding. Torch errors must not switch cameras.
Deliver the minified, resource-shrunk, non-debuggable release build using the
preserved signer. Verify the APK has no demo assets or V1 template data.

- Windows built-in curl fails HTTPS here. Use the curl-mingw executable at
  `C:\Users\eliot\AppData\Local\Microsoft\WinGet\Links\curl.exe` (verified LibreSSL build),
  not `C:\Windows\System32\curl.exe`. Do not disable TLS verification.
- Run every build, test, or Gradle command with Codex
  `sandbox_permissions: "require_escalated"`. Sandboxed subprocess/cache writes can
  produce misleading Access is denied failures. This means unsandboxed tool execution,
  not Windows UAC; do not use `RunAs`.
- Use `tools/build.ps1`, which sets JDK/SDK and cache variables in the same invocation.
  Environment changes from earlier shell tool calls do not persist. Check any existing
  `local.properties` for a stale SDK path; never commit it.
- Reuse the installed SDK/JDK and v2rayNG Gradle user cache. Keep project caches,
  Android user home, logs, and generated outputs in this project. Never clean the
  shared Gradle cache or run a shared `gradle --stop` to fix a local problem.
- The GitHub source repository is `eliotcougar/Leaf-area-app`. Recheck the branch
  and worktree before Git work; v2rayNG branches and upstream instructions do not
  apply to this independent app. Do not commit output artifacts, caches, logs,
  signing material, or local reference photos.
- `tools/smoke-project` is a build verification fixture, not the application.
  Its package ID and SDK minimum are provisional and must not define product requirements.
- Wait for quiet builds while their processes are still running; inspect the log
  and process ownership before interrupting.
- Never run more than one Android emulator at a time on this PC. Before starting
  one, check both `adb devices -l` and emulator/QEMU processes. Do not stop shared
  adb, emulator, Java, or Gradle processes without establishing ownership.
- Preserve the chosen signing key across app updates. Compare certificate SHA-256
  with an earlier APK before delivering an update; do not borrow v2rayNG signing keys.
- Report compilation, APK assembly, emulator behavior, and physical-device checks
  separately. A successful smoke build does not validate area measurements or camera behavior.

Borrowing source: `E:\programming\v2rayng\AGENTS.md` and
`.tools\build-v2rayng-release-verify.ps1`, inspected 2026-09-10.
