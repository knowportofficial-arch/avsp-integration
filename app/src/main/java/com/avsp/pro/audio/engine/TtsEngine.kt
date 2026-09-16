package com.avsp.pro.audio.engine

import com.avsp.pro.audio.contract.VoiceSettings

enum class TtsProviderMode {
    MOCK,
    ANDROID_LOCAL,
    REMOTE
}

data class TtsSynthesisRequest(
    val text: String,
    val language: String,
    val voice: VoiceSettings,
    val targetDurationMs: Long? = null
)

data class TtsSynthesisResult(
    val audioBytes: ByteArray,
    val durationMs: Long,
    val mimeType: String,
    val providerId: String,
    val language: String,
    val voiceId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TtsSynthesisResult) return false
        return durationMs == other.durationMs &&
            mimeType == other.mimeType &&
            providerId == other.providerId &&
            language == other.language &&
            voiceId == other.voiceId &&
            audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = audioBytes.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + providerId.hashCode()
        return result
    }
}

/**
 * Provider-independent TTS engine.
 * Future providers (Piper, Coqui, Edge, cloud) implement this interface.
 */
interface TtsEngine {
    val providerId: String
    val displayName: String
    val mode: TtsProviderMode
    fun isAvailable(): Boolean
    fun supportedLanguages(): Set<String>
    fun supportsLanguage(languageCode: String): Boolean
    suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult
}

interface TtsEngineRegistry {
    fun available(): List<TtsEngine>
    fun resolve(preferredProviderId: String? = null): TtsEngine
    fun isRemoteConfigured(): Boolean
}
