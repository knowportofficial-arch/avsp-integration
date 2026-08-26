package com.avsp.pro.capture.camera.guided

import com.avsp.pro.capture.camera.mission.ShotMediaType
import com.avsp.pro.capture.camera.mission.ShotMission
import com.avsp.pro.capture.camera.mission.ShotMissionItem
import com.avsp.pro.capture.camera.mission.ShotStatus
import com.avsp.pro.capture.camera.model.CameraAspectRatio
import com.avsp.pro.capture.camera.model.CameraFrameRate
import com.avsp.pro.capture.camera.model.CameraResolution
import com.avsp.pro.capture.camera.model.CameraShotType
import com.avsp.pro.capture.camera.model.CaptureOrientation
import com.avsp.pro.capture.camera.planner.MasterShotPlan
import com.avsp.pro.capture.camera.planner.PlannedFraming
import com.avsp.pro.capture.camera.planner.PlannedMediaType
import com.avsp.pro.capture.camera.planner.PlannedShot
import com.avsp.pro.capture.camera.planner.ShotMissionPlanAdapter

/**
 * Maps existing AVSP shot-plan / mission data into [GuidedCaptureTemplate]
 * without inventing semantic names or descriptions.
 *
 * Naming contract preserved via [GuidedClipSpec.captureInstruction]:
 *   "{title} · {CODE} · {Ns} · {Framing} ({existing framing description})"
 */
object GuidedCapturePlanAdapter {

    data class GuidedSession(
        val template: GuidedCaptureTemplate,
        val mission: ShotMission,
        val planId: String,
        val planTitle: String
    )

    fun fromMasterShotPlan(
        plan: MasterShotPlan,
        aspectRatio: CameraAspectRatio = CameraAspectRatio.PORTRAIT_9_16,
        orientation: CaptureOrientation = CaptureOrientation.PORTRAIT
    ): GuidedSession {
        require(plan.shots.isNotEmpty()) { "MasterShotPlan has no shots" }
        val mission = ShotMissionPlanAdapter.toShotMission(plan)
        val clips = plan.shots.mapIndexed { index, shot ->
            toClipSpec(
                shot = shot,
                missionId = mission.id,
                aspectRatio = aspectRatio,
                orientation = orientation,
                sequence = index + 1
            )
        }
        val template = GuidedCaptureTemplate(
            templateId = plan.id.ifBlank { mission.id },
            templateName = plan.title.ifBlank { "Guided Capture" },
            clips = clips
        )
        return GuidedSession(
            template = template,
            mission = mission,
            planId = plan.id,
            planTitle = plan.title
        )
    }

    fun fromShotMission(
        mission: ShotMission,
        aspectRatio: CameraAspectRatio = CameraAspectRatio.PORTRAIT_9_16,
        orientation: CaptureOrientation = CaptureOrientation.PORTRAIT
    ): GuidedSession {
        require(mission.shots.isNotEmpty()) { "ShotMission has no shots" }
        val clips = mission.shots.map { item ->
            toClipSpec(
                item = item,
                missionId = mission.id,
                aspectRatio = aspectRatio,
                orientation = orientation
            )
        }
        val template = GuidedCaptureTemplate(
            templateId = mission.id,
            templateName = mission.title.ifBlank { "Guided Capture" },
            clips = clips
        )
        return GuidedSession(
            template = template,
            mission = mission.copy(
                shots = mission.shots.mapIndexed { index, shot ->
                    if (index == 0) shot.copy(status = ShotStatus.CURRENT)
                    else shot.copy(status = ShotStatus.PENDING)
                },
                currentShotIndex = 0
            ),
            planId = mission.id,
            planTitle = mission.title
        )
    }

    /**
     * Sample-template fallback when no project context is available.
     * Preserves Intro · INTRO · 5s · Wide (Establishing…) naming — never replaces
     * semantic names with framing-only labels.
     */
    fun fromSampleFallback(): GuidedSession {
        val template = GuidedCaptureTemplate.sample()
        val mission = ShotMission(
            id = template.templateId,
            title = template.templateName,
            contextDescription = "Sample guided capture sequence",
            shots = template.clips.mapIndexed { index, clip ->
                ShotMissionItem(
                    id = clip.clipId,
                    sequenceNumber = index + 1,
                    title = clip.clipName,
                    description = clip.framingType.description,
                    shotType = clip.framingType,
                    mediaType = if (clip.mediaType == GuidedClipMediaType.VIDEO) {
                        ShotMediaType.VIDEO
                    } else {
                        ShotMediaType.PHOTO
                    },
                    preferredFrameRate = clip.frameRate,
                    preferredResolution = clip.resolution,
                    subjectHint = clip.subjectHint,
                    compositionHint = clip.guidanceHint,
                    status = if (index == 0) ShotStatus.CURRENT else ShotStatus.PENDING
                )
            },
            currentShotIndex = 0
        )
        return GuidedSession(
            template = template,
            mission = mission,
            planId = template.templateId,
            planTitle = template.templateName
        )
    }

    private fun toClipSpec(
        shot: PlannedShot,
        missionId: String,
        aspectRatio: CameraAspectRatio,
        orientation: CaptureOrientation,
        sequence: Int
    ): GuidedClipSpec {
        val framing = shot.framing.toCameraShotType()
        val isVideo = shot.mediaType == PlannedMediaType.VIDEO
        return GuidedClipSpec(
            clipId = shot.id.ifBlank { "shot_$sequence" },
            clipName = shot.title.trim().ifBlank { "Shot $sequence" },
            category = shotCodeFor(shot.framing, sequence),
            targetDurationSeconds = if (isVideo) 8 else 5,
            aspectRatio = aspectRatio,
            orientation = orientation,
            resolution = CameraResolution.FULL_HD_1080,
            frameRate = CameraFrameRate.FPS_30,
            mediaType = if (isVideo) GuidedClipMediaType.VIDEO else GuidedClipMediaType.PHOTO,
            framingType = framing,
            subjectHint = shot.subject,
            guidanceHint = shot.guidance,
            purpose = shot.purpose,
            missionId = missionId,
            missionShotId = shot.id,
            sceneId = null, // MasterShotPlan has no sceneId contract today
            takeIndex = 1
        )
    }

    private fun toClipSpec(
        item: ShotMissionItem,
        missionId: String,
        aspectRatio: CameraAspectRatio,
        orientation: CaptureOrientation
    ): GuidedClipSpec {
        val isVideo = item.mediaType == ShotMediaType.VIDEO
        return GuidedClipSpec(
            clipId = item.id,
            clipName = item.title.trim().ifBlank { "Shot ${item.sequenceNumber}" },
            category = item.shotType.name,
            targetDurationSeconds = if (isVideo) 8 else 5,
            aspectRatio = aspectRatio,
            orientation = orientation,
            resolution = item.preferredResolution,
            frameRate = item.preferredFrameRate,
            mediaType = if (isVideo) GuidedClipMediaType.VIDEO else GuidedClipMediaType.PHOTO,
            framingType = item.shotType,
            subjectHint = item.subjectHint,
            guidanceHint = item.compositionHint,
            purpose = item.description,
            missionId = missionId,
            missionShotId = item.id,
            sceneId = null,
            takeIndex = 1
        )
    }

    /**
     * Shot code from existing framing enum / sequence — not invented prose.
     * INTRO-style codes remain available when sample() template is used.
     */
    private fun shotCodeFor(framing: PlannedFraming, sequence: Int): String = when (framing) {
        PlannedFraming.WIDE, PlannedFraming.ENVIRONMENTAL, PlannedFraming.FULL_BODY -> "WIDE"
        PlannedFraming.MEDIUM, PlannedFraming.PORTRAIT, PlannedFraming.TRACKING -> "MEDIUM"
        PlannedFraming.CLOSE, PlannedFraming.DETAIL, PlannedFraming.MACRO, PlannedFraming.DOCUMENT -> "CLOSE"
        PlannedFraming.UNSPECIFIED -> "S$sequence"
    }

    private fun PlannedFraming.toCameraShotType(): CameraShotType = when (this) {
        PlannedFraming.WIDE, PlannedFraming.ENVIRONMENTAL, PlannedFraming.FULL_BODY -> CameraShotType.WIDE
        PlannedFraming.MEDIUM, PlannedFraming.PORTRAIT, PlannedFraming.TRACKING -> CameraShotType.MEDIUM
        PlannedFraming.CLOSE, PlannedFraming.DETAIL, PlannedFraming.MACRO, PlannedFraming.DOCUMENT -> CameraShotType.CLOSE
        PlannedFraming.UNSPECIFIED -> CameraShotType.MEDIUM
    }
}
