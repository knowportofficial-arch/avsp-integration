package com.avsp.pro.capture.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Project media entity.
 * Extended for M7 Personal Dataset & AI Vision while remaining backward-compatible
 * with M6 capture writes (new fields have defaults).
 *
 * ORIGINAL MEDIA RULE: M7 never auto-deletes the underlying file.
 */
@Entity(tableName = "project_media")
data class MediaEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val uriString: String,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val displayName: String,
    val shotType: String = "WIDE",
    val missionId: String? = null,
    val missionShotId: String? = null,
    val durationSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    /** Legacy 0–100 integer score used by M6; M7 also writes 0–100. Default 0 = unanalyzed. */
    val qualityScore: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // ---- M7 fields (defaults preserve M6 rows) ----
    val category: String = "Uncategorized",
    val tagsCsv: String = "",
    val thumbnailPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val orientation: String = "UNKNOWN",
    val dateString: String? = null,
    val timeString: String? = null,
    /** 0.0–1.0 stored as stringified double for Room simplicity, or use qualityScore/100. */
    val qualityScoreNormalized: Double = 0.0,
    val blurDetected: Boolean = false,
    val exposureOk: Boolean = true,
    val compositionOk: Boolean = true,
    val faceQuality: Double? = null,
    val closedEye: Boolean? = null,
    val isDuplicate: Boolean = false,
    val recommendation: String = "REVIEW",  // unanalyzed must never default to KEEP
    val isBestShot: Boolean = false,
    val perceptualHash: Long? = null
) {
    fun tagsList(): List<String> =
        if (tagsCsv.isBlank()) emptyList()
        else tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
