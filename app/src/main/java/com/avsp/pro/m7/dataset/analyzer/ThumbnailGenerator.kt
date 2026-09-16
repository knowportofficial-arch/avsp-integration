package com.avsp.pro.m7.dataset.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.util.Size
import java.io.File
import java.io.FileOutputStream

/**
 * Local thumbnail generation / optimization for M7 media library.
 * Writes JPEG thumbnails under app-private files dir. Never touches originals.
 */
class ThumbnailGenerator(private val context: Context) {

    fun generate(
        clipId: String,
        uriString: String,
        mediaType: String,
        maxSide: Int = DEFAULT_MAX_SIDE
    ): String? {
        return try {
            val srcUri = Uri.parse(uriString)
            val bitmap = if (mediaType.equals("VIDEO", ignoreCase = true)) {
                extractVideoFrame(srcUri)
            } else {
                decodeScaled(srcUri, maxSide)
            } ?: return null

            val scaled = if (bitmap.width > maxSide || bitmap.height > maxSide) {
                val ratio = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height)
                val nw = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val nh = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                ThumbnailUtils.extractThumbnail(bitmap, nw, nh).also {
                    if (it != bitmap && !bitmap.isRecycled) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val outFile = File(dir, "${clipId}_thumb.jpg")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            if (!scaled.isRecycled) scaled.recycle()
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVideoFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        return try {
            // Bounds
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while ((bounds.outWidth / sample) > maxSide * 2 ||
                (bounds.outHeight / sample) > maxSide * 2
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_MAX_SIDE = 320
    }
}
