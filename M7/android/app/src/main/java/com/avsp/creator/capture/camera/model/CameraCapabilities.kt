package com.avsp.creator.capture.camera.model

data class CameraCapabilities(
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 8.0f,
    val is60FpsSupported: Boolean = true,
    val is720pSupported: Boolean = true,
    val is1080pSupported: Boolean = true,
    val is4kSupported: Boolean = false,
    val hasTorch: Boolean = false,
    val hasBackCamera: Boolean = true,
    val hasFrontCamera: Boolean = true
) {
    fun isResolutionSupported(resolution: CameraResolution): Boolean {
        return when (resolution) {
            CameraResolution.HD_720 -> is720pSupported
            CameraResolution.FULL_HD_1080 -> is1080pSupported
            CameraResolution.UHD_4K -> is4kSupported
        }
    }

    fun isFrameRateSupported(frameRate: CameraFrameRate): Boolean {
        return when (frameRate) {
            CameraFrameRate.FPS_30 -> true
            CameraFrameRate.FPS_60 -> is60FpsSupported
        }
    }
}
