package com.avsp.pro.m7.dataset.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.avsp.pro.m7.dataset.model.QualityResult
import com.avsp.pro.m7.dataset.model.Recommendation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device quality analyzer for M7.
 * Uses pure bitmap / MediaMetadataRetriever heuristics — no cloud required.
 *
 * Capabilities:
 * - Blur detection via Laplacian variance
 * - Exposure via luminance histogram
 * - Composition via rule-of-thirds edge density heuristic
 * - Face quality / closed-eye: best-effort (returns null when unreliable)
 * - Duplicate flag is supplied by caller (DuplicateDetector)
 */
class LocalQualityAnalyzer(private val context: Context) {

    fun analyze(
        uriString: String,
        mediaType: String,
        isDuplicate: Boolean = false
    ): QualityResult {
        val bitmap = loadPreviewBitmap(uriString, mediaType)
            ?: return QualityResult.analysisFailed()

        return try {
            val blurScore = measureSharpness(bitmap)          // higher = sharper
            val exposureOk = isExposureAcceptable(bitmap)
            val compositionOk = isCompositionAcceptable(bitmap)
            val isBlurred = blurScore < BLUR_THRESHOLD

            // Face metrics left null unless a reliable face detector is wired later.
            // Spec allows null when not technically reliable.
            val faceQuality: Double? = null
            val closedEye: Boolean? = null

            val score = computeOverallScore(
                sharpness = blurScore,
                exposureOk = exposureOk,
                compositionOk = compositionOk,
                isDuplicate = isDuplicate
            )

            // Single source of truth for KEEP / REVIEW / RETAKE.
            QualityResult.fromMetrics(
                score = score,
                blur = isBlurred,
                exposure = exposureOk,
                composition = compositionOk,
                faceQuality = faceQuality,
                closedEye = closedEye,
                duplicate = isDuplicate,
                analysisFailed = false
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun loadPreviewBitmap(uriString: String, mediaType: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            if (mediaType.equals("VIDEO", ignoreCase = true)) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { scaleDown(it, MAX_ANALYZE_DIM) }
                } finally {
                    retriever.release()
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(uriString, MAX_ANALYZE_DIM)
                    }
                    BitmapFactory.decodeStream(stream, null, options)?.let {
                        scaleDown(it, MAX_ANALYZE_DIM)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(uriString: String, maxDim: Int): Int {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                var sample = 1
                var w = opts.outWidth
                var h = opts.outHeight
                while (w / sample > maxDim || h / sample > maxDim) {
                    sample *= 2
                }
                sample
            } ?: 4
        } catch (_: Exception) {
            4
        }
    }

    private fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val nw = max(1, (w * scale).toInt())
        val nh = max(1, (h * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
        if (scaled != src && !src.isRecycled) src.recycle()
        return scaled
    }

    /**
     * Laplacian-variance style sharpness. Higher = sharper.
     * Sampled on a grid for speed.
     */
    private fun measureSharpness(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return 0.0

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        val stepX = max(1, w / 64)
        val stepY = max(1, h / 64)

        var y = 1
        while (y < h - 1) {
            var x = 1
            while (x < w - 1) {
                val c = luminance(bitmap.getPixel(x, y))
                val l = luminance(bitmap.getPixel(x - 1, y))
                val r = luminance(bitmap.getPixel(x + 1, y))
                val u = luminance(bitmap.getPixel(x, y - 1))
                val d = luminance(bitmap.getPixel(x, y + 1))
                val lap = abs(-4 * c + l + r + u + d)
                sum += lap
                sumSq += lap * lap
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return 0.0
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return max(0.0, variance)
    }

    private fun isExposureAcceptable(bitmap: Bitmap): Boolean {
        val hist = IntArray(256)
        val w = bitmap.width
        val h = bitmap.height
        val stepX = max(1, w / 48)
        val stepY = max(1, h / 48)
        var samples = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val lum = luminance(bitmap.getPixel(x, y)).toInt().coerceIn(0, 255)
                hist[lum]++
                samples++
                x += stepX
            }
            y += stepY
        }
        if (samples == 0) return true

        // Clip extremes: too many pure black or pure white → bad exposure
        val dark = hist.slice(0..15).sum().toDouble() / samples
        val bright = hist.slice(240..255).sum().toDouble() / samples
        if (dark > 0.45 || bright > 0.40) return false

        // Mean luminance in reasonable range
        var weighted = 0.0
        for (i in 0..255) weighted += i * hist[i]
        val mean = weighted / samples
        return mean in 40.0..215.0
    }

    /**
     * Simple composition heuristic: edge energy near rule-of-thirds intersections
     * should not be zero; overall edge density should be moderate.
     */
    private fun isCompositionAcceptable(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return true

        val thirdX = w / 3
        val twoThirdX = 2 * w / 3
        val thirdY = h / 3
        val twoThirdY = 2 * h / 3

        fun localEdgeEnergy(cx: Int, cy: Int, radius: Int): Double {
            var energy = 0.0
            var n = 0
            for (dy in -radius..radius step 2) {
                for (dx in -radius..radius step 2) {
                    val x = (cx + dx).coerceIn(1, w - 2)
                    val y = (cy + dy).coerceIn(1, h - 2)
                    val c = luminance(bitmap.getPixel(x, y))
                    val r = luminance(bitmap.getPixel(x + 1, y))
                    val d = luminance(bitmap.getPixel(x, y + 1))
                    energy += abs(c - r) + abs(c - d)
                    n++
                }
            }
            return if (n == 0) 0.0 else energy / n
        }

        val points = listOf(
            localEdgeEnergy(thirdX, thirdY, 6),
            localEdgeEnergy(twoThirdX, thirdY, 6),
            localEdgeEnergy(thirdX, twoThirdY, 6),
            localEdgeEnergy(twoThirdX, twoThirdY, 6)
        )
        val maxPoint = points.maxOrNull() ?: 0.0
        // Extremely flat image (no structure at thirds) → weak composition
        return maxPoint > 4.0
    }

    private fun computeOverallScore(
        sharpness: Double,
        exposureOk: Boolean,
        compositionOk: Boolean,
        isDuplicate: Boolean
    ): Double {
        // Normalize sharpness: typical variance ranges ~0–800 for our sampling
        val sharpNorm = (sharpness / 400.0).coerceIn(0.0, 1.0)
        var score = 0.45 * sharpNorm
        score += if (exposureOk) 0.30 else 0.05
        score += if (compositionOk) 0.20 else 0.05
        if (isDuplicate) score *= 0.6
        return score
    }

    private fun luminance(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    companion object {
        private const val MAX_ANALYZE_DIM = 320
        /** Laplacian variance below this is considered blurred. Tuned for downscaled previews. */
        private const val BLUR_THRESHOLD = 18.0
    }
}
