package com.avsp.creator.capture.camera.guided

import com.avsp.creator.capture.camera.model.CameraAspectRatio
import com.avsp.creator.capture.camera.model.CaptureOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin unit tests for the M6 guided-capture template/state logic. These run on the
 * local JVM (no device/emulator needed): `./gradlew testDebugUnitTest`.
 *
 * CameraX-dependent behavior (preview binding, actual recording, real storage I/O) cannot be
 * unit tested this way and is covered instead by the on-device manual test procedure in the
 * M6 handoff doc.
 */
class GuidedCaptureTemplateTest {

    @Test
    fun `sample template matches the M6 spec example sequence`() {
        val template = GuidedCaptureTemplate.sample()
        assertEquals(4, template.clips.size)
        assertEquals(listOf("Intro", "Wide", "Medium", "Close"), template.clips.map { it.clipName })
        assertEquals(listOf(5, 10, 8, 5), template.clips.map { it.targetDurationSeconds })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `template with no clips is rejected`() {
        GuidedCaptureTemplate(templateName = "Empty", clips = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `clip with non-positive duration is rejected`() {
        GuidedClipSpec(
            clipName = "Bad",
            category = "WIDE",
            targetDurationSeconds = 0,
            aspectRatio = CameraAspectRatio.LANDSCAPE_16_9,
            orientation = CaptureOrientation.LANDSCAPE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `clip with blank name is rejected`() {
        GuidedClipSpec(
            clipName = "  ",
            category = "WIDE",
            targetDurationSeconds = 5,
            aspectRatio = CameraAspectRatio.LANDSCAPE_16_9,
            orientation = CaptureOrientation.LANDSCAPE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `M6_2 - inconsistent aspectRatio and orientation is rejected`() {
        // LANDSCAPE_16_9 paired with PORTRAIT is exactly the kind of mismatch that would
        // silently produce output matching neither field -- must be caught at construction.
        GuidedClipSpec(
            clipName = "Inconsistent",
            category = "WIDE",
            targetDurationSeconds = 5,
            aspectRatio = CameraAspectRatio.LANDSCAPE_16_9,
            orientation = CaptureOrientation.PORTRAIT
        )
    }

    @Test
    fun `M6_2 - consistent aspectRatio and orientation pairs are accepted`() {
        GuidedClipSpec(
            clipName = "Consistent Portrait",
            category = "WIDE",
            targetDurationSeconds = 5,
            aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
            orientation = CaptureOrientation.PORTRAIT
        )
        GuidedClipSpec(
            clipName = "Consistent Landscape",
            category = "WIDE",
            targetDurationSeconds = 5,
            aspectRatio = CameraAspectRatio.LANDSCAPE_16_9,
            orientation = CaptureOrientation.LANDSCAPE
        )
        // no exception = pass
    }

    @Test
    fun `orientation is derived correctly from device rotation`() {
        assertEquals(CaptureOrientation.PORTRAIT, CaptureOrientation.fromDeviceRotationDegrees(0))
        assertEquals(CaptureOrientation.LANDSCAPE, CaptureOrientation.fromDeviceRotationDegrees(90))
        assertEquals(CaptureOrientation.PORTRAIT, CaptureOrientation.fromDeviceRotationDegrees(180))
        assertEquals(CaptureOrientation.LANDSCAPE, CaptureOrientation.fromDeviceRotationDegrees(270))
        assertEquals(CaptureOrientation.LANDSCAPE, CaptureOrientation.fromDeviceRotationDegrees(-90))
    }

    @Test
    fun `state progress fraction is clamped and last-clip detection is correct`() {
        val template = GuidedCaptureTemplate.sample()
        val midState = GuidedCaptureState(template = template, currentClipIndex = 1, elapsedMs = 5000L, targetDurationMs = 10000L)
        assertEquals(0.5f, midState.progressFraction, 0.001f)
        assertFalse(midState.isLastClip)

        val overrunState = midState.copy(elapsedMs = 99000L)
        assertEquals(1.0f, overrunState.progressFraction, 0.001f)

        val lastState = midState.copy(currentClipIndex = template.clips.lastIndex)
        assertTrue(lastState.isLastClip)
    }

    @Test
    fun `aspect ratio portrait flag is correct`() {
        assertTrue(CameraAspectRatio.PORTRAIT_9_16.isPortrait)
        assertFalse(CameraAspectRatio.LANDSCAPE_16_9.isPortrait)
    }
}
