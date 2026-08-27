package com.avsp.pro.audio.voice

import android.content.Context
import com.avsp.pro.audio.contract.VoiceCloneProfile
import com.avsp.pro.audio.contract.VoiceCloneStatus
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.UUID

/**
 * Global (reusable across projects) optional voice-clone profile storage.
 */
class VoiceCloneProfileStore(
    private val storage: AvspStorage,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) {
    fun load(): VoiceCloneProfile {
        if (!storage.exists(StorageArea.APP_DATA, PROFILE_PATH, null)) {
            return notConfigured()
        }
        return runCatching {
            gson.fromJson(storage.readText(StorageArea.APP_DATA, PROFILE_PATH, null), VoiceCloneProfile::class.java)
        }.getOrElse { notConfigured() }
    }

    fun save(profile: VoiceCloneProfile): VoiceCloneProfile {
        storage.save(StorageArea.APP_DATA, PROFILE_PATH, gson.toJson(profile), null)
        return profile
    }

    fun configure(displayName: String, sampleBytes: ByteArray): VoiceCloneProfile {
        val profileId = "clone_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val samplePath = "${ProjectPaths.AUDIO}/clone/$profileId/sample.wav"
        storage.save(StorageArea.APP_DATA, samplePath, sampleBytes, null)
        return save(
            VoiceCloneProfile(
                profileId = profileId,
                displayName = displayName,
                status = VoiceCloneStatus.CONFIGURED,
                sampleRelativePath = samplePath,
                configuredAt = System.currentTimeMillis()
            )
        )
    }

    fun clear() {
        if (storage.exists(StorageArea.APP_DATA, PROFILE_PATH, null)) {
            storage.delete(StorageArea.APP_DATA, PROFILE_PATH, null)
        }
    }

    fun isConfigured(): Boolean = load().status == VoiceCloneStatus.CONFIGURED

    private fun notConfigured() = VoiceCloneProfile(
        profileId = "",
        displayName = "My Voice Clone",
        status = VoiceCloneStatus.NOT_CONFIGURED
    )

    companion object {
        const val PROFILE_PATH = "voice_clone/profile.json"
    }
}
