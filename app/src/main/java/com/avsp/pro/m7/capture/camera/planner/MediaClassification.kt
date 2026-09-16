package com.avsp.pro.m7.capture.camera.planner

/**
 * Media destination classification.
 *
 * PREDEFINED belongs to a planned Master Shot List item and can be
 * classified as WIDE/MEDIUM/CLOSE or another contextual framing.
 * MANUAL_EXTRA is intentionally not forced into that taxonomy.
 */
enum class MediaClassification {
    PREDEFINED,
    MANUAL_EXTRA
}

data class CaptureMediaMetadata(
    val classification: MediaClassification,
    val shotId: String? = null,
    val executionMode: ShotExecutionMode = ShotExecutionMode.MANUAL,
    val framing: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val qualityScore: Int = 0
)
