package com.avsp.pro.capture.camera.guided

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuidedCaptureReviewUiTest {

    @Test
    fun reviewAlwaysShowsAcceptActionEvenWhenQualitySaysRetake() {
        assertThat(GuidedCaptureReviewUi.shouldShowAcceptAction(GuidedCapturePhase.REVIEW)).isTrue()
        assertThat(
            GuidedCaptureReviewUi.shouldShowAcceptAction(GuidedCapturePhase.INSUFFICIENT_DURATION)
        ).isFalse()
        assertThat(
            GuidedCaptureReviewUi.shouldShowAcceptAction(GuidedCapturePhase.RECORDING)
        ).isFalse()
    }

    @Test
    fun acceptLabelIsNextUntilFinalClipThenFinish() {
        val template = GuidedCaptureTemplate.sample()
        assertThat(template.clips).hasSize(4)

        val shot1 = GuidedCaptureState(template = template, currentClipIndex = 0)
        val shot2 = GuidedCaptureState(template = template, currentClipIndex = 1)
        val shot3 = GuidedCaptureState(template = template, currentClipIndex = 2)
        val shot4 = GuidedCaptureState(template = template, currentClipIndex = 3)

        assertThat(shot1.isLastClip).isFalse()
        assertThat(shot2.isLastClip).isFalse()
        assertThat(shot3.isLastClip).isFalse()
        assertThat(shot4.isLastClip).isTrue()

        assertThat(GuidedCaptureReviewUi.acceptButtonLabel(shot1.isLastClip)).isEqualTo("NEXT")
        assertThat(GuidedCaptureReviewUi.acceptButtonLabel(shot2.isLastClip)).isEqualTo("NEXT")
        assertThat(GuidedCaptureReviewUi.acceptButtonLabel(shot3.isLastClip)).isEqualTo("NEXT")
        assertThat(GuidedCaptureReviewUi.acceptButtonLabel(shot4.isLastClip)).isEqualTo("FINISH")
    }
}
