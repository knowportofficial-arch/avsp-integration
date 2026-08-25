package com.avsp.creator.dataset.model

/**
 * M7 view of a media item. Built from MediaEntity + quality sidecar fields.
 * Original file is never deleted by M7.
 */
data class DatasetMedia(
    val clipId: String,
    val fileUri: String,
    val mediaType: String,             // PHOTO | VIDEO
    val category: String,
    val date: String?,                 // yyyy-MM-dd
    val time: String?,                 // HH:mm:ss
    val locationLat: Double?,
    val locationLng: Double?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val orientation: String,           // PORTRAIT | LANDSCAPE | UNKNOWN
    val tags: List<String>,
    val thumbnailUri: String?,
    val qualityScore: Double,          // 0.0 .. 1.0
    val qualityResult: QualityResult?,
    val isBestShot: Boolean,
    val missionId: String?,
    val missionShotId: String?,
    val projectId: String,
    val displayName: String,
    val createdAt: Long,
    val fileSizeBytes: Long = 0L
)
