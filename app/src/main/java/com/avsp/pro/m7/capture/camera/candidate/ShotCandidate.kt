package com.avsp.pro.m7.capture.camera.candidate

import android.graphics.RectF
import com.avsp.pro.m7.capture.camera.mission.ShotStatus
import com.avsp.pro.m7.capture.camera.model.CameraFrameRate
import com.avsp.pro.m7.capture.camera.model.CameraResolution
import com.avsp.pro.m7.capture.camera.model.CameraShotType

/**
 * AVSP M1.5.4 Evidence-Driven Shot Candidate.
 * Retains complete structured visual evidence, user intent, event linkage,
 * optical feasibility, and coverage history.
 */
data class ShotCandidate(
    val id: String,
    val objective: String,
    val targetSubject: String,
    val detectedLabel: String = "",
    val confidence: Float = 0.0f,
    val boundingBox: RectF? = null,
    val subjectSize: Float = 0.0f,
    val subjectPositionX: Float = 0.5f,
    val subjectPositionY: Float = 0.5f,
    val motionMagnitude: Float = 0.0f,
    val shotType: CameraShotType = CameraShotType.MEDIUM,
    val priority: Int = 10,
    val reason: String,
    val requiredComposition: String,
    val visualEvidence: List<String> = emptyList(),
    val sceneEvidence: List<String> = emptyList(),
    val userIntent: String? = null,
    val eventEvidence: String? = null,
    val eventRelevance: String? = null,
    val isCurrentlyVisible: Boolean = false,
    val cinematographicValue: Float = 0.5f,
    val opportunityScore: Float = 0.0f,
    val framingFeasibility: Float = 0.5f,
    val accessibility: Float = 0.5f,
    val novelty: Float = 1.0f,
    val status: ShotStatus = ShotStatus.PENDING,
    val preferredFrameRate: CameraFrameRate = CameraFrameRate.FPS_30,
    val preferredResolution: CameraResolution = CameraResolution.FULL_HD_1080,
    val score: Float = 0f,
    val isCompleted: Boolean = false,
    val capturedMediaUri: String? = null
)
