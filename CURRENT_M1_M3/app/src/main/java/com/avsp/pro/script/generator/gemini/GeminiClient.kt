package com.avsp.pro.script.generator.gemini

import org.json.JSONObject

/**
 * Thin REST client for Gemini generateContent. Does not log API keys.
 */
class GeminiClient(
    private val transport: GeminiHttpTransport,
    val config: GeminiConfig = GeminiConfig(),
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000
) {
    fun generateContent(apiKey: String, jsonBody: String): GeminiHttpResponse {
        val url = config.generateContentUrl(apiKey)
        return transport.postJson(url, jsonBody, connectTimeoutMs, readTimeoutMs)
    }

    companion object {
        fun safeHttpErrorMessage(code: Int, body: String): String {
            val extracted = extractApiErrorMessage(body)
            val sanitized = stripSecrets(extracted.ifBlank { body.take(400) })
            val pathHint = "POST ${GeminiConfig.DEFAULT_API_VERSION} models/<model>:generateContent"
            return if (sanitized.isBlank()) {
                "Gemini HTTP $code ($pathHint)"
            } else {
                "Gemini HTTP $code: $sanitized"
            }
        }

        fun extractApiErrorMessage(body: String): String {
            if (body.isBlank()) return ""
            return try {
                val root = JSONObject(body)
                val err = root.optJSONObject("error")
                val msg = err?.optString("message").orEmpty().trim()
                val status = err?.optString("status").orEmpty().trim()
                when {
                    msg.isNotBlank() && status.isNotBlank() -> "$status — $msg"
                    msg.isNotBlank() -> msg
                    else -> ""
                }
            } catch (_: Exception) {
                body.replace('\n', ' ').take(300)
            }
        }

        fun stripSecrets(text: String): String {
            var out = text
            out = API_KEY_QUERY.replace(out, "key=REDACTED")
            out = AIZA_KEY.replace(out, "REDACTED_KEY")
            return out.take(400)
        }

        private val API_KEY_QUERY = Regex("""(?i)key=[^&\s"']+""")
        private val AIZA_KEY = Regex("""AIza[0-9A-Za-z\-_]{10,}""")
    }
}
