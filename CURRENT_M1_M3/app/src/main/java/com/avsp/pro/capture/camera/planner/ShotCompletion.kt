package com.avsp.pro.capture.camera.planner

data class ShotCompletion(
    val shotId: String,
    val status: PlannedShotStatus,
    val executionMode: ShotExecutionMode,
    val mediaUri: String? = null,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
