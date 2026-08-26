package com.avsp.pro.capture.camera.guided

import androidx.camera.video.VideoRecordEvent
import java.io.File

/**
 * Binding / finalize policy for Guided Capture video.
 *
 * CameraX [VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE] (= 4) is emitted when
 * VideoCapture is unbound while a recording is active. Guided Capture must therefore
 * never re-call bind/unbind during RECORDING/SAVING.
 */
object GuidedCaptureVideoPolicy {

    /** Only idle READY sessions should (re)bind CameraX use cases. */
    fun shouldBindCamera(phase: GuidedCapturePhase): Boolean =
        phase == GuidedCapturePhase.READY

    /**
     * ERROR_SOURCE_INACTIVE can still produce a usable file (frames up to detach).
     * Recover when the output exists and has non-trivial size.
     */
    fun isRecoverableFinalizeError(errorCode: Int, videoFile: File): Boolean {
        if (errorCode != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) return false
        return videoFile.exists() && videoFile.length() >= MIN_RECOVERABLE_VIDEO_BYTES
    }

    fun cleanupPartialVideo(videoFile: File) {
        runCatching {
            if (videoFile.exists()) videoFile.delete()
        }
    }

    const val ERROR_SOURCE_INACTIVE = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE

    /** Below this, treat SOURCE_INACTIVE output as unusable (no valid media). */
    const val MIN_RECOVERABLE_VIDEO_BYTES = 8_192L
}
