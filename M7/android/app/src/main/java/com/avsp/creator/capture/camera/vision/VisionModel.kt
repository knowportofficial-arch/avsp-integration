package com.avsp.creator.capture.camera.vision

import androidx.camera.core.ImageProxy

/**
 * Interface for on-device computer vision models executing on live CameraX ImageProxy frames.
 */
interface VisionModel {
    val modelName: String
    val isAvailable: Boolean

    /**
     * Executes real on-device inference on the incoming camera frame buffer.
     * Must be called from a background executor thread.
     */
    fun processFrame(imageProxy: ImageProxy): RawVisionInference

    /**
     * Releases any underlying model resources.
     */
    fun close()
}
