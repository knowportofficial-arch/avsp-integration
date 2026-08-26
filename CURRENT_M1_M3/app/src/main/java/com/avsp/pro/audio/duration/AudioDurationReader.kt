package com.avsp.pro.audio.duration

import android.media.MediaMetadataRetriever
import com.avsp.pro.audio.wav.WavEncoder
import java.io.File

/**
 * Measures actual media duration. Prefers WAV PCM math; falls back to the Android retriever.
 */
object AudioDurationReader {

    fun fromBytes(bytes: ByteArray, tempHintFile: File? = null): Long {
        val wavMs = fromWavBytes(bytes)
        if (wavMs != null && wavMs > 0L) return wavMs
        val file = tempHintFile
        if (file != null && file.exists()) {
            val fileMs = fromFile(file.absolutePath)
            if (fileMs != null && fileMs > 0L) return fileMs
        }
        return 0L
    }

    fun fromWavBytes(bytes: ByteArray): Long? {
        if (bytes.size < 44) return null
        val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
        if (riff != "RIFF") return null
        return WavEncoder.durationMsForPcmBytes(bytes.size - 44).coerceAtLeast(1L)
    }

    fun fromFile(absolutePath: String): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            raw?.toLongOrNull()?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
