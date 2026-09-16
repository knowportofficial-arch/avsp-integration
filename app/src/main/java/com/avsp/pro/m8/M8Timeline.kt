package com.avsp.pro.m8

data class M8VisualEvent(val start: Double, val end: Double, val path: String, val type: String = "video")
data class M8CaptionEvent(val start: Double, val end: Double, val text: String, val emphasis: Boolean = false)
data class M8Timeline(
    val projectId: String,
    val format: String = "9:16",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Double = 30.0,
    val duration: Double = 0.0,
    val visuals: List<M8VisualEvent> = emptyList(),
    val captions: List<M8CaptionEvent> = emptyList(),
    val musicPath: String? = null,
    val ctaStart: Double? = null,
    val ctaEnd: Double? = null
) {
    fun validateCta(tolerance: Double = 0.15): Boolean = ctaStart != null && ctaEnd != null && kotlin.math.abs((ctaEnd!! - ctaStart!!) - 5.0) <= tolerance
    fun validate(): List<String> = buildList {
        if (projectId.isBlank()) add("project_id is required")
        if (width <= 0 || height <= 0) add("invalid canvas dimensions")
        if (fps <= 0) add("fps must be positive")
        if (duration < 0) add("duration cannot be negative")
        if (ctaStart != null && ctaEnd != null && !validateCta()) add("CTA duration must be 5.0 ± 0.15s")
    }
}
