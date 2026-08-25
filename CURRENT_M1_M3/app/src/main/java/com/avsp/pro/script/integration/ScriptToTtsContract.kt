package com.avsp.pro.script.integration

import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene

/**
 * M2 → M3 integration contract.
 * M3 (Audio/TTS) consumes narration + timing — M2 does NOT implement TTS.
 */
data class NarrationSegment(
    val sceneId: String,
    val order: Int,
    val narration: String,
    val language: String,
    val durationMs: Long,
    val pauseAfterMs: Long = 0L
)

data class ScriptNarrationHandoff(
    val projectId: String,
    val scriptId: String,
    val language: String,
    val title: String,
    val segments: List<NarrationSegment>,
    val totalNarrationDurationMs: Long,
    val scriptVersion: String
)

object ScriptToTtsContract {
    fun fromPackage(script: ScriptPackage): ScriptNarrationHandoff {
        val segments = script.scenes
            .sortedBy { it.order }
            .map { scene -> scene.toNarrationSegment(script.language) }
        return ScriptNarrationHandoff(
            projectId = script.projectId,
            scriptId = script.scriptId,
            language = script.language,
            title = script.title,
            segments = segments,
            totalNarrationDurationMs = script.estimatedNarrationDurationMs,
            scriptVersion = script.version
        )
    }

    private fun ScriptScene.toNarrationSegment(language: String) = NarrationSegment(
        sceneId = sceneId,
        order = order,
        narration = narration,
        language = language,
        durationMs = durationMs,
        pauseAfterMs = if (transition.name == "FADE") 400L else 150L
    )
}
