package com.avsp.pro.audio.engine

import com.avsp.pro.audio.contract.DiscoveredVoice
import com.avsp.pro.audio.contract.VoiceCloneStatus

/**
 * Optional TTS engine wrapping [VoiceCloneProvider].
 * Always listed in the UI as a mode; generation fails clearly when unconfigured.
 */
class VoiceCloneTtsEngine(
    private val cloneProvider: VoiceCloneProvider
) : TtsEngine {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "My Voice Clone"
    override val mode: TtsProviderMode = TtsProviderMode.LOCAL_CLONE

    override fun isAvailable(): Boolean = cloneProvider.isConfigured()

    override fun supportedLanguages(): Set<String> =
        if (isAvailable()) setOf("en", "bn", "hi") else emptySet()

    override fun supportsLanguage(languageCode: String): Boolean =
        isAvailable() && languageCode.lowercase() in supportedLanguages()

    override fun listVoices(): List<DiscoveredVoice> {
        val profile = cloneProvider.profile() ?: return emptyList()
        if (profile.status != VoiceCloneStatus.CONFIGURED) return emptyList()
        return listOf(
            DiscoveredVoice(
                voiceId = profile.profileId,
                name = profile.displayName,
                locale = "und",
                languageCode = "und",
                gender = null,
                installed = true,
                providerId = PROVIDER_ID
            )
        )
    }

    override suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult =
        cloneProvider.synthesize(request)

    companion object {
        const val PROVIDER_ID = "voice_clone"
    }
}
