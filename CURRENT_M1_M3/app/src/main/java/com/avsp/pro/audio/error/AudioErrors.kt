package com.avsp.pro.audio.error

/**
 * Structured M3 audio/TTS error codes.
 */
enum class AudioErrorCode(val code: String) {
    SCRIPT_REQUIRED("SCRIPT_REQUIRED"),
    TTS_PROVIDER_UNAVAILABLE("TTS_PROVIDER_UNAVAILABLE"),
    TTS_LANGUAGE_UNAVAILABLE("TTS_LANGUAGE_UNAVAILABLE"),
    TTS_GENERATION_FAILED("TTS_GENERATION_FAILED"),
    AUDIO_STORAGE_FAILED("AUDIO_STORAGE_FAILED"),
    AUDIO_PLAYBACK_FAILED("AUDIO_PLAYBACK_FAILED"),
    INVALID_AUDIO_TIMELINE("INVALID_AUDIO_TIMELINE"),
    INVALID_INPUT("INVALID_INPUT"),
    VOICE_CLONE_UNAVAILABLE("VOICE_CLONE_UNAVAILABLE"),
    VOICE_UNAVAILABLE("VOICE_UNAVAILABLE"),
    AUDIO_CORRUPT("AUDIO_CORRUPT");

    companion object {
        fun fromRaw(raw: String): AudioErrorCode =
            entries.find { it.code.equals(raw, true) || it.name.equals(raw, true) }
                ?: INVALID_INPUT
    }
}

class AudioException(
    val errorCode: AudioErrorCode,
    message: String,
    val details: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
