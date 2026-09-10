# Leaf Area

Measure the projected leaf area inside a **6 cm² LI-6800 chamber opening** using an Android phone and a printed calibration mask.

The app uses markers around the opening to correct perspective and convert pixels to real area. Only leaf tissue inside the circle is counted. Processing works entirely on the device, without an internet connection or account.

## What you need

- An Android phone running Android 8.0 or newer.
- The [V2 cutout PDF](app/src/main/assets/LI6800-6cm2-marker-cutout-v2.pdf), printed on A4 paper.
- A flat, matte white backing, or a blue mat for contrasting with the leaf.

For a stiffer mask, attach the printout to a 3D-printed backing made from the [0.4 mm STEP model](app/src/main/assets/LI6800-6cm2-mask-v2-0.4mm.step). Both files can also be exported from the app’s menu.

## Measure a leaf

1. **Print at Actual size / 100%.** Disable “Fit to page” and check the printed 50 mm ruler. Cut out the mask and its circular opening, leaving the square markers intact.
2. Place the leaf flat on the backing with the mask on top. Keep the leaf and markers as close to the same plane as possible.
3. Open the camera and hold the phone nearly overhead. Keep the complete opening and at least four markers visible, distributed around the circle. Use even lighting and avoid glare.
4. Switch to **Calibration** to inspect the highlighted tissue. Select the matching backing color and adjust sensitivity until the selection follows the leaf. The area updates live in both views.
5. **Freeze** to inspect a reading, or tap the round **Save** button between Camera and Calibration to store it with an optional sample ID.

If markers disappear, the app keeps the last calibrated reading and labels it **Last reading**. Measurements resume when tracking returns. Saving a held reading stores its original calibrated image.

The camera button opens a list of available cameras and lenses. Your camera choice and leaf sensitivity are remembered. Entries marked **automatic** let the phone choose the lens; choose an individual lens when you want to keep it fixed.

Use the flashlight button beside the camera button to toggle the selected camera’s light. It stays on in circle preview and turns off when you freeze or change cameras. Cameras without a flash show a disabled button.

For the LI-6800, enter the measured area in **cm²** as **S** under **Constants → Gas Exchange**. Keep the instrument’s chamber aperture set to 6 cm², and ensure the measured leaf region is the region enclosed by the chamber.

## Save and export

Open **Saved** to share a measurement as a ZIP containing:

- A CSV with the measured area.
- The source image, corrected circle image, and highlighted selection.
- A tissue mask and calibration metadata.

You can also measure an existing image using **Open photo**. Saved measurements stay on your device; export important records before uninstalling the app or clearing its data.

## Measurement limits

This app measures **projected area**, not the surface area of a curled leaf. Print scaling, a warped mask, lens distortion, uneven lighting, and shadows can affect the result. Inspect the selection before saving and check accuracy against known areas before using it in an experiment.

Detached dark shadows near the rim are filtered out, but shadows touching the leaf may still be counted. Very thin, gray tissue at the rim may be excluded. A physical measurement accuracy specification has not yet been established.

## Build from source

Open the project in Android Studio, using **JDK 21**, **Android SDK 37**, and **Build Tools 37.0.0**. Let Gradle sync, then run the app on a device or emulator.

Alternatively, with the JDK and Android SDK configured:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The APK is written to `app/build/outputs/apk/debug/`.

For an optimized build, use `:app:assembleRelease`. Release builds enable code and resource shrinking; configure your own release signing key before distributing a build.

Built with Kotlin, Jetpack Compose, CameraX, and OpenCV. The template and mask generators are in [`tools/`](tools/).
