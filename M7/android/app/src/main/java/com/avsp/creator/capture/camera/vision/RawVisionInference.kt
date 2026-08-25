package com.avsp.creator.capture.camera.vision

import android.graphics.RectF

/**
 * Represents a semantic label produced by on-device computer vision models.
 */
data class VisionLabel(
    val text: String,
    val confidence: Float,
    val index: Int = 0
)

/**
 * Represents an object detected by an on-device vision model (e.g. ML Kit Object Detection).
 * Contains real bounding boxes, tracking ID (if streaming), labels, and normalized coordinates [0.0, 1.0].
 */
data class DetectedObjectEntity(
    val trackingId: Int? = null,
    val boundingBox: RectF,
    val normalizedCenterX: Float,
    val normalizedCenterY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val labels: List<VisionLabel> = emptyList(),
    val primaryLabel: String = "Object",
    val confidence: Float = 0.0f
)

/**
 * Encapsulates raw inference results from on-device vision models.
 */
data class RawVisionInference(
    val detectedObjects: List<DetectedObjectEntity> = emptyList(),
    val imageLabels: List<VisionLabel> = emptyList(),
    val modelName: String = "None",
    val inferenceLatencyMs: Long = 0L,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
) {
    val hasDetections: Boolean
        get() = detectedObjects.isNotEmpty() || imageLabels.isNotEmpty()
}
