package com.avsp.pro.capture.camera.guided

/**
 * REVIEW-phase action labels. Quality badge may say RETAKE · N%; the KEEP control
 * remains available whenever [GuidedCapturePhase.REVIEW] is shown (duration already met).
 */
object GuidedCaptureReviewUi {

    /** Non-last clips → NEXT; final clip → FINISH. */
    fun acceptButtonLabel(isLastClip: Boolean): String =
        if (isLastClip) "FINISH" else "NEXT"

    /** REVIEW always exposes KEEP/NEXT (or FINISH). Short takes use INSUFFICIENT_DURATION. */
    fun shouldShowAcceptAction(phase: GuidedCapturePhase): Boolean =
        phase == GuidedCapturePhase.REVIEW
}
