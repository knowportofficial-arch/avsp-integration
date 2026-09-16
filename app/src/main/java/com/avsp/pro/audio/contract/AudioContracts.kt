package com.avsp.pro.audio.contract

/**
 * M3 Audio/TTS — stable audio data contracts.
 */

enum class AudioSegmentStatus {
    PENDING,
    GENERATED,
    FAILED,
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
    val providerId: String = "mock"
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
    val sourceText: String,
    val relativeAudioPath: String,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val provider: String,
    val language: String,
    val status: AudioSegmentStatus = AudioSegmentStatus.GENERATED,
    val plannedDurationMs: Long? = null,
    val durationDeltaMs: Long? = null,
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
    val segments: List<AudioSegment>,
    val totalDurationMs: Long,
    val validation: AudioValidation,
    val metadata: AudioPackageMetadata
) {
    companion object {
        const val CURRENT_VERSION = "1.0"
    }
}

data class AudioGenerationRequest(
    val projectId: String,
    val preferredProviderId: String? = null,
    val voice: VoiceSettings? = null
)
