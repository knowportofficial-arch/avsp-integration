package com.avsp.creator.capture.camera.analyzer

import com.avsp.creator.capture.camera.detector.DetectedSubject
import com.avsp.creator.capture.camera.model.CameraFrameRate
import com.avsp.creator.capture.camera.model.CameraShotType

enum class FocusState(val label: String) {
    FOCUSED("FOCUSED"),
    HUNTING("SEARCHING FOCUS"),
    BLURRED("OUT OF FOCUS")
}

enum class ExposureState(val label: String) {
    OPTIMAL("EXPOSURE OK"),
    UNDEREXPOSED("TOO DARK"),
    OVEREXPOSED("TOO BRIGHT")
}

enum class StabilityState(val label: String) {
    STABLE("STABLE"),
    SLIGHT_SHAKE("MINOR SHAKE"),
    UNSTABLE("UNSTABLE — HOLD STEADY")
}

enum class ShotReadinessStatus(val label: String, val minScore: Int) {
    NOT_READY("NOT READY", 0),
    ADJUST("ADJUST FRAMING", 40),
    ALMOST_READY("ALMOST READY", 70),
    GOOD_SHOT("GOOD SHOT — READY", 85)
}

data class ShotAnalysisResult(
    val subjectDetected: Boolean = false,
    val isRealDetection: Boolean = false,
    val detectedSubject: DetectedSubject? = null,
    val subjectType: String = "Scene Context",
    val confidence: Float = 0.0f,
    val recommendedShotType: CameraShotType = CameraShotType.WIDE,
    val recommendedZoomRatio: Float = 1.0f,
    val focusState: FocusState = FocusState.FOCUSED,
    val exposureState: ExposureState = ExposureState.OPTIMAL,
    val stabilityState: StabilityState = StabilityState.STABLE,
    val compositionScore: Int = 85,
    val framingScore: Int = 80,
    val isHorizonTilted: Boolean = false,
    val horizonAngleDegrees: Float = 0f,
    val isMovementDetected: Boolean = false,
    val recommendedFrameRate: CameraFrameRate = CameraFrameRate.FPS_30,
    val userGuidanceInstruction: String = "Point camera at the requested subject.",
    val readinessScore: Int = 50,
    val readinessStatus: ShotReadinessStatus = ShotReadinessStatus.NOT_READY
)
