package com.avsp.pro.m7.capture.camera.planner

object MediaCaptureMetadataFactory {

    fun predefined(
        shot: PlannedShot,
        mode: ShotExecutionMode,
        latitude: Double?,
        longitude: Double?,
        qualityScore: Int
    ): CaptureMediaMetadata =
        CaptureMediaMetadata(
            classification = MediaClassification.PREDEFINED,
            shotId = shot.id,
            executionMode = mode,
            framing = shot.framing.name,
            latitude = latitude,
            longitude = longitude,
            qualityScore = qualityScore
        )

    fun manualExtra(
        latitude: Double?,
        longitude: Double?,
        qualityScore: Int
    ): CaptureMediaMetadata =
        CaptureMediaMetadata(
            classification = MediaClassification.MANUAL_EXTRA,
            shotId = null,
            executionMode = ShotExecutionMode.MANUAL,
            framing = null,
            latitude = latitude,
            longitude = longitude,
            qualityScore = qualityScore
        )
}
