package com.avsp.creator.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

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
    val qualityScore: Int = 85,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val createdAt: Long = System.currentTimeMillis()
)
