package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.mission.ShotMissionItem

/**
 * Preserved Guided Capture / shot-plan naming contract.
 *
 * VIDEO (canonical):
 *   "Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)"
 *
 * PHOTO (no fake video duration):
 *   "Entrance · WIDE · Photo · Wide (Establishing, environment & landscape shot)"
 *
 * - [semanticName] / [shotCode] come from existing shot-plan / template data.
 * - WIDE / MEDIUM / CLOSE ([framing]) remain technical framing only.
 * - Duration seconds are shown only for VIDEO clips.
 */
object GuidedCaptureShotNaming {

    fun format(
        semanticName: String,
        shotCode: String,
        durationSeconds: Int,
        framing: CameraShotType,
        mediaType: GuidedClipMediaType = GuidedClipMediaType.VIDEO
    ): String {
        val durationOrPhoto = when (mediaType) {
            GuidedClipMediaType.VIDEO -> "${durationSeconds}s"
            GuidedClipMediaType.PHOTO -> "Photo"
        }
        return "$semanticName · $shotCode · $durationOrPhoto · ${framing.displayName} (${framing.description})"
    }

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
