package com.avsp.pro.m7.capture.camera.engine

import android.graphics.RectF
import com.avsp.pro.m7.capture.camera.analyzer.ShotReadinessStatus
import com.avsp.pro.m7.capture.camera.mission.ShotMissionItem
import com.avsp.pro.m7.capture.camera.model.CameraFrameRate
import com.avsp.pro.m7.capture.camera.model.CameraProfile
import com.avsp.pro.m7.capture.camera.model.CameraShotType

/**
 * Layer C: Structured Output of the AI Cinematographer Decision Engine.
 * Answers "What should I capture NOW?" and delivers real-time framing guidance.
 */
data class CinematographerDecision(
    val currentShot: ShotMissionItem? = null,
    val targetSubject: String = "Subject",
    val shotType: CameraShotType = CameraShotType.WIDE,
    val cameraProfile: CameraProfile = CameraProfile(),
    val recommendedFps: CameraFrameRate = CameraFrameRate.FPS_30,
    val guidance: String = "TARGET NOT DETECTED",
    val readiness: Int = 0,
    val isReady: Boolean = false,
    val reason: String = "Target subject is not visible in camera frame",
    val priority: Int = 10,
    val nextShot: ShotMissionItem? = null,

    // Live framing & telemetry details
    val status: ShotReadinessStatus = ShotReadinessStatus.NOT_READY,
    val targetBoundingBox: RectF? = null,
    val targetFocusNormalizedX: Float = 0.5f,
    val targetFocusNormalizedY: Float = 0.5f,
    val shouldFocusSubject: Boolean = false,
    val movementInstruction: String? = null,
    val isSubjectMatched: Boolean = false,
    val isFramingAcceptable: Boolean = false,
    val isReadyForAutoCapture: Boolean = false
) {
    val instruction: String
        get() = guidance

    val readinessScore: Int
        get() = readiness
}
