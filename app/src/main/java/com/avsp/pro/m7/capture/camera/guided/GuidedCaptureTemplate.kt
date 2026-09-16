package com.avsp.pro.m7.capture.camera.guided

import com.avsp.pro.m7.capture.camera.model.CameraAspectRatio
import com.avsp.pro.m7.capture.camera.model.CameraFrameRate
import com.avsp.pro.m7.capture.camera.model.CameraResolution
import com.avsp.pro.m7.capture.camera.model.CaptureOrientation
import java.util.UUID

/**
 * M6 — plain, non-AI guided-capture clip specification, exactly as defined by
 * the M6 spec: clip name, category, target duration, aspect ratio, orientation.
 *
 * This is intentionally independent of [com.avsp.pro.m7.capture.camera.mission.ShotMission],
 * which belongs to the pre-existing AI cinematographer subsystem (out of scope for M6).
 */
enum class GuidedClipMediaType { PHOTO, VIDEO }

data class GuidedClipSpec(
    val clipId: String = "clip_${UUID.randomUUID().toString().take(8)}",
    val clipName: String,
    val category: String,
    val targetDurationSeconds: Int,
    val aspectRatio: CameraAspectRatio,
    val orientation: CaptureOrientation,
    val resolution: CameraResolution = CameraResolution.FULL_HD_1080,
    val frameRate: CameraFrameRate = CameraFrameRate.FPS_30,
    val mediaType: GuidedClipMediaType = GuidedClipMediaType.VIDEO,
    val timerSeconds: Int = 0 // 0 = no self-timer; e.g. 3 or 10
) {
    init {
        require(clipName.isNotBlank()) { "clipName must not be blank" }
        require(category.isNotBlank()) { "category must not be blank" }
        require(targetDurationSeconds > 0) { "targetDurationSeconds must be > 0" }
        // M6.2 (FIX 2): aspectRatio and orientation must agree -- CameraX has no distinct
        // "9:16" ratio class, portrait vs landscape comes entirely from orientation/rotation,
        // so an inconsistent pair (e.g. LANDSCAPE_16_9 + PORTRAIT) would silently produce a
        // result that doesn't match either field. Caught here instead of at capture time.
        require(aspectRatio.isPortrait == (orientation == CaptureOrientation.PORTRAIT)) {
            "aspectRatio ($aspectRatio) and orientation ($orientation) are inconsistent"
        }
    }
}

/**
 * An ordered list of clips forming one guided-capture session, e.g. the M6 spec example:
 * Intro (5s) -> Wide (10s) -> Medium (8s) -> Close (5s)
 */
data class GuidedCaptureTemplate(
    val templateId: String = "template_${UUID.randomUUID().toString().take(8)}",
    val templateName: String,
    val clips: List<GuidedClipSpec>
) {
    init {
        require(clips.isNotEmpty()) { "GuidedCaptureTemplate must contain at least one clip" }
    }

    companion object {
        /** The exact example template given in the M6 spec, useful for standalone testing. */
        fun sample(): GuidedCaptureTemplate = GuidedCaptureTemplate(
            templateName = "Standard Product Sequence",
            clips = listOf(
                GuidedClipSpec(
                    clipName = "Intro",
                    category = "INTRO",
                    targetDurationSeconds = 5,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT
                ),
                GuidedClipSpec(
                    clipName = "Wide",
                    category = "WIDE",
                    targetDurationSeconds = 10,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT
                ),
                GuidedClipSpec(
                    clipName = "Medium",
                    category = "MEDIUM",
                    targetDurationSeconds = 8,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT
                ),
                GuidedClipSpec(
                    clipName = "Close",
                    category = "CLOSE",
                    targetDurationSeconds = 5,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT
                )
            )
        )
    }
}
