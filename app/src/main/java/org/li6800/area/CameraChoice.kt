package org.li6800.area

import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

data class CameraChoice(val key: String, val selector: CameraSelector)

/** Include separately exposed lenses and physical lenses behind logical cameras. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
fun cameraChoices(provider: ProcessCameraProvider): List<CameraChoice> {
    val cameras = provider.availableCameraInfos.sortedBy { if (it.lensFacing == CameraSelector.LENS_FACING_BACK) 0 else 1 }
    val directIds = cameras.map { Camera2CameraInfo.from(it).cameraId }.toSet()
    return buildList {
        for (camera in cameras) {
            val id = Camera2CameraInfo.from(camera).cameraId
            add(CameraChoice(id, camera.cameraSelector))
            for (physical in camera.physicalCameraInfos.sortedBy { Camera2CameraInfo.from(it).cameraId }) {
                val physicalId = Camera2CameraInfo.from(physical).cameraId
                if (physicalId in directIds) continue
                add(CameraChoice("$id/$physicalId", CameraSelector.Builder()
                    .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == id } }
                    .setPhysicalCameraId(physicalId).build()))
            }
        }
    }
}
