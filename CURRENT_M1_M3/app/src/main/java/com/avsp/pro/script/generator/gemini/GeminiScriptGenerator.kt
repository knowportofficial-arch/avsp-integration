package com.avsp.pro.script.generator.gemini

import com.avsp.pro.core.error.ErrorCode
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.generator.GeneratorMode
import com.avsp.pro.script.generator.ScriptGenerator
import com.avsp.pro.script.validation.ScriptValidator
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.SecureConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real Gemini remote [ScriptGenerator].
 *
 * - API key from [SecureConfigStore] only (never hard-coded).
 * - Failures throw [GeminiScriptException] — never silent mock substitution.
 * - Output is always the existing [ScriptPackage] contract.
 */
class GeminiScriptGenerator(
    private val secureConfigStore: SecureConfigStore,
    private val transport: GeminiHttpTransport = HttpUrlConnectionGeminiTransport(),
    private val model: String = DEFAULT_MODEL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000
) : ScriptGenerator {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "Gemini Script Generator"
    override val mode: GeneratorMode = GeneratorMode.REMOTE

    override suspend fun generate(request: ScriptGenerationRequest): ScriptPackage {
        val requestValidation = ScriptValidator.validateRequest(request)
        if (!requestValidation.isValid) {
            throw GeminiScriptException(
                message = requestValidation.errors.firstOrNull() ?: "Invalid script request",
                code = ErrorCode.INVALID_INPUT,
                details = requestValidation.errors.joinToString("; ")
            )
        }

        val apiKey = secureConfigStore.getSecret(SecureConfigKeys.AI_API)?.trim().orEmpty()
        if (apiKey.isBlank()) {
            throw GeminiScriptException(
                message = "Gemini API key is not configured",
                code = ErrorCode.CONFIG_ERROR,
                details = "missing_ai_api_credential"
            )
        }

        val prompt = GeminiScriptPrompt.systemAndUserPrompt(request)
        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.35)
                    .put("responseMimeType", "application/json")
            )
            .toString()

        // Key only in query param for Generative Language API — never logged.
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val response = withContext(Dispatchers.IO) {
            transport.postJson(url, requestBody, connectTimeoutMs, readTimeoutMs)
        }

        when (response.code) {
            in 200..299 -> Unit
            401, 403 -> throw GeminiScriptException(
                message = "Gemini API key rejected",
                code = ErrorCode.CONFIG_ERROR,
                details = "http_${response.code}"
            )
            429 -> throw GeminiScriptException(
                message = "Gemini rate limit exceeded",
                code = ErrorCode.MODULE_ERROR,
                details = "http_429"
            )
            else -> throw GeminiScriptException(
                message = "Gemini HTTP ${response.code}",
                code = ErrorCode.MODULE_ERROR,
                details = response.body.take(400)
            )
        }

        val modelText = GeminiScriptResponseParser.extractModelTextFromApiResponse(response.body)
        return GeminiScriptResponseParser.parseToPackage(
            rawModelText = modelText,
            request = request,
            providerId = providerId,
            generatorMode = mode
        )
    }

    companion object {
        const val PROVIDER_ID = "gemini"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}
