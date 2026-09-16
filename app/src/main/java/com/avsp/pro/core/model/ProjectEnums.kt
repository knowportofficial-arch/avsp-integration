package com.avsp.pro.core.model

/**
 * Controlled project lifecycle status. Invalid values are rejected at the boundary.
 */
enum class ProjectStatus {
    DRAFT,
    PROCESSING,
    READY,
    FAILED,
    COMPLETED;

    companion object {
        fun fromRaw(raw: String): ProjectStatus =
            entries.find { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid project status: $raw")
    }
}

/**
 * Supported aspect ratios for AVSP video projects.
 */
enum class AspectRatio(val label: String, val width: Int, val height: Int) {
    RATIO_9_16("9:16", 1080, 1920),
    RATIO_16_9("16:9", 1920, 1080),
    RATIO_1_1("1:1", 1080, 1080),
    RATIO_4_5("4:5", 1080, 1350);

    companion object {
        fun fromLabel(label: String): AspectRatio =
            entries.find { it.label == label || it.name.equals(label, true) }
                ?: throw IllegalArgumentException("Invalid aspect ratio: $label")
    }
}

/**
 * Supported primary languages from the master specification.
 */
enum class ProjectLanguage(val code: String, val displayName: String) {
    BENGALI("bn", "Bengali"),
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi");

    companion object {
        fun fromCode(code: String): ProjectLanguage =
            entries.find { it.code.equals(code, true) || it.name.equals(code, true) }
                ?: throw IllegalArgumentException("Unsupported language: $code")
    }
}
