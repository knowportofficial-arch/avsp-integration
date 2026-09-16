package com.avsp.pro.video.contract

import com.avsp.pro.audio.integration.AudioToVideoHandoff

/** M4 render contract. Android and desktop backends consume the same plan. */
data class VideoSceneInput(
    val sceneId: String,
    val order: Int,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val audioRelativePath: String,
    val visualRelativePath: String?,
    val visualMimeType: String? = null,
    val overlayText: String? = null
)

data class VideoRenderPlan(
    val projectId: String,
    val scriptId: String,
    val audioPackageId: String,
    val language: String,
    val totalDurationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val scenes: List<VideoSceneInput>
) {
    companion object {
        const val DEFAULT_SHORTS_WIDTH = 1080
        const val DEFAULT_SHORTS_HEIGHT = 1920
        const val DEFAULT_LANDSCAPE_WIDTH = 1920
        const val DEFAULT_LANDSCAPE_HEIGHT = 1080
        const val DEFAULT_SQUARE_WIDTH = 1080
        const val DEFAULT_SQUARE_HEIGHT = 1080
        const val DEFAULT_PORTRAIT_4_5_WIDTH = 1080
        const val DEFAULT_PORTRAIT_4_5_HEIGHT = 1350
        const val DEFAULT_FPS = 30
    }
}

object VideoRenderPlanFactory {
    fun fromAudio(
        handoff: AudioToVideoHandoff,
        visualsByScene: Map<String, String>,
        visualMimeByScene: Map<String, String> = emptyMap(),
        width: Int = VideoRenderPlan.DEFAULT_SHORTS_WIDTH,
        height: Int = VideoRenderPlan.DEFAULT_SHORTS_HEIGHT,
        fps: Int = VideoRenderPlan.DEFAULT_FPS
    ): VideoRenderPlan {
        val segments = handoff.segments.sortedBy { it.order }
        return VideoRenderPlan(
            projectId = handoff.projectId,
            scriptId = handoff.scriptId,
            audioPackageId = handoff.audioPackageId,
            language = handoff.language,
            totalDurationMs = handoff.totalDurationMs,
            width = width,
            height = height,
            fps = fps,
            scenes = segments.map { segment ->
                VideoSceneInput(
                    sceneId = segment.sceneId,
                    order = segment.order,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    durationMs = segment.durationMs,
                    audioRelativePath = segment.relativeAudioPath,
                    visualRelativePath = visualsByScene[segment.sceneId],
                    visualMimeType = visualMimeByScene[segment.sceneId]
                )
            }
        )
    }
}

data class VideoRenderResult(
    val projectId: String,
    val outputRelativePath: String,
    val outputAbsolutePath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String,
    val audioCodec: String,
    val status: String
)
