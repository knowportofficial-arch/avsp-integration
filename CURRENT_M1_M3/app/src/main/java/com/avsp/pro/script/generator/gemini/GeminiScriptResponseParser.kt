package com.avsp.pro.script.generator.gemini

import com.avsp.pro.core.error.ErrorCode
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptMetadata
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene
import com.avsp.pro.script.contract.ScriptValidation
import com.avsp.pro.script.contract.ShotType
import com.avsp.pro.script.contract.TransitionIntent
import com.avsp.pro.script.contract.ValidationStatus
import com.avsp.pro.script.generator.GeneratorMode
import com.avsp.pro.script.language.ScriptLanguageRegistry
import com.avsp.pro.script.validation.ScriptValidator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Parses Gemini structured JSON into the existing [ScriptPackage] contract.
 * Pure / testable — no network.
 */
object GeminiScriptResponseParser {

    fun parseToPackage(
        rawModelText: String,
        request: ScriptGenerationRequest,
        providerId: String,
        generatorMode: GeneratorMode = GeneratorMode.REMOTE
    ): ScriptPackage {
        val jsonText = stripCodeFences(rawModelText)
        if (jsonText.isBlank()) {
            throw GeminiScriptException(
                message = "Gemini returned an empty script payload",
                code = ErrorCode.INVALID_INPUT,
                details = "empty_response"
            )
        }
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            throw GeminiScriptException(
                message = "Gemini returned malformed JSON",
                code = ErrorCode.INVALID_INPUT,
                details = e.message,
                cause = e
            )
        }

        val language = try {
            ScriptLanguageRegistry.resolve(request.languageCode).code
        } catch (e: Exception) {
            throw GeminiScriptException(
                message = "Unsupported language: ${request.languageCode}",
                code = ErrorCode.INVALID_INPUT,
                cause = e
            )
        }

        val scenesJson = root.optJSONArray("scenes")
            ?: throw GeminiScriptException(
                message = "Gemini JSON missing scenes[]",
                code = ErrorCode.INVALID_INPUT
            )
        if (scenesJson.length() == 0) {
            throw GeminiScriptException(
                message = "Gemini returned zero scenes",
                code = ErrorCode.INVALID_INPUT
            )
        }

        val scenes = buildList {
            for (i in 0 until scenesJson.length()) {
                val s = scenesJson.optJSONObject(i)
                    ?: throw GeminiScriptException(
                        message = "Gemini scene[$i] is not an object",
                        code = ErrorCode.INVALID_INPUT
                    )
                add(parseScene(s, i))
            }
        }.sortedBy { it.order }
            .mapIndexed { index, scene -> scene.copy(order = index, sceneId = "scn_${index.toString().padStart(2, '0')}") }

        val total = scenes.sumOf { it.durationMs }
        val narrationMs = estimateNarrationMs(scenes)
        val now = System.currentTimeMillis()
        val topic = request.topic.trim()

        val draft = ScriptPackage(
            version = ScriptPackage.CURRENT_VERSION,
            projectId = request.projectId,
            scriptId = "scr_" + UUID.randomUUID().toString().replace("-", "").take(16),
            topic = topic,
            language = language,
            title = root.optString("title").trim().ifBlank { topic },
            hook = root.optString("hook").trim(),
            introduction = root.optString("introduction").trim(),
            scenes = scenes,
            cta = root.optString("cta").trim(),
            ending = root.optString("ending").trim(),
            estimatedDurationMs = total,
            targetDurationMs = request.duration.resolveTargetMs(),
            estimatedNarrationDurationMs = narrationMs,
            validation = ScriptValidation(true, ValidationStatus.VALID),
            metadata = ScriptMetadata(
                contentType = request.contentType,
                audience = request.audience,
                platform = request.platform,
                aspectRatio = request.aspectRatioLabel,
                generatorId = providerId,
                generatorMode = generatorMode.name,
                createdAt = now,
                updatedAt = now,
                userInstructions = request.userInstructions,
                factualRequirements = request.factualRequirements
            )
        )

        val validation = ScriptValidator.validatePackage(draft, request.durationToleranceRatio)
        if (!validation.isValid) {
            throw GeminiScriptException(
                message = "Gemini script failed ScriptPackage validation",
                code = ErrorCode.INVALID_INPUT,
                details = validation.errors.joinToString("; ")
            )
        }
        return draft.copy(validation = validation)
    }

    fun extractModelTextFromApiResponse(apiBody: String): String {
        val root = try {
            JSONObject(apiBody)
        } catch (e: Exception) {
            throw GeminiScriptException(
                message = "Gemini API returned non-JSON body",
                code = ErrorCode.MODULE_ERROR,
                details = e.message,
                cause = e
            )
        }
        if (root.has("error")) {
            val err = root.optJSONObject("error")
            val msg = err?.optString("message").orEmpty().ifBlank { "Gemini API error" }
            throw GeminiScriptException(
                message = msg,
                code = ErrorCode.MODULE_ERROR,
                details = err?.optString("status")
            )
        }
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw GeminiScriptException(
                message = "Gemini API response had no candidates",
                code = ErrorCode.MODULE_ERROR
            )
        }
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw GeminiScriptException(
                message = "Gemini API response missing content.parts",
                code = ErrorCode.MODULE_ERROR
            )
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val t = part.optString("text")
                if (t.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(t)
                }
            }
        }.trim()
        if (text.isBlank()) {
            throw GeminiScriptException(
                message = "Gemini API returned empty text parts",
                code = ErrorCode.MODULE_ERROR
            )
        }
        return text
    }

    internal fun stripCodeFences(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```")
            if (text.startsWith("json", ignoreCase = true)) {
                text = text.substring(4)
            }
            text = text.trim()
            if (text.endsWith("```")) {
                text = text.removeSuffix("```").trim()
            }
        }
        return text
    }

    private fun parseScene(s: JSONObject, index: Int): ScriptScene {
        val narration = s.optString("narration").trim()
        if (narration.isBlank()) {
            throw GeminiScriptException(
                message = "Gemini scene[$index] missing narration",
                code = ErrorCode.INVALID_INPUT
            )
        }
        val durationMs = s.optLong("durationMs", -1L)
        if (durationMs < ScriptValidator.MIN_SCENE_MS) {
            throw GeminiScriptException(
                message = "Gemini scene[$index] durationMs invalid: $durationMs",
                code = ErrorCode.INVALID_INPUT
            )
        }
        val shotType = runCatching {
            ShotType.valueOf(s.optString("shotType", "MEDIUM").trim().uppercase())
        }.getOrDefault(ShotType.MEDIUM)
        val transition = runCatching {
            TransitionIntent.valueOf(s.optString("transition", "CUT").trim().uppercase())
        }.getOrDefault(TransitionIntent.CUT)
        return ScriptScene(
            sceneId = "scn_tmp_$index",
            order = s.optInt("order", index),
            durationMs = durationMs,
            narration = narration,
            onScreenText = s.optString("onScreenText").trim(),
            visualDescription = s.optString("visualDescription").trim(),
            shotType = shotType,
            cameraDirection = s.optString("cameraDirection").trim(),
            bRollSuggestion = s.optString("bRollSuggestion").trim(),
            transition = transition,
            notes = s.optString("notes").trim()
        )
    }

    private fun estimateNarrationMs(scenes: List<ScriptScene>): Long =
        scenes.sumOf { scene ->
            val fromText = (scene.narration.length / 13.0 * 1000.0).roundToLong()
            minOf(scene.durationMs, max(1_000L, fromText))
        }
}
