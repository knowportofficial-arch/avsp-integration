package com.avsp.pro.audio.integration

import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.AudioSegmentStatus

/**
 * M3 → M4 integration contract.
 * M4 Video Engine consumes deterministic clip timeline metadata — M3 does NOT render video.
 */
data class VideoAudioClipRef(
    val clipId: String,
    val sceneId: String?,
    val type: String,
    val order: Int,
    val text: String,
    val voiceId: String,
    val language: String,
    val audioUri: String,
    val durationMs: Long,
    val status: String
)

data class AudioToVideoHandoff(
    val projectId: String,
    val scriptId: String,
    val audioPackageId: String,
    val language: String,
    val provider: String,
    val voiceId: String,
    val totalDurationMs: Long,
    val targetDurationMs: Long,
    val actualNarrationDurationMs: Long,
    val durationDeltaMs: Long,
    val clips: List<VideoAudioClipRef>,
    val audioPackageVersion: String,
    val packageStatus: String
) {
    /** @deprecated use [clips] */
    val segments: List<VideoAudioClipRef> get() = clips
}

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
            targetDurationMs = audio.targetDurationMs,
            actualNarrationDurationMs = audio.actualNarrationDurationMs,
            durationDeltaMs = audio.durationDeltaMs,
            clips = audio.segments.sortedBy { it.order }.map { it.toClipRef() },
            audioPackageVersion = audio.version,
            packageStatus = audio.status
        )
    }

    private fun AudioSegment.toClipRef() = VideoAudioClipRef(
        clipId = segmentId,
        sceneId = sceneId,
        type = role.name,
        order = order,
        text = sourceText,
        voiceId = voiceId,
        language = language,
        audioUri = relativeAudioPath,
        durationMs = durationMs,
        status = when (status) {
            AudioSegmentStatus.READY, AudioSegmentStatus.GENERATED -> "READY"
            AudioSegmentStatus.STALE -> "STALE"
            AudioSegmentStatus.FAILED -> "FAILED"
            AudioSegmentStatus.GENERATING -> "GENERATING"
            else -> "NOT_GENERATED"
        }
    )
}
