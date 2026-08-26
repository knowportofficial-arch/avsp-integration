package com.avsp.pro.capture.camera.detector

import androidx.camera.core.ImageProxy

/**
 * Provider-independent visual detection interface.
 * Can be backed by on-device ML (e.g. MediaPipe, ML Kit, TensorFlow Lite)
 * or safe local fallback detectors.
 */
interface VisualSubjectDetector {
    val isDetectorAvailable: Boolean
    val detectorName: String

    /**
     * Analyzes the camera frame image buffer to detect subjects.
     * Implementations MUST NOT close the ImageProxy if caller manages it.
     */
    fun detect(
        imageProxy: ImageProxy,
        targetSubjectHint: String? = null
    ): SubjectDetectionResult
}
