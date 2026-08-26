package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.mission.ShotMissionItem

/**
 * Preserved Guided Capture / shot-plan naming contract.
 *
 * Canonical format (do not redesign):
 *   "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
 *
 * - [semanticName] / [shotCode] come from existing shot-plan / template data
 *   (e.g. Intro, Scene 1, Patriotic, Established — never invent replacements).
 * - WIDE / MEDIUM / CLOSE ([framing]) remain technical framing only.
 */
object GuidedCaptureShotNaming {

    fun format(
        semanticName: String,
        shotCode: String,
        durationSeconds: Int,
        framing: CameraShotType
    ): String =
        "$semanticName · $shotCode · ${durationSeconds}s · ${framing.displayName} (${framing.description})"

    /**
     * Mission-shot variant when an explicit target duration is not on the item.
     * Semantic [ShotMissionItem.title] stays primary; framing stays technical.
     */
    fun formatMissionShot(shot: ShotMissionItem): String {
        val framing = shot.shotType
        val detail = shot.description.ifBlank { framing.description }
        return "${shot.title} · ${framing.name} · ${framing.displayName} ($detail)"
    }
}
