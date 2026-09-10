package org.li6800.area

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class MeasurementScreenTest {
    @Test fun fixedScreenRetainsAreaAndSwitchesLiveModesWithoutScrolling() {
        val test = InstrumentationRegistry.getInstrumentation()
        val context = test.targetContext
        test.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        context.getSharedPreferences("measurement-settings", 0).edit().remove("camera-key").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AreaViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[AreaViewModel::class.java]; vm.settings(sensitivity = .5f) }
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (vm.state.mode != InputMode.CAMERA && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertEquals(InputMode.CAMERA, vm.state.mode)
            fun frame(name: String) {
                val image = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/$name.png").use { BitmapFactory.decodeStream(it) }
                // Hold the analyzer's own queue long enough to pass its frame throttle.
                vm.executor.submit { SystemClock.sleep(210); vm.onCameraFrame(image) }.get(10, TimeUnit.SECONDS)
                test.waitForIdleSync()
            }
            fun node(label: String): AccessibilityNodeInfo {
                // Traverse Compose's virtual nodes: the platform text-search API
                // need not be implemented by a virtual accessibility provider.
                fun find(item: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (item == null) return null
                    if (!item.refresh()) return null
                    if (item.text?.toString() == label || item.contentDescription?.toString() == label) return item
                    for (i in 0 until item.childCount) find(item.getChild(i))?.let { return it }
                    return null
                }
                val until = SystemClock.elapsedRealtime() + 5000
                do {
                    val found = find(test.uiAutomation.rootInActiveWindow)
                    if (found != null) return found
                    SystemClock.sleep(60)
                } while (SystemClock.elapsedRealtime() < until)
                val labels = mutableListOf<String>()
                fun dump(item: AccessibilityNodeInfo?) {
                    if (item == null) return
                    labels += "${item.text} | ${item.contentDescription}"
                    for (i in 0 until item.childCount) dump(item.getChild(i))
                }
                dump(test.uiAutomation.rootInActiveWindow)
                test.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(context.filesDir, "qa-v4/failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                error("Missing UI label: $label; state=${vm.state.mode}, held=${vm.state.trackingLost}; nodes=$labels")
            }
            fun bounds(label: String) = Rect().also { node(label).getBoundsInScreen(it) }
            fun click(label: String) {
                var item: AccessibilityNodeInfo? = node(label)
                while (item != null && !item.isClickable) item = item.parent
                assertTrue(item?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                test.waitForIdleSync()
            }
            fun assertFixed(root: AccessibilityNodeInfo?) {
                if (root == null) return
                assertFalse("Main measurement screen must not scroll", root.isScrollable)
                for (i in 0 until root.childCount) assertFixed(root.getChild(i))
            }
            val evidence = File(context.filesDir, "qa-v4").apply { mkdirs() }
            fun screenshot(name: String) {
                test.uiAutomation.takeScreenshot()!!.let { bitmap ->
                    File(evidence, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            frame("half")
            val held = vm.state.measurement!!
            assertTrue(held.valid)
            val area = context.getString(R.string.area_cm, held.areaMm2!! / 100)
            val areaBounds = bounds(area)
            val cameraBounds = bounds(context.getString(R.string.camera))
            val circleBounds = bounds(context.getString(R.string.circle_sensitivity))
            screenshot("camera")
            frame("missing-markers")
            assertTrue(vm.state.trackingLost)
            assertSame(held.source, vm.state.measurement!!.source)
            node(context.getString(R.string.last_reading))
            assertEquals(areaBounds, bounds(area))
            assertEquals(cameraBounds, bounds(context.getString(R.string.camera)))
            assertEquals(circleBounds, bounds(context.getString(R.string.circle_sensitivity)))
            assertFixed(test.uiAutomation.rootInActiveWindow)
            assertFalse("No square-millimeter row in measurement header", test.uiAutomation.rootInActiveWindow
                .findAccessibilityNodeInfosByText("mm²").isNotEmpty())
            screenshot("held")
            click(context.getString(R.string.circle_sensitivity))
            node(context.getString(R.string.sensitivity_value, 50))
            val square = bounds(context.getString(R.string.selection_image))
            assertTrue(square.width() > 100)
            assertEquals(square.width(), square.height())
            assertEquals(areaBounds, bounds(area))
            assertEquals(InputMode.CAMERA, vm.state.mode)
            assertFixed(test.uiAutomation.rootInActiveWindow)
            screenshot("circle-sensitivity")
            // The second mode is live too: a fresh frame updates its measurement.
            frame("full")
            val fullArea = context.getString(R.string.area_cm, vm.state.measurement!!.areaMm2!! / 100)
            assertTrue(vm.state.measurement!!.areaMm2!! > 598)
            node(fullArea)
            assertEquals(areaBounds, bounds(fullArea))
            click(context.getString(R.string.camera))
            assertEquals(InputMode.CAMERA, vm.state.mode)
            node(fullArea)
            // Emulator exposes front and rear cameras. Switching invalidates the old
            // camera frame and binds another camera while leaving the mode live.
            val oldSession = vm.cameraSessionId
            val provider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
            val rear = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).first()
            if (rear.hasFlashUnit()) {
                click(context.getString(R.string.flashlight_on))
                node(context.getString(R.string.flashlight_off))
                assertEquals(androidx.camera.core.TorchState.ON, rear.torchState.value)
                screenshot("flashlight-on")
                click(context.getString(R.string.circle_sensitivity))
                assertEquals(androidx.camera.core.TorchState.ON, rear.torchState.value)
                click(context.getString(R.string.camera))
                click(context.getString(R.string.flashlight_off))
                node(context.getString(R.string.flashlight_on))
                assertEquals(androidx.camera.core.TorchState.OFF, rear.torchState.value)
                // Leave the light on to verify camera switching releases it.
                click(context.getString(R.string.flashlight_on))
                node(context.getString(R.string.flashlight_off))
            } else {
                var flashlight: AccessibilityNodeInfo? = node(context.getString(R.string.flashlight_unavailable))
                while (flashlight != null && !flashlight.isCheckable) flashlight = flashlight.parent
                assertTrue("Unavailable flashlight must be disabled", flashlight != null && !flashlight.isEnabled)
            }
            assertEquals("Flashlight controls must not rebind the camera", oldSession, vm.cameraSessionId)
            File(evidence, "flashlight.txt").writeText("rearFlashAvailable=${rear.hasFlashUnit()}\n")
            click(context.getString(R.string.switch_camera))
            assertEquals("Opening the list must not switch cameras", oldSession, vm.cameraSessionId)
            val front = cameraChoices(provider).first { it.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT }
            click(front.label(context))
            assertTrue(vm.cameraSessionId > oldSession)
            assertNull(vm.state.measurement?.areaMm2)
            assertEquals(InputMode.CAMERA, vm.state.mode)
            assertEquals(front.key, vm.state.cameraKey)
            SystemClock.sleep(1500)
            assertEquals(androidx.camera.core.TorchState.OFF, rear.torchState.value)
            assertEquals(InputMode.CAMERA, vm.state.mode)
            click(context.getString(R.string.switch_camera))
            var selectedNode: AccessibilityNodeInfo? = node(front.label(context))
            while (selectedNode != null && !selectedNode.isCheckable) selectedNode = selectedNode.parent
            assertTrue("Selected camera must be marked in the list", selectedNode?.isChecked == true)
            screenshot("camera-list")
            click(context.getString(R.string.close))
            click(context.getString(R.string.circle_sensitivity))
            click(context.getString(R.string.camera))
            assertEquals(front.key, vm.state.cameraKey)
            scenario.recreate()
            scenario.onActivity { vm = ViewModelProvider(it)[AreaViewModel::class.java] }
            assertEquals(front.key, vm.state.cameraKey)
            // Missing/failed choices must not bind the default camera behind the user's back.
            scenario.onActivity { vm.selectCamera("missing-camera-test") }
            node(context.getString(R.string.camera_error_title))
            assertEquals(InputMode.FROZEN, vm.state.mode)
            assertEquals("missing-camera-test", vm.state.cameraKey)
            click(context.getString(R.string.switch_camera))
            click(front.label(context))
            assertEquals(front.key, vm.state.cameraKey)
            assertEquals(InputMode.CAMERA, vm.state.mode)
            File(evidence, "bounds.txt").writeText("area=$areaBounds\ncamera=$cameraBounds\ncircle=$circleBounds\nsquare=$square\n")
        }
    }
}
