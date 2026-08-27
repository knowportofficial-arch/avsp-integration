package com.avsp.pro.audio.integration

import com.avsp.pro.audio.contract.SegmentRole
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene
import com.avsp.pro.script.contract.ShotType
import java.security.MessageDigest

/**
 * M3-only adapter: builds an audio generation plan from the saved M2 [ScriptPackage]
 * without modifying M2 generators or UI.
 */
data class M3AudioPlanItem(
    val segmentKey: String,
    val sceneId: String,
    val order: Int,
    val role: SegmentRole,
    val title: String,
    val narration: String,
    val language: String,
    val plannedDurationMs: Long,
    val sourceTextHash: String
)

data class M3AudioPlan(
    val projectId: String,
    val scriptId: String,
    val scriptVersion: String,
    val language: String,
    val title: String,
    val items: List<M3AudioPlanItem>
)

object M3ScriptAudioPlan {
    fun fromScript(script: ScriptPackage): M3AudioPlan {
        val items = mutableListOf<M3AudioPlanItem>()
        var order = 0

        if (script.introduction.isNotBlank()) {
            items += planItem(
                segmentKey = "intro",
                sceneId = "intro",
                order = order++,
                role = SegmentRole.INTRO,
                title = "Intro",
                narration = script.introduction,
                language = script.language,
                plannedDurationMs = estimateMs(script.introduction)
            )
        }

        script.scenes.sortedBy { it.order }.forEach { scene ->
            val role = scene.toSegmentRole()
            items += planItem(
                segmentKey = scene.sceneId,
                sceneId = scene.sceneId,
                order = order++,
                role = role,
                title = sceneTitle(scene),
                narration = scene.narration,
                language = script.language,
                plannedDurationMs = scene.durationMs
            )
        }

        val endingText = listOf(script.cta, script.ending)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (endingText.isNotBlank()) {
            items += planItem(
                segmentKey = "outro",
                sceneId = "outro",
                order = order,
                role = SegmentRole.OUTRO,
                title = "Outro",
                narration = endingText,
                language = script.language,
                plannedDurationMs = estimateMs(endingText)
            )
        }

        return M3AudioPlan(
            projectId = script.projectId,
            scriptId = script.scriptId,
            scriptVersion = script.version,
            language = script.language,
            title = script.title,
            items = items
        )
    }

    fun hashText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.trim().encodeToByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun planItem(
        segmentKey: String,
        sceneId: String,
        order: Int,
        role: SegmentRole,
        title: String,
        narration: String,
        language: String,
        plannedDurationMs: Long
    ) = M3AudioPlanItem(
        segmentKey = segmentKey,
        sceneId = sceneId,
        order = order,
        role = role,
        title = title,
        narration = narration,
        language = language,
        plannedDurationMs = plannedDurationMs,
        sourceTextHash = hashText(narration)
    )

    private fun ScriptScene.toSegmentRole(): SegmentRole = when (shotType) {
        ShotType.INTRO -> SegmentRole.INTRO
        ShotType.OUTRO -> SegmentRole.OUTRO
        else -> SegmentRole.SCENE
    }

    private fun sceneTitle(scene: ScriptScene): String =
        scene.notes.ifBlank { "Scene ${scene.order + 1}" }

    private fun estimateMs(text: String): Long =
        maxOf(1_000L, text.length * 55L)
}
