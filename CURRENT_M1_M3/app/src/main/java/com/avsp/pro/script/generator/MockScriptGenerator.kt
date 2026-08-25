package com.avsp.pro.script.generator

import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptMetadata
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene
import com.avsp.pro.script.contract.ScriptValidation
import com.avsp.pro.script.contract.ShotType
import com.avsp.pro.script.contract.TransitionIntent
import com.avsp.pro.script.contract.ValidationStatus
import com.avsp.pro.script.language.ScriptLanguageRegistry
import com.avsp.pro.script.validation.ScriptValidator
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Deterministic development/test generator.
 * Produces structurally valid ScriptPackage data — NOT claimed as AI-generated.
 */
class MockScriptGenerator : ScriptGenerator {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "Mock Script Generator"
    override val mode: GeneratorMode = GeneratorMode.MOCK

    override suspend fun generate(request: ScriptGenerationRequest): ScriptPackage {
        val requestValidation = ScriptValidator.validateRequest(request)
        if (!requestValidation.isValid) {
            return invalidStub(request, requestValidation)
        }

        val language = ScriptLanguageRegistry.resolve(request.languageCode)
        val targetMs = request.duration.resolveTargetMs()
        val sceneCount = sceneCountFor(targetMs)
        val sceneDurations = distributeDuration(targetMs, sceneCount)
        val now = System.currentTimeMillis()
        val topic = request.topic.trim()

        val hookScene = ScriptScene(
            sceneId = "scn_hook",
            order = 0,
            durationMs = sceneDurations[0],
            narration = language.hookFor(topic),
            onScreenText = language.onScreenFor(topic, 0),
            visualDescription = language.visualFor(topic, 0),
            shotType = ShotType.INTRO,
            cameraDirection = language.cameraDirection(0),
            bRollSuggestion = language.bRollFor(topic, 0),
            transition = TransitionIntent.CUT,
            notes = "Hook"
        )

        val mid = (1 until sceneCount - 1).map { index ->
            ScriptScene(
                sceneId = "scn_${index.toString().padStart(2, '0')}",
                order = index,
                durationMs = sceneDurations[index],
                narration = language.sectionNarration(topic, index - 1, sceneCount - 2),
                onScreenText = language.onScreenFor(topic, index),
                visualDescription = language.visualFor(topic, index),
                shotType = if (index % 2 == 0) ShotType.B_ROLL else ShotType.MEDIUM,
                cameraDirection = language.cameraDirection(index),
                bRollSuggestion = language.bRollFor(topic, index),
                transition = TransitionIntent.CUT,
                notes = "Body"
            )
        }

        val outro = ScriptScene(
            sceneId = "scn_outro",
            order = sceneCount - 1,
            durationMs = sceneDurations.last(),
            narration = "${language.ctaFor(topic)} ${language.endingFor(topic)}".trim(),
            onScreenText = language.ctaFor(topic),
            visualDescription = language.visualFor(topic, sceneCount - 1),
            shotType = ShotType.OUTRO,
            cameraDirection = language.cameraDirection(sceneCount - 1),
            bRollSuggestion = language.bRollFor(topic, sceneCount - 1),
            transition = TransitionIntent.FADE,
            notes = "CTA / Ending"
        )

        val scenes = listOf(hookScene) + mid + listOf(outro)
        val total = scenes.sumOf { it.durationMs }
        val narrationMs = estimateNarrationMs(scenes)

        val draft = ScriptPackage(
            version = ScriptPackage.CURRENT_VERSION,
            projectId = request.projectId,
            scriptId = "scr_" + UUID.randomUUID().toString().replace("-", "").take(16),
            topic = topic,
            language = language.code,
            title = language.titleFor(topic),
            hook = language.hookFor(topic),
            introduction = language.introFor(topic),
            scenes = scenes,
            cta = language.ctaFor(topic),
            ending = language.endingFor(topic),
            estimatedDurationMs = total,
            targetDurationMs = targetMs,
            estimatedNarrationDurationMs = narrationMs,
            validation = ScriptValidation(true, ValidationStatus.VALID),
            metadata = ScriptMetadata(
                contentType = request.contentType,
                audience = request.audience,
                platform = request.platform,
                aspectRatio = request.aspectRatioLabel,
                generatorId = providerId,
                generatorMode = mode.name,
                createdAt = now,
                updatedAt = now,
                userInstructions = request.userInstructions,
                factualRequirements = request.factualRequirements
            )
        )

        val validation = ScriptValidator.validatePackage(draft, request.durationToleranceRatio)
        return draft.copy(validation = validation)
    }

    private fun invalidStub(
        request: ScriptGenerationRequest,
        validation: ScriptValidation
    ): ScriptPackage {
        val now = System.currentTimeMillis()
        return ScriptPackage(
            projectId = request.projectId,
            scriptId = "scr_invalid",
            topic = request.topic,
            language = request.languageCode,
            title = "",
            hook = "",
            introduction = "",
            scenes = emptyList(),
            cta = "",
            ending = "",
            estimatedDurationMs = 0L,
            targetDurationMs = runCatching { request.duration.resolveTargetMs() }.getOrDefault(0L),
            estimatedNarrationDurationMs = 0L,
            validation = validation,
            metadata = ScriptMetadata(
                contentType = request.contentType,
                audience = request.audience,
                platform = request.platform,
                aspectRatio = request.aspectRatioLabel,
                generatorId = providerId,
                generatorMode = mode.name,
                createdAt = now,
                updatedAt = now,
                userInstructions = request.userInstructions,
                factualRequirements = request.factualRequirements
            )
        )
    }

    private fun sceneCountFor(targetMs: Long): Int = when {
        targetMs <= 30_000L -> 3
        targetMs <= 60_000L -> 4
        targetMs <= 120_000L -> 5
        else -> 6
    }

    private fun distributeDuration(totalMs: Long, count: Int): List<Long> {
        require(count >= 2)
        val base = (totalMs.toDouble() / count).roundToLong()
        val durations = MutableList(count) { max(ScriptValidator.MIN_SCENE_MS, base) }
        var diff = totalMs - durations.sum()
        var i = 0
        while (diff != 0L && i < count * 8) {
            val idx = i % count
            if (diff > 0) {
                durations[idx] = durations[idx] + 1
                diff--
            } else if (durations[idx] > ScriptValidator.MIN_SCENE_MS) {
                durations[idx] = durations[idx] - 1
                diff++
            }
            i++
        }
        return durations
    }

    private fun estimateNarrationMs(scenes: List<ScriptScene>): Long {
        // ~13 chars/sec speaking estimate, capped by scene duration.
        return scenes.sumOf { scene ->
            val fromText = (scene.narration.length / 13.0 * 1000.0).roundToLong()
            minOf(scene.durationMs, max(1_000L, fromText))
        }
    }

    companion object {
        const val PROVIDER_ID = "mock"
    }
}
