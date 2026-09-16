# LI-6800 product requirements

Read these requirements before changing measurements, templates, cameras, or
the measurement UI. They reflect the decisions through version 0.4.6; update
them when the product requirements change.

They take precedence over superseded proposals in
[MEASUREMENT-DESIGN.md](MEASUREMENT-DESIGN.md), including its proposed
mm²/coverage display and marker-loss presentation. The design document remains
the source for original-PDF findings and scientific validation requirements.
Release evidence belongs in versioned validation reports; the snapshot at this
revision is [VALIDATION-v046.md](VALIDATION-v046.md).

## Measurement and template

- Preserve `calibration_pad_pro.pdf` and `LI-6800-mask.pdf` unchanged. Print
  templates at Actual size / 100%, without resizing.
- Measure leaf tissue only within the 6.000 cm² (600 mm²) circular opening.
  Keep measured original-PDF geometry separate from that intended geometry;
  never silently rescale measurements to hide a mismatch.
- Require at least four markers around the opening for live calibration.
  Only V2 is calibrated; V1 support was removed in 0.4.0. Never reuse marker IDs
  across scales.
- The V2 PDF/STEP uses ten 8 mm markers, IDs 20-29, with two rear-center markers
  and forward side wings. Preserve the 111 × 68 × 0.4 mm backing, central front
  R20 arc, and 6 cm² opening.
- Use automatic segmentation with backing and sensitivity controls; the user
  excluded an add/erase workflow. Remember leaf sensitivity and selected matte
  across app restarts; default to white when no valid matte preference exists.
- Remove detached low-color rim shadows without shrinking the aperture.
  Preserve interior tissue and colored leaf tips.
- On marker loss, retain the last valid area, source, and timestamp with an
  explicit held-reading label; do not present a stale result as live.
- Keep demo workflows out of production and synthetic images in
  `androidTest/assets`, not `main/assets`. The menu exports PDF and STEP offline.

## Measurement screen

Keep the screen fixed and non-scrollable. Its two live display modes are a
full-screen camera and a square circle preview with sensitivity below. Both show
area at the top, in cm², without a mm²/coverage row.

The bottom row is Camera, a circular Save icon, and Calibration. Save is available
in both modes; do not duplicate it in the top overlay. Keep the same camera
session across display and tracking changes.

In Camera view, show the selected matte as a white or blue circle beside the
flashlight, matching its circle size. It is a status indicator; matte selection
remains in Calibration. Give the indicator an accessible selected-matte label.

While `InputMode.CAMERA` is active, keep the activity window awake in either
display mode, including marker loss. Clear `FLAG_KEEP_SCREEN_ON` when live mode
ends and on disposal; allow normal background timeout. Do not use a CPU wake lock.

## Camera selection and torch

The camera button lists exposed cameras/physical lenses and marks the remembered
selection. Distinguish automatic logical cameras from fixed physical lenses.
Keep failed choices selectable for retry, ignore error callbacks from old
sessions, and do not silently fall back on binding or runtime failure. Changing
cameras clears the previous calibration. Keep autofocus.

With the current CameraX 1.6.1 integration, preserve
`CameraChoice.configureOutputs` applying Camera2Interop physical IDs to both
Preview and ImageAnalysis builders. A selector's physical ID alone does not
establish the selected output. Verify resolved session/output IDs and
camera-service streams, not just the remembered picker key.

The flashlight toggle beside the picker uses the bound CameraX camera and
observed torch state. Disable it when flash is unavailable, keep torch on across
display changes, and turn it off when unbinding. Torch errors must not switch
cameras.
