package com.avsp.pro.m7.dataset.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.avsp.pro.m7.database.entity.MediaEntity
import kotlin.math.abs
import kotlin.math.min

/**
 * Local duplicate detection for M7.
 * Uses a lightweight average-hash (aHash) on a 8x8 grayscale grid.
 * Original files are never deleted — only flagged.
 */
class DuplicateDetector(private val context: Context) {

    data class HashResult(val hash: Long, val width: Int, val height: Int, val sizeBytes: Long)

    fun computeHash(uriString: String, mediaType: String, sizeBytes: Long = 0L): HashResult? {
        val bitmap = loadSmallBitmap(uriString, mediaType) ?: return null
        return try {
            val hash = averageHash(bitmap)
            HashResult(hash, bitmap.width, bitmap.height, sizeBytes)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Returns true if [candidate] is considered a near-duplicate of any item in [existing]
     * that shares the same missionShotId (or same project when mission is null).
     */
    fun isDuplicateOf(
        candidateUri: String,
        candidateType: String,
        candidateSize: Long,
        existing: List<MediaEntity>,
        missionShotId: String?
    ): Boolean {
        val candHash = computeHash(candidateUri, candidateType, candidateSize) ?: return false
        val peers = existing.filter { entity ->
            if (missionShotId != null) {
                entity.missionShotId == missionShotId
            } else {
                // Same project, same media type, within time window is handled by caller;
                // here we only compare hash among provided list.
                entity.mediaType == candidateType
            }
        }
        for (peer in peers) {
            if (peer.uriString == candidateUri) continue
            val peerHash = computeHash(peer.uriString, peer.mediaType, peer.fileSizeBytes) ?: continue
            val distance = hammingDistance(candHash.hash, peerHash.hash)
            if (distance <= HAMMING_THRESHOLD) return true
            // Also treat identical dimensions + near-identical size as soft duplicate signal
            if (candHash.width == peerHash.width &&
                candHash.height == peerHash.height &&
                candidateSize > 0 && peer.fileSizeBytes > 0 &&
                abs(candidateSize - peer.fileSizeBytes) < candidateSize * 0.02
            ) {
                if (distance <= HAMMING_THRESHOLD + 2) return true
            }
        }
        return false
    }

    private fun loadSmallBitmap(uriString: String, mediaType: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            if (mediaType.equals("VIDEO", ignoreCase = true)) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, HASH_SIZE, HASH_SIZE, true) }
                } finally {
                    retriever.release()
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 16
                    }
                    val decoded = BitmapFactory.decodeStream(stream, null, opts) ?: return null
                    Bitmap.createScaledBitmap(decoded, HASH_SIZE, HASH_SIZE, true).also {
                        if (it != decoded && !decoded.isRecycled) decoded.recycle()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun averageHash(bitmap: Bitmap): Long {
        val size = HASH_SIZE
        var sum = 0.0
        val lum = DoubleArray(size * size)
        var i = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val px = bitmap.getPixel(
                    min(x, bitmap.width - 1),
                    min(y, bitmap.height - 1)
                )
                val v = 0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)
                lum[i++] = v
                sum += v
            }
        }
        val avg = sum / lum.size
        var hash = 0L
        for (idx in lum.indices) {
            if (lum[idx] >= avg) {
                hash = hash or (1L shl idx)
            }
        }
        return hash
    }

    private fun hammingDistance(a: Long, b: Long): Int {
        var x = a xor b
        var count = 0
        while (x != 0L) {
            count += (x and 1L).toInt()
            x = x ushr 1
        }
        return count
    }

    companion object {
        private const val HASH_SIZE = 8
        /** Max Hamming distance (of 64 bits) to still consider near-duplicate. */
        private const val HAMMING_THRESHOLD = 8
    }
}
