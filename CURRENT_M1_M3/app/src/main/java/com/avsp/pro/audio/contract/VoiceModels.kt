package com.avsp.pro.audio.contract

/**
 * M3 voice configuration — local-first, clone optional.
 * Azure/neural names are never required; devices expose their own TTS voices.
 */
enum class VoiceMode {
    SIMPLE_LOCAL,
    MY_VOICE_CLONE
}

enum class ClipRole {
    INTRO,
    SCENE,
    OUTRO
}

enum class VoiceCloneStatus {
    NOT_CONFIGURED,
    CONFIGURED,
    INVALID
}

data class DiscoveredVoice(
    val voiceId: String,
    val name: String,
    val locale: String,
    val languageCode: String,
    val gender: String?,
    val installed: Boolean,
    val providerId: String,
    val requiresNetwork: Boolean = false
)

data class VoiceCloneProfile(
    val profileId: String,
    val displayName: String,
    val status: VoiceCloneStatus,
    val createdAt: Long,
    val notes: String? = null
)

/**
 * Project-level defaults plus optional intro/body/outro and per-scene overrides.
 * Scene ids win over role defaults; role defaults win over project default.
 */
data class VoiceAssignment(
    val voiceMode: VoiceMode = VoiceMode.SIMPLE_LOCAL,
    val providerId: String = "android_local",
    val voiceId: String = "default",
    val language: String = "en",
    val introVoiceId: String? = null,
    val introProviderId: String? = null,
    val bodyVoiceId: String? = null,
    val bodyProviderId: String? = null,
    val outroVoiceId: String? = null,
    val outroProviderId: String? = null,
    val sceneVoiceIds: Map<String, String> = emptyMap(),
    val sceneProviderIds: Map<String, String> = emptyMap()
) {
    fun resolveVoiceId(role: ClipRole, sceneId: String): String {
        val sceneOverride = sceneVoiceIds[sceneId]
        if (!sceneOverride.isNullOrBlank()) return sceneOverride
        return when (role) {
            ClipRole.INTRO -> introVoiceId?.takeIf { it.isNotBlank() } ?: voiceId
            ClipRole.OUTRO -> outroVoiceId?.takeIf { it.isNotBlank() } ?: voiceId
            ClipRole.SCENE -> bodyVoiceId?.takeIf { it.isNotBlank() } ?: voiceId
        }
    }

    fun resolveProviderId(role: ClipRole, sceneId: String): String {
        val sceneOverride = sceneProviderIds[sceneId]
        if (!sceneOverride.isNullOrBlank()) return sceneOverride
        return when (role) {
            ClipRole.INTRO -> introProviderId?.takeIf { it.isNotBlank() } ?: providerId
            ClipRole.OUTRO -> outroProviderId?.takeIf { it.isNotBlank() } ?: providerId
            ClipRole.SCENE -> bodyProviderId?.takeIf { it.isNotBlank() } ?: providerId
        }
    }
}
