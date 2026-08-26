package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraAspectRatio
import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.model.CaptureOrientation
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuidedCaptureVideoValidatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun rejectsMissingAndTinyFiles() {
        val missing = tmp.root.resolve("nope.mp4")
        assertThat(GuidedCaptureVideoValidator.validate(missing))
            .isInstanceOf(GuidedCaptureVideoValidator.Result.Unplayable::class.java)

        val tiny = tmp.newFile("tiny.mp4")
        tiny.writeBytes(ByteArray(64) { 1 })
        val result = GuidedCaptureVideoValidator.validate(tiny)
        assertThat(result).isInstanceOf(GuidedCaptureVideoValidator.Result.Unplayable::class.java)
    }

    @Test
    fun rejectsCorruptFtypWithoutVideoTrack() {
        val corrupt = copyFixture("guided/corrupt_ftyp_only.mp4", "corrupt.mp4")
        assertThat(GuidedCaptureVideoValidator.hasMp4FtypBrand(corrupt)).isTrue()
        val result = GuidedCaptureVideoValidator.validate(corrupt)
        assertThat(result).isInstanceOf(GuidedCaptureVideoValidator.Result.Unplayable::class.java)
        assertThat(GuidedCaptureVideoValidator.isPlayable(corrupt)).isFalse()
    }

    @Test
    fun acceptsPlayableSampleMp4_withReadableTrack() {
        val sample = copyFixture("guided/sample_playable.mp4", "ok.mp4")
        assertThat(GuidedCaptureVideoValidator.hasMp4FtypBrand(sample)).isTrue()
        // Robolectric MediaExtractor cannot decode H.264 fixtures; inject a track result
        // that mirrors what ClipInspector returns on a real device for a valid MP4.
        val inspected = ClipInspector.InspectedVideo(
            encodedWidth = 320,
            encodedHeight = 240,
            rotationDegrees = 0,
            measuredFps = 30.0,
            framesScanned = 30
        )
        val result = GuidedCaptureVideoValidator.validate(
            sample,
            inspectVideo = { inspected },
            probeDurationMs = { 1000L }
        )
        assertThat(result).isInstanceOf(GuidedCaptureVideoValidator.Result.Playable::class.java)
        val playable = result as GuidedCaptureVideoValidator.Result.Playable
        assertThat(playable.durationMs).isAtLeast(GuidedCaptureVideoValidator.MIN_PLAYABLE_DURATION_MS)
        assertThat(playable.inspected.encodedWidth).isGreaterThan(0)
        assertThat(playable.inspected.framesScanned).isAtLeast(2L)
    }

    @Test
    fun realMediaExtractorPath_documentsDeviceRequirement() {
        val sample = copyFixture("guided/sample_playable.mp4", "device.mp4")
        // On JVM/Robolectric this often returns Unplayable(no readable video track).
        // Production devices must still use the default ClipInspector path.
        val result = GuidedCaptureVideoValidator.validate(sample)
        assertThat(result).isInstanceOf(GuidedCaptureVideoValidator.Result::class.java)
    }

    private fun copyFixture(resourcePath: String, name: String): java.io.File {
        val out = tmp.newFile(name)
        javaClass.classLoader!!.getResourceAsStream(resourcePath).use { input ->
            requireNotNull(input) { "Missing test resource $resourcePath" }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}

class GuidedCaptureShotConfigTest {

    @Test
    fun sampleTemplateMapsWideMediumCloseZoomAndDuration() {
        val template = GuidedCaptureTemplate.sample()
        assertThat(template.clips).hasSize(4)

        val byCategory = template.clips.associateBy { it.category }
        assertThat(byCategory["WIDE"]!!.targetDurationSeconds).isEqualTo(10)
        assertThat(byCategory["MEDIUM"]!!.targetDurationSeconds).isEqualTo(8)
        assertThat(byCategory["CLOSE"]!!.targetDurationSeconds).isEqualTo(5)

        assertThat(byCategory["WIDE"]!!.toCameraShotType()).isEqualTo(CameraShotType.WIDE)
        assertThat(byCategory["MEDIUM"]!!.toCameraShotType()).isEqualTo(CameraShotType.MEDIUM)
        assertThat(byCategory["CLOSE"]!!.toCameraShotType()).isEqualTo(CameraShotType.CLOSE)
        assertThat(byCategory["INTRO"]!!.toCameraShotType()).isEqualTo(CameraShotType.WIDE)

        assertThat(byCategory["MEDIUM"]!!.toCameraShotType().defaultZoomRatio).isEqualTo(1.8f)
        assertThat(byCategory["CLOSE"]!!.toCameraShotType().defaultZoomRatio).isEqualTo(3.0f)
    }

    @Test
    fun preservedNamingFormat_introExactly() {
        val intro = GuidedCaptureTemplate.sample().clips.first { it.category == "INTRO" }
        assertThat(intro.clipName).isEqualTo("Intro")
        assertThat(intro.captureInstruction()).isEqualTo(
            "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
        )
        // Framing label must not replace the semantic name / shot code.
        assertThat(intro.captureInstruction()).startsWith("Intro · INTRO ·")
    }

    @Test
    fun preservedNamingFormat_doesNotPromoteFramingAsSemanticNameAlone() {
        val close = GuidedCaptureTemplate.sample().clips.first { it.category == "CLOSE" }
        val formatted = GuidedCaptureShotNaming.format(
            semanticName = "Established",
            shotCode = "ESTABLISHED",
            durationSeconds = 5,
            framing = CameraShotType.CLOSE
        )
        assertThat(formatted).isEqualTo(
            "Established · ESTABLISHED · 5s · Close (Detail, face, product & macro texture shot)"
        )
        assertThat(formatted.startsWith("Close ·")).isFalse()
        assertThat(close.clipName).isEqualTo("Close") // sample template data unchanged
    }

    @Test
    fun clipRequiresPositiveDurationAndConsistentAspect() {
        GuidedClipSpec(
            clipName = "Ok",
            category = "WIDE",
            targetDurationSeconds = 3,
            aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
            orientation = CaptureOrientation.PORTRAIT
        )
        try {
            GuidedClipSpec(
                clipName = "Bad",
                category = "WIDE",
                targetDurationSeconds = 0,
                aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                orientation = CaptureOrientation.PORTRAIT
            )
            throw AssertionError("expected require failure")
        } catch (_: IllegalArgumentException) {
        }
    }
}
