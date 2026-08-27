package com.avsp.pro.audio.contract

/**
 * M3 voice mode and assignment configuration — persisted per project.
 */

enum class VoiceMode {
    SIMPLE_LOCAL,
    MY_VOICE_CLONE
}

enum class SegmentRole {
    INTRO,
    BODY,
    OUTRO,
    SCENE
}

enum class AssignmentScope {
    ENTIRE_PROJECT,
    INTRO_BODY_OUTRO,
    PER_SCENE
}

data class LocalVoiceInfo(
    val voiceId: String,
    val displayName: String,
    val languageTag: String,
    val gender: String? = null,
    val installed: Boolean = true,
    val requiresDownload: Boolean = false
)

enum class VoiceCloneStatus {
    NOT_CONFIGURED,
    CONFIGURED,
    INVALID
}

data class VoiceCloneProfile(
    val profileId: String,
    val displayName: String,
    val status: VoiceCloneStatus,
    val sampleRelativePath: String? = null,
    val configuredAt: Long? = null
)

data class SegmentVoiceAssignment(
    val segmentKey: String,
    val role: SegmentRole,
    val voiceMode: VoiceMode,
    val voiceId: String,
    val language: String,
    val providerId: String = "android_local"
)

data class VoiceConfiguration(
    val projectId: String,
    val voiceMode: VoiceMode = VoiceMode.SIMPLE_LOCAL,
    val assignmentScope: AssignmentScope = AssignmentScope.ENTIRE_PROJECT,
    val defaultLanguage: String = "en",
    val defaultVoiceId: String = "default",
    val defaultProviderId: String = "android_local",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val introAssignment: SegmentVoiceAssignment? = null,
    val bodyAssignment: SegmentVoiceAssignment? = null,
    val outroAssignment: SegmentVoiceAssignment? = null,
    val sceneAssignments: Map<String, SegmentVoiceAssignment> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis()
)
