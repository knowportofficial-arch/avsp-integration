package com.avsp.pro.m7.capture.camera.ui

import com.avsp.pro.m7.capture.camera.planner.MasterShotPlan
import com.avsp.pro.m7.capture.camera.planner.PlannedShotStatus

data class ShotListDisplayItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: PlannedShotStatus,
    val selected: Boolean,
    val modeLabel: String? = null
)

data class ShotListDisplayState(
    val visible: Boolean = false,
    val planTitle: String = "",
    val items: List<ShotListDisplayItem> = emptyList()
)
