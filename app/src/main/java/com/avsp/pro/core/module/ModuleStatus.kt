package com.avsp.pro.core.module

/**
 * Module lifecycle status reported by each AVSP module.
 * Accepted baseline modules are represented as [FROZEN].
 * M3 is READY; M4 is RUNNING during Android acceptance. M5-M9 remain FROZEN as integration baselines.
 */
enum class ModuleRunStatus {
    NOT_STARTED,
    READY,
    RUNNING,
    SUCCESS,
    FAILED,
    DISABLED,
    FROZEN;

    companion object {
        fun fromRaw(raw: String): ModuleRunStatus =
            entries.find { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid module status: $raw")
    }
}

/**
 * Stable module identity + status contract for integration.
 */
data class ModuleStatus(
    val moduleId: String,
    val displayName: String,
    val version: String,
    val status: ModuleRunStatus,
    val lastUpdated: Long,
    val error: String? = null
)

/**
 * Canonical AVSP module identifiers (M1â€“M9).
 * Frozen modules are listed for status display only â€” their source is not modified by later modules.
 */
object AvspModules {
    const val M1_CORE_UI = "M1"
    const val M2_SCRIPT_AI = "M2"
    const val M3_AUDIO_TTS = "M3"
    const val M4_VIDEO_ENGINE = "M4"
    const val M5_YOUTUBE_SCREEN = "M5"
    const val M6_CAMERA = "M6"
    const val M7_DATASET_VISION = "M7"
    const val M8_AUTOMATION = "M8"
    const val M9_PUBLISHING = "M9"

    /** Frozen integration baselines protected from casual status mutation. */
    val FROZEN_MODULE_IDS: Set<String> = setOf(
        M1_CORE_UI,
        M2_SCRIPT_AI,
        M5_YOUTUBE_SCREEN,
        M6_CAMERA,
        M7_DATASET_VISION,
        M8_AUTOMATION,
        M9_PUBLISHING,
    )

    data class Definition(
        val moduleId: String,
        val displayName: String,
        val version: String,
        val defaultStatus: ModuleRunStatus
    )

    val ALL: List<Definition> = listOf(
        Definition(M1_CORE_UI, "M1 Core/UI", "1.0.0", ModuleRunStatus.FROZEN),
        Definition(M2_SCRIPT_AI, "M2 Script AI", "1.0.0", ModuleRunStatus.FROZEN),
        Definition(M3_AUDIO_TTS, "M3 Audio/TTS", "1.0.0", ModuleRunStatus.READY),
        Definition(M4_VIDEO_ENGINE, "M4 Video Engine", "1.0.0-android", ModuleRunStatus.RUNNING),
        Definition(M5_YOUTUBE_SCREEN, "M5 YouTube/Screen Android", "1.0.0-android", ModuleRunStatus.FROZEN),
        Definition(M6_CAMERA, "M6 AI Camera", "1.0.0-android", ModuleRunStatus.FROZEN),
        Definition(M7_DATASET_VISION, "M7 Dataset/Vision", "1.0.0-android", ModuleRunStatus.FROZEN),
        Definition(M8_AUTOMATION, "M8 Creative/Timeline Android", "1.0.0-android", ModuleRunStatus.FROZEN),
        Definition(M9_PUBLISHING, "M9 Publishing Android", "1.0.0-android", ModuleRunStatus.FROZEN)
    )
}

