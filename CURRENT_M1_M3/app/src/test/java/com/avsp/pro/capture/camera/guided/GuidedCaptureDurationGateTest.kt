package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraAspectRatio
import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.model.CaptureOrientation
import com.avsp.pro.dataset.model.Recommendation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression: KEEP must not appear before the shot-plan required duration is met.
 */
class GuidedCaptureDurationGateTest {

    private fun videoClip(seconds: Int, name: String = "Intro", code: String = "INTRO") =
        GuidedClipSpec(
            clipName = name,
            category = code,
            targetDurationSeconds = seconds,
            aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
            orientation = CaptureOrientation.PORTRAIT,
            mediaType = GuidedClipMediaType.VIDEO,
            framingType = CameraShotType.WIDE
        )

    private fun photoClip() =
        GuidedClipSpec(
            clipName = "Still",
            category = "PHOTO",
            targetDurationSeconds = 5,
            aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
            orientation = CaptureOrientation.PORTRAIT,
            mediaType = GuidedClipMediaType.PHOTO,
            framingType = CameraShotType.MEDIUM
        )

    @Test
    fun fiveSecondShot_keepUnavailableBeforeRequiredDuration() {
        val clip = videoClip(5)
        val required = GuidedCaptureDurationGate.requiredDurationMs(clip)
        assertThat(required).isEqualTo(5_000L)

        // 0–4.99s window
        for (ms in listOf(0L, 1L, 100L, 499L, 1_000L, 2_500L, 4_999L)) {
            assertThat(
                GuidedCaptureDurationGate.hasMetRequiredDuration(required, ms)
            ).isFalse()
            assertThat(
                GuidedCaptureDurationGate.isKeepEligible(
                    mediaType = GuidedClipMediaType.VIDEO,
                    requiredDurationMs = required,
                    actualDurationMs = ms,
                    qualityRecommendation = Recommendation.KEEP
                )
            ).isFalse()
            assertThat(
                GuidedCaptureDurationGate.canShowKeepAction(
                    mediaType = GuidedClipMediaType.VIDEO,
                    requiredDurationMs = required,
                    actualDurationMs = ms,
                    qualityRecommendation = Recommendation.KEEP
                )
            ).isFalse()
            assertThat(
                GuidedCaptureDurationGate.resolveRecommendation(
                    mediaType = GuidedClipMediaType.VIDEO,
                    requiredDurationMs = required,
                    actualDurationMs = ms,
                    qualityRecommendation = Recommendation.KEEP
                )
            ).isEqualTo(Recommendation.RETAKE)
        }
    }

    @Test
    fun fiveSecondShot_keepEligibleOnlyAfterRequiredDurationAndQualityKeep() {
        val required = 5_000L
        assertThat(GuidedCaptureDurationGate.hasMetRequiredDuration(required, 5_000L)).isTrue()
        assertThat(GuidedCaptureDurationGate.hasMetRequiredDuration(required, 5_001L)).isTrue()

        assertThat(
            GuidedCaptureDurationGate.isKeepEligible(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = 5_000L,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isTrue()

        // Duration met but quality says REVIEW → no KEEP button
        assertThat(
            GuidedCaptureDurationGate.isKeepEligible(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = 5_000L,
                qualityRecommendation = Recommendation.REVIEW
            )
        ).isFalse()
    }

    @Test
    fun tenSecondShot_sameDurationGate() {
        val clip = videoClip(10, name = "Wide", code = "WIDE")
        val required = GuidedCaptureDurationGate.requiredDurationMs(clip)
        assertThat(required).isEqualTo(10_000L)

        assertThat(GuidedCaptureDurationGate.hasMetRequiredDuration(required, 9_999L)).isFalse()
        assertThat(
            GuidedCaptureDurationGate.isKeepEligible(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = 9_999L,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isFalse()

        assertThat(GuidedCaptureDurationGate.hasMetRequiredDuration(required, 10_000L)).isTrue()
        assertThat(
            GuidedCaptureDurationGate.isKeepEligible(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = 10_000L,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isTrue()
    }

    @Test
    fun earlyStopCannotProduceKeep() {
        val required = 5_000L
        val early = 1_200L
        assertThat(
            GuidedCaptureDurationGate.resolveRecommendation(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = early,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isEqualTo(Recommendation.RETAKE)
        assertThat(
            GuidedCaptureDurationGate.canShowKeepAction(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = early,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isFalse()
        val message = GuidedCaptureDurationGate.insufficientDurationMessage(required, early)
        assertThat(message).contains("Insufficient duration")
        assertThat(message).contains("need 5s")
    }

    @Test
    fun completedValidVideoProceedsToQualityRecommendation() {
        val required = 5_000L
        val actual = 5_200L
        assertThat(
            GuidedCaptureDurationGate.resolveRecommendation(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = actual,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isEqualTo(Recommendation.KEEP)
        assertThat(
            GuidedCaptureDurationGate.resolveRecommendation(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = actual,
                qualityRecommendation = Recommendation.REVIEW
            )
        ).isEqualTo(Recommendation.REVIEW)
        assertThat(
            GuidedCaptureDurationGate.resolveRecommendation(
                mediaType = GuidedClipMediaType.VIDEO,
                requiredDurationMs = required,
                actualDurationMs = actual,
                qualityRecommendation = Recommendation.RETAKE
            )
        ).isEqualTo(Recommendation.RETAKE)
    }

    @Test
    fun photoFlowUnaffectedByDurationGate() {
        val clip = photoClip()
        // Photo metadata may still carry a display duration; gate must be 0.
        assertThat(GuidedCaptureDurationGate.requiredDurationMs(clip)).isEqualTo(0L)
        assertThat(GuidedCaptureDurationGate.hasMetRequiredDuration(0L, 0L)).isTrue()
        assertThat(
            GuidedCaptureDurationGate.isKeepEligible(
                mediaType = GuidedClipMediaType.PHOTO,
                requiredDurationMs = 0L,
                actualDurationMs = 0L,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isTrue()
        assertThat(
            GuidedCaptureDurationGate.resolveRecommendation(
                mediaType = GuidedClipMediaType.PHOTO,
                requiredDurationMs = 0L,
                actualDurationMs = 0L,
                qualityRecommendation = Recommendation.KEEP
            )
        ).isEqualTo(Recommendation.KEEP)
    }

    @Test
    fun recordingPhaseStateNeverMarksKeepEligible() {
        val template = GuidedCaptureTemplate.sample()
        val recording = GuidedCaptureState(
            template = template,
            currentClipIndex = 0,
            phase = GuidedCapturePhase.RECORDING,
            elapsedMs = 2_000L,
            targetDurationMs = 5_000L,
            keepEligible = false
        )
        assertThat(recording.phase).isEqualTo(GuidedCapturePhase.RECORDING)
        assertThat(recording.keepEligible).isFalse()
        assertThat(recording.elapsedMs).isLessThan(recording.targetDurationMs)
    }

    @Test
    fun requiredDurationComesFromClipMetadataNotHardCodedWideMediumClose() {
        assertThat(GuidedCaptureDurationGate.requiredDurationMs(videoClip(5))).isEqualTo(5_000L)
        assertThat(GuidedCaptureDurationGate.requiredDurationMs(videoClip(8))).isEqualTo(8_000L)
        assertThat(GuidedCaptureDurationGate.requiredDurationMs(videoClip(10))).isEqualTo(10_000L)
        // Sample Intro is 5s VIDEO — not replaced by framing-type constants.
        val intro = GuidedCaptureTemplate.sample().clips.first()
        assertThat(intro.clipName).isEqualTo("Intro")
        assertThat(intro.targetDurationSeconds).isEqualTo(5)
        assertThat(GuidedCaptureDurationGate.requiredDurationMs(intro)).isEqualTo(5_000L)
    }
}
