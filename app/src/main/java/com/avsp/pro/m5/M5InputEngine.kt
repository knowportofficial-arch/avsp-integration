package com.avsp.pro.m5

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.Locale
import java.util.regex.Pattern

data class M5InputResult(
    val source: String,
    val reference: String,
    val title: String = "",
    val durationSeconds: Long = 0L,
    val text: String = "",
    val numbers: List<Double> = emptyList(),
    val confidence: Double = 0.0,
    val processedAt: Long = System.currentTimeMillis()
)

object M5InputEngine {
    private val youtube = Pattern.compile("(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|shorts/|embed/))([A-Za-z0-9_-]{6,})")
    private val numberPattern = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?")

    fun parseYouTubeUrl(url: String): String? = youtube.matcher(url.trim()).takeIf { it.find() }?.group(1)

    fun analyzeText(source: String, reference: String, text: String): M5InputResult {
        val nums = numberPattern.matcher(text).let { m ->
            buildList { while (m.find()) m.group().replace(",", "").toDoubleOrNull()?.let(::add) }.distinct()
        }
        return M5InputResult(source, reference, text = text.trim(), numbers = nums, confidence = if (text.isBlank()) 0.0 else 0.9)
    }

    fun analyzeLocalMedia(context: Context, uri: Uri): M5InputResult {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000) ?: 0L
            M5InputResult("screen", uri.toString(), durationSeconds = duration, confidence = if (duration > 0) 0.8 else 0.4)
        } finally { r.release() }
    }
}
