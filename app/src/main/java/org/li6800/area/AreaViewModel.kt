package org.li6800.area

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

enum class InputMode { IDLE, CAMERA, PHOTO, FROZEN }
data class AreaState(
    val mode: InputMode = InputMode.IDLE,
    val measurement: Measurement? = null,
    val trackingLost: Boolean = false,
    val trackingIssue: Int? = null,
    val busy: Boolean = false,
    val backing: Backing = Backing.WHITE,
    val sensitivity: Float = .5f,
    val source: String = "",
    val synthetic: Boolean = false,
    val message: Int? = null,
    val history: List<SavedMeasurement> = emptyList(),
) {
    fun withAnalysis(result: Measurement): AreaState {
        val held = mode == InputMode.CAMERA && !result.valid && measurement?.valid == true
        return copy(measurement = if (held) measurement else result,
            trackingLost = held, trackingIssue = result.issue, busy = false, message = null)
    }
}

class AreaViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("measurement-settings", android.content.Context.MODE_PRIVATE)
    var state by mutableStateOf(AreaState(sensitivity = preferences.getFloat("sensitivity", .5f)
        .takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .5f)); private set
    val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private val engine by lazy { MeasurementEngine(getApplication()) }
    private val store = MeasurementStore(application)
    private var workerMeasurement: Measurement? = null
    private var lastFrameTime = 0L
    init { refreshHistory() }

    private fun post(token: Long, change: (AreaState) -> AreaState) {
        main.post { if (generation.get() == token) state = change(state) }
    }
    private fun begin(mode: InputMode, source: String = "", synthetic: Boolean = false): Long {
        val token = generation.incrementAndGet()
        state = state.copy(mode = mode, measurement = null, busy = mode != InputMode.CAMERA,
            source = source, synthetic = synthetic, message = null, trackingLost = false, trackingIssue = null)
        executor.execute { workerMeasurement = null }
        return token
    }
    fun startCamera() { begin(InputMode.CAMERA, "camera"); state = state.copy(busy = false) }
    fun stopCamera() {
        generation.incrementAndGet()
        state = state.copy(mode = InputMode.FROZEN, busy = false)
        val captured = state.measurement
        executor.execute { workerMeasurement = captured }
    }
    fun cameraError() {
        generation.incrementAndGet()
        state = state.copy(mode = InputMode.FROZEN, trackingLost = state.measurement?.valid == true,
            trackingIssue = R.string.camera_unavailable, message = R.string.camera_unavailable, busy = false)
        val captured = state.measurement
        executor.execute { workerMeasurement = captured }
    }

    /** Called on the same executor as ImageAnalysis; latest-frame backpressure bounds work. */
    fun shouldAnalyzeFrame(): Boolean = state.mode == InputMode.CAMERA && System.currentTimeMillis() - lastFrameTime >= 180

    val cameraSessionId get() = generation.get()

    fun onCameraFrame(bitmap: Bitmap, sessionId: Long = cameraSessionId) {
        if (state.mode != InputMode.CAMERA || sessionId != generation.get()) return
        val now = System.currentTimeMillis()
        if (now - lastFrameTime < 180) return
        lastFrameTime = now
        analyze(bitmap, sessionId, state.backing, state.sensitivity)
    }

    fun importPhoto(uri: Uri) {
        val token = begin(InputMode.PHOTO, "photo")
        executor.execute {
            try {
                val resolver = getApplication<Application>().contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = 1
                    while (max(bounds.outWidth, bounds.outHeight) / inSampleSize > 2300) inSampleSize *= 2
                }
                var bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Cannot decode photo")
                val exif = resolver.openInputStream(uri).use { stream -> stream?.let { ExifInterface(it) } }
                val transform = Matrix()
                if (exif?.isFlipped == true) transform.postScale(-1f, 1f)
                transform.postRotate((exif?.rotationDegrees ?: 0).toFloat())
                if (!transform.isIdentity) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
                analyze(bitmap, token, state.backing, state.sensitivity)
            } catch (error: Exception) { failure(token, error, R.string.image_failed) }
        }
    }

    private fun analyze(bitmap: Bitmap, token: Long, backing: Backing, sensitivity: Float) {
        if (generation.get() != token) return
        try {
            val result = engine.process(bitmap, backing, sensitivity)
            if (generation.get() != token) return
            if (result.valid || state.mode != InputMode.CAMERA) workerMeasurement = result
            post(token) { it.withAnalysis(result) }
        } catch (error: Exception) { failure(token, error, R.string.processing_failed) }
    }

    private fun failure(token: Long, error: Exception, message: Int) {
        Log.e("LeafArea", "Image processing/storage operation failed", error)
        post(token) {
            if (it.mode == InputMode.CAMERA) it.copy(busy = false,
                trackingLost = it.measurement?.valid == true, trackingIssue = message)
            else it.copy(busy = false, measurement = null, message = message)
        }
    }

    fun settings(backing: Backing = state.backing, sensitivity: Float = state.sensitivity) {
        if (!sensitivity.isFinite()) return
        val sensitivity = sensitivity.coerceIn(0f, 1f)
        if (sensitivity != state.sensitivity) preferences.edit().putFloat("sensitivity", sensitivity).apply()
        state = state.copy(backing = backing, sensitivity = sensitivity)
        val token = generation.get()
        executor.execute {
            val base = workerMeasurement ?: return@execute
            if (base.rectified == null || generation.get() != token) return@execute
            val result = engine.segment(base, backing, sensitivity)
            workerMeasurement = result
            post(token) { it.copy(measurement = result) }
        }
    }
    fun save(sampleId: String) {
        if (state.busy || state.mode == InputMode.CAMERA || state.measurement?.valid != true) return
        val snapshot = state
        val token = generation.get()
        state = state.copy(busy = true)
        executor.execute {
            try {
                val result = workerMeasurement ?: error("No current measurement")
                store.save(result, sampleId.take(120), snapshot.source, snapshot.synthetic,
                    result.backing, result.sensitivity, snapshot.trackingLost)
                val records = store.list()
                post(token) { it.copy(busy = false, history = records, message = R.string.saved_message) }
            } catch (error: Exception) {
                Log.e("LeafArea", "Saving measurement failed", error)
                post(token) { it.copy(busy = false, message = R.string.storage_failed) }
            }
        }
    }
    fun refreshHistory() {
        executor.execute { val records = store.list(); main.post { state = state.copy(history = records) } }
    }
    fun dismissMessage() { state = state.copy(message = null) }
    override fun onCleared() { generation.incrementAndGet(); executor.shutdown() }
}
