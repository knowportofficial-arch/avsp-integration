package com.avsp.pro.audio.contract

/**
 * M3 Audio/TTS — stable audio data contracts.
 */

enum class AudioSegmentStatus {
    NOT_GENERATED,
    GENERATING,
    READY,
    FAILED,
    STALE,
    /** @deprecated use NOT_GENERATED */
    PENDING,
    /** @deprecated use READY */
    GENERATED,
    SKIPPED
}

enum class AudioValidationStatus {
    VALID,
    WARNING,
    INVALID
}

data class VoiceSettings(
    val language: String,
    val voiceId: String = "default",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val providerId: String = "mock",
    val voiceMode: VoiceMode = VoiceMode.SIMPLE_LOCAL
)

data class AudioFormatInfo(
    val format: String = "wav",
    val sampleRateHz: Int = 16_000,
    val channels: Int = 1,
    val encoding: String = "pcm_s16le",
    val bitsPerSample: Int = 16
)

data class AudioValidation(
    val isValid: Boolean,
    val status: AudioValidationStatus,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

data class AudioSegment(
    val segmentId: String,
    val sceneId: String,
    val order: Int,
    val role: SegmentRole = SegmentRole.SCENE,
    val title: String = "",
    val sourceText: String,
    val sourceTextHash: String = "",
    val relativeAudioPath: String,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val provider: String,
    val language: String,
    val voiceId: String = "default",
    val voiceMode: VoiceMode = VoiceMode.SIMPLE_LOCAL,
    val status: AudioSegmentStatus = AudioSegmentStatus.READY,
    val plannedDurationMs: Long? = null,
    val durationDeltaMs: Long? = null,
    val generatedAt: Long? = null,
    val voiceAssignmentHash: String = "",
    val errorCode: String? = null,
    val errorMessage: String? = null
)

data class AudioPackageMetadata(
    val createdAt: Long,
    val updatedAt: Long,
    val scriptVersion: String,
    val format: AudioFormatInfo = AudioFormatInfo(),
    val voice: VoiceSettings,
    val timingDriftWarnings: List<String> = emptyList()
)

/**
 * Full M3 audio package — scene-aligned, persistable, M4-consumable.
 */
data class AudioPackage(
    val version: String = CURRENT_VERSION,
    val projectId: String,
    val scriptId: String,
    val audioPackageId: String,
    val language: String,
    val provider: String,
    val voice: VoiceSettings,
    val voiceConfiguration: VoiceConfiguration? = null,
    val segments: List<AudioSegment>,
    val introAudio: AudioSegment? = null,
    val outroAudio: AudioSegment? = null,
    val totalDurationMs: Long,
    val targetDurationMs: Long = 0L,
    val actualNarrationDurationMs: Long = 0L,
    val durationDeltaMs: Long = 0L,
    val generatedAt: Long? = null,
    val status: String = "NOT_GENERATED",
    val validation: AudioValidation,
    val metadata: AudioPackageMetadata
) {
    companion object {
        const val CURRENT_VERSION = "1.2"
    }

    val playableSegments: List<AudioSegment>
        get() = segments
            .sortedBy { it.order }
            .filter { it.status == AudioSegmentStatus.READY && it.relativeAudioPath.isNotBlank() }

    val sceneAudio: List<AudioSegment>
        get() = segments.filter { it.role == SegmentRole.SCENE || it.role == SegmentRole.BODY }
}

object AudioPackageStatus {
    const val NOT_GENERATED = "NOT_GENERATED"
    const val GENERATING = "GENERATING"
    const val READY = "READY"
    const val FAILED = "FAILED"
    const val STALE = "STALE"

    fun fromSegments(segments: List<AudioSegment>): String = when {
        segments.isEmpty() -> NOT_GENERATED
        segments.any { it.status == AudioSegmentStatus.FAILED } -> FAILED
        segments.any { it.status == AudioSegmentStatus.STALE } -> STALE
        segments.any { it.status == AudioSegmentStatus.GENERATING } -> GENERATING
        segments.all { it.status == AudioSegmentStatus.READY } -> READY
        else -> NOT_GENERATED
    }
}

data class AudioGenerationRequest(
    val projectId: String,
    val preferredProviderId: String? = null,
    val voice: VoiceSettings? = null,
    val voiceConfiguration: VoiceConfiguration? = null
)
