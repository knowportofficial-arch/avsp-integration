package com.avsp.pro.audio.engine

import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.audio.voice.VoiceCloneProfileStore
import com.avsp.pro.audio.wav.WavEncoder
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea

/**
 * Optional local voice-clone provider. Reports clearly when not configured.
 * Uses stored reference sample as a deterministic placeholder synthesis path
 * until a real on-device clone engine is plugged in.
 */
class VoiceCloneTtsEngine(
    private val profileStore: VoiceCloneProfileStore,
    private val storage: AvspStorage,
    private val fallback: TtsEngine
) : TtsEngine {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "My Voice Clone"
    override val mode: TtsProviderMode = TtsProviderMode.ANDROID_LOCAL

    override fun isAvailable(): Boolean = profileStore.isConfigured()

    override fun supportedLanguages(): Set<String> = AudioLanguageRegistry.supportedCodes()

    override fun supportsLanguage(languageCode: String): Boolean =
        isAvailable() && AudioLanguageRegistry.isSupported(languageCode)

    override suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult {
        val profile = profileStore.load()
        if (profile.status != com.avsp.pro.audio.contract.VoiceCloneStatus.CONFIGURED) {
            throw AudioException(
                AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED,
                "My Voice Clone is not configured."
            )
        }
        val samplePath = profile.sampleRelativePath
            ?: throw AudioException(
                AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED,
                "My Voice Clone is not configured."
            )
        if (!storage.exists(StorageArea.APP_DATA, samplePath, null)) {
            throw AudioException(
                AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED,
                "My Voice Clone sample is missing."
            )
        }
        val sampleBytes = storage.readBytes(StorageArea.APP_DATA, samplePath, null)
        val targetMs = request.targetDurationMs ?: maxOf(800L, request.text.length * 60L)
        val durationMs = targetMs.coerceAtLeast(250L)
        val pcm = WavEncoder.extractPcmOrGenerateTone(sampleBytes, durationMs)
        val wav = WavEncoder.encodePcm(pcm)
        return TtsSynthesisResult(
            audioBytes = wav,
            durationMs = WavEncoder.durationMsForPcmBytes(pcm.size),
            mimeType = "audio/wav",
            providerId = providerId,
            language = request.language.lowercase(),
            voiceId = profile.profileId
        )
    }

    companion object {
        const val PROVIDER_ID = "voice_clone"
    }
}
