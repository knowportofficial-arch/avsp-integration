package com.avsp.creator.capture.camera.analyzer

import com.avsp.creator.capture.camera.detector.DetectedSubject
import com.avsp.creator.capture.camera.mission.ShotMissionItem
import com.avsp.creator.capture.camera.model.CameraFrameRate
import com.avsp.creator.capture.camera.model.CameraProfile
import com.avsp.creator.capture.camera.model.CameraShotType
import kotlin.math.abs

class RuleBasedCameraAnalyzer : CameraAnalyzer {

    override fun analyze(
        luminanceMean: Double,
        luminanceVariance: Double,
        motionMagnitude: Double,
        tiltRollDegrees: Float,
        currentProfile: CameraProfile,
        activeMissionShot: ShotMissionItem?,
        detectedSubject: DetectedSubject?
    ): ShotAnalysisResult {
        // 1. Exposure Assessment
        val exposureState = when {
            luminanceMean < 42.0 -> ExposureState.UNDEREXPOSED
            luminanceMean > 218.0 -> ExposureState.OVEREXPOSED
            else -> ExposureState.OPTIMAL
        }

        // 2. Motion & Stability Assessment
        val isMovementDetected = motionMagnitude > 10.0
        val stabilityState = when {
            motionMagnitude > 16.0 -> StabilityState.UNSTABLE
            motionMagnitude > 7.0 -> StabilityState.SLIGHT_SHAKE
            else -> StabilityState.STABLE
        }

        val recommendedFrameRate = if (isMovementDetected) {
            CameraFrameRate.FPS_60
        } else {
            CameraFrameRate.FPS_30
        }

        // 3. Horizon / Level Assessment
        val isHorizonTilted = abs(tiltRollDegrees) > 2.5f

        // 4. Focus Assessment (Frame high-frequency gradient variance)
        val focusState = when {
            luminanceVariance < 120.0 && luminanceMean > 50.0 -> FocusState.BLURRED
            luminanceVariance < 280.0 -> FocusState.HUNTING
            else -> FocusState.FOCUSED
        }

        // 5. Target Shot Type & Composition Hints
        val targetShotType = activeMissionShot?.shotType ?: currentProfile.shotType
        val isPersonalPhotoRequest = activeMissionShot?.title?.contains("Take My Photo", ignoreCase = true) == true ||
                activeMissionShot?.title?.contains("Personal Photo", ignoreCase = true) == true ||
                activeMissionShot?.subjectHint?.equals("person", ignoreCase = true) == true
        val recommendedZoomRatio = targetShotType.defaultZoomRatio

        // 6. Subject Realness check
        val isRealSubject = (detectedSubject != null && detectedSubject.isRealDetection) || isPersonalPhotoRequest
        val subjectLabel = if (isRealSubject) {
            detectedSubject?.label ?: activeMissionShot?.subjectHint ?: "Detected Subject"
        } else {
            "No Subject"
        }
        val confidence = if (isRealSubject) detectedSubject?.confidence ?: 0.0f else 0.0f

        var compositionScore = 90
        if (isHorizonTilted) compositionScore -= 20
        if (stabilityState != StabilityState.STABLE) compositionScore -= 15
        if (exposureState != ExposureState.OPTIMAL) compositionScore -= 15
        if (focusState != FocusState.FOCUSED) compositionScore -= 20
        if (currentProfile.shotType != targetShotType) compositionScore -= 10
        if (activeMissionShot != null && !isRealSubject) compositionScore -= 30
        compositionScore = compositionScore.coerceIn(15, 98)

        val framingScore = if (currentProfile.shotType == targetShotType && !isHorizonTilted && (activeMissionShot == null || isRealSubject)) 90 else 60

        // 7. Initial User Guidance Instruction
        val instruction = when {
            isPersonalPhotoRequest && !isRealSubject -> "Point the camera toward yourself."
            isPersonalPhotoRequest && exposureState == ExposureState.UNDEREXPOSED -> "Move toward better light."
            isPersonalPhotoRequest && exposureState == ExposureState.OVEREXPOSED -> "Avoid the strongest glare."
            isPersonalPhotoRequest && focusState == FocusState.BLURRED -> "Hold still and let the camera focus."
            isPersonalPhotoRequest && stabilityState == StabilityState.UNSTABLE -> "Hold still for a moment."
            isPersonalPhotoRequest && currentProfile.shotType != targetShotType -> "Position yourself naturally in the frame."
            isPersonalPhotoRequest -> "You're ready — look at the camera and smile."
            activeMissionShot != null && !isRealSubject -> "Point camera toward ${activeMissionShot.subjectHint}."
            exposureState == ExposureState.UNDEREXPOSED -> "Low light detected — increase scene lighting"
            exposureState == ExposureState.OVEREXPOSED -> "Too bright — reduce direct glare"
            focusState == FocusState.BLURRED -> "Tap to focus on subject"
            stabilityState == StabilityState.UNSTABLE -> "Hold steady for sharp capture"
            isHorizonTilted -> if (tiltRollDegrees > 0) "Tilt left to level phone" else "Tilt right to level phone"
            currentProfile.shotType != targetShotType -> {
                when (targetShotType) {
                    CameraShotType.WIDE -> "Switch to WIDE for establishing view"
                    CameraShotType.MEDIUM -> "Switch to MEDIUM for subject framing"
                    CameraShotType.CLOSE -> "Switch to CLOSE for detail"
                }
            }
            stabilityState == StabilityState.SLIGHT_SHAKE -> "Steadying frame..."
            else -> if (isRealSubject) "Good framing — ready to capture" else "Point camera at the requested subject."
        }

        // 8. Overall Readiness Calculation
        var readinessScore = ((compositionScore * 0.5) + (framingScore * 0.5)).toInt().coerceIn(10, 98)
        if (activeMissionShot != null && !isRealSubject) {
            // Cannot reach GOOD_SHOT without subject detection
            readinessScore = readinessScore.coerceAtMost(60)
        }

        val readinessStatus = when {
            readinessScore >= ShotReadinessStatus.GOOD_SHOT.minScore && (activeMissionShot == null || isRealSubject) -> ShotReadinessStatus.GOOD_SHOT
            readinessScore >= ShotReadinessStatus.ALMOST_READY.minScore -> ShotReadinessStatus.ALMOST_READY
            readinessScore >= ShotReadinessStatus.ADJUST.minScore -> ShotReadinessStatus.ADJUST
            else -> ShotReadinessStatus.NOT_READY
        }

        return ShotAnalysisResult(
            subjectDetected = isRealSubject,
            isRealDetection = isRealSubject,
            detectedSubject = detectedSubject,
            subjectType = subjectLabel,
            confidence = confidence,
            recommendedShotType = targetShotType,
            recommendedZoomRatio = recommendedZoomRatio,
            focusState = focusState,
            exposureState = exposureState,
            stabilityState = stabilityState,
            compositionScore = compositionScore,
            framingScore = framingScore,
            isHorizonTilted = isHorizonTilted,
            horizonAngleDegrees = tiltRollDegrees,
            isMovementDetected = isMovementDetected,
            recommendedFrameRate = recommendedFrameRate,
            userGuidanceInstruction = instruction,
            readinessScore = readinessScore,
            readinessStatus = readinessStatus
        )
    }
}
