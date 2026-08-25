package com.avsp.creator.capture.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.creator.capture.camera.analyzer.ExposureState
import com.avsp.creator.capture.camera.analyzer.FocusState
import com.avsp.creator.capture.camera.analyzer.ShotAnalysisResult
import com.avsp.creator.capture.camera.analyzer.ShotReadinessStatus
import com.avsp.creator.capture.camera.analyzer.StabilityState
import com.avsp.creator.capture.camera.candidate.CandidateRanker
import com.avsp.creator.capture.camera.candidate.DefaultCandidateRanker
import com.avsp.creator.capture.camera.candidate.DefaultShotCandidateGenerator
import com.avsp.creator.capture.camera.candidate.ShotCandidateGenerator
import com.avsp.creator.capture.camera.candidate.ShotMemory
import com.avsp.creator.capture.camera.engine.CinematographerDecision
import com.avsp.creator.capture.camera.engine.CinematographerDecisionEngine
import com.avsp.creator.capture.camera.engine.DefaultCinematographerDecisionEngine
import com.avsp.creator.capture.camera.planner.LocalShotPlanner
import com.avsp.creator.capture.camera.planner.ShotPlanner
import com.avsp.creator.capture.camera.mission.DefaultShotMissionGenerator
import com.avsp.creator.capture.camera.mission.ShotMission
import com.avsp.creator.capture.camera.mission.ShotMissionItem
import com.avsp.creator.capture.camera.mission.ShotMissionGenerator
import com.avsp.creator.capture.camera.mission.ShotStatus
import com.avsp.creator.capture.camera.mission.ShotMediaType
import com.avsp.creator.capture.camera.mission.UserReportedEvent
import com.avsp.creator.capture.camera.model.CameraCapabilities
import com.avsp.creator.capture.camera.model.CameraControlMode
import com.avsp.creator.capture.camera.model.CameraFrameRate
import com.avsp.creator.capture.camera.model.CameraProfile
import com.avsp.creator.capture.camera.model.CameraResolution
import com.avsp.creator.capture.camera.model.CameraShotType
import com.avsp.creator.capture.camera.understanding.DefaultSceneUnderstandingEngine
import com.avsp.creator.capture.camera.understanding.DefaultSubjectUnderstandingEngine
import com.avsp.creator.capture.camera.understanding.SceneUnderstandingEngine
import com.avsp.creator.capture.camera.understanding.SceneUnderstandingResult
import com.avsp.creator.capture.camera.understanding.SubjectUnderstandingEngine
import com.avsp.creator.capture.camera.understanding.SubjectUnderstandingResult
import com.avsp.creator.capture.camera.vision.DefaultVisualAnalyzer
import com.avsp.creator.capture.camera.vision.VisualAnalysisResult
import com.avsp.creator.capture.camera.vision.VisualAnalyzer
import com.avsp.creator.data.repository.MediaRepository
import com.avsp.creator.data.repository.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CameraExperienceMode {
    AI_AUTO,
    AI_SHOT_LIST,
    MANUAL
}

enum class CaptureMode {
    PHOTO,
    VIDEO
}

enum class CapturedMediaType {
    PHOTO,
    VIDEO
}

data class CapturedMedia(
    val uri: Uri,
    val type: CapturedMediaType,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val shotType: CameraShotType = CameraShotType.WIDE,
    val missionId: String? = null,
    val missionShotId: String? = null,
    val durationSeconds: Long = 0L,
    val qualityScore: Int = 85,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null
)

data class CameraUiState(
    val projectId: String = "",
    val projectTitle: String = "AVSP Project",
    val experienceMode: CameraExperienceMode = CameraExperienceMode.AI_AUTO,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val isPermissionGranted: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val currentProfile: CameraProfile = CameraProfile(),
    val capabilities: CameraCapabilities = CameraCapabilities(),

    // Real Evidence-Driven Vision Intelligence
    val userProvidedContext: String? = null,
    val visualAnalysis: VisualAnalysisResult = VisualAnalysisResult(),
    val sceneUnderstanding: SceneUnderstandingResult = SceneUnderstandingResult(
        userProvidedContext = null,
        detectedVisualEvidence = emptyList(),
        inferredSceneSummary = "Live Viewfinder (Awaiting Visual Evidence)",
        contextEvidenceAlignmentScore = 0.0f,
        isContextVisuallyConfirmed = false,
        dominantVisualCategory = "Unclassified Scene"
    ),
    val subjectUnderstanding: SubjectUnderstandingResult = SubjectUnderstandingResult(
        targetSubjectName = "Subject",
        primaryTargetSubject = null,
        isTargetSubjectDetected = false,
        matchConfidence = 0.0f,
        explanation = "Awaiting camera frame",
        candidateCount = 0
    ),
    val decision: CinematographerDecision = CinematographerDecision(),

    // Evidence-Driven Coverage Plan & Shot Memory
    val shotMemory: ShotMemory = ShotMemory(),
    val activeMission: ShotMission? = null,
    val activeEvent: UserReportedEvent? = null,
    val isMissionActive: Boolean = false,
    val showMissionDialog: Boolean = false,
    /** Hides the Shot List selector while the chosen shot is actively being executed. */
    val isShotListTemporarilyHidden: Boolean = false,
    val showEventDialog: Boolean = false,
    val nowVsLaterSummary: String = "",

    // Hardware and Lifecycle States
    val isTorchOn: Boolean = false,
    val isCameraAvailable: Boolean = true,
    val isInitializing: Boolean = false,
    val isBound: Boolean = false,
    val isCapturingPhoto: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val isAutoCaptureEnabled: Boolean = false,
    val autoCaptureHoldProgress: Float = 0.0f,

    // Review & Persistence States
    val lastCapturedMedia: CapturedMedia? = null,
    val pendingReviewMedia: CapturedMedia? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class CameraViewModel(
    private val mediaRepository: MediaRepository? = null,
    private val projectRepository: ProjectRepository? = null,
    val visualAnalyzer: VisualAnalyzer = DefaultVisualAnalyzer(),
    private val sceneUnderstandingEngine: SceneUnderstandingEngine = DefaultSceneUnderstandingEngine(),
    private val subjectUnderstandingEngine: SubjectUnderstandingEngine = DefaultSubjectUnderstandingEngine(),
    private val decisionEngine: CinematographerDecisionEngine = DefaultCinematographerDecisionEngine(),
    private val candidateGenerator: ShotCandidateGenerator = DefaultShotCandidateGenerator(),
    private val candidateRanker: CandidateRanker = DefaultCandidateRanker(),
    private val missionGenerator: ShotMissionGenerator = DefaultShotMissionGenerator()
) : ViewModel() {

    // M1.7 contextual planner
    private val masterShotPlanner: ShotPlanner = LocalShotPlanner()

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _autoCaptureEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoCaptureEvent: SharedFlow<Unit> = _autoCaptureEvent.asSharedFlow()

    private var timerJob: Job? = null
    private var currentTiltRoll: Float = 0f

    // Auto-capture steady state confirmation tracker & cooldown
    private var goodShotHoldStartTime: Long = 0L
    private var lastAutoCaptureTimestamp: Long = 0L

    fun setProjectId(id: String) {
        _uiState.update { it.copy(projectId = id) }
        if (id.isNotBlank() && projectRepository != null) {
            viewModelScope.launch {
                when (val result = projectRepository.getProjectById(id)) {
                    is com.avsp.creator.core.common.Resource.Success -> {
                        val title = result.data?.title?.ifBlank { "AVSP Project" } ?: "AVSP Project"
                        _uiState.update { it.copy(projectTitle = title) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun setExperienceMode(mode: CameraExperienceMode) {
        if (_uiState.value.isRecording) return
        _uiState.update { state ->
            state.copy(
                experienceMode = mode,
                // AI SHOT provides live guidance but never takes a photo/video without a user tap.
                isAutoCaptureEnabled = false,
                autoCaptureHoldProgress = 0f,
                statusMessage = when (mode) {
                    CameraExperienceMode.AI_AUTO -> "AI Shot â€” guided capture"
                    CameraExperienceMode.AI_SHOT_LIST -> "Shot List â€” choose a mission shot"
                    CameraExperienceMode.MANUAL -> "Manual â€” AI capture disabled"
                }
            )
        }
    }

    fun updateLocation(latitude: Double?, longitude: Double?, accuracy: Float?) {
        _uiState.update { it.copy(latitude = latitude, longitude = longitude, locationAccuracyMeters = accuracy) }
    }

    fun onPermissionResult(cameraGranted: Boolean, audioGranted: Boolean = false) {
        _uiState.update {
            it.copy(
                isPermissionGranted = cameraGranted,
                hasAudioPermission = audioGranted,
                errorMessage = if (cameraGranted && it.errorMessage?.contains("permission", ignoreCase = true) == true) null else it.errorMessage
            )
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(captureMode = mode, errorMessage = null) }
    }

    fun setControlMode(mode: CameraControlMode) {
        _uiState.update { state ->
            state.copy(currentProfile = state.currentProfile.copy(controlMode = mode))
        }
    }

    fun toggleAutoCapture() {
        _uiState.update { state ->
            val toggled = !state.isAutoCaptureEnabled
            state.copy(
                isAutoCaptureEnabled = toggled,
                autoCaptureHoldProgress = 0.0f,
                statusMessage = if (toggled) "Auto-Shutter Enabled" else "Auto-Shutter Disabled (Manual Only)"
            )
        }
    }

    fun setShotType(shotType: CameraShotType) {
        _uiState.update { state ->
            val updated = state.currentProfile.copy(
                shotType = shotType,
                zoomRatio = shotType.defaultZoomRatio
            )
            state.copy(currentProfile = updated)
        }
    }

    fun setResolution(resolution: CameraResolution) {
        if (!_uiState.value.capabilities.isResolutionSupported(resolution)) {
            _uiState.update { it.copy(statusMessage = "${resolution.displayName} is not supported on this camera") }
            return
        }
        _uiState.update { state ->
            state.copy(currentProfile = state.currentProfile.copy(resolution = resolution))
        }
    }

    fun setFrameRate(frameRate: CameraFrameRate) {
        if (!_uiState.value.capabilities.isFrameRateSupported(frameRate)) {
            _uiState.update { it.copy(statusMessage = "${frameRate.displayName} is not supported on this camera") }
            return
        }
        _uiState.update { state ->
            state.copy(currentProfile = state.currentProfile.copy(frameRate = frameRate))
        }
    }

    fun setZoomRatio(ratio: Float) {
        _uiState.update { state ->
            state.copy(currentProfile = state.currentProfile.copy(zoomRatio = ratio))
        }
    }

    fun toggleGridOverlay() {
        _uiState.update { state ->
            val toggled = !state.currentProfile.isGridOverlayEnabled
            state.copy(currentProfile = state.currentProfile.copy(isGridOverlayEnabled = toggled))
        }
    }

    fun toggleHorizonLevel() {
        _uiState.update { state ->
            val toggled = !state.currentProfile.isHorizonLevelEnabled
            state.copy(currentProfile = state.currentProfile.copy(isHorizonLevelEnabled = toggled))
        }
    }

    fun toggleTorch(onToggled: (Boolean) -> Unit) {
        val next = !_uiState.value.isTorchOn
        _uiState.update { it.copy(isTorchOn = next) }
        onToggled(next)
    }

    fun toggleLensFacing() {
        if (_uiState.value.isRecording) return
        _uiState.update { state ->
            val nextLens = if (state.currentProfile.lensFacing == CameraSelector.LENS_FACING_BACK) {
                if (state.capabilities.hasFrontCamera) CameraSelector.LENS_FACING_FRONT else state.currentProfile.lensFacing
            } else {
                if (state.capabilities.hasBackCamera) CameraSelector.LENS_FACING_BACK else state.currentProfile.lensFacing
            }
            state.copy(
                currentProfile = state.currentProfile.copy(lensFacing = nextLens),
                errorMessage = null
            )
        }
    }

    fun updateCapabilities(capabilities: CameraCapabilities) {
        _uiState.update { state ->
            var profile = state.currentProfile
            if (!capabilities.isResolutionSupported(profile.resolution)) {
                profile = profile.copy(resolution = CameraResolution.FULL_HD_1080)
            }
            if (!capabilities.isFrameRateSupported(profile.frameRate)) {
                profile = profile.copy(frameRate = CameraFrameRate.FPS_30)
            }
            state.copy(capabilities = capabilities, currentProfile = profile)
        }
    }

    // Dynamic AI Shot Mission & Candidate Plan Handlers
    fun showMissionDialog(show: Boolean) {
        _uiState.update { it.copy(showMissionDialog = show) }
    }

    fun showEventDialog(show: Boolean) {
        _uiState.update { it.copy(showEventDialog = show) }
    }

    fun startMissionWithContext(userContext: String) {
        val normalizedContext = userContext.lowercase().trim()
        val isPersonalPhotoRequest = listOf(
            "take my photo", "take a photo of me", "take my picture", "take my portrait",
            "my photo", "my picture", "portrait of me", "selfie",
            "amar photo nao", "amar chobi tolo", "amar chobi nao", "amar chobi tule dao",
            "personal photo plan", "establish personal photo plan", "take a personal photo", "personal portrait"
        ).any(normalizedContext::contains)

        if (isPersonalPhotoRequest && _uiState.value.capabilities.hasFrontCamera &&
            _uiState.value.currentProfile.lensFacing != CameraSelector.LENS_FACING_FRONT) {
            _uiState.update { it.copy(currentProfile = it.currentProfile.copy(lensFacing = CameraSelector.LENS_FACING_FRONT)) }
        }

        // M1.7 is now the single user-plan entry point. The live evidence layer must not
        // replace this plan with transient object candidates while the user is shooting.
        createM17ShotPlan(userContext)
    }

    /**
     * Loads an M1.7 Master Shot Plan through the existing camera/mission pipeline.
     */
    /**
     * M1.7 contextual planning entry point.
     */
    fun createM17ShotPlan(request: String) {
        viewModelScope.launch {
            try {
                val plan = masterShotPlanner.createPlan(request)
                loadMasterShotPlan(plan)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "M1.7 planning failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun loadMasterShotPlan(plan: com.avsp.creator.capture.camera.planner.MasterShotPlan) {
        val mission = com.avsp.creator.capture.camera.planner.ShotMissionPlanAdapter.toShotMission(plan)
        loadPresetMission(mission)
        _uiState.update { it.copy(userProvidedContext = plan.userRequest) }
    }

    fun loadPresetMission(mission: ShotMission) {
        _uiState.update {
            it.copy(
                userProvidedContext = mission.title,
                activeMission = mission,
                isMissionActive = true,
                experienceMode = CameraExperienceMode.AI_AUTO,
                isAutoCaptureEnabled = false,
                showMissionDialog = false,
                nowVsLaterSummary = mission.contextDescription,
                statusMessage = "Active Plan: ${mission.title}"
            )
        }
        mission.currentShot?.let { firstShot ->
            setShotType(firstShot.shotType)
            setFrameRate(firstShot.preferredFrameRate)
            setResolution(firstShot.preferredResolution)
            setCaptureMode(if (firstShot.mediaType == ShotMediaType.VIDEO) CaptureMode.VIDEO else CaptureMode.PHOTO)
        }
    }

    /**
     * Injects a generic high-priority user-reported event (e.g. "A special train has arrived", "Cultural dance started").
     */
    fun reportLiveEvent(eventText: String) {
        val event = UserReportedEvent(
            description = eventText,
            priority = 100
        )

        val state = _uiState.value
        val currentMission = state.activeMission
        val updatedMission = if (currentMission != null) {
            currentMission.injectGenericEvent(event)
        } else {
            missionGenerator.createGenericEventPlan(event, state.capabilities)
        }

        _uiState.update {
            it.copy(
                activeMission = updatedMission,
                activeEvent = event,
                isMissionActive = true,
                experienceMode = CameraExperienceMode.AI_AUTO,
                isAutoCaptureEnabled = true,
                showEventDialog = false,
                statusMessage = "âš¡ Opportunity: $eventText"
            )
        }

        updatedMission.currentShot?.let { shot ->
            setShotType(shot.shotType)
            setFrameRate(shot.preferredFrameRate)
            setResolution(shot.preferredResolution)
        }
    }

    /**
     * Select a plan shot for execution. Once selected, return to AI SHOT so the
     * shot-list overlay disappears during capture. The plan itself remains intact.
     * Completed shots are intentionally selectable again for retakes/re-capture.
     */
    fun promoteShot(shotId: String) {
        val state = _uiState.value
        val mission = state.activeMission ?: return
        val target = mission.shots.firstOrNull { it.id == shotId } ?: return

        // A shot-selection session is locked while the selected shot is being executed.
        // Once the review is kept/skipped, the list becomes available again and ANY
        // shot—including a previously completed shot—may be selected for another take.
        // Do not use mission.currentShot as the lock: after a capture it already points
        // to the next pending shot, which used to incorrectly block retakes of completed shots.
        if (state.isShotListTemporarilyHidden) {
            val current = mission.currentShot
            if (current != null && current.id != shotId) return
        }

        val updated = mission.promoteShot(shotId)
        _uiState.update {
            it.copy(
                activeMission = updated,
                experienceMode = CameraExperienceMode.AI_AUTO,
                showMissionDialog = false,
                isShotListTemporarilyHidden = true
            )
        }
        updated.currentShot?.let { shot ->
            setShotType(shot.shotType)
            setFrameRate(shot.preferredFrameRate)
            setResolution(shot.preferredResolution)
            setCaptureMode(if (shot.mediaType == ShotMediaType.VIDEO) CaptureMode.VIDEO else CaptureMode.PHOTO)
            _uiState.update { it.copy(statusMessage = "Ready: ${shot.title}") }
        }
    }

    fun skipCurrentShot() {
        val mission = _uiState.value.activeMission ?: return
        val updated = mission.skipCurrentShot()
        _uiState.update {
            it.copy(
                activeMission = updated,
                isShotListTemporarilyHidden = false,
                statusMessage = if (updated.currentShot == null) "All planned shots complete" else "Shot skipped"
            )
        }
        updated.currentShot?.let { shot ->
            setShotType(shot.shotType)
            setFrameRate(shot.preferredFrameRate)
            setResolution(shot.preferredResolution)
            setCaptureMode(if (shot.mediaType == ShotMediaType.VIDEO) CaptureMode.VIDEO else CaptureMode.PHOTO)
        }
    }

    fun clearMission() {
        _uiState.update {
            it.copy(
                activeMission = null,
                activeEvent = null,
                userProvidedContext = null,
                isMissionActive = false,
                statusMessage = "Shot plan cleared"
            )
        }
    }

    // Three-Layer Live Vision & Cinematographer Pipeline
    fun onVisualAnalysisReceived(analysis: VisualAnalysisResult) {
        val state = _uiState.value
        var activeMission = state.activeMission

        // 1. Layer B: Scene Understanding (separates model evidence, user context, inference)
        val sceneUnderstanding = sceneUnderstandingEngine.evaluateScene(
            userContext = state.userProvidedContext,
            visualAnalysis = analysis
        )

        // 2. Live evidence is advisory only. Never replace the user's Master Shot List with
        // transient detected-object candidates. The selected plan item remains authoritative
        // until the user captures, skips, or explicitly selects another shot.
        val dynamicSummary = state.nowVsLaterSummary


        val missionShot = activeMission?.currentShot

        // 3. Layer B: Subject Understanding (generic semantic relevance scoring)
        val subjectUnderstanding = subjectUnderstandingEngine.evaluateSubject(
            targetSubjectHint = missionShot?.subjectHint,
            activeEventText = state.activeEvent?.description,
            userContext = state.userProvidedContext,
            visualAnalysis = analysis
        )

        // 4. Layer C: Cinematographer Decision
        val decision = decisionEngine.evaluate(
            userContext = state.userProvidedContext,
            sceneUnderstanding = sceneUnderstanding,
            subjectUnderstanding = subjectUnderstanding,
            visualAnalysis = analysis,
            activeMission = activeMission,
            currentProfile = state.currentProfile,
            capabilities = state.capabilities,
            isAutoMode = state.currentProfile.controlMode == CameraControlMode.AUTO
        )

        // 5. Evaluate Automatic Photo Capture Confirmation Hold
        val now = System.currentTimeMillis()
        var holdProgress = 0.0f

        val canAutoCapture = state.experienceMode == CameraExperienceMode.AI_AUTO &&
                state.isMissionActive &&
                state.isAutoCaptureEnabled &&
                state.captureMode == CaptureMode.PHOTO &&
                !state.isCapturingPhoto &&
                state.pendingReviewMedia == null &&
                decision.isReadyForAutoCapture &&
                analysis.stabilityState == StabilityState.STABLE &&
                (now - lastAutoCaptureTimestamp > 4000L) // 4s cooldown between auto-captures

        if (canAutoCapture) {
            if (goodShotHoldStartTime == 0L) {
                goodShotHoldStartTime = now
            }
            val elapsedHoldMs = now - goodShotHoldStartTime
            holdProgress = (elapsedHoldMs / 800.0f).coerceIn(0.0f, 1.0f) // 0.8s steady confirmation

            if (elapsedHoldMs >= 800L) {
                // Trigger Auto-Capture!
                goodShotHoldStartTime = 0L
                lastAutoCaptureTimestamp = now
                _autoCaptureEvent.tryEmit(Unit)
            }
        } else {
            goodShotHoldStartTime = 0L
            holdProgress = 0.0f
        }

        _uiState.update { current ->
            var updatedProfile = current.currentProfile
            if (current.currentProfile.controlMode == CameraControlMode.AUTO) {
                // M1.5.5: let the selected cinematographic candidate drive the camera profile.
                // Wide/Medium/Close are now consequences of the live decision, not manual
                // presets. Frame rate remains F30 for normal still/scene work and F60 for
                // genuine movement/opportunity decisions when the hardware supports it.
                val selectedShot = activeMission?.currentShot
                if (selectedShot != null) {
                    updatedProfile = updatedProfile.copy(
                        shotType = selectedShot.shotType,
                        zoomRatio = selectedShot.shotType.defaultZoomRatio
                    )
                    if (decision.recommendedFps != updatedProfile.frameRate &&
                        current.capabilities.isFrameRateSupported(decision.recommendedFps)) {
                        updatedProfile = updatedProfile.copy(frameRate = decision.recommendedFps)
                    }
                    if (current.capabilities.isResolutionSupported(selectedShot.preferredResolution)) {
                        updatedProfile = updatedProfile.copy(resolution = selectedShot.preferredResolution)
                    }
                } else if (decision.recommendedFps != current.currentProfile.frameRate &&
                    current.capabilities.isFrameRateSupported(decision.recommendedFps)) {
                    updatedProfile = updatedProfile.copy(frameRate = decision.recommendedFps)
                }
            }

            current.copy(
                activeMission = activeMission,
                visualAnalysis = analysis,
                sceneUnderstanding = sceneUnderstanding,
                subjectUnderstanding = subjectUnderstanding,
                decision = decision,
                currentProfile = updatedProfile,
                autoCaptureHoldProgress = holdProgress,
                nowVsLaterSummary = dynamicSummary
            )
        }
    }

    fun onOrientationChanged(rollDegrees: Float) {
        this.currentTiltRoll = rollDegrees
    }

    fun setInitializing(isInitializing: Boolean) {
        _uiState.update { it.copy(isInitializing = isInitializing) }
    }

    fun setCameraBound(isBound: Boolean) {
        _uiState.update { it.copy(isBound = isBound) }
    }

    // Photo Capture Handlers
    fun onPhotoCaptureStarted() {
        _uiState.update { it.copy(isCapturingPhoto = true, autoCaptureHoldProgress = 0.0f, errorMessage = null) }
    }

    fun onPhotoCaptured(uri: Uri, displayName: String) {
        val currentShot = _uiState.value.activeMission?.currentShot
        val media = CapturedMedia(
            uri = uri,
            type = CapturedMediaType.PHOTO,
            name = displayName,
            shotType = _uiState.value.currentProfile.shotType,
            missionId = _uiState.value.activeMission?.id,
            missionShotId = currentShot?.id,
            qualityScore = _uiState.value.decision.readiness,
            latitude = _uiState.value.latitude,
            longitude = _uiState.value.longitude,
            locationAccuracyMeters = _uiState.value.locationAccuracyMeters
        )

        _uiState.update {
            it.copy(
                isCapturingPhoto = false,
                lastCapturedMedia = media,
                pendingReviewMedia = media,
                autoCaptureHoldProgress = 0.0f,
                statusMessage = "Photo captured: $displayName"
            )
        }
    }

    fun onPhotoCaptureFailed(error: String) {
        _uiState.update {
            it.copy(
                isCapturingPhoto = false,
                autoCaptureHoldProgress = 0.0f,
                errorMessage = "Photo capture failed: $error"
            )
        }
    }

    // Video Recording Handlers
    fun onRecordingStarted() {
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingDurationSeconds = 0L,
                errorMessage = null,
                statusMessage = "Recording started"
            )
        }
        startTimer()
    }

    fun onRecordingFinalized(uri: Uri?, displayName: String?) {
        val duration = _uiState.value.recordingDurationSeconds
        stopTimer()

        if (uri != null && displayName != null) {
            val currentShot = _uiState.value.activeMission?.currentShot
            val media = CapturedMedia(
                uri = uri,
                type = CapturedMediaType.VIDEO,
                name = displayName,
                shotType = _uiState.value.currentProfile.shotType,
                missionId = _uiState.value.activeMission?.id,
                missionShotId = currentShot?.id,
                durationSeconds = duration,
                qualityScore = _uiState.value.decision.readiness,
                latitude = _uiState.value.latitude,
                longitude = _uiState.value.longitude,
                locationAccuracyMeters = _uiState.value.locationAccuracyMeters
            )
            _uiState.update {
                it.copy(
                    isRecording = false,
                    lastCapturedMedia = media,
                    pendingReviewMedia = media,
                    statusMessage = "Video recorded: $displayName"
                )
            }
        } else {
            _uiState.update { it.copy(isRecording = false) }
        }
    }

    fun onRecordingFailed(error: String) {
        stopTimer()
        _uiState.update {
            it.copy(
                isRecording = false,
                errorMessage = "Video recording failed: $error"
            )
        }
    }

    // Capture Review Actions: KEEP / RETAKE / DELETE
    fun keepPendingMedia(onCompleted: () -> Unit) {
        val media = _uiState.value.pendingReviewMedia ?: return
        val projectId = _uiState.value.projectId

        viewModelScope.launch {
            if (mediaRepository != null && projectId.isNotBlank()) {
                mediaRepository.saveCapturedMedia(
                    projectId = projectId,
                    uriString = media.uri.toString(),
                    mediaType = media.type.name,
                    displayName = media.name,
                    shotType = media.shotType.name,
                    missionId = media.missionId,
                    missionShotId = media.missionShotId,
                    durationSeconds = media.durationSeconds,
                    qualityScore = media.qualityScore,
                    latitude = media.latitude,
                    longitude = media.longitude,
                    locationAccuracyMeters = media.locationAccuracyMeters
                )
            }

            // Update ShotMemory to track what has already been captured
            var updatedMemory = _uiState.value.shotMemory
            val currentMission = _uiState.value.activeMission
            val currentShot = currentMission?.currentShot

            if (currentShot != null) {
                val candidatePlaceholder = com.avsp.creator.capture.camera.candidate.ShotCandidate(
                    id = currentShot.id,
                    objective = currentShot.title,
                    targetSubject = currentShot.subjectHint,
                    shotType = currentShot.shotType,
                    reason = currentShot.description,
                    requiredComposition = currentShot.compositionHint
                )
                updatedMemory = updatedMemory.recordCapture(candidatePlaceholder, media.uri.toString())
            }

            // Advance dynamic shot mission to next best candidate
            if (currentMission != null && media.missionShotId != null) {
                val updatedMission = currentMission.markShotCaptured(media.missionShotId, media.uri.toString())
                _uiState.update {
                    it.copy(
                        activeMission = updatedMission,
                        shotMemory = updatedMemory,
                        // The review is now complete, so the user may reopen the list
                        // and select any shot, including this completed one.
                        isShotListTemporarilyHidden = false,
                        statusMessage = if (updatedMission.currentShot == null) {
                            "All planned shots complete"
                        } else {
                            "Saved to Project Media Library"
                        }
                    )
                }

                updatedMission.currentShot?.let { nextShot ->
                    setShotType(nextShot.shotType)
                    setFrameRate(nextShot.preferredFrameRate)
                    setResolution(nextShot.preferredResolution)
                }
            } else {
                _uiState.update { it.copy(shotMemory = updatedMemory) }
            }

            _uiState.update { current ->
                current.copy(
                    pendingReviewMedia = null,
                    isShotListTemporarilyHidden = false,
                    statusMessage = if (current.activeMission?.currentShot == null) {
                        "All planned shots complete"
                    } else {
                        "Saved to Project Media Library"
                    }
                )
            }
            onCompleted()
        }
    }

    fun retakePendingMedia(onCompleted: () -> Unit) {
        _uiState.update { it.copy(pendingReviewMedia = null) }
        onCompleted()
    }

    fun deletePendingMedia(context: Context, onCompleted: () -> Unit) {
        val media = _uiState.value.pendingReviewMedia
        if (media != null) {
            try {
                context.contentResolver.delete(media.uri, null, null)
            } catch (_: Exception) {}
        }
        _uiState.update { it.copy(pendingReviewMedia = null, statusMessage = "Media discarded") }
        onCompleted()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var elapsed = 0L
            while (true) {
                delay(1000)
                elapsed += 1
                _uiState.update { it.copy(recordingDurationSeconds = elapsed) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun setCameraError(message: String?) {
        _uiState.update { it.copy(errorMessage = message, isInitializing = false, isBound = false) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        visualAnalyzer.close()
    }
}
