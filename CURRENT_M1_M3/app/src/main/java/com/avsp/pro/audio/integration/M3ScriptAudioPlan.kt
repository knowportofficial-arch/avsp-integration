package com.avsp.pro.audio.integration

import com.avsp.pro.audio.contract.SegmentRole
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene
import com.avsp.pro.script.contract.ShotType
import java.security.MessageDigest
import java.util.Locale

/**
 * M3-only adapter: builds a deterministic, de-duplicated audio timeline from M2 [ScriptPackage].
 *
 * Canonical sequence (when content exists and is unique):
 * INTRO → HOOK → SCENE(s) → OUTRO
 *
 * Top-level M2 fields are not duplicated when the same narration already appears in a scene.
 */
data class M3AudioPlanItem(
    val clipId: String,
    val segmentKey: String,
    val sceneId: String?,
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
    val targetDurationMs: Long,
    val items: List<M3AudioPlanItem>
)

object M3ScriptAudioPlan {
    fun fromScript(script: ScriptPackage): M3AudioPlan {
        val items = mutableListOf<M3AudioPlanItem>()
        val seenHashes = mutableSetOf<String>()
        var sequence = 0

        fun addUnique(item: M3AudioPlanItem) {
            if (seenHashes.add(item.sourceTextHash)) {
                items += item.copy(order = sequence++)
            }
        }

        val normalized = { text: String -> normalizeText(text) }

        if (script.introduction.isNotBlank()) {
            addUnique(
                buildItem(
                    clipSuffix = "intro",
                    segmentKey = "intro",
                    sceneId = null,
                    role = SegmentRole.INTRO,
                    title = "Intro",
                    narration = script.introduction,
                    language = script.language,
                    plannedDurationMs = script.scenes.firstOrNull()?.durationMs ?: estimateMs(script.introduction)
                )
            )
        }

        val hookScene = script.scenes.filter { it.shotType == ShotType.INTRO }.minByOrNull { it.order }
        val hookText = hookScene?.narration?.takeIf { it.isNotBlank() }
            ?: script.hook.takeIf { it.isNotBlank() }
        if (!hookText.isNullOrBlank()) {
            addUnique(
                buildItem(
                    clipSuffix = "hook",
                    segmentKey = hookScene?.sceneId ?: "hook",
                    sceneId = hookScene?.sceneId,
                    role = SegmentRole.HOOK,
                    title = "Hook",
                    narration = hookText,
                    language = script.language,
                    plannedDurationMs = hookScene?.durationMs ?: estimateMs(hookText)
                )
            )
        }

        val bodyScenes = script.scenes
            .sortedBy { it.order }
            .filter { it.shotType != ShotType.INTRO && it.shotType != ShotType.OUTRO }
        bodyScenes.forEachIndexed { index, scene ->
            addUnique(
                buildItem(
                    clipSuffix = "scene_${(index + 1).toString().padStart(2, '0')}",
                    segmentKey = scene.sceneId,
                    sceneId = scene.sceneId,
                    role = SegmentRole.SCENE,
                    title = sceneTitle(scene, index + 1),
                    narration = scene.narration,
                    language = script.language,
                    plannedDurationMs = scene.durationMs
                )
            )
        }

        val outroScene = script.scenes.filter { it.shotType == ShotType.OUTRO }.maxByOrNull { it.order }
        val endingFromFields = listOf(script.cta, script.ending)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val outroText = outroScene?.narration?.takeIf { it.isNotBlank() }
            ?: endingFromFields.takeIf { it.isNotBlank() }
        if (!outroText.isNullOrBlank()) {
            val outroNormalized = normalized(outroText)
            val endingNormalized = normalized(endingFromFields)
            if (outroScene != null || endingNormalized.isBlank() || outroNormalized != endingNormalized || !seenHashes.contains(hashText(endingFromFields))) {
                addUnique(
                    buildItem(
                        clipSuffix = "outro",
                        segmentKey = outroScene?.sceneId ?: "outro",
                        sceneId = outroScene?.sceneId,
                        role = SegmentRole.OUTRO,
                        title = "CTA / Outro",
                        narration = outroText,
                        language = script.language,
                        plannedDurationMs = outroScene?.durationMs ?: estimateMs(outroText)
                    )
                )
            }
        }

        return M3AudioPlan(
            projectId = script.projectId,
            scriptId = script.scriptId,
            scriptVersion = script.version,
            language = script.language,
            title = script.title,
            targetDurationMs = script.targetDurationMs,
            items = items
        )
    }

    fun hashText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(normalizeText(text).encodeToByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun normalizeText(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.US)

    private fun buildItem(
        clipSuffix: String,
        segmentKey: String,
        sceneId: String?,
        role: SegmentRole,
        title: String,
        narration: String,
        language: String,
        plannedDurationMs: Long
    ): M3AudioPlanItem {
        val orderPlaceholder = 0
        val clipId = "clip_${clipSuffix}"
        return M3AudioPlanItem(
            clipId = clipId,
            segmentKey = segmentKey,
            sceneId = sceneId,
            order = orderPlaceholder,
            role = role,
            title = title,
            narration = narration,
            language = language,
            plannedDurationMs = plannedDurationMs,
            sourceTextHash = hashText(narration)
        )
    }

    private fun sceneTitle(scene: ScriptScene, bodyIndex: Int): String =
        when {
            scene.notes.equals("Body", ignoreCase = true) -> "Scene $bodyIndex"
            scene.notes.isNotBlank() -> scene.notes
            else -> "Scene $bodyIndex"
        }

    private fun estimateMs(text: String): Long = maxOf(1_000L, text.length * 55L)
}
