package com.avsp.pro.audio.integration

import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.ClipRole

/**
 * M3 → M4 integration contract.
 * M4 Video Engine consumes audio segment references + timing — M3 does NOT render video.
 */
data class VideoAudioSegmentRef(
    val segmentId: String,
    val sceneId: String,
    val order: Int,
    val relativeAudioPath: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val language: String,
    val voiceId: String,
    val narration: String = "",
    val role: ClipRole = ClipRole.SCENE
)

data class AudioToVideoHandoff(
    val projectId: String,
    val scriptId: String,
    val audioPackageId: String,
    val language: String,
    val provider: String,
    val voiceId: String,
    val totalDurationMs: Long,
    val segments: List<VideoAudioSegmentRef>,
    val audioPackageVersion: String,
    val introDurationMs: Long = 0L,
    val outroDurationMs: Long = 0L
)

object AudioToVideoContract {
    fun fromPackage(audio: AudioPackage): AudioToVideoHandoff {
        val playable = audio.segments.filter { it.isPlayable() }.sortedBy { it.order }
        return AudioToVideoHandoff(
            projectId = audio.projectId,
            scriptId = audio.scriptId,
            audioPackageId = audio.audioPackageId,
            language = audio.language,
            provider = audio.provider,
            voiceId = audio.voice.voiceId,
            totalDurationMs = audio.totalDurationMs,
            segments = playable.map { it.toRef() },
            audioPackageVersion = audio.version,
            introDurationMs = audio.introAudio()?.takeIf { it.isPlayable() }?.durationMs ?: 0L,
            outroDurationMs = audio.outroAudio()?.takeIf { it.isPlayable() }?.durationMs ?: 0L
        )
    }

    private fun AudioSegment.toRef() = VideoAudioSegmentRef(
        segmentId = segmentId,
        sceneId = sceneId,
        order = order,
        relativeAudioPath = relativeAudioPath,
        startMs = startMs,
        endMs = endMs,
        durationMs = durationMs,
        language = language,
        voiceId = voiceId.ifBlank { "" },
        narration = sourceText,
        role = role
    )
}
