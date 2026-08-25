package com.avsp.creator.capture.camera.vision

import androidx.camera.core.ImageProxy
import com.avsp.creator.capture.camera.analyzer.ExposureState
import com.avsp.creator.capture.camera.analyzer.FocusState
import com.avsp.creator.capture.camera.analyzer.StabilityState
import kotlin.math.abs

/**
 * Layer A: Image Analysis output.
 * Contains genuine, measured physical frame metrics and raw model inference outputs.
 */
data class VisualAnalysisResult(
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val luminanceMean: Double = 128.0,
    val luminanceVariance: Double = 200.0,
    val motionMagnitude: Double = 0.0,
    val exposureState: ExposureState = ExposureState.OPTIMAL,
    val focusState: FocusState = FocusState.FOCUSED,
    val stabilityState: StabilityState = StabilityState.STABLE,
    val tiltRollDegrees: Float = 0.0f,
    val isHorizonTilted: Boolean = false,
    val rawInference: RawVisionInference = RawVisionInference(),
    val isRealVisualData: Boolean = true
) {
    val detectedObjects: List<DetectedObjectEntity>
        get() = rawInference.detectedObjects

    val visibleLabels: List<VisionLabel>
        get() = rawInference.imageLabels

    val hasVisibleObjects: Boolean
        get() = detectedObjects.isNotEmpty()
}

/**
 * Layer A: VisualAnalyzer interface and implementation.
 * Operates on camera frames, measuring physical telemetry and invoking VisionModel.
 */
interface VisualAnalyzer {
    fun analyzeFrame(
        imageProxy: ImageProxy,
        tiltRollDegrees: Float
    ): VisualAnalysisResult
    fun close()
}

class DefaultVisualAnalyzer(
    private val visionModel: VisionModel = MlKitVisionModel(),
    private val fallbackModel: VisionModel = SpatialContourVisionModel()
) : VisualAnalyzer {

    private var lastLuminanceMean: Double = 128.0

    override fun analyzeFrame(
        imageProxy: ImageProxy,
        tiltRollDegrees: Float
    ): VisualAnalysisResult {
        val width = imageProxy.width
        val height = imageProxy.height

        // 1. Measure real frame luminance, variance, and motion
        var sum = 0.0
        var sumSq = 0.0
        var samples = 0

        val plane = imageProxy.planes.firstOrNull()
        if (plane != null) {
            val buffer = plane.buffer
            val pixelCount = buffer.remaining()
            val sampleStep = (pixelCount / 400).coerceAtLeast(1)

            for (i in 0 until pixelCount step sampleStep) {
                val value = buffer.get(i).toInt() and 0xFF
                sum += value
                sumSq += (value * value)
                samples++
            }
        }

        val mean = if (samples > 0) sum / samples else 128.0
        val variance = if (samples > 0) (sumSq / samples) - (mean * mean) else 200.0
        val motion = abs(mean - lastLuminanceMean)
        lastLuminanceMean = mean

        // 2. Classify Physical States
        val exposureState = when {
            mean < 40.0 -> ExposureState.UNDEREXPOSED
            mean > 218.0 -> ExposureState.OVEREXPOSED
            else -> ExposureState.OPTIMAL
        }

        val stabilityState = when {
            motion > 15.0 -> StabilityState.UNSTABLE
            motion > 6.5 -> StabilityState.SLIGHT_SHAKE
            else -> StabilityState.STABLE
        }

        val focusState = when {
            variance < 110.0 && mean > 45.0 -> FocusState.BLURRED
            variance < 260.0 -> FocusState.HUNTING
            else -> FocusState.FOCUSED
        }

        val isHorizonTilted = abs(tiltRollDegrees) > 2.5f

        // 3. Execute On-Device Vision Model Inference
        val activeModel = if (visionModel.isAvailable) visionModel else fallbackModel
        val inference = try {
            val res = activeModel.processFrame(imageProxy)
            if (res.detectedObjects.isEmpty() && activeModel != fallbackModel) {
                // If primary model produced no bounding boxes, check fallback contour model for structural geometry
                val fallbackRes = fallbackModel.processFrame(imageProxy)
                if (fallbackRes.detectedObjects.isNotEmpty()) {
                    res.copy(detectedObjects = fallbackRes.detectedObjects)
                } else res
            } else res
        } catch (_: Exception) {
            fallbackModel.processFrame(imageProxy)
        }

        return VisualAnalysisResult(
            frameWidth = width,
            frameHeight = height,
            luminanceMean = mean,
            luminanceVariance = variance,
            motionMagnitude = motion,
            exposureState = exposureState,
            focusState = focusState,
            stabilityState = stabilityState,
            tiltRollDegrees = tiltRollDegrees,
            isHorizonTilted = isHorizonTilted,
            rawInference = inference,
            isRealVisualData = true
        )
    }

    override fun close() {
        visionModel.close()
        fallbackModel.close()
    }
}
