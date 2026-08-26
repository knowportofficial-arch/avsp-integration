package com.avsp.pro.capture.camera.analyzer

import com.avsp.pro.capture.camera.detector.DetectedSubject
import com.avsp.pro.capture.camera.mission.ShotMissionItem
import com.avsp.pro.capture.camera.model.CameraProfile

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
