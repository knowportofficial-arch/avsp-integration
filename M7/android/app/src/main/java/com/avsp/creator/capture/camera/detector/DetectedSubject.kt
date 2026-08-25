package com.avsp.creator.capture.camera.detector

import android.graphics.RectF

/**
 * Represents a visually detected subject in the live camera frame.
 * Normalized coordinates are in the range [0.0, 1.0].
 * (0.0, 0.0) is top-left, (0.5, 0.5) is center, (1.0, 1.0) is bottom-right.
 */
data class DetectedSubject(
    val type: String = "Subject",
    val confidence: Float = 0.0f,
    val normalizedCenterX: Float = 0.5f,
    val normalizedCenterY: Float = 0.5f,
    val normalizedWidth: Float = 0.3f,
    val normalizedHeight: Float = 0.3f,
    val boundingBox: RectF? = null,
    val isRealDetection: Boolean = false,
    val label: String? = null,
    val edgeEnergy: Float = 0.0f,
    val luminanceContrast: Float = 0.0f
)

/**
 * Encapsulates detection output from visual detector providers.
 */
data class SubjectDetectionResult(
    val subjects: List<DetectedSubject> = emptyList(),
    val isDetectorAvailable: Boolean = false,
    val detectorName: String = "None"
) {
    val primarySubject: DetectedSubject?
        get() = subjects.maxByOrNull { it.confidence }

    val hasRealSubjectDetected: Boolean
        get() = isDetectorAvailable && subjects.any { it.isRealDetection && it.confidence >= 0.45f }
}
