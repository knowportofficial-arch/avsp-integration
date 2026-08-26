package com.avsp.pro.audio.plan

import com.avsp.pro.audio.contract.ClipRole
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.integration.ScriptToTtsContract

/**
 * M3-side plan built from the frozen M2 [ScriptPackage].
 * Does not change M2 generation; consumes hook/introduction/scenes/cta/ending.
 */
data class AudioPlanClip(
    val clipId: String,
    val sceneId: String,
    val role: ClipRole,
    val order: Int,
    val title: String,
    val narration: String,
    val language: String,
    val plannedDurationMs: Long
)

object ScriptAudioPlanner {
    const val INTRO_SCENE_ID = "intro"
    const val OUTRO_SCENE_ID = "outro"

    fun fromScript(script: ScriptPackage): List<AudioPlanClip> {
        val language = script.language
        val clips = mutableListOf<AudioPlanClip>()
        var order = 0
        val introText = listOf(script.hook, script.introduction)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (introText.isNotBlank()) {
            clips += AudioPlanClip(
                clipId = "intro",
                sceneId = INTRO_SCENE_ID,
                role = ClipRole.INTRO,
                order = order++,
                title = "Intro",
                narration = introText,
                language = language,
                plannedDurationMs = estimateMs(introText)
            )
        }
        ScriptToTtsContract.fromPackage(script).segments.sortedBy { it.order }.forEachIndexed { index, seg ->
            clips += AudioPlanClip(
                clipId = "scene_${(index + 1).toString().padStart(2, '0')}",
                sceneId = seg.sceneId,
                role = ClipRole.SCENE,
                order = order++,
                title = "Scene ${index + 1}",
                narration = seg.narration,
                language = language,
                plannedDurationMs = seg.durationMs
            )
        }
        val outroText = listOf(script.cta, script.ending)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (outroText.isNotBlank()) {
            clips += AudioPlanClip(
                clipId = "outro",
                sceneId = OUTRO_SCENE_ID,
                role = ClipRole.OUTRO,
                order = order,
                title = "Outro",
                narration = outroText,
                language = language,
                plannedDurationMs = estimateMs(outroText)
            )
        }
        return clips
    }

    fun relativeWavName(clip: AudioPlanClip): String = "${clip.clipId}.wav"

    private fun estimateMs(text: String): Long =
        (text.length * 60L).coerceIn(2_000L, 20_000L)
}
