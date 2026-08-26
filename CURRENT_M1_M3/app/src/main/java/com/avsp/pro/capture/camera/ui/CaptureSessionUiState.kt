package com.avsp.pro.capture.camera.ui

data class CaptureSessionUiState(
    val mode: CaptureUiMode = CaptureUiMode.AI_AUTO,
    val showShotList: Boolean = false,
    val voice: VoiceGuidanceSettings = VoiceGuidanceSettings(),
    val statusText: String = "",
    val galleryProjectName: String = ""
)
