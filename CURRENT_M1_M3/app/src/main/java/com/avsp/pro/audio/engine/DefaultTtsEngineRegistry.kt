package com.avsp.pro.audio.engine

import android.content.Context
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.settings.ConfigState
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.SecureConfigStore

/**
 * Resolves TTS engines. Always offers Mock. Android Local is registered when constructible.
 * Remote/cloud providers remain extension points and are never mandatory.
 */
class DefaultTtsEngineRegistry(
    context: Context,
    private val secureConfigStore: SecureConfigStore,
    private val mock: TtsEngine = MockTtsEngine(),
    androidEngineFactory: (Context) -> TtsEngine? = { ctx ->
        runCatching { AndroidTtsEngine(ctx) }.getOrNull()
    },
    private val voiceCloneEngine: TtsEngine = VoiceCloneTtsEngine(UnconfiguredVoiceCloneProvider())
) : TtsEngineRegistry {

    private val androidEngine: TtsEngine? = androidEngineFactory(context.applicationContext)

    override fun available(): List<TtsEngine> {
        val list = mutableListOf(mock)
        val android = androidEngine
        if (android != null && android.isAvailable()) {
            list.add(android)
        }
        list.add(voiceCloneEngine)
        return list
    }

    override fun isRemoteConfigured(): Boolean =
        secureConfigStore.configState(SecureConfigKeys.AI_API) == ConfigState.CONFIGURED

    override fun resolve(preferredProviderId: String?): TtsEngine {
        val preferred = preferredProviderId?.let { id ->
            available().find { it.providerId == id }
        }
        if (preferred != null) return preferred

        if (preferredProviderId == VoiceCloneTtsEngine.PROVIDER_ID) {
            return voiceCloneEngine
        }

        // Prefer Android local when available; otherwise Mock (safe offline/default).
        val android = androidEngine
        if (android != null && android.isAvailable()) return android
        return mock
    }

    fun requireProvider(providerId: String): TtsEngine {
        val engine = available().find { it.providerId == providerId }
            ?: throw AudioException(
                AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                "TTS provider unavailable: $providerId"
            )
        if (!engine.isAvailable()) {
            throw AudioException(
                AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                "TTS provider unavailable: $providerId"
            )
        }
        return engine
    }

    fun aiConfigState(): ConfigState = secureConfigStore.configState(SecureConfigKeys.AI_API)
}
