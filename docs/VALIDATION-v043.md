# Version 0.4.3 — physical lens routing

Validated 2026-09-10. The user reported a Pixel 9 Pro briefly changing lenses,
then snapping back while the selected picker entry remained unchanged.

## Cause and correction

The app set `CameraSelector.physicalCameraId`, but CameraX 1.6.1's
`LifecycleCameraProviderImpl.bindToLifecycleInternal` does not copy that ID to
the use cases in the single-camera binding path. Its multi-camera path does.
The saved choice therefore did not ensure physical routing of preview/analysis.

`CameraChoice.configureOutputs` now applies `Camera2Interop.Extender` physical
IDs to both Preview and ImageAnalysis before building them. Automatic entries
leave the ID unset. Autofocus remains enabled. The selected key, failure handling,
torch behavior, and calibration reset rules are preserved.

Inspected the exact pinned library's
[1.6.1 sources](https://dl.google.com/dl/android/maven2/androidx/camera/camera-lifecycle/1.6.1/camera-lifecycle-1.6.1-sources.jar)
and the documented
[Camera2 physical output API](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender#setPhysicalCameraId(java.lang.String)).
Source copies and inspection extracts are ignored under `.build-logs/`.

## Checks

- Release/debug assembly, test APK assembly, two JVM tests and release lint passed.
  Final application build: `.build-logs/gradle-20260910-194246-609-15292.log`,
  1m 12s. Lint: zero errors, 30 warnings. The initial lint run required replacing
  a Kotlin `check` with an explicit API-level guard recognized by lint.
- Existing 10 instrumentation tests passed on Pixel_6a API 33 x86_64. The new
  physical-output test initially read only per-output IDs. CameraX's graph provider
  gives the session-wide interop ID precedence, so the test was corrected to
  inspect the resolved ID using the same rule.
- Corrected physical binding test: **OK (1 test)**, 2.402s, using the final debug
  APK. It binds physical 3, physical 4 and automatic, requires three live analysis
  frames from each, and checks both bound streams' resolved physical IDs:
  `1/3 → [3,3]`, `1/4 → [4,4]`, `1 → [null,null]`.
  Logs: `.build-logs/qa/physical-binding-v043.txt` and `physical-outputs-v043.txt`.
- Signed minified release installed. Selected Front lens 3 through the picker.
  Android `dumpsys media.camera` showed **Physical camera id: 3** on both output
  streams, with 537 frames produced on each, and active physical ID `3`.
- Camera/circle mode changes did not cause a rebind. Force-stop/relaunch restored
  `1/3`; camera service again reported physical ID 3 on both streams, with 1,352
  frames each. Logs contained only intended bindings and no LeafArea/runtime errors.
  Evidence: `.build-logs/qa/v043-release-camera.txt`,
  `v043-release-restarted-camera.txt`, `v043-fixed-lens.xml`, `runtime-v043.txt`.
- The task-owned emulator was shut down after verification.

The Pixel 9 Pro's optical close-focus behavior was not tested on physical hardware.
The correction is verified through the emulator camera service and live frames;
this does not establish physical-phone autofocus performance or measurement accuracy.

## Delivered release

`output/apk/LI6800-Area-0.4.3-release-universal.apk`, versionCode 7,
149,605,069 bytes. R8 minified, resource-shrunk, non-debuggable, four ABIs.

- SHA-256: `bec95c056055464aa152d94205c594eb228390592ffa6f7a2a54381c81c3a2ca`.
- Signature and 16 KB ZIP alignment checks passed. Certificate matches 0.4.2:
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
- Assets contain unchanged V2 PDF/STEP/JSON and baseline profiles; no demo or V1
  assets. Reference PDFs and mask geometry were not modified.
- Copied to `C:\Users\eliot\Dropbox\LI6800-Area-0.4.3-release-universal.apk`;
  source and copied hashes match. Cloud synchronization was not checked.
