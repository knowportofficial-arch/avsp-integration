package com.avsp.pro.capture.camera.model

/**
 * M6 — output aspect ratio for a guided-capture clip.
 * Independent of [CameraShotType] (WIDE/MEDIUM/CLOSE framing), which is owned
 * by the existing AI mission system and is left untouched.
 */
enum class CameraAspectRatio(
    val displayName: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    PORTRAIT_9_16(
        displayName = "9:16",
        widthRatio = 9,
        heightRatio = 16
    ),
    LANDSCAPE_16_9(
        displayName = "16:9",
        widthRatio = 16,
        heightRatio = 9
    );

    /** True if this aspect ratio implies a portrait-oriented frame. */
    val isPortrait: Boolean get() = this == PORTRAIT_9_16
}
