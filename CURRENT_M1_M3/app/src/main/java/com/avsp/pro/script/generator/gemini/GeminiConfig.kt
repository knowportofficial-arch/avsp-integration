package com.avsp.pro.script.generator.gemini

/**
 * Configurable Generative Language REST settings for M2 Gemini REMOTE.
 *
 * Default model is a generation-free flash alias. `gemini-2.0-flash` was retired
 * (shutdown 2026-06-01) and returns HTTP 404 "no longer available".
 */
data class GeminiConfig(
    val host: String = DEFAULT_HOST,
    val apiVersion: String = DEFAULT_API_VERSION,
    val model: String = DEFAULT_MODEL
) {
    fun generateContentPath(): String = "models/$model:generateContent"

    fun generateContentUrl(apiKey: String): String =
        "$host/$apiVersion/${generateContentPath()}?key=$apiKey"

    companion object {
        const val DEFAULT_HOST = "https://generativelanguage.googleapis.com"
        const val DEFAULT_API_VERSION = "v1beta"
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}
