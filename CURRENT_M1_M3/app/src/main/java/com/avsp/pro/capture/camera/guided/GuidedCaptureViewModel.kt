package com.avsp.pro.capture.camera.guided

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.camera.core.ImageProxy
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.capture.camera.CameraController
import com.avsp.pro.capture.camera.model.CameraProfile
import com.avsp.pro.capture.camera.vision.VisualAnalysisResult
import com.avsp.pro.capture.camera.vision.VisualAnalyzer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * M6 — Guided Capture ViewModel.
 *
 * Independently runnable/testable: does not depend on the existing AI CameraViewModel,
 * ShotMission engine, or vision/planner subsystems. Consumes a plain [GuidedCaptureTemplate]
 * and produces MP4 + metadata.json + thumbnail.jpg per clip via [GuidedCaptureStorage].
 *
 * android:image-analysis is bound (CameraController.bindCamera requires a VisualAnalyzer) but
 * this ViewModel supplies a no-op analyzer -- no AI tagging or quality analysis is performed
 * here, per the M6 spec constraint. M7 is responsible for any downstream analysis.
 */
class GuidedCaptureViewModel(
    private val appContext: Context,
    private val cameraController: CameraController = CameraController(appContext),
    private val storage: GuidedCaptureStorage = GuidedCaptureStorage(appContext)
) : ViewModel() {

    private val _state = MutableStateFlow(GuidedCaptureState())
    val state: StateFlow<GuidedCaptureState> = _state

    private val selfTimer = SelfTimer()
    private var recordingProgressJob: kotlinx.coroutines.Job? = null
    private var recordingStartedAtMs: Long = 0L

    /**
     * M6.2 (FIX 3) — hardens the narrow lifecycle edge case where a self-timer's countdown
     * completion callback could already be queued on the main thread at the moment the
     * ViewModel is cleared (back navigation / activity destruction). Set as the very first
     * statement in onCleared(), checked as the very first statement in
     * startCaptureForCurrentClip() -- the only entry point a stale timer callback can reach.
     * This does not change SelfTimer's design; it's a guard at the call site.
     */
    @Volatile
    private var isCleared = false

    private val noOpVisualAnalyzer = object : VisualAnalyzer {
        override fun analyzeFrame(imageProxy: ImageProxy, tiltRollDegrees: Float): VisualAnalysisResult {
            imageProxy.close()
            return VisualAnalysisResult()
        }
        override fun close() {}
    }

    fun loadTemplate(template: GuidedCaptureTemplate) {
        _state.update {
            GuidedCaptureState(
                template = template,
                currentClipIndex = 0,
                phase = GuidedCapturePhase.READY
            )
        }
    }

    /** Test #11 — permission denial path. Call after the caller's permission-request flow resolves. */
    fun onPermissionResult(cameraGranted: Boolean, audioGranted: Boolean) {
        if (!cameraGranted) {
            _state.update {
                it.copy(
                    phase = GuidedCapturePhase.ERROR,
                    errorMessage = "Camera permission was denied. Guided capture requires camera access."
                )
            }
            return
        }
        // Audio denial is non-fatal: recording proceeds without sound (matches existing CameraController behavior).
        _state.update { it.copy(phase = GuidedCapturePhase.READY) }
    }

    suspend fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        // Never rebind while a Guided Capture recording is in flight — that detaches
        // VideoCapture and finalizes with ERROR_SOURCE_INACTIVE (4).
        if (cameraController.isRecordingActive()) return
        if (!GuidedCaptureVideoPolicy.shouldBindCamera(_state.value.phase)) return

        val clip = _state.value.currentClip ?: return
        val shotType = clip.toCameraShotType()
        val profile = CameraProfile(
            shotType = shotType,
            resolution = clip.resolution,
            frameRate = clip.frameRate,
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            isGridOverlayEnabled = _state.value.isGridEnabled,
            preferredAspectRatio = clip.aspectRatio,
            enforceRequestedFrameRate = true
        )
        val result = cameraController.bindCamera(lifecycleOwner, previewView, profile, noOpVisualAnalyzer) {}
        result.onFailure { e ->
            _state.update { it.copy(phase = GuidedCapturePhase.ERROR, errorMessage = e.message ?: "Failed to bind camera") }
            return
        }
        // M6.1: request the clip's orientation be applied to capture output (see
        // CameraController.applyOrientation KDoc for exactly what this can and can't guarantee).
        cameraController.applyOrientation(clip.orientation)
        // Reuse existing CameraShotType zoom contract for WIDE/MEDIUM/CLOSE categories.
        cameraController.applyShotTypeZoom(shotType)
        val range = cameraController.exposureCompensationRange() ?: 0..0
        _state.update { it.copy(exposureIndex = 0, exposureRange = range) }
    }

    fun toggleFlash() {
        val next = !_state.value.isFlashOn
        cameraController.toggleTorch(next)
        _state.update { it.copy(isFlashOn = next) }
    }

    fun toggleGrid() {
        _state.update { it.copy(isGridEnabled = !it.isGridEnabled) }
    }

    fun onDeviceOrientationChanged(degrees: Int) {
        _state.update { it.copy(deviceOrientationDegrees = degrees) }
    }

    fun setExposureIndex(index: Int) {
        cameraController.setExposureCompensation(index)
        _state.update { it.copy(exposureIndex = index) }
    }

    /** M6 feature #11: starts the self-timer (if the clip specifies one), then begins capture. */
    fun beginCapture() {
        val clip = _state.value.currentClip ?: return
        if (clip.timerSeconds > 0) {
            _state.update { it.copy(phase = GuidedCapturePhase.COUNTDOWN, countdownSecondsRemaining = clip.timerSeconds) }
            selfTimer.start(
                scope = viewModelScope,
                seconds = clip.timerSeconds,
                onTick = { remaining -> _state.update { it.copy(countdownSecondsRemaining = remaining) } },
                onFinished = { startCaptureForCurrentClip() }
            )
        } else {
            startCaptureForCurrentClip()
        }
    }

    fun cancelCountdown() {
        selfTimer.cancel()
        _state.update { it.copy(phase = GuidedCapturePhase.READY, countdownSecondsRemaining = 0) }
    }

    private fun startCaptureForCurrentClip() {
        if (isCleared) return // M6.2 (FIX 3): a stale self-timer callback must not start a recording
        val template = _state.value.template ?: return
        val clip = _state.value.currentClip ?: return

        val prepared = storage.prepareClipStorage(template.templateId, clip.clipId)
        if (prepared is GuidedCaptureStorage.StorageResult.Failed) {
            // Test #12 — storage failure path.
            _state.update { it.copy(phase = GuidedCapturePhase.ERROR, errorMessage = "Storage error: ${prepared.reason}") }
            return
        }
        val ready = prepared as GuidedCaptureStorage.StorageResult.Ready

        when (clip.mediaType) {
            GuidedClipMediaType.VIDEO -> startVideoCapture(clip, ready)
            GuidedClipMediaType.PHOTO -> startPhotoCapture(clip, ready)
        }
    }

    private fun startVideoCapture(clip: GuidedClipSpec, ready: GuidedCaptureStorage.StorageResult.Ready) {
        _state.update {
            it.copy(
                phase = GuidedCapturePhase.RECORDING,
                elapsedMs = 0L,
                targetDurationMs = clip.targetDurationSeconds * 1000L
            )
        }
        recordingStartedAtMs = System.currentTimeMillis()

        cameraController.startRecordingToFile(
            targetFile = ready.videoFile,
            onEvent = { event -> handleVideoRecordEvent(event, clip, ready) },
            onError = { e ->
                // Test #13 — interrupted recording path.
                stopRecordingProgressTicker()
                _state.update { it.copy(phase = GuidedCapturePhase.ERROR, errorMessage = "Recording failed: ${e.message}") }
            }
        )

        recordingProgressJob = viewModelScope.launch {
            while (_state.value.phase == GuidedCapturePhase.RECORDING) {
                val elapsed = System.currentTimeMillis() - recordingStartedAtMs
                _state.update { it.copy(elapsedMs = elapsed) }
                if (elapsed >= clip.targetDurationSeconds * 1000L) {
                    stopRecording()
                    break
                }
                kotlinx.coroutines.delay(100L)
            }
        }
    }

    private fun handleVideoRecordEvent(
        event: VideoRecordEvent,
        clip: GuidedClipSpec,
        ready: GuidedCaptureStorage.StorageResult.Ready
    ) {
        when (event) {
            is VideoRecordEvent.Finalize -> {
                stopRecordingProgressTicker()
                if (event.hasError()) {
                    // ERROR_SOURCE_INACTIVE (4): CameraX may still have written frames before
                    // detach. Recover when the output file looks usable; otherwise fail cleanly.
                    if (GuidedCaptureVideoPolicy.isRecoverableFinalizeError(event.error, ready.videoFile)) {
                        finalizeVideoClip(clip, ready)
                        return
                    }
                    GuidedCaptureVideoPolicy.cleanupPartialVideo(ready.videoFile)
                    _state.update {
                        it.copy(
                            phase = GuidedCapturePhase.ERROR,
                            errorMessage = "Recording finalized with error code ${event.error}"
                        )
                    }
                    return
                }
                finalizeVideoClip(clip, ready)
            }
            else -> Unit
        }
    }

    /** Public so the UI's "stop early" control can end a clip before its target duration. */
    fun stopRecording() {
        cameraController.stopRecording()
    }

    private fun stopRecordingProgressTicker() {
        recordingProgressJob?.cancel()
        recordingProgressJob = null
    }

    private fun finalizeVideoClip(clip: GuidedClipSpec, ready: GuidedCaptureStorage.StorageResult.Ready) {
        _state.update { it.copy(phase = GuidedCapturePhase.SAVING) }
        viewModelScope.launch {
            // Reject unplayable / truncated containers even if Finalize reported success
            // or SOURCE_INACTIVE recovery left a non-empty file on disk.
            when (val validation = GuidedCaptureVideoValidator.validate(ready.videoFile)) {
                is GuidedCaptureVideoValidator.Result.Unplayable -> {
                    GuidedCaptureVideoPolicy.cleanupPartialVideo(ready.videoFile)
                    _state.update {
                        it.copy(
                            phase = GuidedCapturePhase.ERROR,
                            errorMessage = "Recorded video is not playable: ${validation.reason}"
                        )
                    }
                    return@launch
                }
                is GuidedCaptureVideoValidator.Result.Playable -> {
                    val thumbOk = storage.writeThumbnail(ready.videoFile, ready.thumbFile)
                    val wallClockMs = System.currentTimeMillis() - recordingStartedAtMs
                    val durationMs = validation.durationMs.takeIf { it > 0L } ?: wallClockMs
                    val location = bestEffortLocation()
                    val inspected = validation.inspected

                    val requestedWidth = clip.resolution.width
                    val requestedHeight = clip.resolution.height
                    val requestedFps = clip.frameRate.targetFps
                    val requestedAspectLabel = clip.aspectRatio.displayName

                    val fpsVerified = inspected.measuredFps != null
                    val effectiveFps = inspected.measuredFps?.let { Math.round(it).toInt() } ?: requestedFps

                    val actualAspectClassification =
                        AspectRatioValidator.classify(inspected.displayWidth, inspected.displayHeight)
                    val actualAspectLabel = when (actualAspectClassification) {
                        AspectRatioValidator.Classification.PORTRAIT_9_16 -> "9:16"
                        AspectRatioValidator.Classification.LANDSCAPE_16_9 -> "16:9"
                        AspectRatioValidator.Classification.OTHER -> "OTHER"
                    }
                    val aspectMatchesRequest = AspectRatioValidator.matches(
                        inspected.displayWidth,
                        inspected.displayHeight,
                        clip.aspectRatio
                    )
                    val actualOrientationLabel =
                        if (inspected.displayWidth < inspected.displayHeight) "PORTRAIT" else "LANDSCAPE"

                    val metadata = ClipMetadata(
                        clipId = clip.clipId,
                        clipName = clip.clipName,
                        category = clip.category,
                        date = GuidedCaptureStorage.currentDate(),
                        time = GuidedCaptureStorage.currentTime(),
                        durationMs = durationMs,
                        width = inspected.displayWidth,
                        height = inspected.displayHeight,
                        fps = effectiveFps,
                        orientation = clip.orientation.name,
                        requestedWidth = requestedWidth,
                        requestedHeight = requestedHeight,
                        requestedFps = requestedFps,
                        requestedAspectRatio = requestedAspectLabel,
                        actualFps = inspected.measuredFps,
                        actualAspectRatio = actualAspectLabel,
                        actualOrientation = actualOrientationLabel,
                        dimensionsVerified = true,
                        fpsVerified = fpsVerified,
                        aspectRatioMatchesRequest = aspectMatchesRequest,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        device = storage.deviceLabel(),
                        file = ready.videoFile.absolutePath
                    )
                    val jsonOk = storage.writeMetadataJson(ready.jsonFile, metadata)
                    if (!jsonOk) {
                        _state.update {
                            it.copy(
                                phase = GuidedCapturePhase.ERROR,
                                errorMessage = "Failed to write clip metadata JSON."
                            )
                        }
                        return@launch
                    }

                    _state.update {
                        it.copy(
                            phase = GuidedCapturePhase.REVIEW,
                            lastRecordedFile = ready.videoFile.absolutePath,
                            lastRecordedThumbnail = if (thumbOk) ready.thumbFile.absolutePath else null,
                            completedClips = it.completedClips + metadata
                        )
                    }
                }
            }
        }
    }

    private fun startPhotoCapture(clip: GuidedClipSpec, ready: GuidedCaptureStorage.StorageResult.Ready) {
        _state.update { it.copy(phase = GuidedCapturePhase.SAVING) }
        cameraController.takePhotoToFile(
            targetFile = File(ready.clipDir, "clip.jpg"),
            onImageSaved = { file ->
                viewModelScope.launch {
                    val location = bestEffortLocation()
                    val inspected = ClipInspector.inspectPhoto(file)

                    val requestedWidth = clip.resolution.width
                    val requestedHeight = clip.resolution.height
                    val requestedAspectLabel = clip.aspectRatio.displayName
                    val dimensionsVerified = inspected != null

                    val effectiveWidth = inspected?.width ?: requestedWidth
                    val effectiveHeight = inspected?.height ?: requestedHeight

                    val actualAspectClassification = inspected?.let { AspectRatioValidator.classify(it.width, it.height) }
                    val actualAspectLabel = when (actualAspectClassification) {
                        AspectRatioValidator.Classification.PORTRAIT_9_16 -> "9:16"
                        AspectRatioValidator.Classification.LANDSCAPE_16_9 -> "16:9"
                        AspectRatioValidator.Classification.OTHER -> "OTHER"
                        null -> null
                    }
                    val aspectMatchesRequest = inspected?.let {
                        AspectRatioValidator.matches(it.width, it.height, clip.aspectRatio)
                    }
                    val actualOrientationLabel = inspected?.let {
                        if (it.width < it.height) "PORTRAIT" else "LANDSCAPE"
                    }

                    val metadata = ClipMetadata(
                        clipId = clip.clipId,
                        clipName = clip.clipName,
                        category = clip.category,
                        date = GuidedCaptureStorage.currentDate(),
                        time = GuidedCaptureStorage.currentTime(),
                        durationMs = 0L,
                        width = effectiveWidth,
                        height = effectiveHeight,
                        fps = 0,
                        orientation = clip.orientation.name,
                        requestedWidth = requestedWidth,
                        requestedHeight = requestedHeight,
                        requestedFps = 0,
                        requestedAspectRatio = requestedAspectLabel,
                        actualFps = null, // not applicable to still photos
                        actualAspectRatio = actualAspectLabel,
                        actualOrientation = actualOrientationLabel,
                        dimensionsVerified = dimensionsVerified,
                        fpsVerified = false, // fps is N/A for photos, never claimed verified
                        aspectRatioMatchesRequest = aspectMatchesRequest,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        device = storage.deviceLabel(),
                        file = file.absolutePath
                    )
                    val jsonOk = storage.writeMetadataJson(ready.jsonFile, metadata)
                    if (!jsonOk) {
                        _state.update { it.copy(phase = GuidedCapturePhase.ERROR, errorMessage = "Failed to write clip metadata JSON.") }
                        return@launch
                    }
                    _state.update {
                        it.copy(
                            phase = GuidedCapturePhase.REVIEW,
                            lastRecordedFile = file.absolutePath,
                            lastRecordedThumbnail = file.absolutePath,
                            completedClips = it.completedClips + metadata
                        )
                    }
                }
            },
            onError = { e ->
                _state.update { it.copy(phase = GuidedCapturePhase.ERROR, errorMessage = "Photo capture failed: ${e.message}") }
            }
        )
    }

    /** M6 feature #18 — retake: discards the just-captured clip and re-enters capture for the same clip. */
    fun retake() {
        val lastMetadata = _state.value.completedClips.lastOrNull()
        if (lastMetadata != null) {
            try { File(lastMetadata.file).delete() } catch (_: Exception) {}
        }
        _state.update {
            it.copy(
                phase = GuidedCapturePhase.READY,
                elapsedMs = 0L,
                lastRecordedFile = null,
                lastRecordedThumbnail = null,
                completedClips = it.completedClips.dropLast(1)
            )
        }
    }

    /** Accepts the last captured clip and advances to the next clip, or COMPLETE if this was the last. */
    fun acceptAndAdvance() {
        val state = _state.value
        if (state.isLastClip) {
            _state.update { it.copy(phase = GuidedCapturePhase.COMPLETE) }
        } else {
            _state.update {
                it.copy(
                    phase = GuidedCapturePhase.READY,
                    currentClipIndex = it.currentClipIndex + 1,
                    elapsedMs = 0L,
                    lastRecordedFile = null,
                    lastRecordedThumbnail = null
                )
            }
        }
    }

    private fun bestEffortLocation(): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        return try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = lm.getProviders(true)
            providers.mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        isCleared = true
        super.onCleared()
        selfTimer.cancel()
        stopRecordingProgressTicker()
        cameraController.unbind()
    }
}
