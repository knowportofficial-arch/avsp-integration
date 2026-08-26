package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraAspectRatio
import com.avsp.pro.capture.camera.model.CameraFrameRate
import com.avsp.pro.capture.camera.model.CameraResolution
import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.model.CaptureOrientation
import java.util.UUID

/**
 * Guided Capture clip specification.
 *
 * Semantic [clipName] + [category] (shot code) come from shot-plan / sample template data.
 * [framingType] is technical WIDE/MEDIUM/CLOSE only — never a replacement for the semantic name.
 *
 * Naming (preserved):
 *   "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
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
    val timerSeconds: Int = 0,
    /** Technical framing / zoom — WIDE / MEDIUM / CLOSE. */
    val framingType: CameraShotType = CameraShotType.WIDE,
    val subjectHint: String = "",
    val guidanceHint: String = "",
    val purpose: String = "",
    val missionId: String? = null,
    val missionShotId: String? = null,
    val sceneId: String? = null,
    val takeIndex: Int = 1
) {
    init {
        require(clipName.isNotBlank()) { "clipName must not be blank" }
        require(category.isNotBlank()) { "category must not be blank" }
        require(targetDurationSeconds > 0) { "targetDurationSeconds must be > 0" }
        require(aspectRatio.isPortrait == (orientation == CaptureOrientation.PORTRAIT)) {
            "aspectRatio ($aspectRatio) and orientation ($orientation) are inconsistent"
        }
    }

    fun toCameraShotType(): CameraShotType = framingType

    /**
     * Preserved Guided Capture naming using existing CameraShotType descriptions only.
     * VIDEO includes duration seconds; PHOTO shows "Photo" (never a fake Ns video cue).
     */
    fun captureInstruction(): String = GuidedCaptureShotNaming.format(
        semanticName = clipName,
        shotCode = category,
        durationSeconds = targetDurationSeconds,
        framing = framingType,
        mediaType = mediaType
    )

    /** Zoom ratio requested from the existing CameraShotType contract. */
    fun requestedZoomRatio(): Float = framingType.defaultZoomRatio
}

/**
 * Ordered guided-capture session clips from either the sample template or a real shot plan.
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
        /**
         * M6 sample template — preserved semantic names/codes/durations/framing descriptions.
         * Not a substitute for a real project shot plan when one can be generated.
         */
        fun sample(): GuidedCaptureTemplate = GuidedCaptureTemplate(
            templateName = "Standard Product Sequence",
            clips = listOf(
                GuidedClipSpec(
                    clipName = "Intro",
                    category = "INTRO",
                    targetDurationSeconds = 5,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT,
                    framingType = CameraShotType.WIDE
                ),
                GuidedClipSpec(
                    clipName = "Wide",
                    category = "WIDE",
                    targetDurationSeconds = 10,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT,
                    framingType = CameraShotType.WIDE
                ),
                GuidedClipSpec(
                    clipName = "Medium",
                    category = "MEDIUM",
                    targetDurationSeconds = 8,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT,
                    framingType = CameraShotType.MEDIUM
                ),
                GuidedClipSpec(
                    clipName = "Close",
                    category = "CLOSE",
                    targetDurationSeconds = 5,
                    aspectRatio = CameraAspectRatio.PORTRAIT_9_16,
                    orientation = CaptureOrientation.PORTRAIT,
                    framingType = CameraShotType.CLOSE
                )
            )
        )
    }
}
