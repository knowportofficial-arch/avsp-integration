package com.avsp.pro.capture.camera.guided

import androidx.camera.video.VideoRecordEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression tests for Guided Capture video finalize / bind policy.
 * Device-level CameraX recording still requires a physical device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun recoverableFinalize_rejectsNonPlayableEvenIfLarge() {
        val empty = tmp.newFile("empty.mp4")
        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                GuidedCaptureVideoPolicy.ERROR_SOURCE_INACTIVE,
                empty
            )
        ).isFalse()

        // Large but corrupt (no valid video track) must NOT recover — this was the
        // "saved but unplayable" device failure mode.
        val bogus = tmp.newFile("bogus.mp4")
        bogus.writeBytes(ByteArray(GuidedCaptureVideoPolicy.MIN_RECOVERABLE_VIDEO_BYTES.toInt()) { 1 })
        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                GuidedCaptureVideoPolicy.ERROR_SOURCE_INACTIVE,
                bogus
            )
        ).isFalse()

        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                bogus
            )
        ).isFalse()
    }

    @Test
    fun recoverableFinalize_acceptsPlayableSample() {
        val sample = copyFixture("guided/sample_playable.mp4", "sample.mp4")
        assertThat(GuidedCaptureVideoValidator.hasMp4FtypBrand(sample)).isTrue()
        val playableGate: (java.io.File) -> Boolean = {
            GuidedCaptureVideoValidator.isPlayable(
                it,
                inspectVideo = {
                    ClipInspector.InspectedVideo(320, 240, 0, 30.0, 30)
                },
                probeDurationMs = { 1000L }
            )
        }
        assertThat(playableGate(sample)).isTrue()
        assertThat(
            GuidedCaptureVideoPolicy.isRecoverableFinalizeError(
                GuidedCaptureVideoPolicy.ERROR_SOURCE_INACTIVE,
                sample,
                isPlayable = playableGate
            )
        ).isTrue()
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
        var previousKey = true // READY
        val nextPhase = GuidedCapturePhase.RECORDING
        val nextKey = nextPhase == GuidedCapturePhase.READY
        assertThat(previousKey).isNotEqualTo(nextKey)
        assertThat(GuidedCaptureVideoPolicy.shouldBindCamera(nextPhase)).isFalse()
    }

    private fun copyFixture(resourcePath: String, name: String): File {
        val out = tmp.newFile(name)
        javaClass.classLoader!!.getResourceAsStream(resourcePath).use { input ->
            requireNotNull(input) { "Missing test resource $resourcePath" }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}
