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
    transport: GeminiHttpTransport = HttpUrlConnectionGeminiTransport(),
    private val config: GeminiConfig = GeminiConfig(),
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 45_000
) : ScriptGenerator {

    private val client = GeminiClient(
        transport = transport,
        config = config,
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs
    )

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
                    .put("maxOutputTokens", 8192)
            )
            .toString()

        val response = withContext(Dispatchers.IO) {
            client.generateContent(apiKey, requestBody)
        }

        val endpointLabel = "POST ${config.apiVersion}/${config.generateContentPath()}"
        when (response.code) {
            in 200..299 -> Unit
            401, 403 -> throw GeminiScriptException(
                message = GeminiClient.safeHttpErrorMessage(response.code, response.body)
                    .ifBlank { "Gemini API key rejected" },
                code = ErrorCode.CONFIG_ERROR,
                details = endpointLabel
            )
            429 -> throw GeminiScriptException(
                message = GeminiClient.safeHttpErrorMessage(response.code, response.body)
                    .ifBlank { "Gemini rate limit exceeded" },
                code = ErrorCode.MODULE_ERROR,
                details = endpointLabel
            )
            else -> throw GeminiScriptException(
                message = GeminiClient.safeHttpErrorMessage(response.code, response.body),
                code = ErrorCode.MODULE_ERROR,
                details = endpointLabel
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
        const val DEFAULT_MODEL = GeminiConfig.DEFAULT_MODEL
    }
}
