package org.li6800.area

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.util.zip.ZipFile

class MeasurementEngineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun detachedRimShadowIsRemovedWithoutShrinkingLeafArea() {
        val engine = MeasurementEngine(context)
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/half.png").use { BitmapFactory.decodeStream(it) }
        val calibrated = engine.process(reference, Backing.WHITE, .5f)
        fun image(shadow: Boolean, leaf: Int?): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(360, 360, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint()
            if (shadow) {
                paint.color = android.graphics.Color.rgb(70, 70, 70)
                canvas.drawCircle(180f, 180f, 138.2f, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(180f, 180f, 129f, paint)
            }
            if (leaf != null) { paint.color = leaf; canvas.drawCircle(180f, 180f, 60f, paint) }
            return bitmap
        }
        val green = android.graphics.Color.rgb(35, 125, 45)
        val noShadow = engine.segment(calibrated.copy(rectified = image(false, green)), Backing.WHITE, .5f)
        val withShadow = engine.segment(calibrated.copy(rectified = image(true, green)), Backing.WHITE, .5f)
        assertEquals(0.0, engine.segment(calibrated.copy(rectified = image(true, null)), Backing.WHITE, .5f).areaMm2!!, 0.0)
        assertEquals(noShadow.areaMm2!!, withShadow.areaMm2!!, .05)
        assertEquals(kotlin.math.PI * 36, withShadow.areaMm2!!, 1.0)
        // A dark interior leaf is retained, as are small colored fragments at the edge.
        assertEquals(noShadow.areaMm2!!, engine.segment(calibrated.copy(rectified = image(true,
            android.graphics.Color.rgb(55, 55, 55))), Backing.WHITE, .5f).areaMm2!!, .05)
        val tips = image(false, null)
        android.graphics.Canvas(tips).apply {
            val paint = android.graphics.Paint().apply { color = green }
            drawCircle(310f, 180f, 4f, paint)
            drawCircle(50f, 180f, 4f, paint)
        }
        assertTrue(engine.segment(calibrated.copy(rectified = tips), Backing.WHITE, .5f).areaMm2!! > .8)
    }

    @Test fun liveLossRetainsLastReadingAndSourceUntilReacquisition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: AreaViewModel
        val holder = androidx.lifecycle.ViewModelStore()
        instrumentation.runOnMainSync {
            vm = AreaViewModel(context.applicationContext as android.app.Application)
            holder.put("retention-test", vm)
            vm.startCamera()
        }
        fun drain() { vm.executor.submit {}.get(10, java.util.concurrent.TimeUnit.SECONDS); instrumentation.waitForIdleSync() }
        fun frame(name: String) {
            val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/$name.png").use { BitmapFactory.decodeStream(it) }
            vm.executor.submit { android.os.SystemClock.sleep(190); vm.onCameraFrame(bitmap) }.get(10, java.util.concurrent.TimeUnit.SECONDS)
            instrumentation.waitForIdleSync()
        }
        try {
            frame("half")
            val original = vm.state.measurement!!
            assertTrue(original.valid)
            frame("missing-markers")
            assertSame(original, vm.state.measurement)
            assertTrue(vm.state.trackingLost)
            assertEquals(R.string.need_four, vm.state.trackingIssue)
            frame("missing-markers")
            assertSame(original, vm.state.measurement)
            instrumentation.runOnMainSync { vm.settings(sensitivity = .6f) }
            drain()
            assertTrue(vm.state.trackingLost)
            assertSame(original.source, vm.state.measurement!!.source)
            assertEquals(original.analyzedAtUtc, vm.state.measurement!!.analyzedAtUtc)
            assertEquals(.6f, vm.state.measurement!!.sensitivity, 0f)
            frame("full")
            assertFalse(vm.state.trackingLost)
            assertTrue(vm.state.measurement!!.areaMm2!! > 598)
            val imported = java.io.File(context.cacheDir, "missing-markers-test.png")
            InstrumentationRegistry.getInstrumentation().context.assets.open("samples/missing-markers.png").use { input ->
                imported.outputStream().use { input.copyTo(it) }
            }
            instrumentation.runOnMainSync { vm.importPhoto(android.net.Uri.fromFile(imported)) }
            drain()
            assertFalse(vm.state.measurement!!.valid)
            assertFalse(vm.state.trackingLost)
        } finally { instrumentation.runOnMainSync { holder.clear() } }
    }

    @Test fun realOpenCvDetectionCalibrationAndSegmentation() {
        val engine = MeasurementEngine(context)
        val expected = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("samples/expected.json").bufferedReader().use { it.readText() })
        for (name in listOf("empty", "half", "full", "leaf-hole", "perspective", "yellow")) {
            val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/$name.png").use { BitmapFactory.decodeStream(it) }
            val result = engine.process(bitmap, Backing.WHITE, .5f)
            android.util.Log.i("LeafAreaTest", "$name area=${result.areaMm2} mm2 markers=${result.markerCount} fit=${result.fitErrorMm} issue=${result.issue}")
            assertTrue("$name failed with issue ${result.issue}", result.valid)
            assertEquals(10, result.markerCount)
            assertTrue("$name reprojection error ${result.fitErrorMm}", result.fitErrorMm < .15)
            assertEquals(name, expected.getJSONObject(name).getDouble("expectedAreaMm2"), result.areaMm2!!, 3.0)
        }
    }

    @Test fun missingMarkersInvalidateTheResult() {
        val engine = MeasurementEngine(context)
        val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/missing-markers.png").use { BitmapFactory.decodeStream(it) }
        val result = engine.process(bitmap, Backing.WHITE, .5f)
        assertFalse(result.valid)
        assertNull(result.areaMm2)
        assertEquals(R.string.need_four, result.issue)
    }

    @Test fun newMarkersAllowOcclusionWhileOneSidedLayoutsAreRejected() {
        val engine = MeasurementEngine(context)
        for ((name, count) in listOf("four-markers" to 4, "rear-occluded" to 6)) {
            val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/$name.png").use { BitmapFactory.decodeStream(it) }
            val result = engine.process(bitmap, Backing.WHITE, .5f)
            assertTrue("$name: ${result.issue}", result.valid)
            assertEquals(count, result.markerCount)
            assertEquals(300.0, result.areaMm2!!, 3.0)
        }
        val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/one-side.png").use { BitmapFactory.decodeStream(it) }
        val rejected = engine.process(bitmap, Backing.WHITE, .5f)
        assertFalse(rejected.valid)
        assertEquals(R.string.spread_markers, rejected.issue)
    }

    @Test fun legacyPrintIsNoLongerCalibrated() {
        val engine = MeasurementEngine(context)
        val oldBitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples-v1/half.png").use { BitmapFactory.decodeStream(it) }
        val result = engine.process(oldBitmap, Backing.WHITE, .5f)
        assertFalse(result.valid)
        assertEquals(0, result.markerCount)
        assertEquals(R.string.need_four, result.issue)
        assertEquals("LI6800-6-V2", result.template!!.id)
    }

    @Test fun sensitivityPersistsAcrossViewModelInstances() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val holder = androidx.lifecycle.ViewModelStore()
        instrumentation.runOnMainSync {
            val first = AreaViewModel(context.applicationContext as android.app.Application)
            holder.put("first", first)
            first.settings(sensitivity = .73f)
            val second = AreaViewModel(context.applicationContext as android.app.Application)
            holder.put("second", second)
            assertEquals(.73f, second.state.sensitivity, 0f)
            second.settings(sensitivity = .5f)
            holder.clear()
        }
    }

    @Test fun savedArchiveRetainsMaskSourceAndMetricResult() {
        val engine = MeasurementEngine(context)
        val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("samples/half.png").use { BitmapFactory.decodeStream(it) }
        val measurement = engine.process(bitmap, Backing.WHITE, .5f)
        val store = MeasurementStore(context)
        val saved = store.save(measurement, "=unsafe,\"sample\"", "half", true, Backing.WHITE, .5f)
        try {
            ZipFile(saved.file).use { zip ->
                val details = JSONObject(zip.getInputStream(zip.getEntry("measurement.json")).bufferedReader().use { it.readText() })
                assertEquals(measurement.areaMm2!!, details.getDouble("areaMm2"), 0.0)
                assertEquals("LI6800-6-V2", details.getString("templateId"))
                assertNotNull(zip.getEntry("source.jpg")); assertNotNull(zip.getEntry("tissue-mask.png"))
                val csv = zip.getInputStream(zip.getEntry("measurement.csv")).bufferedReader().use { it.readText() }
                assertTrue(csv.contains("\"'=unsafe,"))
            }
            assertTrue(store.list().any { it.file == saved.file })
        } finally { saved.file.delete() }
    }
}
