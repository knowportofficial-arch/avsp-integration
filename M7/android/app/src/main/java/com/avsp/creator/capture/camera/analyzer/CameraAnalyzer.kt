package com.avsp.creator.capture.camera.analyzer

import com.avsp.creator.capture.camera.detector.DetectedSubject
import com.avsp.creator.capture.camera.mission.ShotMissionItem
import com.avsp.creator.capture.camera.model.CameraProfile

interface CameraAnalyzer {
    fun analyze(
        luminanceMean: Double,
        luminanceVariance: Double,
        motionMagnitude: Double,
        tiltRollDegrees: Float,
        currentProfile: CameraProfile,
        activeMissionShot: ShotMissionItem?,
        detectedSubject: DetectedSubject? = null
    ): ShotAnalysisResult
}
