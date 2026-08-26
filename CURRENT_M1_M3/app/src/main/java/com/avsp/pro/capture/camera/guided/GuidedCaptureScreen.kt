package com.avsp.pro.capture.camera.guided

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avsp.pro.capture.theme.*

@Composable
fun GuidedCaptureScreen(
    template: GuidedCaptureTemplate,
    onFinished: (List<ClipMetadata>) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: GuidedCaptureViewModel = viewModel(
        factory = GuidedCaptureViewModelFactory(context)
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(template) { viewModel.loadTemplate(template) }

    // Orientation sensor (M6 feature #12)
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val roll = Math.toDegrees(Math.atan2(x.toDouble(), y.toDouble())).toFloat()
                    viewModel.onDeviceOrientationChanged(roll.toInt())
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    // Permissions (M6 test #11)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        viewModel.onPermissionResult(cameraGranted, audioGranted)
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(cameraGranted, audioGranted)
        if (!cameraGranted) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    // CRITICAL: do NOT rebind when leaving READY → RECORDING/COUNTDOWN.
    // The previous key used `phase == READY` (Boolean). Leaving READY flipped that key,
    // restarted this effect, and called bindCamera() → ProcessCameraProvider.unbindAll()
    // while VideoCapture was recording — CameraX Finalize ERROR_SOURCE_INACTIVE (4).
    LaunchedEffect(state.currentClipIndex, previewViewRef, state.phase) {
        val pv = previewViewRef ?: return@LaunchedEffect
        if (GuidedCaptureVideoPolicy.shouldBindCamera(state.phase)) {
            viewModel.bindCamera(lifecycleOwner, pv)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (state.phase == GuidedCapturePhase.PERMISSION_REQUIRED) {
            PermissionRequiredView(onExit)
            return@Box
        }
        if (state.phase == GuidedCapturePhase.ERROR) {
            ErrorView(state.errorMessage ?: "Unknown error", onRetry = { viewModel.loadTemplate(template) }, onExit = onExit)
            return@Box
        }
        if (state.phase == GuidedCapturePhase.COMPLETE) {
            CompleteView(state.completedClips, onFinished)
            return@Box
        }

        // Camera preview
        AndroidView(
            factory = { ctx -> PreviewView(ctx).also { previewViewRef = it } },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isGridEnabled) {
            GuidedGridOverlay(modifier = Modifier.fillMaxSize())
        }

        // Top bar: clip name/category, aspect ratio, flash, grid
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.currentClip?.let { clip ->
                Surface(shape = RoundedCornerShape(8.dp), color = Slate900.copy(alpha = 0.8f)) {
                    Text(
                        text = "${clip.clipName} • ${clip.category} • ${clip.aspectRatio.displayName} • ${clip.targetDurationSeconds}s",
                        color = Slate100,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { viewModel.toggleFlash() }) {
                    Icon(
                        if (state.isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Slate100
                    )
                }
                IconButton(onClick = { viewModel.toggleGrid() }) {
                    Icon(Icons.Default.GridOn, contentDescription = "Grid", tint = if (state.isGridEnabled) Emerald500 else Slate400)
                }
            }
        }

        // Countdown overlay
        if (state.phase == GuidedCapturePhase.COUNTDOWN) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${state.countdownSecondsRemaining}", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black)
            }
        }

        // Recording progress (M6 feature #19)
        if (state.phase == GuidedCapturePhase.RECORDING) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 64.dp, start = 16.dp, end = 16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Rose500,
                    trackColor = Slate800
                )
                Text(
                    text = "${state.elapsedMs / 1000}s / ${state.targetDurationMs / 1000}s",
                    color = Slate200,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Exposure slider (M6 feature #14)
        if (state.exposureRange.first != 0 || state.exposureRange.last != 0) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).height(200.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Slider(
                    value = state.exposureIndex.toFloat(),
                    onValueChange = { viewModel.setExposureIndex(it.toInt()) },
                    valueRange = state.exposureRange.first.toFloat()..state.exposureRange.last.toFloat(),
                    steps = (state.exposureRange.last - state.exposureRange.first - 1).coerceAtLeast(0),
                    modifier = Modifier.width(160.dp).rotate(-90f)
                )
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state.phase) {
                GuidedCapturePhase.READY -> {
                    Button(
                        onClick = { viewModel.beginCapture() },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {}
                }
                GuidedCapturePhase.RECORDING -> {
                    Button(
                        onClick = { viewModel.stopRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(72.dp)
                    ) {}
                }
                GuidedCapturePhase.COUNTDOWN -> {
                    OutlinedButton(onClick = { viewModel.cancelCountdown() }) { Text("Cancel") }
                }
                GuidedCapturePhase.REVIEW -> {
                    OutlinedButton(onClick = { viewModel.retake() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose500)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retake")
                        Spacer(Modifier.width(6.dp))
                        Text("RETAKE")
                    }
                    Button(onClick = { viewModel.acceptAndAdvance() }, colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) {
                        Icon(Icons.Default.Check, contentDescription = "Keep")
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isLastClip) "FINISH" else "NEXT")
                    }
                }
                GuidedCapturePhase.SAVING -> {
                    CircularProgressIndicator(color = Slate100)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PermissionRequiredView(onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is required for guided capture.", color = Slate100)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onExit) { Text("Back") }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = Slate100, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onExit) { Text("Exit") }
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun CompleteView(clips: List<ClipMetadata>, onFinished: (List<ClipMetadata>) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald500, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("${clips.size} clips captured", color = Slate100, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onFinished(clips) }, colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) {
                Text("Done")
            }
        }
    }
}

