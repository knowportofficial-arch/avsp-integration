package com.avsp.pro.audio.language

/**
 * Language abstraction for M3 — avoids hard-coding EN/BN/HI throughout the engine.
 */
interface AudioLanguageSupport {
    val code: String
    val displayName: String
    /** BCP-47 / Android locale tag used by platform TTS when available. */
    val localeTag: String
}

object AudioLanguageRegistry {
    private val supported: Map<String, AudioLanguageSupport> = listOf(
        object : AudioLanguageSupport {
            override val code = "en"
            override val displayName = "English"
            override val localeTag = "en-IN"
        },
        object : AudioLanguageSupport {
            override val code = "bn"
            override val displayName = "Bengali"
            override val localeTag = "bn-IN"
        },
        object : AudioLanguageSupport {
            override val code = "hi"
            override val displayName = "Hindi"
            override val localeTag = "hi-IN"
        }
    ).associateBy { it.code.lowercase() }

    fun supportedCodes(): Set<String> = supported.keys

    fun isSupported(code: String): Boolean = supported.containsKey(code.trim().lowercase())

    fun resolve(code: String): AudioLanguageSupport =
        supported[code.trim().lowercase()]
            ?: throw IllegalArgumentException("Unsupported audio language: $code")
}
