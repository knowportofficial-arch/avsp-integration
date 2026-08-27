package com.avsp.pro.audio.engine

import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.audio.wav.WavEncoder
import kotlin.math.max

/**
 * Deterministic MOCK TTS — generates tone WAV, never claims real speech.
 */
class MockTtsEngine : TtsEngine {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "Mock TTS"
    override val mode: TtsProviderMode = TtsProviderMode.MOCK

    override fun isAvailable(): Boolean = true

    override fun supportedLanguages(): Set<String> = AudioLanguageRegistry.supportedCodes()

    override fun supportsLanguage(languageCode: String): Boolean =
        AudioLanguageRegistry.isSupported(languageCode)

    override suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult {
        if (request.text.isBlank()) {
            throw AudioException(AudioErrorCode.INVALID_INPUT, "TTS text must not be blank")
        }
        if (!supportsLanguage(request.language)) {
            throw AudioException(
                AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE,
                "Language unavailable for Mock TTS: ${request.language}"
            )
        }
        // Natural duration from text length — never stretch to M2 target duration.
        val durationMs = max(500L, request.text.length * 60L)
        val wav = WavEncoder.synthesizeToneWav(request.text, durationMs)
        val pcmSize = wav.size - 44
        val actualMs = WavEncoder.durationMsForPcmBytes(pcmSize)
        return TtsSynthesisResult(
            audioBytes = wav,
            durationMs = actualMs,
            mimeType = "audio/wav",
            providerId = providerId,
            language = request.language.lowercase(),
            voiceId = request.voice.voiceId.ifBlank { "mock-default" }
        )
    }

    companion object {
        const val PROVIDER_ID = "mock"
    }
}
