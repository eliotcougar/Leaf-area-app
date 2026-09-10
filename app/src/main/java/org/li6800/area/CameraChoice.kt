package org.li6800.area

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider

data class CameraChoice(val key: String, val selector: CameraSelector, val lensFacing: Int,
                        val cameraId: String, val physical: Boolean, val automatic: Boolean,
                        val focalLengthMm: Float?) {
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun configureOutputs(preview: Preview.Builder, analysis: ImageAnalysis.Builder) {
        if (!physical) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            error("Physical camera selection requires Android 9 or newer")
        }
        // CameraX 1.6.1 does not propagate CameraSelector.physicalCameraId in
        // single-camera binding. Pin BOTH streams so preview and area analysis
        // cannot remain on the logical camera's automatic lens selection.
        Camera2Interop.Extender(preview).setPhysicalCameraId(cameraId)
        Camera2Interop.Extender(analysis).setPhysicalCameraId(cameraId)
    }

    fun label(context: Context): String {
        val facing = context.getString(when (lensFacing) {
            CameraSelector.LENS_FACING_BACK -> R.string.camera_rear
            CameraSelector.LENS_FACING_FRONT -> R.string.camera_front
            else -> R.string.camera_external
        })
        val name = context.getString(if (physical) R.string.camera_lens_name else R.string.camera_name, facing, cameraId)
        return when {
            automatic -> context.getString(R.string.camera_automatic, name)
            focalLengthMm != null -> context.getString(R.string.camera_focal_length, name, focalLengthMm)
            else -> name
        }
    }
}

/** Include separately exposed lenses and physical lenses behind logical cameras. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
fun cameraChoices(provider: ProcessCameraProvider): List<CameraChoice> {
    val cameras = provider.availableCameraInfos.sortedBy { if (it.lensFacing == CameraSelector.LENS_FACING_BACK) 0 else 1 }
    return buildList {
        for (camera in cameras) {
            val id = Camera2CameraInfo.from(camera).cameraId
            val physicalCameras = camera.physicalCameraInfos.sortedBy { Camera2CameraInfo.from(it).cameraId }
            add(CameraChoice(id, camera.cameraSelector, camera.lensFacing, id, false,
                physicalCameras.isNotEmpty(), Camera2CameraInfo.from(camera)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()))
            for (physical in physicalCameras) {
                val physicalId = Camera2CameraInfo.from(physical).cameraId
                // Even when an ID is exposed directly, its fixed physical selector
                // differs from the logical selector that may switch lenses itself.
                add(CameraChoice("$id/$physicalId", CameraSelector.Builder()
                    .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }
                    .setPhysicalCameraId(physicalId).build(), camera.lensFacing, physicalId, true, false,
                    Camera2CameraInfo.from(physical)
                        .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()))
            }
        }
    }
}
