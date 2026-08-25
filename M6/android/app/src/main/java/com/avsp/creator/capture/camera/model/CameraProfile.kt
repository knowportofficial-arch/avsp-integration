package com.avsp.creator.capture.camera.model

import androidx.camera.core.CameraSelector

enum class CameraControlMode(val displayName: String) {
    AUTO("AUTO"),
    MANUAL("MANUAL")
}

data class CameraProfile(
    val shotType: CameraShotType = CameraShotType.WIDE,
    val resolution: CameraResolution = CameraResolution.FULL_HD_1080,
    val frameRate: CameraFrameRate = CameraFrameRate.FPS_30,
    val zoomRatio: Float = 1.0f,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val controlMode: CameraControlMode = CameraControlMode.AUTO,
    val isAutoFramingEnabled: Boolean = true,
    val isGridOverlayEnabled: Boolean = true,
    val isHorizonLevelEnabled: Boolean = true,
    /**
     * M6.2 — optional hint used only by the guided-capture flow to constrain ImageCapture's
     * resolution selection to a 16:9-family sensor mode via CameraX's real
     * ResolutionSelector/AspectRatioStrategy API. Defaults to null, which preserves the
     * exact pre-existing behavior for every caller that doesn't set it (i.e. the AI camera
     * screen is unaffected). Video (Recorder) is NOT affected by this field — CameraX
     * 1.3.2's Recorder has no ResolutionSelector API; see CameraController.bindCamera()
     * comments for why.
     */
    val preferredAspectRatio: CameraAspectRatio? = null,
    /**
     * M6.2 — when true, bindCamera() resolves and applies a real Camera2 AE target FPS
     * range for [frameRate], falling back to a supported 30fps range if necessary.
     * Enabled by default so the visible FPS selection is connected to the real camera session.
     */
    val enforceRequestedFrameRate: Boolean = true
)
