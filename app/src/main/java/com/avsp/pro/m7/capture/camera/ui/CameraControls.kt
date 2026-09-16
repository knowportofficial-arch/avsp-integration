package com.avsp.pro.m7.capture.camera.ui

/**
 * UI contract for the camera screen.
 *
 * The actual Compose screen should render:
 * - AI AUTO / AI SHOT / MANUAL selector
 * - always-available Shot List button
 * - mute/unmute voice control
 * - language selector
 * - PHOTO / VIDEO
 * - contextual framing controls only when applicable
 *
 * This contract deliberately avoids changing the existing CameraViewModel
 * until the final integration pass.
 */
data class CameraControls(
    val captureMode: CaptureUiMode,
    val showFullShotList: Boolean = true,
    val voiceMuted: Boolean = false,
    val interfaceLanguage: String = "en",
    val mediaType: String = "PHOTO"
)
