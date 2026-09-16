package com.avsp.pro.m7.capture.camera.ui

import com.avsp.pro.m7.capture.camera.planner.MasterShotPlan
import com.avsp.pro.m7.capture.camera.planner.PlannedShotStatus
import com.avsp.pro.m7.capture.camera.planner.ShotExecutionMode
import com.avsp.pro.m7.capture.camera.planner.ShotCompletion

object ShotListUiMapper {

    fun map(
        plan: MasterShotPlan?,
        selectedShotId: String?,
        completions: Map<String, ShotCompletion>,
        visible: Boolean = true
    ): ShotListDisplayState {
        if (plan == null) return ShotListDisplayState(visible = false)

        return ShotListDisplayState(
            visible = visible,
            planTitle = plan.title,
            items = plan.shots.map { shot ->
                val completion = completions[shot.id]
                val status = completion?.status ?: PlannedShotStatus.PENDING
                ShotListDisplayItem(
                    id = shot.id,
                    title = shot.title,
                    subtitle = buildString {
                        append(shot.mediaType)
                        if (shot.framing.name.isNotBlank()) {
                            append(" â€¢ ")
                            append(shot.framing.name)
                        }
                    },
                    status = status,
                    selected = shot.id == selectedShotId,
                    modeLabel = completion?.executionMode?.displayName()
                )
            }
        )
    }

    private fun ShotExecutionMode.displayName(): String = when (this) {
        ShotExecutionMode.AUTO -> "AUTO"
        ShotExecutionMode.SHOT -> "SHOT"
        ShotExecutionMode.MANUAL -> "MANUAL"
    }
}

