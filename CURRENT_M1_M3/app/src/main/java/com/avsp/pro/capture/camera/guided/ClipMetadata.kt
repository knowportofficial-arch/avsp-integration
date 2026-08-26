package com.avsp.pro.capture.camera.guided

import org.json.JSONObject

/**
 * M6.1 output metadata — one JSON sidecar per recorded clip.
 *
 * M6.1 correction (external audit): requested and actually-measured values are now kept
 * separate and explicitly labeled, instead of a single `width`/`height`/`fps` that silently
 * meant "requested." The original M6 top-level keys (`width`, `height`, `fps`, `orientation`)
 * are preserved for M7 backward compatibility, but now hold the MEASURED value when
 * measurement succeeded, falling back to the requested value with `dimensions_verified` /
 * `fps_verified` set to false when it didn't. M7 (and anyone else reading this file) MUST
 * check the `*_verified` flags before treating `width`/`height`/`fps` as ground truth.
 */
data class ClipMetadata(
    val clipId: String,
    /** Semantic shot name from the template / shot-plan (e.g. Intro, Scene 1). */
    val clipName: String,
    /** Shot code from the template / shot-plan (e.g. INTRO, WIDE). */
    val category: String,
    val date: String,           // yyyy-MM-dd
    val time: String,           // HH:mm:ss (24h, local)
    val durationMs: Long,

    // Best-known actual values: measured post-recording when possible, else the request.
    val width: Int,
    val height: Int,
    val fps: Int,                       // rounded from actualFps when verified, else requestedFps
    val orientation: String,            // "PORTRAIT" | "LANDSCAPE" — as REQUESTED by the template

    // What was requested (always present, always exactly what the template asked for).
    val requestedWidth: Int,
    val requestedHeight: Int,
    val requestedFps: Int,
    val requestedAspectRatio: String,   // "9:16" | "16:9"

    // What was actually measured post-recording (null when measurement was not possible).
    val actualFps: Double?,             // unrounded measured frame rate; null = unknown, NOT assumed
    val actualAspectRatio: String?,     // "9:16" | "16:9" | "OTHER"; null = could not classify
    val actualOrientation: String?,     // "PORTRAIT" | "LANDSCAPE"; derived from measured display dims

    // Explicit honesty flags — false means the corresponding top-level field is a fallback
    // to the requested value, NOT a confirmed measurement.
    val dimensionsVerified: Boolean,
    val fpsVerified: Boolean,
    val aspectRatioMatchesRequest: Boolean?, // null when actualAspectRatio is null

    val latitude: Double?,
    val longitude: Double?,
    val device: String,
    val file: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("clip_id", clipId)
        put("clip_name", clipName)
        put("category", category)
        put("date", date)
        put("time", time)
        put("duration_ms", durationMs)
        put("width", width)
        put("height", height)
        put("fps", fps)
        put("orientation", orientation)
        put("requested_width", requestedWidth)
        put("requested_height", requestedHeight)
        put("requested_fps", requestedFps)
        put("requested_aspect_ratio", requestedAspectRatio)
        put("actual_fps", actualFps ?: JSONObject.NULL)
        put("actual_aspect_ratio", actualAspectRatio ?: JSONObject.NULL)
        put("actual_orientation", actualOrientation ?: JSONObject.NULL)
        put("dimensions_verified", dimensionsVerified)
        put("fps_verified", fpsVerified)
        put("aspect_ratio_matches_request", aspectRatioMatchesRequest ?: JSONObject.NULL)
        put("latitude", latitude ?: JSONObject.NULL)
        put("longitude", longitude ?: JSONObject.NULL)
        put("device", device)
        put("file", file)
    }

    companion object {
        fun fromJson(json: JSONObject): ClipMetadata = ClipMetadata(
            clipId = json.getString("clip_id"),
            clipName = if (json.has("clip_name") && !json.isNull("clip_name")) {
                json.getString("clip_name")
            } else {
                json.optString("category", "")
            },
            category = json.getString("category"),
            date = json.getString("date"),
            time = json.getString("time"),
            durationMs = json.getLong("duration_ms"),
            width = json.getInt("width"),
            height = json.getInt("height"),
            fps = json.getInt("fps"),
            orientation = json.getString("orientation"),
            requestedWidth = json.getInt("requested_width"),
            requestedHeight = json.getInt("requested_height"),
            requestedFps = json.getInt("requested_fps"),
            requestedAspectRatio = json.getString("requested_aspect_ratio"),
            actualFps = if (json.isNull("actual_fps")) null else json.getDouble("actual_fps"),
            actualAspectRatio = if (json.isNull("actual_aspect_ratio")) null else json.getString("actual_aspect_ratio"),
            actualOrientation = if (json.isNull("actual_orientation")) null else json.getString("actual_orientation"),
            dimensionsVerified = json.getBoolean("dimensions_verified"),
            fpsVerified = json.getBoolean("fps_verified"),
            aspectRatioMatchesRequest = if (json.isNull("aspect_ratio_matches_request")) null else json.getBoolean("aspect_ratio_matches_request"),
            latitude = if (json.isNull("latitude")) null else json.getDouble("latitude"),
            longitude = if (json.isNull("longitude")) null else json.getDouble("longitude"),
            device = json.getString("device"),
            file = json.getString("file")
        )
    }
}
