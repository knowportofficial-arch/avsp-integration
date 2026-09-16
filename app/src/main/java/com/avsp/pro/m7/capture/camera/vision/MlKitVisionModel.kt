package com.avsp.pro.m7.capture.camera.vision

import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Real on-device semantic vision model using Google ML Kit Object Detection & Image Labeling.
 * Executes genuinely on live camera frame buffers without cloud network requests or simulated delays.
 */
class MlKitVisionModel : VisionModel {

    override val modelName: String = "MLKit-OnDeviceVision"
    override var isAvailable: Boolean = true
        private set

    private var objectDetector: ObjectDetector? = null
    private var imageLabeler: ImageLabeler? = null

    init {
        try {
            val detectorOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
            objectDetector = ObjectDetection.getClient(detectorOptions)

            val labelerOptions = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.40f)
                .build()
            imageLabeler = ImageLabeling.getClient(labelerOptions)
        } catch (e: Exception) {
            isAvailable = false
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun processFrame(imageProxy: ImageProxy): RawVisionInference {
        val startTime = System.currentTimeMillis()
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isAvailable) {
            return RawVisionInference(
                modelName = modelName,
                inferenceLatencyMs = System.currentTimeMillis() - startTime,
                frameWidth = imageProxy.width,
                frameHeight = imageProxy.height
            )
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val frameWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val frameHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        val detectedObjectEntities = mutableListOf<DetectedObjectEntity>()
        val detectedLabels = mutableListOf<VisionLabel>()

        try {
            val detector = objectDetector
            if (detector != null) {
                val objectTask = detector.process(inputImage)
                val objects = Tasks.await(objectTask, 350, TimeUnit.MILLISECONDS)

                for (detected in objects) {
                    val rawBox = detected.boundingBox
                    val normLeft = (rawBox.left.toFloat() / frameWidth).coerceIn(0.0f, 1.0f)
                    val normTop = (rawBox.top.toFloat() / frameHeight).coerceIn(0.0f, 1.0f)
                    val normRight = (rawBox.right.toFloat() / frameWidth).coerceIn(0.0f, 1.0f)
                    val normBottom = (rawBox.bottom.toFloat() / frameHeight).coerceIn(0.0f, 1.0f)

                    val normWidth = (normRight - normLeft).coerceIn(0.05f, 1.0f)
                    val normHeight = (normBottom - normTop).coerceIn(0.05f, 1.0f)
                    val normCenterX = (normLeft + normRight) / 2.0f
                    val normCenterY = (normTop + normBottom) / 2.0f

                    val entityLabels = detected.labels.map { label ->
                        VisionLabel(
                            text = label.text,
                            confidence = label.confidence,
                            index = label.index
                        )
                    }

                    val primaryLabelText = entityLabels.maxByOrNull { it.confidence }?.text ?: "Detected Object"
                    val topConfidence = entityLabels.maxByOrNull { it.confidence }?.confidence ?: 0.65f

                    detectedObjectEntities.add(
                        DetectedObjectEntity(
                            trackingId = detected.trackingId,
                            boundingBox = RectF(normLeft, normTop, normRight, normBottom),
                            normalizedCenterX = normCenterX,
                            normalizedCenterY = normCenterY,
                            normalizedWidth = normWidth,
                            normalizedHeight = normHeight,
                            labels = entityLabels,
                            primaryLabel = primaryLabelText,
                            confidence = topConfidence
                        )
                    )
                }
            }

            val labeler = imageLabeler
            if (labeler != null) {
                val labelTask = labeler.process(inputImage)
                val labels = Tasks.await(labelTask, 350, TimeUnit.MILLISECONDS)
                for (label in labels) {
                    detectedLabels.add(
                        VisionLabel(
                            text = label.text,
                            confidence = label.confidence,
                            index = label.index
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Non-blocking timeout or executor cancellation
        }

        val latency = System.currentTimeMillis() - startTime
        return RawVisionInference(
            detectedObjects = detectedObjectEntities,
            imageLabels = detectedLabels,
            modelName = modelName,
            inferenceLatencyMs = latency,
            frameWidth = frameWidth,
            frameHeight = frameHeight
        )
    }

    override fun close() {
        try {
            objectDetector?.close()
            imageLabeler?.close()
        } catch (_: Exception) {}
    }
}
