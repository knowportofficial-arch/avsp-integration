package com.avsp.pro.capture.camera.guided

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M6 — local storage layout for guided-capture output.
 *
 * Each clip gets its own folder so MP4 + metadata JSON + thumbnail travel together as one
 * unit for M7 to consume:
 *
 *   <app-external-files>/AVSP/Guided/<templateId>/<clip_id>/
 *       clip.mp4
 *       metadata.json
 *       thumbnail.jpg
 *
 * App-specific external storage is used (not MediaStore) so the three output files for a
 * clip are guaranteed to live together and require no storage permission on API 26+.
 */
class GuidedCaptureStorage(private val context: Context) {

    /** Result of a storage-directory preparation attempt. Never throws; check [StorageResult]. */
    sealed class StorageResult {
        data class Ready(val clipDir: File, val videoFile: File, val jsonFile: File, val thumbFile: File) : StorageResult()
        data class Failed(val reason: String) : StorageResult()
    }

    fun prepareClipStorage(templateId: String, clipId: String): StorageResult {
        return try {
            val root = context.getExternalFilesDir(null)
                ?: return StorageResult.Failed("External storage is unavailable on this device.")

            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                return StorageResult.Failed("External storage is not mounted (state=${Environment.getExternalStorageState()}).")
            }

            val clipDir = File(root, "AVSP/Guided/$templateId/$clipId")
            if (!clipDir.exists() && !clipDir.mkdirs()) {
                return StorageResult.Failed("Could not create output directory: ${clipDir.absolutePath}")
            }

            val freeBytes = clipDir.usableSpace
            val minimumRequiredBytes = 20L * 1024 * 1024 // 20MB safety floor for a short clip
            if (freeBytes < minimumRequiredBytes) {
                return StorageResult.Failed("Insufficient storage space (${freeBytes / (1024 * 1024)}MB free).")
            }

            StorageResult.Ready(
                clipDir = clipDir,
                videoFile = File(clipDir, "clip.mp4"),
                jsonFile = File(clipDir, "metadata.json"),
                thumbFile = File(clipDir, "thumbnail.jpg")
            )
        } catch (e: Exception) {
            StorageResult.Failed(e.message ?: "Unknown storage error")
        }
    }

    /** Extracts a JPEG thumbnail from the recorded MP4 at roughly its midpoint. Best-effort. */
    fun writeThumbnail(videoFile: File, thumbFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) * 1000L
            val frameTimeUs = if (durationUs > 0) durationUs / 2 else 0L
            val frame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return false
            FileOutputStream(thumbFile).use { out ->
                frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun writeMetadataJson(jsonFile: File, metadata: ClipMetadata): Boolean {
        return try {
            jsonFile.writeText(metadata.toJson().toString(2))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    companion object {
        fun currentDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        fun currentTime(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}
