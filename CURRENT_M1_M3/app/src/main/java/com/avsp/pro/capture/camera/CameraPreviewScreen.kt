package com.avsp.pro.capture.camera

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.avsp.pro.R
import com.avsp.pro.capture.camera.composition.CompositionOverlay
import com.avsp.pro.capture.camera.control.IntelligentCameraControls
import com.avsp.pro.capture.camera.mission.DefaultShotMissionGenerator
import com.avsp.pro.capture.camera.mission.LiveEventDialog
import com.avsp.pro.capture.camera.mission.ShotMissionDialog
import com.avsp.pro.capture.camera.model.CameraControlMode
import com.avsp.pro.capture.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun CameraPreviewScreen(
    projectId: String,
    viewModel: CameraViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val voiceController = remember { VoiceGuidanceController(context) { AppPreferences.language } }

    val cameraController = remember { CameraController(context) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var selfTimerSeconds by remember { mutableIntStateOf(0) }
    var selfTimerRemaining by remember { mutableIntStateOf(0) }
    var selfTimerRunning by remember { mutableStateOf(false) }

    val timerLabel = when (selfTimerSeconds) {
        0 -> "Timer OFF"
        else -> "Timer ${selfTimerSeconds}s"
    }

    val missionPresets = remember { DefaultShotMissionGenerator().getPresetMissions() }

    fun safeFolderName(value: String): String = value.trim().ifBlank { "AVSP Project" }
        .replace(Regex("[^A-Za-z0-9 _-]"), "")
        .replace(Regex("\\s+"), " ")
        .take(60)

    val projectFolder = safeFolderName(uiState.projectTitle)
    val shotFolder = uiState.currentProfile.shotType.name
    val photoRelativePath = "Pictures/AVSP/$projectFolder/Photo/$shotFolder"
    val videoRelativePath = "Movies/AVSP/$projectFolder/Video/$shotFolder"


    LaunchedEffect(projectId) {
        viewModel.setProjectId(projectId)
    }

    // Accelerometer sensor for live leveling HUD
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val rollAngle = Math.toDegrees(Math.atan2(x.toDouble(), y.toDouble())).toFloat()
                    cameraController.setTiltRoll(rollAngle)
                    viewModel.onOrientationChanged(rollAngle)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    // Permission launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        viewModel.onPermissionResult(cameraGranted, audioGranted)
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        viewModel.onPermissionResult(cameraGranted, audioGranted)
        if (!cameraGranted || !audioGranted) {
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

    // Lightweight location metadata: keep the latest device location without adding a heavy dependency.
    LaunchedEffect(uiState.isPermissionGranted) {
        if (!uiState.isPermissionGranted) return@LaunchedEffect
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@LaunchedEffect
        while (true) {
            try {
                val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) {
                    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    val location = providers.mapNotNull { provider ->
                        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                    }.maxByOrNull { it.time }
                    if (location != null) {
                        viewModel.updateLocation(location.latitude, location.longitude, location.accuracy)
                    }
                }
            } catch (_: SecurityException) { }
            kotlinx.coroutines.delay(10_000)
        }
    }

    // Bind camera lifecycle with selected CameraProfile and Layer A VisualAnalyzer
    LaunchedEffect(
        uiState.isPermissionGranted,
        uiState.currentProfile.lensFacing,
        uiState.currentProfile.resolution,
        uiState.currentProfile.frameRate,
        previewViewRef
    ) {
        val previewView = previewViewRef
        if (uiState.isPermissionGranted && previewView != null) {
            viewModel.setInitializing(true)
            val result = cameraController.bindCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                profile = uiState.currentProfile,
                visualAnalyzer = viewModel.visualAnalyzer,
                onVisualAnalysis = { analysis ->
                    viewModel.onVisualAnalysisReceived(analysis)
                }
            )

            result.fold(
                onSuccess = {
                    val caps = cameraController.getCapabilities(uiState.currentProfile.lensFacing)
                    viewModel.updateCapabilities(caps)
                    viewModel.setCameraBound(true)
                    viewModel.setInitializing(false)
                    cameraController.applyShotTypeZoom(uiState.currentProfile.shotType)
                },
                onFailure = { error ->
                    viewModel.setCameraError(error.localizedMessage ?: "Failed to initialize camera.")
                }
            )
        }
    }

    // Subject-Aware Auto-Focus Trigger
    LaunchedEffect(
        uiState.decision.shouldFocusSubject,
        uiState.decision.targetFocusNormalizedX,
        uiState.decision.targetFocusNormalizedY
    ) {
        if (uiState.decision.shouldFocusSubject) {
            previewViewRef?.let { preview ->
                cameraController.focusOnSubject(
                    previewView = preview,
                    normalizedX = uiState.decision.targetFocusNormalizedX,
                    normalizedY = uiState.decision.targetFocusNormalizedY
                )
            }
        }
    }

    // Automatic Photo Capture Trigger Listener
    LaunchedEffect(Unit) {
        viewModel.autoCaptureEvent.collectLatest {
            if (uiState.captureMode == CaptureMode.PHOTO && !uiState.isCapturingPhoto && uiState.isBound) {
                viewModel.onPhotoCaptureStarted()
                cameraController.takePhoto(
                    relativePath = photoRelativePath,
                    onImageSaved = { uri, name ->
                        viewModel.onPhotoCaptured(uri, name)
                    },
                    onError = { exc ->
                        viewModel.onPhotoCaptureFailed(exc.localizedMessage ?: "Auto capture failed")
                    }
                )
            }
        }
    }

    // Zoom update listener
    LaunchedEffect(uiState.currentProfile.shotType) {
        cameraController.applyShotTypeZoom(uiState.currentProfile.shotType)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.unbind()
            voiceController.stop()
        }
    }

    LaunchedEffect(uiState.decision.guidance, uiState.decision.status, uiState.decision.shotType, AppPreferences.voiceGuidanceEnabled) {
        voiceController.speakDecision(
            uiState.decision,
            AppPreferences.voiceGuidanceEnabled && uiState.experienceMode != CameraExperienceMode.MANUAL
        )
    }

    // Capture Review Screen (M1.4.1)
    if (uiState.pendingReviewMedia != null) {
        CaptureReviewScreen(
            media = uiState.pendingReviewMedia!!,
            onKeep = {
                viewModel.keepPendingMedia(onCompleted = {
                    // Ready for next shot
                })
            },
            onRetake = {
                viewModel.retakePendingMedia(onCompleted = {
                    // Reset to live camera
                })
            },
            onDelete = {
                viewModel.deletePendingMedia(context, onCompleted = {
                    // Discard and reset
                })
            }
        )
        return
    }

    // Main Live Camera Screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        if (!uiState.isPermissionGranted) {
            // Permission Request State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.camera_permission_required),
                    color = Slate100,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AVSP requires camera and audio permissions for cinematography recording and real-time guidance.",
                    color = Slate400,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(id = R.string.grant_camera_permission), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Live PreviewView with Tap-To-Focus
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            previewViewRef?.let { preview ->
                                cameraController.tapToFocus(preview, offset.x, offset.y)
                            }
                        }
                    }
            )

            // Composition HUD & Overlays
            CompositionOverlay(
                profile = uiState.currentProfile,
                visualAnalysis = uiState.visualAnalysis,
                sceneUnderstanding = uiState.sceneUnderstanding,
                decision = uiState.decision,
                activeMissionShot = uiState.activeMission?.currentShot,
                autoCaptureHoldProgress = uiState.autoCaptureHoldProgress,
                isAutoCaptureEnabled = uiState.isAutoCaptureEnabled,
                onShotPlanClick = { viewModel.showMissionDialog(true) }
            )

            // Top Bar: Back, Project Title / Mission Status, Switch Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Slate950.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = Slate100)
                }

                // Current shot title — semantic shot-plan name first; framing remains technical.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate950.copy(alpha = 0.72f)
                ) {
                    val missionShot = uiState.activeMission?.currentShot
                    Text(
                        text = missionShot?.let { com.avsp.pro.capture.camera.guided.GuidedCaptureShotNaming.formatMissionShot(it) }
                            ?: "${uiState.currentProfile.shotType.displayName.uppercase()} SHOT",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        maxLines = 2
                    )
                }

                // Recording Duration Indicator (when recording)
                if (uiState.isRecording) {
                    val minutes = uiState.recordingDurationSeconds / 60
                    val seconds = uiState.recordingDurationSeconds % 60
                    val durationText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Rose500.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Text(
                                text = "REC $durationText",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Self Timer lives in the persistent top camera bar so it never covers
                // AI SHOT / SHOT LIST / MANUAL controls or the AI instruction plate.
                Surface(
                    modifier = Modifier
                        .clickable(enabled = !uiState.isRecording && !selfTimerRunning) {
                            selfTimerSeconds = when (selfTimerSeconds) {
                                0 -> 3
                                3 -> 5
                                5 -> 10
                                else -> 0
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selfTimerSeconds > 0) Indigo500.copy(alpha = 0.9f) else Slate950.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = "Self Timer", tint = Color.White, modifier = Modifier.size(15.dp))
                        Text(
                            text = if (selfTimerSeconds == 0) "Timer" else "${selfTimerSeconds}s",
                            color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleLensFacing() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Slate950.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = stringResource(id = R.string.switch_camera),
                        tint = Slate100
                    )
                }
            }

            // Primary capture mode: always visible and never hidden behind advanced controls.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp),
                shape = RoundedCornerShape(14.dp),
                color = Slate950.copy(alpha = 0.88f)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOfNotNull(
                        CameraExperienceMode.AI_AUTO to "AI SHOT",
                        if (!uiState.isShotListTemporarilyHidden) CameraExperienceMode.AI_SHOT_LIST to "SHOT LIST" else null,
                        CameraExperienceMode.MANUAL to "MANUAL"
                    ).forEach { (mode, label) ->
                        val selected = uiState.experienceMode == mode
                        Text(
                            text = label,
                            color = if (selected) Color.White else Slate400,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Indigo500 else Color.Transparent)
                                .clickable {
                                    viewModel.setExperienceMode(mode)
                                    if (mode == CameraExperienceMode.AI_SHOT_LIST) viewModel.showMissionDialog(true)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Full-screen AI plan header: user-facing plan language only.
            uiState.activeMission?.let { mission ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 124.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Indigo500, modifier = Modifier.size(17.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mission.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(
                                text = "${mission.capturedShots.size}/${mission.shots.size} complete  •  ${mission.currentShot?.title ?: "Choose a shot"}",
                                color = Slate300, fontSize = 9.sp, maxLines = 1
                            )
                        }
                    }
                }
            }

            // Voice is an explicit user control; it is off by default.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 124.dp, end = 16.dp),
                shape = CircleShape,
                color = Slate950.copy(alpha = 0.75f)
            ) {
                IconButton(onClick = {
                    val enabled = !AppPreferences.voiceGuidanceEnabled
                    AppPreferences.setVoiceGuidance(context, enabled)
                }) {
                    Icon(
                        imageVector = if (AppPreferences.voiceGuidanceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Voice guidance",
                        tint = if (AppPreferences.voiceGuidanceEnabled) Emerald500 else Slate300
                    )
                }
            }

            // Visible countdown so the user knows exactly when capture will happen.
            if (selfTimerRunning && selfTimerRemaining > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.85f))
                ) {
                    Text(
                        text = selfTimerRemaining.toString(),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(28.dp)
                    )
                }
            }

            // Bottom Controls Area: Format Bar + Mode Switcher + Shutter
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Technical W/M/C controls stay internal to AI modes. Manual mode exposes them.
                if (uiState.experienceMode == CameraExperienceMode.MANUAL || uiState.activeMission == null) {
                    IntelligentCameraControls(
                        profile = uiState.currentProfile,
                        capabilities = uiState.capabilities,
                        isTorchOn = uiState.isTorchOn,
                        isRecording = uiState.isRecording,
                        isAutoCaptureEnabled = uiState.isAutoCaptureEnabled,
                        onShotTypeSelected = { shotType -> viewModel.setShotType(shotType) },
                        onResolutionSelected = { res -> viewModel.setResolution(res) },
                        onFrameRateSelected = { fps -> viewModel.setFrameRate(fps) },
                        onToggleControlMode = {
                            val next = if (uiState.currentProfile.controlMode == CameraControlMode.AUTO) CameraControlMode.MANUAL else CameraControlMode.AUTO
                            viewModel.setControlMode(next)
                        },
                        onToggleAutoCapture = { viewModel.toggleAutoCapture() },
                        onToggleTorch = {
                            viewModel.toggleTorch { isEnabled -> cameraController.toggleTorch(isEnabled) }
                        },
                        onToggleGrid = { viewModel.toggleGridOverlay() },
                        onToggleHorizon = { viewModel.toggleHorizonLevel() },
                        onOpenMissionDialog = { viewModel.showMissionDialog(true) },
                        onOpenEventDialog = { viewModel.showEventDialog(true) }
                    )
                } else {
                    // Minimal AI capture controls: torch/grid/level remain available through the plan button.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (uiState.captureMode == CaptureMode.PHOTO) "PHOTO" else "VIDEO",
                            color = Slate300, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                        Text("AI controls active", color = Slate500, fontSize = 9.sp)
                    }
                }
                // Mode Selector: PHOTO vs VIDEO
                if (!uiState.isRecording) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PHOTO",
                            color = if (uiState.captureMode == CaptureMode.PHOTO) Amber500 else Slate400,
                            fontWeight = if (uiState.captureMode == CaptureMode.PHOTO) FontWeight.Black else FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setCaptureMode(CaptureMode.PHOTO) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "VIDEO",
                            color = if (uiState.captureMode == CaptureMode.VIDEO) Amber500 else Slate400,
                            fontWeight = if (uiState.captureMode == CaptureMode.VIDEO) FontWeight.Black else FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setCaptureMode(CaptureMode.VIDEO) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                uiState.captureMode == CaptureMode.PHOTO -> Color.White
                                uiState.isRecording -> Rose500
                                else -> Rose500
                            }
                        )
                        .clickable(
                            enabled = !uiState.isCapturingPhoto && uiState.isBound && !selfTimerRunning,
                            onClick = {
                                if (uiState.isRecording) {
                                    cameraController.stopRecording()
                                } else if (selfTimerSeconds > 0 && !selfTimerRunning) {
                                    selfTimerRunning = true
                                    selfTimerRemaining = selfTimerSeconds
                                    scope.launch {
                                        while (selfTimerRemaining > 0) {
                                            delay(1000)
                                            selfTimerRemaining -= 1
                                        }
                                        selfTimerRunning = false
                                        if (uiState.captureMode == CaptureMode.PHOTO) {
                                            viewModel.onPhotoCaptureStarted()
                                            cameraController.takePhoto(
                                                relativePath = photoRelativePath,
                                                onImageSaved = { uri, name -> viewModel.onPhotoCaptured(uri, name) },
                                                onError = { exc -> viewModel.onPhotoCaptureFailed(exc.localizedMessage ?: "Photo capture failed") }
                                            )
                                        } else {
                                            cameraController.startRecording(
                                                relativePath = videoRelativePath,
                                                onEvent = { event ->
                                                    when (event) {
                                                        is VideoRecordEvent.Start -> viewModel.onRecordingStarted()
                                                        is VideoRecordEvent.Finalize -> {
                                                            if (event.hasError()) viewModel.onRecordingFailed("Error: ${event.error}")
                                                            else viewModel.onRecordingFinalized(event.outputResults.outputUri, "AVSP_VID_${System.currentTimeMillis()}.mp4")
                                                        }
                                                    }
                                                },
                                                onError = { exc -> viewModel.onRecordingFailed(exc.localizedMessage ?: "Video recording error") }
                                            )
                                        }
                                    }
                                } else if (uiState.captureMode == CaptureMode.PHOTO) {
                                    viewModel.onPhotoCaptureStarted()
                                    cameraController.takePhoto(
                                        relativePath = photoRelativePath,
                                        onImageSaved = { uri, name -> viewModel.onPhotoCaptured(uri, name) },
                                        onError = { exc -> viewModel.onPhotoCaptureFailed(exc.localizedMessage ?: "Photo capture failed") }
                                    )
                                } else {
                                    cameraController.startRecording(
                                        relativePath = videoRelativePath,
                                        onEvent = { event ->
                                            when (event) {
                                                is VideoRecordEvent.Start -> viewModel.onRecordingStarted()
                                                is VideoRecordEvent.Finalize -> {
                                                    if (event.hasError()) viewModel.onRecordingFailed("Error: ${event.error}")
                                                    else viewModel.onRecordingFinalized(event.outputResults.outputUri, "AVSP_VID_${System.currentTimeMillis()}.mp4")
                                                }
                                            }
                                        },
                                        onError = { exc -> viewModel.onRecordingFailed(exc.localizedMessage ?: "Video recording error") }
                                    )
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isRecording) {
                        // Square stop icon
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // AI Mission Dialog (Plan Management & Creation)
            if (uiState.showMissionDialog) {
                ShotMissionDialog(
                    onDismiss = { viewModel.showMissionDialog(false) },
                    onStartContextMission = { contextText ->
                        viewModel.createM17ShotPlan(contextText)
                    },
                    onSelectPresetMission = { preset ->
                        viewModel.loadPresetMission(preset)
                    },
                    presets = missionPresets,
                    activeMission = uiState.activeMission,
                    // Selecting a shot immediately closes the list. The selected shot
                    // is then locked as the current execution target until it is
                    // completed/reviewed; the list becomes available again afterwards.
                    onPromoteShot = { shotId ->
                        viewModel.promoteShot(shotId)
                        viewModel.showMissionDialog(false)
                    },
                    onSkipCurrentShot = { viewModel.skipCurrentShot() },
                    onCaptureModeSelected = { mode -> viewModel.setCaptureMode(mode) }
                )
            }

            // Live Event / Opportunity Dialog
            if (uiState.showEventDialog) {
                LiveEventDialog(
                    onDismiss = { viewModel.showEventDialog(false) },
                    onReportEvent = { eventText ->
                        viewModel.reportLiveEvent(eventText)
                    }
                )
            }
        }
    }
}

