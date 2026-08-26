package com.avsp.pro.capture.camera.planner

/**
 * Adapter boundary between the new Master Shot List and the existing
 * mission/capture layer.
 */
object MasterShotListAdapter {

    fun toCaptureMetadata(
        shot: PlannedShot,
        mode: ShotExecutionMode,
        isExtraManualCapture: Boolean = false
    ): Map<String, String> = buildMap {
        put("shotId", shot.id)
        put("title", shot.title)
        put("mediaType", shot.mediaType.name)
        put("framing", shot.framing.name)
        put("executionMode", mode.name)
        put("predefined", (!isExtraManualCapture).toString())
        put("plannerVersion", "M1.7")
    }
}
