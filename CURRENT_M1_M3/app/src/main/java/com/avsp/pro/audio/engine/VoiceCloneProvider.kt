package com.avsp.pro.audio.engine

import com.avsp.pro.audio.contract.VoiceCloneProfile
import com.avsp.pro.audio.contract.VoiceCloneStatus
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea
import com.google.gson.Gson
import com.google.gson.GsonBuilder

const val VOICE_CLONE_NOT_CONFIGURED = "My Voice Clone is not configured."

interface VoiceCloneProvider {
    fun isConfigured(): Boolean
    fun profile(): VoiceCloneProfile?
    suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult
}

/**
 * Optional on-device clone slot. A profile JSON may be installed later;
 * until then generation fails with a clear message — never fake speech.
 */
class LocalFileVoiceCloneProvider(
    private val storage: AvspStorage,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : VoiceCloneProvider {

    override fun isConfigured(): Boolean {
        val profile = profile() ?: return false
        return profile.status == VoiceCloneStatus.CONFIGURED
    }

    override fun profile(): VoiceCloneProfile? {
        if (!storage.exists(StorageArea.APP_DATA, PROFILE_PATH)) return null
        return runCatching {
            gson.fromJson(
                storage.readText(StorageArea.APP_DATA, PROFILE_PATH),
                VoiceCloneProfile::class.java
            )
        }.getOrNull()
    }

    override suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult {
        if (!isConfigured()) {
            throw AudioException(
                AudioErrorCode.VOICE_CLONE_UNAVAILABLE,
                VOICE_CLONE_NOT_CONFIGURED
            )
        }
        throw AudioException(
            AudioErrorCode.VOICE_CLONE_UNAVAILABLE,
            "My Voice Clone profile is present but no local clone synthesizer is installed."
        )
    }

    companion object {
        const val PROFILE_PATH = "voice_clone/profile.json"
    }
}

class UnconfiguredVoiceCloneProvider : VoiceCloneProvider {
    override fun isConfigured(): Boolean = false
    override fun profile(): VoiceCloneProfile? = null
    override suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult {
        throw AudioException(
            AudioErrorCode.VOICE_CLONE_UNAVAILABLE,
            VOICE_CLONE_NOT_CONFIGURED
        )
    }
}
