package com.avsp.pro.audio.integration

import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegment

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
    val voiceId: String
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
    val audioPackageVersion: String
)

object AudioToVideoContract {
    fun fromPackage(audio: AudioPackage): AudioToVideoHandoff {
        return AudioToVideoHandoff(
            projectId = audio.projectId,
            scriptId = audio.scriptId,
            audioPackageId = audio.audioPackageId,
            language = audio.language,
            provider = audio.provider,
            voiceId = audio.voice.voiceId,
            totalDurationMs = audio.totalDurationMs,
            segments = audio.segments.sortedBy { it.order }.map { it.toRef(audio.voice.voiceId) },
            audioPackageVersion = audio.version
        )
    }

    private fun AudioSegment.toRef(voiceId: String) = VideoAudioSegmentRef(
        segmentId = segmentId,
        sceneId = sceneId,
        order = order,
        relativeAudioPath = relativeAudioPath,
        startMs = startMs,
        endMs = endMs,
        durationMs = durationMs,
        language = language,
        voiceId = this.voiceId.ifBlank { voiceId }
    )
}
