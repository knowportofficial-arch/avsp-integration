package com.avsp.pro.capture.camera.guided

import androidx.camera.video.VideoRecordEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression tests for Guided Capture video finalize / bind policy.
 * Device-level CameraX recording still requires a physical device.
 */
class GuidedCaptureVideoPolicyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun errorSourceInactive_isCodeFour() {
        assertThat(VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE).isEqualTo(4)
        assertThat(GuidedCaptureVideoPolicy.ERROR_SOURCE_INACTIVE).isEqualTo(4)
    }

    @Test
    fun shouldBindCamera_onlyWhenReady() {
        GuidedCapturePhase.entries.forEach { phase ->
            val expected = phase == GuidedCapturePhase.READY
            assertThat(GuidedCaptureVideoPolicy.shouldBindCamera(phase)).isEqualTo(expected)
        }
    }

    @Test
    fun recoverableFinalize_requiresSourceInactiveAndNonTrivialFile() {
        val empty = tmp.newFile("empty.mp4")
        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                GuidedCaptureVideoPolicy.ERROR_SOURCE_INACTIVE,
                empty
            )
        ).isFalse()

        val ok = tmp.newFile("ok.mp4")
        ok.writeBytes(ByteArray(GuidedCaptureVideoPolicy.MIN_RECOVERABLE_VIDEO_BYTES.toInt()) { 1 })
        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                GuidedCaptureVideoPolicy.ERROR_SOURCE_INACTIVE,
                ok
            )
        ).isTrue()

        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                ok
            )
        ).isFalse()
    }

    @Test
    fun cleanupPartialVideo_deletesFile() {
        val f = tmp.newFile("partial.mp4")
        f.writeText("partial")
        GuidedCaptureVideoPolicy.cleanupPartialVideo(f)
        assertThat(f.exists()).isFalse()
    }

    @Test
    fun leavingReadyMustNotTriggerBind_documentsComposeKeyBug() {
        // Simulates the previous boolean key: (phase == READY).
        // Transition READY → RECORDING flipped that key and rebound the camera.
        var previousKey = true // READY
        val nextPhase = GuidedCapturePhase.RECORDING
        val nextKey = nextPhase == GuidedCapturePhase.READY
        assertThat(previousKey).isNotEqualTo(nextKey)
        assertThat(GuidedCaptureVideoPolicy.shouldBindCamera(nextPhase)).isFalse()
    }
}
