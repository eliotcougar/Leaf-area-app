# LI-6800 leaf area measurement: research and design

Research date: 2026-09-10. This document separates manufacturer information,
observed local inputs, and proposed implementation. No measurement app or new
printable template was created during this research.

## Measurement contract

The app measures the **one-sided projected area of leaf tissue within a circular
6.000 cm² opening**, equivalent to 600 mm². Leaf portions outside the opening,
paper, background, and holes where tissue is absent contribute zero. Yellow,
brown, or necrotic tissue still counts as leaf tissue; this is not a greenness meter.
For a flat leaf this is the planar area. It is not a reconstruction of the surface
area of a curled leaf, and should not double the result for the two leaf surfaces.

The user confirmed true-scale PDF printing at 100%, with no printer resizing, and
clarified that the intended opening is **6 cm²**. The new mask definition should
therefore use radius `sqrt(600/pi) = 13.819765979 mm`, diameter `27.639531958 mm`.
The target is not 5.94 cm².

## LI-COR fluorometer head

The relevant attachment is the **6800-01A Multiphase Flash Fluorometer**, a chamber
and light source for gas exchange and chlorophyll-a fluorescence over the same
leaf area. It uses PAM measurement and supports multiphase saturation flashes.
Its interchangeable circular apertures are nominally 6 cm² and 2 cm².

| Manufacturer specification | Value |
| --- | --- |
| Measuring/red light peak | 625 nm |
| Blue light peak | 475 nm |
| Far-red peak | 735 nm |
| Total actinic light, at 25 °C | 0-3,000 µmol m⁻² s⁻¹ |
| Saturating flash, at 25 °C | Up to 16,000 µmol m⁻² s⁻¹ |
| Attachment size and mass | 16.6 × 11.5 × 13.6 cm; 0.86 kg |

Source: [LI-COR fluorometer product specifications](https://www.licor.com/products/photosynthesis/LI-6800/fluorometer).

Upper and lower apertures must match. The instrument recognizes the chamber but
does not identify the installed aperture automatically: the operator selects the
aperture in Chamber Setup. Changing only leaf area is not a substitute for choosing
the correct hardware aperture. [LI-COR installation instructions](https://bio.licor.com/env/support/LI-6800/topics/chamber-fluorometer-installation.html).

LI-COR lists 6800-50 for the 6 cm² aperture and 6800-51 for 2 cm², with two apertures
required for operation. These identify hardware sizes, not dimensions inferred
from a photograph. [LI-COR ordering information](https://www.licor.com/products/photosynthesis/LI-6800/ordering).

Under **Constants > Gas Exchange**, `S` is the area of the chamber occupied by the
leaf, in cm². LI-COR explicitly uses this area when calculating assimilation and
provides an onboard area calculator. Our app should supply this occupied area,
which can be smaller than 6 cm², while the hardware aperture setting stays at
6 cm². [LI-COR leaf chamber controls](https://www.licor.com/support/LI-6800/topics/leaf-measurement-controls.html).

Design consequence: report mm², cm², and percentage of the opening. The first
version can present a value to enter as `S`; connection to the LI-6800 is a separate
feature. Photograph the same leaf region enclosed during gas exchange. Use physical
registration marks or a documented placement procedure so repositioning the mask
does not select a different patch. Paper measurement masks should be treated as
external imaging aids, not assumed to be validated chamber inserts.

## Local PDF and photograph findings

The supplied `2026-09-10 12.40.27.jpg` shows a white mask with a manually cut circular
opening beside a separate PETIOLE PRO pad No. 5, on a wood-grain surface. There is
no leaf in the photograph and there are no markers on the mask. The image helps
establish physical layout; it provides no evidence of leaf segmentation accuracy.

Direct vector inspection of `LI-6800-mask.pdf` found four approximately 100 × 40 mm
outlines, each with a circle bounded by approximately 27.4997 × 27.5001 mm. The
ideal ellipse area computed from those bounds is 5.93953 cm². This is a finding
about the source drawing, not a measurement of the LI-COR hardware. LI-COR's
nominal 6 cm² specification does not establish the precise bore diameter.

The original PDF remains unchanged. A future corrected template should explicitly
encode the user's 600 mm² target and known marker coordinates. Do not silently
scale a 27.5 mm opening to 6 cm² in the software: that would also rescale the leaf
measurement. A full opening on the corrected template should return 600 mm².

`calibration_pad_pro.pdf` contains four pads (5-8). Pad No. 5 has 5 mm checkerboard
squares in the PDF and eight 5 × 5 mm embedded image tiles at marker locations.
The exact legacy marker family, IDs, and black-border dimensions were not decoded;
the embedded image bounds alone do not identify the marker dictionary.

The reproducible mask extraction is in `tools/inspect-reference-geometry.py`;
`docs/reference-geometry.json` records source hashes, original vector dimensions,
and the separate 6 cm² application target. The original files were only read.

## Libraries and precedent

| Candidate | Role and assessment |
| --- | --- |
| CameraX | Android camera preview and analysis frames; use lifecycle binding and latest-frame backpressure. |
| OpenCV ArUco + a custom Board | Preferred marker detector: identified corners and a board of known corner coordinates. |
| OpenCV homography and image processing | Rectify the paper plane, segment tissue, and intersect it with the circle. |
| AprilTag 3 | Viable alternate fiducial system if trials favor it; direct native integration adds work. |
| ARCore Augmented Images | Designed for tracking reference images in AR. It is not needed for this planar measurement pipeline. |

CameraX provides CPU-accessible analysis frames and `STRATEGY_KEEP_ONLY_LATEST` to
avoid a backlog; the analyzer must close every `ImageProxy`.
[CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze?hl=en).

OpenCV's modern marker API is `ArucoDetector` in `objdetect`, with a Java API
callable from Kotlin. Its Android distribution has a Maven Central installation
route. Select and pin an actual artifact during implementation, and verify its
Java API and packaged ABIs in an Android build before claiming integration.
[Marker tutorial](https://docs.opencv.org/4.13.0/d5/dae/tutorial_aruco_detection.html),
[Java API](https://docs.opencv.org/4.13.0/javadoc/org/opencv/objdetect/ArucoDetector.html),
[Android distribution options](https://opencv.org/opencv4android-usage-models/).

A Board defines marker IDs and their known corner coordinates; it need not cover
the opening with a chessboard. [OpenCV boards](https://docs.opencv.org/4.13.0/db/da9/tutorial_aruco_board_detection.html).
AprilTag is another established visual fiducial implementation.
[AprilRobotics source](https://github.com/AprilRobotics/apriltag).
ARCore reference images require sufficient image features and at least 25% frame
coverage for initial detection; a custom small-marker board is a more direct fit
here. This preference is our design assessment, not a measured performance comparison.
[ARCore Augmented Images](https://developers.google.com/ar/develop/augmented-images).

Petiole's current documentation explicitly describes eight ArUco markers, known
print size, and keeping the leaf and calibration plate in the same plane. That
confirms the calibration workflow exists. Its current documentation does not
prove the internals of the older app associated with the supplied 2021 PDF. No
legacy APK was downloaded or decompiled, and its segmentation or claimed accuracy
has not been reproduced.
[Petiole calibration workflow](https://petiole.pro/blog/leaf-area-measurement/do-i-need-calibration-plate-petiole-pro/),
[2021 Petiole pad instructions](https://www.petiolepro.com/blog/how-can-i-get-the-calibration-pad-for-petiole-app/).

## Proposed printable marker layout

Print at least four **distinct, identified markers around the opening**, with their
corners stored in millimetres in a versioned template definition. Six or eight can
add redundancy if physical space permits; a result still requires at least four
well-distributed visible markers, following the user's requirement. Use all their
corners, not just four marker centers. Markers clustered on one side should not
qualify as a well-supported measurement of the opening.

The original rounded end has a 20 mm outside radius. With the target aperture,
only about **6.18 mm of radial paper** remains. Large square markers plus white
clearance will not fit freely around that ring. Prototype compact rotated markers
on this exact outline first; if they cannot be reliably read at the camera's
working distance, an enlarged imaging collar is a separate template design choice.
Do not enlarge the opening or place markers over it to make them fit.

A known ArUco dictionary such as `DICT_4X4_50` is a reasonable initial trial for
small markers. Print clean black borders with white clearance and unique IDs.
Keep the aperture and marker positions in one generated coordinate system. Add a
scale bar and template version outside the measurement opening. Users print at
Actual size / 100%, with Fit to page disabled, as requested. A ruler check verifies
printer output rather than changing the intended true-scale workflow.

## Proposed live measurement pipeline

1. Acquire a frame on a background analyzer. Handle rotation, cropping, and image
   plane strides correctly; keep preview overlay coordinates consistent with analysis.
2. Detect expected marker IDs and refine their corners. Require at least four
   markers spread around the aperture and sufficient sharpness and pixel coverage.
3. Estimate the image-to-template planar homography from known marker corner
   coordinates, rejecting inconsistent points. Use lens-distortion correction where
   needed and validated for the camera stream. A homography does not itself model
   radial lens distortion or a bent sheet.
4. Rectify the aperture region to a metric grid. For example, at `q` pixels/mm,
   each rectified pixel represents `1/q²` mm². This is a projective calibration,
   not a single pixels/mm ratio applied everywhere in a tilted camera image.
5. Segment leaf tissue inside the known circle. Begin with a controlled matte,
   contrasting backing and automatic color-based segmentation with backing and
   sensitivity controls. The user excluded add/erase editing. Green-only thresholding is
   insufficient for yellow/brown leaves, and dark pixels alone are not leaf tissue.
6. Compute `area_mm² = sum(leaf_mask AND aperture_mask) / q²`, retaining holes.
   At edges, use subpixel coverage or quantify rasterization error. Convert to cm²
   by dividing by 100; coverage percent is `100 * area_mm² / 600`.
7. Overlay accepted tissue and the opening boundary. Permit saving only while
   calibration and segmentation quality are valid. Losing markers must invalidate
   the live result rather than silently reusing stale scale. Temporal smoothing can
   stabilize accepted results but must not imply higher measurement accuracy.

The perspective transformation is supported by the planar geometry described in
[OpenCV's homography tutorial](https://docs.opencv.org/4.x/d9/dab/tutorial_homography.html).
The segmentation choices, quality gates, and metric integration above are our
proposed implementation, not a claim about Petiole's source code.

The leaf and marker plane need to be nearly coincident. A leaf sagging below the
opening or curled above it introduces parallax that paper markers cannot correct.
Use a flat backing and a near-normal camera view. The wood grain in the supplied
photo is useful for layout inspection but is not a controlled segmentation background.
Cut accuracy also matters: the app should check for paper intruding into the intended
circle and reject a badly cut mask rather than count hidden tissue.

## Validation before reporting scientific accuracy

- Analytic fixtures: empty circle = 0; full circle = 600 mm²; half circle = 300 mm²;
  known polygons crossing the circle; interior holes and separate leaf fragments.
- Geometry: varying distance, perspective, image rotation, missing markers, wrong
  template IDs, partial occlusion, blurred frames, curved paper, and lens changes.
- Segmentation: green/yellow/brown leaves, reflections, shadows, thin tips, insect
  holes, and backgrounds that resemble the leaf. Check the overlay against a manual
  reference; do not fill true holes during morphological cleanup.
- Physical standards: measured cutouts of known area spanning the 0-600 mm² range,
  repeated on multiple prints, lighting conditions, and phone cameras. Use a ruler
  or independent area standard; the template cannot verify its own print accuracy.
- Preserve sample ID, area, coverage, template version, calibration residuals,
  available image metadata, analyzed frame, segmentation mask, and threshold settings.
- Report absolute error in mm², bias, repeatability, and failure rates. Set numeric
  acceptance thresholds from these trials; neither library availability nor a smooth
  live number demonstrates scientific accuracy.

The research above informed the implemented first iteration. See `VALIDATION-v1.md`
for build and emulator evidence. Physical accuracy trials remain outstanding.
