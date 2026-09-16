# LI-6800 Leaf Area agent guide

## Read for the task

- Before changing measurement, templates, camera behavior, or UI, read
  [product requirements](docs/PRODUCT-REQUIREMENTS.md). They preserve current
  decisions, including keep-awake behavior, and supersede historical proposals.
- For measurement/geometry work, also read
  [MEASUREMENT-DESIGN.md](docs/MEASUREMENT-DESIGN.md) for source findings and
  scientific validation limits.
- Before builds, tests, signing, or installation, read [BUILDING.md](BUILDING.md).
  Find the app version in `app/build.gradle.kts` and relevant evidence through
  [README.md](README.md) and `docs/VALIDATION-*.md`; older results are historical.

## Essential constraints

Preserve both original PDFs. The target is a true-scale 6.000 cm² circular
opening with at least four surrounding markers for live calibration. Count
tissue only inside the circle. Keep source-PDF measurements distinct from the
intended 600 mm² geometry; do not rescale away a mismatch. Use automatic
segmentation with backing/sensitivity controls, without add/erase editing.

Only V2 is calibrated. Preserve its geometry and unique marker IDs across scales.
Keep demo workflows and synthetic images out of production.

## Local development

- This is the independent `eliotcougar/Leaf-area-app` repository. Before edits,
  confirm root, branch, worktrees, and dirty state; preserve unrelated work.
  Read any nested `AGENTS.md` along changed paths. v2rayNG branch/product
  instructions do not apply here.
- Use `tools/build.ps1`, with `require_escalated` for builds/tests/Gradle and no
  Windows UAC `RunAs`. Check existing `local.properties` for stale SDK paths;
  never commit it. Use the curl-mingw path in BUILDING.md for HTTPS; keep TLS
  verification enabled.
- Share the Gradle dependency cache only. Keep project caches, Android user home,
  logs, and outputs here; never clean the shared cache or run shared
  `gradle --stop` for a local problem.
- Run at most one emulator on this PC. Check ADB and emulator/QEMU processes
  before launch; establish ownership before stopping tools. Wait for quiet
  builds while running and account for task-owned tooling at completion.
- Deliver a minified, resource-shrunk, non-debuggable release with the preserved
  signer. Compare its certificate with the previous APK and verify no demo/V1
  assets. Keep artifacts, caches, logs, signing material, and reference photos
  out of commits.
- Report compilation, packaging, emulator, and physical-device evidence
  separately. The `tools/smoke-project` fixture has provisional package/SDK
  settings and validates tooling only, not product requirements or accuracy.
