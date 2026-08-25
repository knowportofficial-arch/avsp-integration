package com.avsp.creator.capture.camera.model

enum class CameraFrameRate(
    val displayName: String,
    val targetFps: Int
) {
    FPS_30(
        displayName = "30 FPS",
        targetFps = 30
    ),
    FPS_60(
        displayName = "60 FPS",
        targetFps = 60
    )
}
