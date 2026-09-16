package com.avsp.pro.m7.capture.camera.vision

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device computer vision fallback using real frame buffer gradient analysis.
 * Extracts dominant geometric contours and spatial centroids from live frames.
 * Does not fake semantic classification labels.
 */
class SpatialContourVisionModel : VisionModel {

    override val modelName: String = "OnDevice-SpatialContourVision"
    override val isAvailable: Boolean = true

    override fun processFrame(imageProxy: ImageProxy): RawVisionInference {
        val startTime = System.currentTimeMillis()
        val plane = imageProxy.planes.firstOrNull() ?: return RawVisionInference(
            modelName = modelName,
            inferenceLatencyMs = System.currentTimeMillis() - startTime,
            frameWidth = imageProxy.width,
            frameHeight = imageProxy.height
        )

        val buffer = plane.buffer
        val bufferSize = buffer.remaining()
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (width <= 0 || height <= 0 || bufferSize <= 0) {
            return RawVisionInference(modelName = modelName, inferenceLatencyMs = 0L, frameWidth = width, frameHeight = height)
        }

        val gridCols = 16
        val gridRows = 12
        val cellWidth = width / gridCols
        val cellHeight = height / gridRows

        val cellEnergy = Array(gridRows) { FloatArray(gridCols) }
        var totalEnergy = 0.0
        var sampleCount = 0

        for (r in 0 until gridRows) {
            val startY = r * cellHeight
            val endY = min(startY + cellHeight, height - 1)

            for (c in 0 until gridCols) {
                val startX = c * cellWidth
                val endX = min(startX + cellWidth, width - 1)

                var gradSum = 0.0
                var count = 0
                val stepY = max(1, (endY - startY) / 3)
                val stepX = max(1, (endX - startX) / 3)

                for (y in startY until endY step stepY) {
                    val rowOffset = y * rowStride
                    for (x in startX until endX step stepX) {
                        val pos = rowOffset + (x * pixelStride)
                        if (pos >= 0 && pos + pixelStride < bufferSize) {
                            val lum1 = buffer.get(pos).toInt() and 0xFF
                            val lum2 = buffer.get(pos + pixelStride).toInt() and 0xFF
                            gradSum += abs(lum1 - lum2)
                            count++
                        }
                    }
                }

                if (count > 0) {
                    val avgGrad = (gradSum / count).toFloat()
                    cellEnergy[r][c] = avgGrad
                    totalEnergy += gradSum
                    sampleCount += count
                }
            }
        }

        if (sampleCount == 0) {
            return RawVisionInference(modelName = modelName, inferenceLatencyMs = System.currentTimeMillis() - startTime, frameWidth = width, frameHeight = height)
        }

        val frameAvgEnergy = (totalEnergy / sampleCount).toFloat()
        val threshold = max(7.0f, frameAvgEnergy * 1.2f)

        var minCol = gridCols
        var maxCol = -1
        var minRow = gridRows
        var maxRow = -1
        var weightedX = 0.0
        var weightedY = 0.0
        var totalWeight = 0.0
        var salientCount = 0

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                val energy = cellEnergy[r][c]
                if (energy >= threshold) {
                    minCol = min(minCol, c)
                    maxCol = max(maxCol, c)
                    minRow = min(minRow, r)
                    maxRow = max(maxRow, r)

                    val normX = (c + 0.5f) / gridCols
                    val normY = (r + 0.5f) / gridRows
                    weightedX += normX * energy
                    weightedY += normY * energy
                    totalWeight += energy
                    salientCount++
                }
            }
        }

        val objects = mutableListOf<DetectedObjectEntity>()
        if (salientCount >= 3 && totalWeight > 0.0) {
            val centerX = (weightedX / totalWeight).toFloat().coerceIn(0.05f, 0.95f)
            val centerY = (weightedY / totalWeight).toFloat().coerceIn(0.05f, 0.95f)

            val normMinX = (minCol.toFloat() / gridCols).coerceIn(0.0f, 0.9f)
            val normMaxX = ((maxCol + 1).toFloat() / gridCols).coerceIn(0.1f, 1.0f)
            val normMinY = (minRow.toFloat() / gridRows).coerceIn(0.0f, 0.9f)
            val normMaxY = ((maxRow + 1).toFloat() / gridRows).coerceIn(0.1f, 1.0f)

            val normW = (normMaxX - normMinX).coerceIn(0.1f, 0.98f)
            val normH = (normMaxY - normMinY).coerceIn(0.1f, 0.98f)

            objects.add(
                DetectedObjectEntity(
                    trackingId = 1,
                    boundingBox = RectF(normMinX, normMinY, normMaxX, normMaxY),
                    normalizedCenterX = centerX,
                    normalizedCenterY = centerY,
                    normalizedWidth = normW,
                    normalizedHeight = normH,
                    labels = listOf(VisionLabel("Visible Foreground Contour", 0.70f)),
                    primaryLabel = "Visible Foreground Contour",
                    confidence = 0.70f
                )
            )
        }

        val latency = System.currentTimeMillis() - startTime
        return RawVisionInference(
            detectedObjects = objects,
            imageLabels = emptyList(),
            modelName = modelName,
            inferenceLatencyMs = latency,
            frameWidth = width,
            frameHeight = height
        )
    }

    override fun close() {}
}
