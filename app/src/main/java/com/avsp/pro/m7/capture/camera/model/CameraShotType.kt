package com.avsp.pro.m7.capture.camera.model

enum class CameraShotType(
    val displayName: String,
    val description: String,
    val defaultZoomRatio: Float
) {
    WIDE(
        displayName = "Wide",
        description = "Establishing, environment & landscape shot",
        defaultZoomRatio = 1.0f
    ),
    MEDIUM(
        displayName = "Medium",
        description = "Main subject with surrounding contextual frame",
        defaultZoomRatio = 1.8f
    ),
    CLOSE(
        displayName = "Close",
        description = "Detail, face, product & macro texture shot",
        defaultZoomRatio = 3.0f
    )
}
