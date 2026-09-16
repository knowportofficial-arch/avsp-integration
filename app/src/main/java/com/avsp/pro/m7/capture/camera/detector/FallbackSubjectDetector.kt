package com.avsp.pro.m7.capture.camera.detector

import androidx.camera.core.ImageProxy

/**
 * Safe local fallback detector when no external ML model is bound.
 * Strictly adheres to AVSP requirements:
 * Does NOT falsely claim that a station, train, person, sign, etc. was detected when not present.
 * Reports `isDetectorAvailable = false` and `subjects = emptyList()`.
 */
class FallbackSubjectDetector : VisualSubjectDetector {

    override val isDetectorAvailable: Boolean = false
    override val detectorName: String = "SafeFallbackDetector"

    override fun detect(
        imageProxy: ImageProxy,
        targetSubjectHint: String?
    ): SubjectDetectionResult {
        // Safe fallback returns empty subjects list with isDetectorAvailable = false
        return SubjectDetectionResult(
            subjects = emptyList(),
            isDetectorAvailable = false,
            detectorName = detectorName
        )
    }
}
