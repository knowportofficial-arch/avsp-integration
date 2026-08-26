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
import com.avsp.pro.dataset.model.Recommendation

@Composable
fun GuidedCaptureScreen(
    session: GuidedCapturePlanAdapter.GuidedSession,
    planContext: String? = null,
    onFinished: (List<ClipMetadata>) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: GuidedCaptureViewModel = viewModel(
        factory = GuidedCaptureViewModelFactory(context)
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(session.planId) {
        viewModel.loadSession(session, planContext)
    }

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
            ErrorView(
                message = state.errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadSession(session, planContext) },
                onExit = onExit
            )
            return@Box
        }
        if (state.phase == GuidedCapturePhase.COMPLETE) {
            CompleteView(state.completedClips, onFinished)
            return@Box
        }

        AndroidView(
            factory = { ctx -> PreviewView(ctx).also { previewViewRef = it } },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isGridEnabled) {
            GuidedGridOverlay(modifier = Modifier.fillMaxSize())
        }

        // Top bar: preserved naming + shot progress
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.currentClip?.let { clip ->
                    Surface(shape = RoundedCornerShape(8.dp), color = Slate900.copy(alpha = 0.8f)) {
                        // Preserved naming contract — semantic name first:
                        // "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
                        Text(
                            text = clip.captureInstruction(),
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
                        Icon(
                            Icons.Default.GridOn,
                            contentDescription = "Grid",
                            tint = if (state.isGridEnabled) Emerald500 else Slate400
                        )
                    }
                }
            }
            if (state.shotProgressLabel.isNotBlank()) {
                Text(
                    text = state.shotProgressLabel,
                    color = Slate300,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            // Live READY / NOT READY from ML Kit + decision engine — not "camera open"
            if (state.phase == GuidedCapturePhase.READY || state.phase == GuidedCapturePhase.COUNTDOWN) {
                val guidance = state.liveGuidance
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (guidance.isReady) Emerald500.copy(alpha = 0.85f) else Rose500.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = guidance.headline,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = guidance.message,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        if (state.phase == GuidedCapturePhase.COUNTDOWN) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "${state.countdownSecondsRemaining}",
                    color = Color.White,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        if (state.phase == GuidedCapturePhase.RECORDING) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp, start = 16.dp, end = 16.dp)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.phase == GuidedCapturePhase.REVIEW) {
                val rec = state.lastRecommendation
                Text(
                    text = when (rec) {
                        Recommendation.KEEP -> "KEEP · ${state.lastQualityPercent}%"
                        Recommendation.REVIEW -> "REVIEW · ${state.lastQualityPercent}%"
                        Recommendation.RETAKE -> "RETAKE · ${state.lastQualityPercent}%"
                        null -> "Review capture"
                    },
                    color = when (rec) {
                        Recommendation.KEEP -> Emerald500
                        Recommendation.REVIEW -> Slate100
                        Recommendation.RETAKE -> Rose500
                        null -> Slate200
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (state.phase == GuidedCapturePhase.INSUFFICIENT_DURATION) {
                Text(
                    text = state.insufficientDurationMessage
                        ?: "Insufficient duration — retake required.",
                    color = Rose500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        // Stop only — KEEP must never appear while the duration timer is active.
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
                    GuidedCapturePhase.INSUFFICIENT_DURATION -> {
                        OutlinedButton(
                            onClick = { viewModel.retake() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose500)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retake")
                            Spacer(Modifier.width(6.dp))
                            Text("RETAKE")
                        }
                    }
                    GuidedCapturePhase.REVIEW -> {
                        OutlinedButton(
                            onClick = { viewModel.retake() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose500)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retake")
                            Spacer(Modifier.width(6.dp))
                            Text("RETAKE")
                        }
                        // KEEP only when duration was met and quality recommends KEEP.
                        // REVIEW / NEXT remain available for non-KEEP recommendations after a valid take.
                        if (state.keepEligible) {
                            Button(
                                onClick = { viewModel.acceptAndAdvance() },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Keep")
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.isLastClip) "KEEP · FINISH" else "KEEP · NEXT")
                            }
                        } else if (state.durationSatisfied &&
                            state.lastRecommendation != Recommendation.RETAKE
                        ) {
                            Button(
                                onClick = { viewModel.acceptAndAdvance() },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Accept")
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when {
                                        state.lastRecommendation == Recommendation.REVIEW && state.isLastClip ->
                                            "REVIEW · FINISH"
                                        state.lastRecommendation == Recommendation.REVIEW ->
                                            "REVIEW · NEXT"
                                        state.isLastClip -> "FINISH"
                                        else -> "NEXT"
                                    }
                                )
                            }
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
