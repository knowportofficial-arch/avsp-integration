package com.avsp.pro.capture.camera.model

enum class CameraResolution(
    val displayName: String,
    val width: Int,
    val height: Int
) {
    HD_720(
        displayName = "720p",
        width = 1280,
        height = 720
    ),
    FULL_HD_1080(
        displayName = "1080p",
        width = 1920,
        height = 1080
    ),
    UHD_4K(
        displayName = "4K",
        width = 3840,
        height = 2160
    )
}
