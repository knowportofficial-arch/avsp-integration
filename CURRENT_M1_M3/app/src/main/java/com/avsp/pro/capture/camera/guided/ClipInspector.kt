package com.avsp.pro.capture.camera.guided

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * M6.1 — inspects an actually-recorded clip to report what CameraX/the device really
 * produced, as opposed to what was requested. Added in response to the external audit:
 * "The metadata must represent what was actually produced, not merely what was requested."
 *
 * This does real work against the encoded file (via [MediaExtractor]/[MediaFormat] for
 * video, [BitmapFactory] bounds decoding for photos) -- it does not assume the request
 * succeeded.
 */
object ClipInspector {

    data class InspectedVideo(
        /** Raw encoded track dimensions, before accounting for the rotation flag. */
        val encodedWidth: Int,
        val encodedHeight: Int,
        /** Rotation metadata (0/90/180/270) the container asks players to apply. */
        val rotationDegrees: Int,
        /**
         * Frame rate measured by walking the track's actual sample timestamps
         * (not the container's declared/nominal rate, which can be absent or wrong).
         * Null if the track could not be read or had fewer than 2 samples.
         */
        val measuredFps: Double?,
        val framesScanned: Long
    ) {
        /** Dimensions as they will actually appear after a player applies [rotationDegrees]. */
        val displayWidth: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) encodedHeight else encodedWidth
        val displayHeight: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) encodedWidth else encodedHeight
    }

    /**
     * Scans every sample in the first video track to measure real frame rate from actual
     * timestamps. Safety-capped at [maxFramesToScan] samples for pathologically long files;
     * guided-capture clips are short (seconds), so in practice the whole clip is scanned.
     * Returns null if the file has no readable video track (e.g. write failed, corrupt file,
     * or codec not supported for extraction on this device).
     */
    fun inspectVideo(videoFile: File, maxFramesToScan: Int = 5000): InspectedVideo? {
        if (!videoFile.exists() || videoFile.length() == 0L) return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(videoFile.absolutePath)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            val fmt = format ?: return null
            if (trackIndex < 0) return null
            if (!fmt.containsKey(MediaFormat.KEY_WIDTH) || !fmt.containsKey(MediaFormat.KEY_HEIGHT)) return null

            val width = fmt.getInteger(MediaFormat.KEY_WIDTH)
            val height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0

            extractor.selectTrack(trackIndex)
            var frameCount = 0L
            var firstSampleTimeUs = -1L
            var lastSampleTimeUs = -1L
            while (frameCount < maxFramesToScan) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break
                if (firstSampleTimeUs < 0) firstSampleTimeUs = sampleTime
                lastSampleTimeUs = sampleTime
                frameCount++
                if (!extractor.advance()) break
            }

            val measuredFps = if (frameCount > 1 && lastSampleTimeUs > firstSampleTimeUs) {
                val elapsedSeconds = (lastSampleTimeUs - firstSampleTimeUs) / 1_000_000.0
                (frameCount - 1) / elapsedSeconds
            } else {
                null
            }

            InspectedVideo(
                encodedWidth = width,
                encodedHeight = height,
                rotationDegrees = rotation,
                measuredFps = measuredFps,
                framesScanned = frameCount
            )
        } catch (_: Exception) {
            null
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    data class InspectedPhoto(val width: Int, val height: Int)

    /**
     * Container duration in milliseconds via [MediaMetadataRetriever].
     * Null when the file cannot be opened or reports a non-positive duration.
     */
    fun probeDurationMs(videoFile: File): Long? {
        if (!videoFile.exists() || videoFile.length() == 0L) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            duration?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Reads actual JPEG dimensions without decoding the full bitmap into memory. */
    fun inspectPhoto(photoFile: File): InspectedPhoto? {
        if (!photoFile.exists() || photoFile.length() == 0L) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) null
            else InspectedPhoto(options.outWidth, options.outHeight)
        } catch (_: Exception) {
            null
        }
    }
}
