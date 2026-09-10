package org.li6800.area

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.SystemBarStyle
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF176B52), onPrimary = Color.White,
                primaryContainer = Color(0xFFD7EDDC), onPrimaryContainer = Color(0xFF123D30),
                secondary = Color(0xFF557061), background = Color(0xFFF4F6F1),
                secondaryContainer = Color(0xFFDCEADD), onSecondaryContainer = Color(0xFF214C3B),
                surface = Color(0xFFF4F6F1), surfaceContainer = Color.White,
            )) { AreaScreen() }
        }
    }
}

@Composable
private fun CameraAnalysis(vm: AreaViewModel, cameraKey: String?, onCameras: (List<CameraChoice>) -> Unit,
                           onUnavailable: (String?) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    AndroidView(factory = { previewView }, modifier = modifier)
    DisposableEffect(lifecycleOwner, vm, previewView, cameraKey) {
        val sessionId = vm.cameraSessionId
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var preview: Preview? = null
        future.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val choices = cameraChoices(cameraProvider)
                    onCameras(choices)
                    val choice = choices.firstOrNull { it.key == cameraKey } ?: choices.first()
                    val selector = choice.selector
                    val stream = ImageAnalysis.Builder()
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis = stream
                    stream.setAnalyzer(vm.executor) { proxy ->
                        try {
                            if (disposed || sessionId != vm.cameraSessionId || !vm.shouldAnalyzeFrame()) return@setAnalyzer
                            val plane = proxy.planes[0]
                            check(plane.pixelStride == 4)
                            val buffer = plane.buffer.duplicate()
                            val start = buffer.position()
                            val pixels = ByteArray(proxy.width * proxy.height * 4)
                            // Row padding is not necessarily present after the final image row.
                            for (row in 0 until proxy.height) {
                                buffer.position(start + row * plane.rowStride)
                                buffer.get(pixels, row * proxy.width * 4, proxy.width * 4)
                            }
                            var bitmap = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
                            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
                            val crop = proxy.cropRect
                            if (crop.width() != bitmap.width || crop.height() != bitmap.height) {
                                bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
                            }
                            val rotation = proxy.imageInfo.rotationDegrees
                            if (rotation != 0) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                                Matrix().apply { postRotate(rotation.toFloat()) }, true)
                            vm.onCameraFrame(bitmap, sessionId)
                        } catch (error: Exception) {
                            Log.e("LeafArea", "Camera frame conversion failed", error)
                            ContextCompat.getMainExecutor(context).execute { if (!disposed) vm.cameraError() }
                        } finally { proxy.close() }
                    }
                    val livePreview = Preview.Builder().build()
                    preview = livePreview
                    livePreview.setSurfaceProvider(previewView.surfaceProvider)
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, livePreview, stream)
                } catch (error: Exception) {
                    Log.e("LeafArea", "Binding camera failed", error)
                    onUnavailable(cameraKey)
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            analysis?.let { it.clearAnalyzer(); provider?.unbind(it) }
            preview?.let { provider?.unbind(it) }
        }
    }
}

private enum class DisplayMode { CAMERA, CIRCLE }

@Composable
private fun AreaScreen(vm: AreaViewModel = viewModel()) {
    val state = vm.state
    val result = state.measurement
    val context = LocalContext.current
    var display by rememberSaveable { mutableStateOf(DisplayMode.CAMERA) }
    var showMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var requestedCamera by rememberSaveable { mutableStateOf(false) }
    var availableCameras by remember { mutableStateOf(emptyList<CameraChoice>()) }
    var cameraKey by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableCameras by remember { mutableStateOf(emptySet<String>()) }
    var sampleId by rememberSaveable { mutableStateOf("") }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) vm.startCamera()
    }
    fun camera() {
        permissionDenied = false
        display = DisplayMode.CAMERA
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) vm.startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!requestedCamera && state.mode == InputMode.IDLE) {
            requestedCamera = true
            camera()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { display = DisplayMode.CAMERA; vm.importPhoto(uri) }
    }
    fun exportAsset(uri: Uri?, asset: String) {
        if (uri != null) vm.executor.execute {
            runCatching {
                context.assets.open(asset).use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                        ?: error("Cannot open document")
                }
            }.onFailure {
                Log.e("LeafArea", "Writing cutout file failed", it)
                ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, R.string.storage_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        exportAsset(uri, "LI6800-6cm2-marker-cutout-v2.pdf")
    }
    val saveStep = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/step")) { uri ->
        exportAsset(uri, "LI6800-6cm2-mask-v2-0.4mm.step")
    }
    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.dismissMessage() }
    }
    val valid = result?.valid == true
    val panel = Color(0xE6192823)
    val onPanel = Color(0xFFF4FAF6)
    val muted = Color(0xFFBFD4CA)
    Box(Modifier.fillMaxSize().background(Color(0xFF101A16))) {
        // This camera surface survives every marker-loss and display-mode update.
        // Circle/sensitivity panels cover it without rebinding the CameraX session.
        if (state.mode == InputMode.CAMERA) CameraAnalysis(vm, cameraKey,
            onCameras = { availableCameras = it.filterNot { choice -> choice.key in unavailableCameras } },
            onUnavailable = { failedKey ->
                val failed = failedKey ?: availableCameras.firstOrNull()?.key
                if (failed != null) unavailableCameras = unavailableCameras + failed
                availableCameras = availableCameras.filterNot { it.key in unavailableCameras }
                val fallback = availableCameras.firstOrNull()
                if (fallback == null) vm.cameraError() else {
                    vm.startCamera(); cameraKey = fallback.key
                    Toast.makeText(context, R.string.camera_switch_failed, Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxSize())
        else result?.annotated?.let {
            Image(it.asImageBitmap(), stringResource(R.string.source_frame), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (display != DisplayMode.CAMERA) {
            Box(Modifier.fillMaxSize().background(Color(0xFF101A16)))
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(top = 194.dp, bottom = 256.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
                val circle = result?.overlay
                Box(Modifier.size(side).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDFEAE2)), contentAlignment = Alignment.Center) {
                    if (circle != null) Image(circle.asImageBitmap(), stringResource(R.string.selection_image), Modifier.fillMaxSize())
                    else Text(stringResource(R.string.circle_waiting), Modifier.padding(32.dp), color = Color(0xFF234B3C))
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(color = panel, contentColor = onPanel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        TextButton(onClick = { if (state.mode == InputMode.CAMERA) vm.stopCamera() else camera() },
                            enabled = state.mode != InputMode.CAMERA || valid,
                            colors = ButtonDefaults.textButtonColors(contentColor = onPanel, disabledContentColor = muted)) {
                            Text(stringResource(if (state.mode == InputMode.CAMERA) R.string.freeze_short else R.string.live))
                        }
                        TextButton(onClick = { if (state.mode == InputMode.CAMERA) vm.stopCamera(); showSave = true },
                            enabled = valid && !state.busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = onPanel, disabledContentColor = muted)) {
                            Text(stringResource(R.string.save_short))
                        }
                        Box {
                            TextButton(onClick = { showMenu = true }, colors = ButtonDefaults.textButtonColors(contentColor = onPanel)) {
                                Text(stringResource(R.string.more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.photo)) }, onClick = {
                                    showMenu = false; if (state.mode == InputMode.CAMERA) vm.stopCamera(); pickPhoto.launch("image/*")
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.history)) }, onClick = { showMenu = false; showHistory = true; vm.refreshHistory() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.template)) }, onClick = { showMenu = false; savePdf.launch("LI6800-6cm2-marker-cutout-v2.pdf") })
                                DropdownMenuItem(text = { Text(stringResource(R.string.template_step)) }, onClick = { showMenu = false; saveStep.launch("LI6800-6cm2-mask-v2-0.4mm.step") })
                                DropdownMenuItem(text = { Text(stringResource(R.string.help)) }, onClick = { showMenu = false; showHelp = true })
                            }
                        }
                    }
                    Text(if (valid) stringResource(R.string.area_cm, result!!.areaMm2!! / 100) else stringResource(R.string.none),
                        Modifier.height(62.dp), fontSize = 42.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(stringResource(when {
                        state.trackingLost -> R.string.last_reading
                        state.busy -> R.string.checking
                        valid && state.mode == InputMode.CAMERA -> R.string.live_calibrated
                        valid -> R.string.frozen_reading
                        else -> R.string.looking
                    }), Modifier.height(26.dp), fontWeight = FontWeight.SemiBold,
                        color = if (state.trackingLost) Color(0xFFFFDA8A) else onPanel, maxLines = 1)
                    val quality = if (state.trackingLost || !valid) stringResource(state.trackingIssue ?: R.string.no_frame)
                        else stringResource(R.string.marker_count, result!!.markerCount, result.template!!.markers.size, result.fitErrorMm)
                    Text(quality, Modifier.height(36.dp), color = muted, fontSize = 12.sp, lineHeight = 16.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (display == DisplayMode.CAMERA && state.mode == InputMode.CAMERA && availableCameras.size > 1) {
            FilledIconButton(onClick = {
                val current = availableCameras.indexOfFirst { it.key == cameraKey }.coerceAtLeast(0)
                val next = availableCameras[(current + 1) % availableCameras.size]
                vm.startCamera()
                cameraKey = next.key
            }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 202.dp, end = 16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = panel, contentColor = onPanel)) {
                Icon(painterResource(R.drawable.ic_switch_camera), stringResource(R.string.switch_camera))
            }
        }
        if (permissionDenied && state.mode == InputMode.IDLE) {
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = panel, contentColor = onPanel, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.camera_permission))
                    TextButton(onClick = { requestCamera.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera), color = onPanel) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) {
                        Text(stringResource(R.string.open_settings), color = onPanel)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (display == DisplayMode.CIRCLE) Surface(color = panel, contentColor = onPanel, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().height(166.dp).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.backing), Modifier.weight(1f), color = muted)
                        FilterChip(selected = state.backing == Backing.WHITE, onClick = { vm.settings(backing = Backing.WHITE) }, colors = FilterChipDefaults.filterChipColors(labelColor = onPanel), label = { Text(stringResource(R.string.white)) })
                        FilterChip(selected = state.backing == Backing.BLUE, onClick = { vm.settings(backing = Backing.BLUE) }, colors = FilterChipDefaults.filterChipColors(labelColor = onPanel), label = { Text(stringResource(R.string.blue)) })
                    }
                    Text(stringResource(R.string.sensitivity_value, (state.sensitivity * 100).toInt()), fontWeight = FontWeight.SemiBold)
                    Slider(value = state.sensitivity, onValueChange = { vm.settings(sensitivity = it) })
                    Text(stringResource(R.string.rim_filter_hint), color = muted, fontSize = 12.sp, maxLines = 2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { display = DisplayMode.CAMERA }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = if (display == DisplayMode.CAMERA) Color(0xFF176B52) else Color(0xFF263F34))) {
                    Text(stringResource(R.string.camera), maxLines = 1)
                }
                Button(onClick = { display = DisplayMode.CIRCLE }, Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (display == DisplayMode.CIRCLE) Color(0xFF4B7864) else Color(0xFF263F34))) {
                    Text(stringResource(R.string.circle_sensitivity), fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
    if (showSave) AlertDialog(onDismissRequest = { showSave = false }, title = { Text(stringResource(R.string.save)) },
        text = { Column {
            Text(if (valid) stringResource(R.string.area_cm, result!!.areaMm2!! / 100) else stringResource(R.string.none))
            if (state.trackingLost) Text(stringResource(R.string.saving_last_reading))
            OutlinedTextField(value = sampleId, onValueChange = { sampleId = it.take(120) }, label = { Text(stringResource(R.string.sample_id)) }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = { vm.save(sampleId); showSave = false }, enabled = valid && !state.busy) { Text(stringResource(R.string.save_short)) } },
        dismissButton = { TextButton(onClick = { showSave = false }) { Text(stringResource(R.string.close)) } })
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text(stringResource(R.string.help)) },
        text = { Text(stringResource(R.string.help_body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.close)) } })
    if (showHistory) AlertDialog(onDismissRequest = { showHistory = false }, title = { Text(stringResource(R.string.history)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.history.isEmpty()) Text(stringResource(R.string.empty_history))
            state.history.forEach { record ->
                Column {
                    Text(record.sampleId.ifBlank { record.time.take(19).replace('T', ' ') }, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.area_cm, record.areaMm2 / 100))
                    if (record.synthetic) Text(stringResource(R.string.synthetic), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { shareFile(context, record.file) }) { Text(stringResource(R.string.share)) }
                    HorizontalDivider()
                }
            }
        } }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text(stringResource(R.string.close)) } })
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, context.getString(R.string.share)))
    } catch (error: Exception) {
        Log.e("LeafArea", "Sharing failed", error)
        Toast.makeText(context, R.string.storage_failed, Toast.LENGTH_LONG).show()
    }
}
