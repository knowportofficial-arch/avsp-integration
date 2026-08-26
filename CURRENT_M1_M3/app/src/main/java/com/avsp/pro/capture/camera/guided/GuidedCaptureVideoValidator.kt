package com.avsp.pro.capture.camera.guided

import java.io.File
import java.io.RandomAccessFile

/**
 * Validates that a Guided Capture video file is a usable MP4 container with a readable
 * video track — not merely a non-zero / non-empty file.
 *
 * Real-device failures showed that ERROR_SOURCE_INACTIVE recovery and "saved" DB rows
 * can still leave unplayable truncated containers. Size alone is insufficient.
 */
object GuidedCaptureVideoValidator {

    sealed class Result {
        data class Playable(
            val inspected: ClipInspector.InspectedVideo,
            val durationMs: Long,
            val sizeBytes: Long
        ) : Result()

        data class Unplayable(val reason: String) : Result()
    }

    fun validate(
        videoFile: File,
        inspectVideo: (File) -> ClipInspector.InspectedVideo? = { ClipInspector.inspectVideo(it) },
        probeDurationMs: (File) -> Long? = { ClipInspector.probeDurationMs(it) }
    ): Result {
        if (!videoFile.exists()) return Result.Unplayable("file does not exist")
        val size = videoFile.length()
        if (size < MIN_FILE_BYTES) {
            return Result.Unplayable("file too small ($size bytes)")
        }
        if (!hasMp4FtypBrand(videoFile)) {
            return Result.Unplayable("missing MP4 ftyp brand")
        }

        val inspected = inspectVideo(videoFile)
            ?: return Result.Unplayable("no readable video track")

        if (inspected.encodedWidth <= 0 || inspected.encodedHeight <= 0) {
            return Result.Unplayable("invalid video dimensions")
        }
        if (inspected.framesScanned < 2L) {
            return Result.Unplayable("fewer than 2 video samples")
        }

        val durationMs = probeDurationMs(videoFile)
            ?: estimatedDurationMs(inspected)
        if (durationMs < MIN_PLAYABLE_DURATION_MS) {
            return Result.Unplayable("duration too short (${durationMs}ms)")
        }

        return Result.Playable(
            inspected = inspected,
            durationMs = durationMs,
            sizeBytes = size
        )
    }

    fun isPlayable(
        videoFile: File,
        inspectVideo: (File) -> ClipInspector.InspectedVideo? = { ClipInspector.inspectVideo(it) },
        probeDurationMs: (File) -> Long? = { ClipInspector.probeDurationMs(it) }
    ): Boolean = validate(videoFile, inspectVideo, probeDurationMs) is Result.Playable

    /**
     * ISO BMFF: first box is typically `ftyp`. Accept files whose first 32 bytes contain
     * the ASCII brand marker — rejects raw/truncated dumps that lack a container header.
     */
    fun hasMp4FtypBrand(videoFile: File): Boolean {
        return try {
            RandomAccessFile(videoFile, "r").use { raf ->
                if (raf.length() < 12L) return false
                val header = ByteArray(32)
                val read = raf.read(header)
                if (read < 8) return false
                val asString = header.copyOf(read).toString(Charsets.US_ASCII)
                asString.contains("ftyp")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun estimatedDurationMs(inspected: ClipInspector.InspectedVideo): Long {
        val fps = inspected.measuredFps ?: return 0L
        if (fps <= 0.0 || inspected.framesScanned <= 1L) return 0L
        return ((inspected.framesScanned - 1L) * 1000.0 / fps).toLong()
    }

    /** Absolute floor — smaller than this cannot be a useful guided clip. */
    const val MIN_FILE_BYTES = 512L

    const val MIN_PLAYABLE_DURATION_MS = 200L
}
