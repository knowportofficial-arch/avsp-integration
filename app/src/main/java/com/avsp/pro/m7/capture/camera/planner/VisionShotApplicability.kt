package com.avsp.pro.m7.capture.camera.planner

/**
 * Result of backend vision analysis for a planned shot.
 *
 * This class deliberately contains no ML Kit dependency. Existing vision
 * implementations can map their results into this stable contract.
 */
data class VisionShotApplicability(
    val shotId: String,
    val subjectPresent: Boolean,
    val framingSuitable: Boolean = false,
    val qualityScore: Int = 0,
    val confidence: Float = 0f,
    val reason: String? = null
) {
    val applicable: Boolean
        get() = subjectPresent && (framingSuitable || qualityScore >= 70)
}
