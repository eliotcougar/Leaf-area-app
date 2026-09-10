package org.li6800.area

import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun FlashlightButton(camera: Camera?, panel: Color, foreground: Color) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val available = camera?.cameraInfo?.hasFlashUnit() == true
    var torchOn by remember(camera) { mutableStateOf(false) }
    var pending by remember(camera) { mutableStateOf(false) }
    // Old requests may finish after changing cameras or leaving this display mode.
    val active = remember(camera, lifecycleOwner) { booleanArrayOf(true) }
    DisposableEffect(camera, lifecycleOwner) {
        active[0] = true
        val observer = Observer<Int> { torchOn = it == TorchState.ON }
        camera?.cameraInfo?.torchState?.observe(lifecycleOwner, observer)
        onDispose {
            active[0] = false
            camera?.cameraInfo?.torchState?.removeObserver(observer)
        }
    }
    FilledIconToggleButton(
        checked = torchOn,
        enabled = available && !pending,
        onCheckedChange = { enabled ->
            camera?.let {
                pending = true
                val request = it.cameraControl.enableTorch(enabled)
                request.addListener({
                    if (active[0]) {
                        pending = false
                        try {
                            request.get()
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.flashlight_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = panel, contentColor = foreground,
            checkedContainerColor = Color(0xFFFFDA8A), checkedContentColor = Color(0xFF282314),
            disabledContainerColor = panel, disabledContentColor = foreground.copy(alpha = 0.38f),
        ),
    ) {
        Icon(painterResource(if (torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off),
            stringResource(when {
                !available -> R.string.flashlight_unavailable
                torchOn -> R.string.flashlight_off
                else -> R.string.flashlight_on
            }))
    }
}
