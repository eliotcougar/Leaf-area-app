package org.li6800.area

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.camera2.impl.Camera2ImplConfig
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PhysicalCameraBindingTest {
    // Inspect the actual bound CameraX output configurations, not the saved picker key.
    @SuppressLint("RestrictedApi")
    @Test fun physicalSelectionPinsBothBoundStreamsAndDeliversFrames() {
        val test = InstrumentationRegistry.getInstrumentation()
        val context = test.targetContext
        test.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
        val choices = cameraChoices(provider)
        val fixed = choices.filter { it.physical }
        assertTrue("QA device must expose physical cameras", fixed.isNotEmpty())
        class Owner : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        lateinit var owner: Owner
        val analyzer = Executors.newSingleThreadExecutor()
        val evidence = StringBuilder()
        try {
            test.runOnMainSync {
                owner = Owner()
                owner.registry.currentState = Lifecycle.State.RESUMED
            }
            // Include automatic mode after physical lenses to detect leaked physical IDs.
            for (choice in fixed + choices.first { it.automatic }) {
                val frames = CountDownLatch(3)
                lateinit var preview: Preview
                lateinit var analysis: ImageAnalysis
                test.runOnMainSync {
                    val previewBuilder = Preview.Builder()
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    choice.configureOutputs(previewBuilder, analysisBuilder)
                    preview = previewBuilder.build()
                    analysis = analysisBuilder.build()
                    preview.setSurfaceProvider { request ->
                        val texture = SurfaceTexture(0)
                        texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                        val surface = Surface(texture)
                        request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                            surface.release()
                            texture.release()
                        }
                    }
                    analysis.setAnalyzer(analyzer) { frame -> frame.close(); frames.countDown() }
                    provider.bindToLifecycle(owner, choice.selector, preview, analysis)
                    val expected = choice.cameraId.takeIf { choice.physical }
                    for (useCase in listOf(preview, analysis)) {
                        val outputs = useCase.sessionConfig.outputConfigs
                        assertTrue("Bound stream must have outputs", outputs.isNotEmpty())
                        // CameraGraphConfigProvider resolves the session-wide interop ID
                        // first, then each OutputConfig ID. Interop uses the former.
                        val sessionId = Camera2ImplConfig(useCase.sessionConfig.implementationOptions).getPhysicalCameraId()
                        val ids = outputs.map { sessionId ?: it.physicalCameraId }
                        for (id in ids) assertEquals(expected, id)
                        evidence.appendLine("${choice.key} ${useCase.javaClass.simpleName}: $ids")
                    }
                }
                try {
                    assertTrue("${choice.key} must deliver analysis frames", frames.await(15, TimeUnit.SECONDS))
                } finally {
                    test.runOnMainSync { analysis.clearAnalyzer(); provider.unbind(preview, analysis) }
                }
            }
        } finally {
            test.runOnMainSync { owner.registry.currentState = Lifecycle.State.DESTROYED }
            analyzer.shutdown()
            File(context.filesDir, "physical-outputs-v043.txt").writeText(evidence.toString())
        }
    }
}
