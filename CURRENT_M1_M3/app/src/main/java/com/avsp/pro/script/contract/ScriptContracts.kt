package com.avsp.pro.script.contract

/**
 * M2 Script AI — stable script data contracts.
 * Machine-readable, persistable, editable before M3 handoff.
 */

enum class ContentType {
    EXPLAINER,
    NEWS_UPDATE,
    SHORTS,
    TUTORIAL,
    STORY,
    GENERAL
}

enum class TargetPlatform {
    YOUTUBE_SHORTS,
    YOUTUBE,
    FACEBOOK_REELS,
    INSTAGRAM_REELS,
    GENERIC
}

enum class AudienceType {
    GENERAL,
    STUDENTS,
    PROFESSIONALS,
    LOCAL_COMMUNITY
}

/**
 * Duration request: preset or explicit milliseconds.
 */
sealed class DurationRequest {
    data object ShortForm : DurationRequest()
    data object MediumForm : DurationRequest()
    data object LongForm : DurationRequest()
    data class Explicit(val durationMs: Long) : DurationRequest()

    fun resolveTargetMs(): Long = when (this) {
        ShortForm -> 30_000L
        MediumForm -> 60_000L
        LongForm -> 120_000L
        is Explicit -> durationMs
    }
}

data class ScriptGenerationRequest(
    val projectId: String,
    val topic: String,
    val languageCode: String,
    val duration: DurationRequest,
    val aspectRatioLabel: String = "9:16",
    val contentType: ContentType = ContentType.GENERAL,
    val audience: AudienceType = AudienceType.GENERAL,
    val platform: TargetPlatform = TargetPlatform.YOUTUBE_SHORTS,
    val userInstructions: String? = null,
    val factualRequirements: String? = null,
    val durationToleranceRatio: Double = DEFAULT_TOLERANCE
) {
    companion object {
        const val DEFAULT_TOLERANCE = 0.15
    }
}

enum class ShotType {
    WIDE,
    MEDIUM,
    CLOSE_UP,
    B_ROLL,
    TEXT_CARD,
    INTRO,
    OUTRO
}

enum class TransitionIntent {
    CUT,
    FADE,
    DISSOLVE,
    NONE
}

data class ScriptScene(
    val sceneId: String,
    val order: Int,
    val durationMs: Long,
    val narration: String,
    val onScreenText: String = "",
    val visualDescription: String = "",
    val shotType: ShotType = ShotType.MEDIUM,
    val cameraDirection: String = "",
    val bRollSuggestion: String = "",
    val transition: TransitionIntent = TransitionIntent.CUT,
    val notes: String = ""
)

data class ScriptValidation(
    val isValid: Boolean,
    val status: ValidationStatus,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

enum class ValidationStatus {
    VALID,
    WARNING,
    INVALID
}

data class ScriptMetadata(
    val contentType: ContentType,
    val audience: AudienceType,
    val platform: TargetPlatform,
    val aspectRatio: String,
    val generatorId: String,
    val generatorMode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val userInstructions: String? = null,
    val factualRequirements: String? = null
)

/**
 * Full M2 script package — production-ready, editable, persistable.
 */
data class ScriptPackage(
    val version: String = CURRENT_VERSION,
    val projectId: String,
    val scriptId: String,
    val topic: String,
    val language: String,
    val title: String,
    val hook: String,
    val introduction: String,
    val scenes: List<ScriptScene>,
    val cta: String,
    val ending: String,
    val estimatedDurationMs: Long,
    val targetDurationMs: Long,
    val estimatedNarrationDurationMs: Long,
    val validation: ScriptValidation,
    val metadata: ScriptMetadata
) {
    companion object {
        const val CURRENT_VERSION = "1.0"
    }
}
