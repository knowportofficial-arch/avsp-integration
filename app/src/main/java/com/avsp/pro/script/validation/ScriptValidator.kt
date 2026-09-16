package com.avsp.pro.script.validation

import com.avsp.pro.script.contract.DurationRequest
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptValidation
import com.avsp.pro.script.contract.ValidationStatus
import com.avsp.pro.script.language.ScriptLanguageRegistry
import kotlin.math.abs

/**
 * M2 script validation — structured results, never silent acceptance of bad duration.
 */
object ScriptValidator {

    const val MIN_SCENE_MS = 2_000L
    const val MAX_SCENE_MS = 45_000L
    const val MIN_EXPLICIT_MS = 10_000L
    const val MAX_EXPLICIT_MS = 600_000L

    fun validateRequest(request: ScriptGenerationRequest): ScriptValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (request.projectId.isBlank()) {
            errors += "projectId is required"
        }
        if (request.topic.isBlank()) {
            errors += "topic is required"
        }
        if (!ScriptLanguageRegistry.isSupported(request.languageCode)) {
            errors += "unsupported language: ${request.languageCode}"
        }

        val target = runCatching { request.duration.resolveTargetMs() }.getOrElse {
            errors += "invalid duration request"
            0L
        }
        when (val d = request.duration) {
            is DurationRequest.Explicit -> {
                if (d.durationMs < MIN_EXPLICIT_MS) {
                    errors += "explicit duration must be at least ${MIN_EXPLICIT_MS}ms"
                }
                if (d.durationMs > MAX_EXPLICIT_MS) {
                    errors += "explicit duration must be at most ${MAX_EXPLICIT_MS}ms"
                }
            }
            else -> Unit
        }
        if (target <= 0L && errors.none { it.contains("duration") }) {
            errors += "target duration must be positive"
        }
        if (request.durationToleranceRatio <= 0.0 || request.durationToleranceRatio > 0.5) {
            warnings += "unusual duration tolerance ratio: ${request.durationToleranceRatio}"
        }

        return result(errors, warnings)
    }

    fun validatePackage(
        script: ScriptPackage,
        toleranceRatio: Double = ScriptGenerationRequest.DEFAULT_TOLERANCE
    ): ScriptValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (script.version.isBlank()) errors += "script version is required"
        if (script.projectId.isBlank()) errors += "projectId is required"
        if (script.scriptId.isBlank()) errors += "scriptId is required"
        if (script.topic.isBlank()) errors += "topic is required"
        if (script.title.isBlank()) errors += "title is required"
        if (script.scenes.isEmpty()) errors += "script must contain at least one scene"
        if (script.hook.isBlank()) warnings += "hook is empty"
        if (script.cta.isBlank()) warnings += "CTA is empty"

        if (!ScriptLanguageRegistry.isSupported(script.language)) {
            errors += "unsupported language: ${script.language}"
        }

        val orders = script.scenes.map { it.order }
        if (orders != orders.sorted()) {
            errors += "scenes are not ordered ascending by order field"
        }
        if (orders.distinct().size != orders.size) {
            errors += "duplicate scene order values"
        }

        script.scenes.forEach { scene ->
            if (scene.narration.isBlank()) {
                errors += "scene ${scene.sceneId} missing narration"
            }
            if (scene.durationMs < MIN_SCENE_MS) {
                errors += "scene ${scene.sceneId} duration below minimum (${MIN_SCENE_MS}ms)"
            }
            if (scene.durationMs > MAX_SCENE_MS) {
                warnings += "scene ${scene.sceneId} duration above recommended maximum (${MAX_SCENE_MS}ms)"
            }
        }

        val sceneSum = script.scenes.sumOf { it.durationMs }
        if (script.estimatedDurationMs != sceneSum) {
            warnings += "estimatedDurationMs (${script.estimatedDurationMs}) differs from scene sum ($sceneSum)"
        }

        val target = script.targetDurationMs
        if (target <= 0L) {
            errors += "targetDurationMs must be positive"
        } else {
            val delta = abs(sceneSum - target).toDouble() / target.toDouble()
            if (delta > toleranceRatio) {
                errors += "total scene duration ${sceneSum}ms is outside ±${(toleranceRatio * 100).toInt()}% of target ${target}ms"
            } else if (delta > toleranceRatio / 2.0) {
                warnings += "total scene duration ${sceneSum}ms is close to tolerance limit for target ${target}ms"
            }
        }

        return result(errors, warnings)
    }

    private fun result(errors: List<String>, warnings: List<String>): ScriptValidation {
        val status = when {
            errors.isNotEmpty() -> ValidationStatus.INVALID
            warnings.isNotEmpty() -> ValidationStatus.WARNING
            else -> ValidationStatus.VALID
        }
        return ScriptValidation(
            isValid = errors.isEmpty(),
            status = status,
            warnings = warnings,
            errors = errors
        )
    }
}
