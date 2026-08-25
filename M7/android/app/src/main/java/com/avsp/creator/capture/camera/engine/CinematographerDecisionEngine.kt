package com.avsp.creator.capture.camera.engine

import androidx.camera.core.CameraSelector
import com.avsp.creator.capture.camera.analyzer.ExposureState
import com.avsp.creator.capture.camera.analyzer.FocusState
import com.avsp.creator.capture.camera.analyzer.ShotReadinessStatus
import com.avsp.creator.capture.camera.analyzer.StabilityState
import com.avsp.creator.capture.camera.mission.ShotMission
import com.avsp.creator.capture.camera.mission.ShotMissionItem
import com.avsp.creator.capture.camera.mission.ShotStatus
import com.avsp.creator.capture.camera.model.CameraCapabilities
import com.avsp.creator.capture.camera.model.CameraControlMode
import com.avsp.creator.capture.camera.model.CameraFrameRate
import com.avsp.creator.capture.camera.model.CameraProfile
import com.avsp.creator.capture.camera.model.CameraShotType
import com.avsp.creator.capture.camera.understanding.SceneUnderstandingResult
import com.avsp.creator.capture.camera.understanding.SubjectUnderstandingResult
import com.avsp.creator.capture.camera.vision.VisualAnalysisResult
import kotlin.math.abs

interface CinematographerDecisionEngine {
    fun evaluate(
        userContext: String?,
        sceneUnderstanding: SceneUnderstandingResult,
        subjectUnderstanding: SubjectUnderstandingResult,
        visualAnalysis: VisualAnalysisResult,
        activeMission: ShotMission?,
        currentProfile: CameraProfile,
        capabilities: CameraCapabilities,
        isAutoMode: Boolean = true
    ): CinematographerDecision
}

class DefaultCinematographerDecisionEngine : CinematographerDecisionEngine {

    override fun evaluate(
        userContext: String?,
        sceneUnderstanding: SceneUnderstandingResult,
        subjectUnderstanding: SubjectUnderstandingResult,
        visualAnalysis: VisualAnalysisResult,
        activeMission: ShotMission?,
        currentProfile: CameraProfile,
        capabilities: CameraCapabilities,
        isAutoMode: Boolean
    ): CinematographerDecision {
        val currentShot = activeMission?.currentShot
        val nextShot = activeMission?.shots?.firstOrNull { shot ->
            shot.id != currentShot?.id && (shot.status == ShotStatus.PENDING || shot.status == ShotStatus.OPPORTUNITY)
        }

        val targetShotType = currentShot?.shotType ?: currentProfile.shotType
        val targetSubjectName = subjectUnderstanding.targetSubjectName
        val isPersonalPhotoRequest = userContext?.lowercase()?.let { text ->
            listOf(
                "take my photo", "take a photo of me", "take my picture", "take my portrait",
                "my photo", "my picture", "portrait of me", "selfie",
                "amar photo nao", "amar chobi tolo", "amar chobi nao", "amar chobi tule dao",
                "personal photo plan", "personal portrait", "establish personal photo plan"
            ).any(text::contains)
        } == true

        // User-facing language for personal photos is deliberately non-technical.
        // The internal shot type remains MEDIUM so the camera engine can still control framing.
        if (isPersonalPhotoRequest) {
            val person = visualAnalysis.detectedObjects
                .filter { it.primaryLabel.contains("person", ignoreCase = true) || it.primaryLabel.contains("face", ignoreCase = true) }
                .maxByOrNull { it.confidence }
            // Self-photo is a user-directed capture. When the front camera is active, do not
            // block the user just because the generic object labeler failed to emit "person".
            // ML detection can refine framing when available, but it is not the gate for capture.
            val frontCameraActive = currentProfile.lensFacing == CameraSelector.LENS_FACING_FRONT
            val detected = frontCameraActive || (person != null && person.confidence >= 0.35f)
            val width = person?.normalizedWidth ?: 0.5f
            val centerX = person?.normalizedCenterX ?: 0.5f
            val guidance = when {
                !detected -> "Point the camera toward yourself."
                width < 0.28f -> "Move a little closer."
                width > 0.78f -> "Move a little back."
                centerX < 0.38f -> "Move slightly right."
                centerX > 0.62f -> "Move slightly left."
                visualAnalysis.stabilityState == StabilityState.UNSTABLE -> "Hold still for a moment."
                visualAnalysis.focusState == FocusState.BLURRED -> "Hold still and let the camera focus."
                visualAnalysis.exposureState == ExposureState.UNDEREXPOSED -> "Move toward better light."
                visualAnalysis.exposureState == ExposureState.OVEREXPOSED -> "Avoid the strongest glare."
                else -> "You're ready — look at the camera and smile."
            }
            val readiness = when {
                !detected -> 20
                width !in 0.28f..0.78f -> 68
                centerX !in 0.38f..0.62f -> 72
                visualAnalysis.stabilityState == StabilityState.UNSTABLE -> 65
                visualAnalysis.focusState == FocusState.BLURRED -> 68
                visualAnalysis.exposureState != ExposureState.OPTIMAL -> 72
                else -> 96
            }
            val ready = readiness >= ShotReadinessStatus.GOOD_SHOT.minScore
            return CinematographerDecision(
                currentShot = currentShot,
                targetSubject = "person",
                shotType = CameraShotType.MEDIUM,
                cameraProfile = currentProfile,
                recommendedFps = CameraFrameRate.FPS_30,
                guidance = guidance,
                readiness = readiness,
                isReady = ready,
                reason = if (detected) "Your photo is being composed automatically." else "Your face/person is not visible yet.",
                priority = currentShot?.priority ?: 100,
                nextShot = nextShot,
                status = if (ready) ShotReadinessStatus.GOOD_SHOT else if (readiness >= ShotReadinessStatus.ADJUST.minScore) ShotReadinessStatus.ADJUST else ShotReadinessStatus.NOT_READY,
                targetBoundingBox = person?.boundingBox,
                targetFocusNormalizedX = person?.normalizedCenterX ?: 0.5f,
                targetFocusNormalizedY = person?.normalizedCenterY ?: 0.5f,
                shouldFocusSubject = detected && visualAnalysis.focusState != FocusState.FOCUSED,
                movementInstruction = guidance.takeIf { !ready },
                isSubjectMatched = detected,
                isFramingAcceptable = detected && width in 0.28f..0.78f && centerX in 0.38f..0.62f,
                isReadyForAutoCapture = ready
            )
        }

        // 1. Recommended Frame Rate
        val isDynamicMovement = visualAnalysis.motionMagnitude > 9.0 ||
                (currentShot != null && currentShot.preferredFrameRate == CameraFrameRate.FPS_60)
        val recommendedFps = if (isDynamicMovement && capabilities.is60FpsSupported) {
            CameraFrameRate.FPS_60
        } else {
            CameraFrameRate.FPS_30
        }

        // 2. Environmental establishing shots do not require a single ML bounding box.
        // A lightweight on-device labeler may see sky/building/platform/train/etc. without
        // ever emitting the exact user phrase (for example "railway station"). In that case
        // the cinematographer should still be able to approve a genuine wide establishing shot.
        val detectedSubject = subjectUnderstanding.primaryTargetSubject
        val isEstablishingEnvironment = targetShotType == CameraShotType.WIDE &&
                (currentShot?.subjectHint?.contains("Environment", ignoreCase = true) == true ||
                 currentShot?.subjectHint?.contains("Scene", ignoreCase = true) == true ||
                 currentShot?.title?.contains("Establish", ignoreCase = true) == true)

        if (isEstablishingEnvironment && visualAnalysis.rawInference.hasDetections) {
            var environmentalPenalty = 0
            var environmentGuidance: String? = null
            if (visualAnalysis.isHorizonTilted) {
                environmentalPenalty += (abs(visualAnalysis.tiltRollDegrees) * 5).toInt().coerceIn(15, 30)
                environmentGuidance = "Level the phone."
            } else if (visualAnalysis.stabilityState == StabilityState.UNSTABLE) {
                environmentalPenalty += 30
                environmentGuidance = "Hold steady."
            } else if (visualAnalysis.exposureState == ExposureState.UNDEREXPOSED) {
                environmentalPenalty += 20
                environmentGuidance = "Low light — adjust toward the brighter part of the scene."
            } else if (visualAnalysis.exposureState == ExposureState.OVEREXPOSED) {
                environmentalPenalty += 20
                environmentGuidance = "Too bright — avoid the strongest glare."
            }

            val profilePenalty = if (currentProfile.shotType != CameraShotType.WIDE) 12 else 0
            val readinessScore = (100 - environmentalPenalty - profilePenalty).coerceIn(15, 98)
            val ready = readinessScore >= ShotReadinessStatus.GOOD_SHOT.minScore &&
                    visualAnalysis.stabilityState == StabilityState.STABLE &&
                    visualAnalysis.focusState == FocusState.FOCUSED &&
                    !visualAnalysis.isHorizonTilted &&
                    visualAnalysis.exposureState == ExposureState.OPTIMAL
            val evidenceName = sceneUnderstanding.detectedVisualEvidence.take(2).joinToString { it.label }
            val finalGuidance = when {
                profilePenalty > 0 -> "Switch to WIDE — establish the environment."
                environmentGuidance != null -> environmentGuidance
                ready -> "WIDE ESTABLISHING — keep the main scene and its strongest visible anchor together."
                visualAnalysis.focusState == FocusState.BLURRED -> "Hold on the scene and let focus settle."
                else -> "Hold the wide composition."
            }
            val reason = if (evidenceName.isNotBlank())
                "Establishing scene from live evidence: $evidenceName"
            else
                "Establishing shot based on the user's scene context and live visual evidence"

            return CinematographerDecision(
                currentShot = currentShot,
                targetSubject = targetSubjectName,
                shotType = CameraShotType.WIDE,
                cameraProfile = currentProfile,
                recommendedFps = recommendedFps,
                guidance = finalGuidance,
                readiness = readinessScore,
                isReady = ready,
                reason = reason,
                priority = currentShot?.priority ?: 10,
                nextShot = nextShot,
                status = if (ready) ShotReadinessStatus.GOOD_SHOT else if (readinessScore >= ShotReadinessStatus.ADJUST.minScore) ShotReadinessStatus.ADJUST else ShotReadinessStatus.NOT_READY,
                targetBoundingBox = null,
                targetFocusNormalizedX = 0.5f,
                targetFocusNormalizedY = 0.5f,
                shouldFocusSubject = visualAnalysis.focusState != FocusState.FOCUSED,
                movementInstruction = environmentGuidance,
                isSubjectMatched = true,
                isFramingAcceptable = environmentGuidance == null && profilePenalty == 0,
                isReadyForAutoCapture = ready
            )
        }

        // 3. Check if a specific visible subject is detected.
        if (!subjectUnderstanding.isTargetSubjectDetected || detectedSubject == null) {
            val unreachedGuidance = "TARGET NOT DETECTED — Point camera toward $targetSubjectName"
            return CinematographerDecision(
                currentShot = currentShot,
                targetSubject = targetSubjectName,
                shotType = targetShotType,
                cameraProfile = currentProfile,
                recommendedFps = recommendedFps,
                guidance = unreachedGuidance,
                readiness = 0,
                isReady = false,
                reason = subjectUnderstanding.explanation,
                priority = currentShot?.priority ?: 10,
                nextShot = nextShot,
                status = ShotReadinessStatus.NOT_READY,
                targetBoundingBox = null,
                targetFocusNormalizedX = 0.5f,
                targetFocusNormalizedY = 0.5f,
                shouldFocusSubject = false,
                movementInstruction = "Point camera at $targetSubjectName",
                isSubjectMatched = false,
                isFramingAcceptable = false,
                isReadyForAutoCapture = false
            )
        }

        // 3. Evaluate Distinct WIDE / MEDIUM / CLOSE Framing Requirements on Live Bounding Box
        val subjectWidth = detectedSubject.normalizedWidth
        val subjectCenterX = detectedSubject.normalizedCenterX
        val subjectCenterY = detectedSubject.normalizedCenterY

        var sizeGuidance: String? = null
        var sizePenalty = 0
        var sizeReason: String? = null

        when (targetShotType) {
            CameraShotType.WIDE -> {
                // Wide: Subject occupies ~12% to 38% of frame with surrounding scene context
                if (subjectWidth > 0.40f) {
                    sizeGuidance = "Move back."
                    sizePenalty = ((subjectWidth - 0.40f) * 80).toInt().coerceIn(10, 35)
                    sizeReason = "Subject occupies ${(subjectWidth * 100).toInt()}% of frame (too large for wide establishing view)"
                } else if (subjectWidth < 0.08f) {
                    sizeGuidance = "Move closer."
                    sizePenalty = ((0.08f - subjectWidth) * 80).toInt().coerceIn(10, 30)
                    sizeReason = "Subject too distant/small in frame"
                }
            }
            CameraShotType.MEDIUM -> {
                // Medium: Subject occupies ~36% to 70% of frame
                if (subjectWidth < 0.34f) {
                    sizeGuidance = "Move closer."
                    sizePenalty = ((0.34f - subjectWidth) * 70).toInt().coerceIn(10, 35)
                    sizeReason = "Move closer to frame medium subject"
                } else if (subjectWidth > 0.74f) {
                    sizeGuidance = "Move back."
                    sizePenalty = ((subjectWidth - 0.74f) * 70).toInt().coerceIn(10, 35)
                    sizeReason = "Subject exceeds medium framing bounds"
                }
            }
            CameraShotType.CLOSE -> {
                // Close: Subject occupies ~66% to 94% of frame for macro/detail
                if (subjectWidth < 0.64f) {
                    sizeGuidance = "Move closer."
                    sizePenalty = ((0.64f - subjectWidth) * 80).toInt().coerceIn(10, 35)
                    sizeReason = "Move closer for close detail"
                } else if (subjectWidth > 0.96f) {
                    sizeGuidance = "Move back slightly."
                    sizePenalty = 15
                    sizeReason = "Subject clipped by frame edge"
                }
            }
        }

        // 4. Directional Off-Center Guidance (Move left/right, Tilt up/down)
        var directionGuidance: String? = null
        var centeringPenalty = 0

        if (subjectCenterX < 0.36f) {
            directionGuidance = "Move slightly right."
            centeringPenalty += ((0.36f - subjectCenterX) * 60).toInt().coerceIn(8, 25)
        } else if (subjectCenterX > 0.64f) {
            directionGuidance = "Move slightly left."
            centeringPenalty += ((subjectCenterX - 0.64f) * 60).toInt().coerceIn(8, 25)
        }

        if (directionGuidance == null) {
            if (subjectCenterY < 0.35f) {
                directionGuidance = "Tilt slightly down."
                centeringPenalty += ((0.35f - subjectCenterY) * 60).toInt().coerceIn(8, 25)
            } else if (subjectCenterY > 0.65f) {
                directionGuidance = "Tilt slightly up."
                centeringPenalty += ((subjectCenterY - 0.65f) * 60).toInt().coerceIn(8, 25)
            }
        }

        // 5. Horizon Level Guidance
        var horizonGuidance: String? = null
        var horizonPenalty = 0
        if (visualAnalysis.isHorizonTilted) {
            horizonGuidance = "Level the phone."
            horizonPenalty = (abs(visualAnalysis.tiltRollDegrees) * 5).toInt().coerceIn(15, 30)
        }

        // 6. Stability, Focus, and Exposure Penalties
        var stabilityPenalty = 0
        when (visualAnalysis.stabilityState) {
            StabilityState.UNSTABLE -> stabilityPenalty = 30
            StabilityState.SLIGHT_SHAKE -> stabilityPenalty = 12
            StabilityState.STABLE -> {}
        }

        var focusPenalty = 0
        when (visualAnalysis.focusState) {
            FocusState.BLURRED -> focusPenalty = 25
            FocusState.HUNTING -> focusPenalty = 10
            FocusState.FOCUSED -> {}
        }

        var exposurePenalty = 0
        when (visualAnalysis.exposureState) {
            ExposureState.UNDEREXPOSED -> exposurePenalty = 20
            ExposureState.OVEREXPOSED -> exposurePenalty = 20
            ExposureState.OPTIMAL -> {}
        }

        val profilePenalty = if (currentProfile.shotType != targetShotType) 12 else 0

        // 7. Multi-Factor Honest Readiness Score
        val baseScore = 100 - sizePenalty - centeringPenalty - horizonPenalty - stabilityPenalty - focusPenalty - exposurePenalty - profilePenalty
        val readinessScore = baseScore.coerceIn(15, 98)

        val isFramingAcceptable = (sizeGuidance == null && directionGuidance == null)

        // 8. Readiness Status & Dynamic Instruction Selection
        val status = when {
            readinessScore >= ShotReadinessStatus.GOOD_SHOT.minScore && isFramingAcceptable && !visualAnalysis.isHorizonTilted &&
                    visualAnalysis.stabilityState == StabilityState.STABLE && visualAnalysis.focusState == FocusState.FOCUSED -> {
                ShotReadinessStatus.GOOD_SHOT
            }
            readinessScore >= ShotReadinessStatus.ALMOST_READY.minScore -> ShotReadinessStatus.ALMOST_READY
            readinessScore >= ShotReadinessStatus.ADJUST.minScore -> ShotReadinessStatus.ADJUST
            else -> ShotReadinessStatus.NOT_READY
        }

        val isReadyForAutoCapture = (status == ShotReadinessStatus.GOOD_SHOT)

        val finalGuidance = when {
            sizeGuidance != null -> sizeGuidance
            directionGuidance != null -> directionGuidance
            horizonGuidance != null -> horizonGuidance
            visualAnalysis.stabilityState == StabilityState.UNSTABLE -> "Hold steady."
            visualAnalysis.focusState == FocusState.BLURRED -> "Tap to focus."
            visualAnalysis.exposureState == ExposureState.UNDEREXPOSED -> "Low light — increase scene lighting"
            visualAnalysis.exposureState == ExposureState.OVEREXPOSED -> "Too bright — reduce direct glare"
            currentProfile.shotType != targetShotType -> {
                when (targetShotType) {
                    CameraShotType.WIDE -> "Switch to WIDE for establishing view"
                    CameraShotType.MEDIUM -> "Switch to MEDIUM for subject framing"
                    CameraShotType.CLOSE -> "Switch to CLOSE for detail"
                }
            }
            visualAnalysis.stabilityState == StabilityState.SLIGHT_SHAKE -> "Hold steady..."
            status == ShotReadinessStatus.GOOD_SHOT -> "GOOD SHOT — READY."
            else -> "Hold steady — framing optimal."
        }

        val decisionReason = sizeReason
            ?: directionGuidance?.let { "Subject off-center in frame" }
            ?: horizonGuidance?.let { "Camera tilted (${String.format("%.1f", visualAnalysis.tiltRollDegrees)}°)" }
            ?: (if (visualAnalysis.stabilityState != StabilityState.STABLE) "Camera movement detected" else if (status == ShotReadinessStatus.GOOD_SHOT) "Optimal composition, focus, and stability" else "Refining composition")

        val shouldFocus = visualAnalysis.focusState != FocusState.FOCUSED

        return CinematographerDecision(
            currentShot = currentShot,
            targetSubject = targetSubjectName,
            shotType = targetShotType,
            cameraProfile = currentProfile,
            recommendedFps = recommendedFps,
            guidance = finalGuidance,
            readiness = readinessScore,
            isReady = isReadyForAutoCapture,
            reason = decisionReason,
            priority = currentShot?.priority ?: 10,
            nextShot = nextShot,
            status = status,
            targetBoundingBox = detectedSubject.boundingBox,
            targetFocusNormalizedX = subjectCenterX,
            targetFocusNormalizedY = subjectCenterY,
            shouldFocusSubject = shouldFocus,
            movementInstruction = sizeGuidance ?: directionGuidance,
            isSubjectMatched = true,
            isFramingAcceptable = isFramingAcceptable,
            isReadyForAutoCapture = isReadyForAutoCapture
        )
    }
}
