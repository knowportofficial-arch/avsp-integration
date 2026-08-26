package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6.1 — JVM unit tests for [AspectRatioValidator]. Pure math, no Android dependency, runs
 * with `./gradlew testDebugUnitTest`, no device required.
 *
 * This is deliberately the ONLY new test surface for the M6.1 aspect-ratio correction.
 * [ClipInspector] (which reads real encoded files via `MediaExtractor`/`BitmapFactory`)
 * is NOT unit tested here or anywhere in this JVM suite — it requires the real Android
 * media framework and a real encoded file, neither of which exist in a local JVM test.
 * Faking that would produce a test that always passes regardless of whether real capture
 * works, which is explicitly what the M6.1 audit asked NOT to do. See
 * `docs/M6/M6.1_HANDOFF.md`, "Tests" section, for the device-test coverage of
 * [ClipInspector] instead.
 */
class AspectRatioValidatorTest {

    @Test
    fun `exact 1080x1920 classifies as portrait 9x16`() {
        val result = AspectRatioValidator.classify(1080, 1920)
        assertEquals(AspectRatioValidator.Classification.PORTRAIT_9_16, result)
    }

    @Test
    fun `exact 1920x1080 classifies as landscape 16x9`() {
        val result = AspectRatioValidator.classify(1920, 1080)
        assertEquals(AspectRatioValidator.Classification.LANDSCAPE_16_9, result)
    }

    @Test
    fun `square dimensions classify as other`() {
        val result = AspectRatioValidator.classify(1000, 1000)
        assertEquals(AspectRatioValidator.Classification.OTHER, result)
    }

    @Test
    fun `4x3 dimensions classify as other, not silently accepted as 16x9`() {
        val result = AspectRatioValidator.classify(1600, 1200)
        assertEquals(AspectRatioValidator.Classification.OTHER, result)
    }

    @Test
    fun `zero or negative dimensions classify as other, not crash`() {
        assertEquals(AspectRatioValidator.Classification.OTHER, AspectRatioValidator.classify(0, 1920))
        assertEquals(AspectRatioValidator.Classification.OTHER, AspectRatioValidator.classify(1080, 0))
        assertEquals(AspectRatioValidator.Classification.OTHER, AspectRatioValidator.classify(-1, -1))
    }

    @Test
    fun `slight encoder rounding within tolerance still matches`() {
        // e.g. a device that encodes 1078x1918 instead of an exact 1080x1920
        val result = AspectRatioValidator.classify(1078, 1918)
        assertEquals(AspectRatioValidator.Classification.PORTRAIT_9_16, result)
    }

    @Test
    fun `deviation beyond tolerance is rejected`() {
        // ~9% off from 9:16 — should not be classified as a match
        val result = AspectRatioValidator.classify(1000, 1600, tolerance = 0.015)
        assertEquals(AspectRatioValidator.Classification.OTHER, result)
    }

    @Test
    fun `matches() agrees with classify() for the requested ratio`() {
        assertTrue(AspectRatioValidator.matches(1080, 1920, CameraAspectRatio.PORTRAIT_9_16))
        assertFalse(AspectRatioValidator.matches(1080, 1920, CameraAspectRatio.LANDSCAPE_16_9))
        assertTrue(AspectRatioValidator.matches(1920, 1080, CameraAspectRatio.LANDSCAPE_16_9))
        assertFalse(AspectRatioValidator.matches(1920, 1080, CameraAspectRatio.PORTRAIT_9_16))
    }

    @Test
    fun `matches() returns false for an unrelated ratio regardless of requested value`() {
        assertFalse(AspectRatioValidator.matches(1600, 1200, CameraAspectRatio.PORTRAIT_9_16))
        assertFalse(AspectRatioValidator.matches(1600, 1200, CameraAspectRatio.LANDSCAPE_16_9))
    }
}
