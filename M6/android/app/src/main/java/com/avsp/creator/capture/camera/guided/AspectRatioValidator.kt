package com.avsp.creator.capture.camera.guided

import com.avsp.creator.capture.camera.model.CameraAspectRatio
import kotlin.math.abs

/**
 * M6.1 — validates actually-produced (post-rotation) pixel dimensions against a requested
 * [CameraAspectRatio], within a small tolerance for encoder rounding (e.g. 1920x1080 is
 * exactly 16:9, but some devices produce slightly different pixel counts). Pure Kotlin, no
 * Android dependency, runs as a local JVM unit test -- this is the "explicit validation/test
 * path for the resulting output dimensions/aspect ratio" required by the M6.1 audit.
 */
object AspectRatioValidator {

    /** Default tolerance: actual ratio may differ from the target by up to 1.5%. */
    const val DEFAULT_TOLERANCE = 0.015

    enum class Classification { PORTRAIT_9_16, LANDSCAPE_16_9, OTHER }

    /**
     * Classifies a (display, i.e. rotation-corrected) width x height pair as matching
     * 9:16, 16:9, or neither, within [tolerance].
     */
    fun classify(displayWidth: Int, displayHeight: Int, tolerance: Double = DEFAULT_TOLERANCE): Classification {
        if (displayWidth <= 0 || displayHeight <= 0) return Classification.OTHER
        val ratio = displayWidth.toDouble() / displayHeight.toDouble()
        val target9x16 = 9.0 / 16.0
        val target16x9 = 16.0 / 9.0
        return when {
            relativeDifference(ratio, target9x16) <= tolerance -> Classification.PORTRAIT_9_16
            relativeDifference(ratio, target16x9) <= tolerance -> Classification.LANDSCAPE_16_9
            else -> Classification.OTHER
        }
    }

    /** True if the actual dimensions match what [requested] asked for, within [tolerance]. */
    fun matches(displayWidth: Int, displayHeight: Int, requested: CameraAspectRatio, tolerance: Double = DEFAULT_TOLERANCE): Boolean {
        val classification = classify(displayWidth, displayHeight, tolerance)
        return when (requested) {
            CameraAspectRatio.PORTRAIT_9_16 -> classification == Classification.PORTRAIT_9_16
            CameraAspectRatio.LANDSCAPE_16_9 -> classification == Classification.LANDSCAPE_16_9
        }
    }

    private fun relativeDifference(a: Double, b: Double): Double = abs(a - b) / b
}
