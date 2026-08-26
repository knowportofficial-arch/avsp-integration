package com.avsp.pro.capture.camera.ui

data class VoiceGuidanceSettings(
    val enabled: Boolean = true,
    val languageTag: String = "en-IN",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
)
