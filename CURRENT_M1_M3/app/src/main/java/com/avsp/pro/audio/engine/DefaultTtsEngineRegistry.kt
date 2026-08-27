package com.avsp.pro.audio.engine

import android.content.Context
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.settings.ConfigState
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.SecureConfigStore
import com.avsp.pro.audio.voice.VoiceCloneProfileStore
import com.avsp.pro.storage.AvspStorage

/**
 * Resolves TTS engines. Always offers Mock. Android Local when constructible.
 * Optional My Voice Clone when configured. No mandatory cloud providers.
 */
class DefaultTtsEngineRegistry(
    context: Context,
    private val secureConfigStore: SecureConfigStore,
    private val storage: AvspStorage,
    private val voiceCloneProfileStore: VoiceCloneProfileStore,
    private val mock: TtsEngine = MockTtsEngine(),
    androidEngineFactory: (Context) -> TtsEngine? = { ctx ->
        runCatching { AndroidTtsEngine(ctx) }.getOrNull()
    }
) : TtsEngineRegistry {

    private val androidEngine: TtsEngine? = androidEngineFactory(context.applicationContext)
    private val voiceCloneEngine: VoiceCloneTtsEngine by lazy {
        VoiceCloneTtsEngine(voiceCloneProfileStore, storage, androidEngine ?: mock)
    }

    override fun available(): List<TtsEngine> {
        val list = mutableListOf<TtsEngine>(mock)
        val android = androidEngine
        if (android != null && android.isAvailable()) {
            list.add(android)
        }
        if (voiceCloneEngine.isAvailable()) {
            list.add(voiceCloneEngine)
        }
        return list
    }

    override fun isRemoteConfigured(): Boolean =
        secureConfigStore.configState(SecureConfigKeys.AI_API) == ConfigState.CONFIGURED

    override fun resolve(preferredProviderId: String?): TtsEngine {
        preferredProviderId?.let { id ->
            when (id) {
                VoiceCloneTtsEngine.PROVIDER_ID -> {
                    if (voiceCloneEngine.isAvailable()) return voiceCloneEngine
                    throw AudioException(
                        AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED,
                        "My Voice Clone is not configured."
                    )
                }
                MockTtsEngine.PROVIDER_ID -> return mock
                AndroidTtsEngine.PROVIDER_ID -> {
                    val android = androidEngine
                    if (android != null && android.isAvailable()) return android
                }
                else -> available().find { it.providerId == id }?.let { return it }
            }
        }

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
