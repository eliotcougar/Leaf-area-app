# Android build environment on this PC

The root contains the LI-6800 Leaf Area app. Use `tools/build.ps1` to
build it; see `README.md` and `docs/VALIDATION-v044.md` for current behavior and checks.
The preparation results below remain a historical record of toolchain setup.

Inspected on 2026-09-10 at `E:\programming\li6800-area-app`.
The folder initially contained only the two reference PDFs and had no Git repository,
Android source, Gradle files, or app specification.

## Reference material

Both files have one A4 portrait page (595.28 x 841.89 PDF points).

| Input | Observed contents | SHA-256 |
| --- | --- | --- |
| `calibration_pad_pro.pdf` | Four PETIOLE PRO checkerboard calibration pads, numbers 5-8, with square markers and dashed cut outlines. | `9058EB1641443EC6F49095DF27F2E80B6DDB1639FD3E49665A59577CAB9C2116` |
| `LI-6800-mask.pdf` | Four mask outlines with circular openings near rounded ends. No extractable text or printed dimensions. | `8DBF324A2F4886218DC7214D2A3B5B6DE25125E46D51D00323A2044D38FB05D7` |

The user subsequently confirmed that the PDFs are true scale and must be printed
at Actual size / 100%, with no printer scaling. The application target is a 6 cm²
circle. Direct vector inspection found a small discrepancy in the original mask
drawing; see `docs/MEASUREMENT-DESIGN.md` and `docs/reference-geometry.json` for the
separate source dimensions and intended target. The original PDFs are unchanged.
The added real-world photo and proposed marker/measurement workflow are documented
in that design note. Legacy marker families/IDs have not been decoded.

## Installed tools and borrowed inputs

| Item | Verified local location/version |
| --- | --- |
| Java | `C:\Program Files\Android\Android Studio\jbr`, JetBrains JDK 21.0.10 |
| Android SDK | `C:\Users\eliot\AppData\Local\Android\Sdk` |
| SDK platforms | `platforms\android-36.1`, `platforms\android-37.0` (37 metadata has PreviewSdkInt=0) |
| Build tools | `build-tools\36.0.0`, `36.1.0`, `37.0.0` |
| ADB | SDK `platform-tools\adb.exe` |
| Emulator | SDK `emulator\emulator.exe`; no emulator launched for preparation |
| HTTPS curl | `C:\Users\eliot\AppData\Local\Microsoft\WinGet\Links\curl.exe`, curl-mingw 8.21.0 with LibreSSL 4.3.2 |
| Shared Gradle cache | `E:\programming\v2rayng\.gradle-user-home`, with Gradle 9.5.1 and 9.6.0 distributions |

The SDK's `cmdline-tools` directory is absent. Already installed SDK packages can
still build apps. If additional packages are required, use Android Studio's SDK
Manager or explicitly install command-line tools; do not assume `sdkmanager` is on PATH.

The Gradle wrapper was copied byte-for-byte from
`E:\programming\v2rayng\.worktrees\agp-modernization\V2rayNG`
(inspected HEAD `5f4949c56`). It pins Gradle 9.6.0 with its existing distribution
SHA-256. The smoke fixture uses the locally inspected AGP 9.4.0 / Kotlin 2.4.10
combination, compile/target SDK 37, build tools 37.0.0, and Java bytecode target 17.
These are local build inputs, not a claim about the latest available releases.

AGP in this fixture supplies built-in Kotlin. Its buildscript overrides the Kotlin
plugin dependency to the inspected 2.4.10 version. Do not additionally apply
`org.jetbrains.kotlin.android`. The root app uses the matching Compose compiler
plugin 2.4.10; the original smoke fixture does not use Compose.

Only the standard wrapper files were copied. The SDK/JDK and shared Gradle user
cache are referenced in place. Project caches use separate subdirectories here,
keyed by project path. Android user home and debug signing material remain local.
No v2rayNG VPN code, Go/NDK tooling, native AARs, product flavors, or signing keys
are needed by this Kotlin build fixture.

## Commands

Run from this folder in PowerShell. In Codex, every Gradle/build/test invocation
must use `sandbox_permissions: "require_escalated"`; do not use Windows UAC `RunAs`.
The script itself does not request elevation.

```powershell
# Compile Kotlin, process Android resources, dex, and package a debug smoke APK.
.\tools\smoke-build.ps1 -Offline

# Allow dependency downloads if an offline cache entry is unavailable.
.\tools\smoke-build.ps1

# Build the actual app:
.\tools\build.ps1
.\tools\build.ps1 -Tasks @(':app:assembleDebug', ':app:lintDebug')

# Signed, minified app release (same preserved signing identity):
.\tools\build.ps1 -Tasks @(':app:assembleRelease', ':app:lintRelease')
```

The smoke project lives under `tools\smoke-project`; its artifact is
`tools\smoke-project\app\build\outputs\apk\debug\app-debug.apk`.
It uses a separate `local.li6800.toolchainsmoke` package and provisional minSdk 24.
It is not the LI-6800 app. Running `tools/build.ps1` against the root builds
`app/build/outputs/apk/debug/app-debug.apk`, package `org.li6800.area`.

`tools/build.ps1` accepts `-ProjectPath`, `-JavaHome`, `-AndroidSdk`, and
`-GradleUserHome` overrides. It sets JAVA_HOME, ANDROID_HOME, ANDROID_SDK_ROOT,
ANDROID_USER_HOME, GRADLE_USER_HOME, and PATH together, restores them afterward,
uses `--no-daemon` and `--project-cache-dir`, writes timestamped logs under
`.build-logs`, and throws on Gradle failure. `--no-daemon` can still create a
single-use Gradle daemon that exits when the build finishes.

Use PowerShell array syntax for multiple tasks. Do not concatenate optional flags
into a command string or pass an empty ABI flag: this previously produced a stray
`-` task in another project on this PC.

## Machine-specific failure prevention

- A fresh project may have no ignored `local.properties`. Set both SDK environment
  variables in the same shell invocation as Gradle. A stale `sdk.dir` in an existing
  `local.properties` overrides them; inspect it before building.
- Sandbox cache/temp/subprocess failures can look like broken dependencies or
  compiler errors. Use the required unsandboxed execution from the beginning.
- Use the explicit curl-mingw path for HTTPS downloads; the Windows system curl is
  known to fail here. The installed alternative currently identifies LibreSSL,
  rather than Schannel. Never use `--insecure` as a workaround.
- Share dependency/distribution caches, not project state. Do not delete v2rayNG
  caches, modify its worktrees, or stop shared Gradle daemons during troubleshooting.
- Give builds time; inspect the current log and process command line before
  assuming a quiet process is stuck.
- Run at most one emulator on this PC. Inspect ADB devices and emulator/QEMU
  processes before launch, and establish ownership before stopping any existing one.
- The helper creates a project-local Android user home for future debug signing.
  Preserve the resulting key. For an update APK, compare signer certificate SHA-256
  with the previous artifact/device. Never silently switch to a v2rayNG or user key.

## Verification boundary

See the preparation result below for actual command outcomes. The smoke fixture
only checks the Android build toolchain. Runtime, camera permissions, calibration,
measurement accuracy, Compose dependencies, release signing, and device compatibility
are separate checks. Current app verification is recorded in `docs/VALIDATION-v044.md`.

Borrowed instruction sources: `E:\programming\v2rayng\AGENTS.md`,
`E:\programming\v2rayng\.tools\build-v2rayng-release-verify.ps1`, and the inspected
AGP modernization worktree's wrapper and Gradle configuration.

## Preparation result (2026-09-10)

- `tools/smoke-build.ps1 -Offline`: **passed**, 39 seconds, 34 tasks executed.
  Kotlin compilation, Android resource processing, dexing, and debug APK packaging
  succeeded without dependency downloads.
- Build log: `.build-logs\gradle-20260910-133039-740-5632.log`.
- Smoke APK: 933,835 bytes; SHA-256
  `595E7CBEDE38C37771CAE2DF938ED27A4A33CC92F8B36116C6D93E998524A9F6`.
- `apksigner verify --print-certs`: passed. Debug certificate SHA-256
  `597b4d2452fab4dec76811d4a4c6203ddd732ea8605ad05a1ff54581209766a1`.
  The new key is `.android-user-home\debug.keystore`; keep it ignored and preserve it.
- Both PowerShell scripts parsed successfully. All four copied wrapper files match
  the source SHA-256. Both PDF hashes still match their initial values.
- Final host process inspection found no Java/Gradle, Git, or emulator/QEMU process.
  The ADB device list was empty. The ADB server started by the inventory check is
  intentionally left available on port 5037; no APK was installed.
- **Not run during preparation:** emulator/physical-device execution, Compose
  compilation, camera or measurement validation, and production release signing.
