package com.avsp.creator.capture.camera.planner

import com.avsp.creator.capture.camera.mission.ShotMission
import com.avsp.creator.capture.camera.mission.ShotMissionItem
import com.avsp.creator.capture.camera.mission.ShotStatus
import com.avsp.creator.capture.camera.mission.ShotMediaType
import com.avsp.creator.capture.camera.model.CameraFrameRate
import com.avsp.creator.capture.camera.model.CameraResolution
import com.avsp.creator.capture.camera.model.CameraShotType
import java.util.UUID

/** Converts the new contextual planner output into the existing AVSP mission model. */
object ShotMissionPlanAdapter {
    fun toShotMission(plan: MasterShotPlan): ShotMission {
        val items = plan.shots.mapIndexed { index, shot ->
            ShotMissionItem(
                id = shot.id,
                sequenceNumber = index + 1,
                title = shot.title,
                description = shot.purpose,
                shotType = shot.framing.toCameraShotType(),
                mediaType = if (shot.mediaType == PlannedMediaType.VIDEO) ShotMediaType.VIDEO else ShotMediaType.PHOTO,
                preferredFrameRate = if (shot.mediaType == PlannedMediaType.VIDEO) CameraFrameRate.FPS_30 else CameraFrameRate.FPS_30,
                preferredResolution = CameraResolution.FULL_HD_1080,
                subjectHint = shot.subject,
                compositionHint = shot.guidance,
                status = if (index == 0) ShotStatus.CURRENT else ShotStatus.PENDING,
                priority = shot.priority
            )
        }
        return ShotMission(
            id = plan.id.ifBlank { "plan_${UUID.randomUUID().toString().take(8)}" },
            title = plan.title,
            contextDescription = plan.summary,
            shots = items,
            currentShotIndex = 0
        )
    }

    private fun PlannedFraming.toCameraShotType(): CameraShotType = when (this) {
        PlannedFraming.WIDE, PlannedFraming.ENVIRONMENTAL, PlannedFraming.FULL_BODY -> CameraShotType.WIDE
        PlannedFraming.MEDIUM, PlannedFraming.PORTRAIT, PlannedFraming.TRACKING -> CameraShotType.MEDIUM
        PlannedFraming.CLOSE, PlannedFraming.DETAIL, PlannedFraming.MACRO, PlannedFraming.DOCUMENT -> CameraShotType.CLOSE
        PlannedFraming.UNSPECIFIED -> CameraShotType.MEDIUM
    }
}
