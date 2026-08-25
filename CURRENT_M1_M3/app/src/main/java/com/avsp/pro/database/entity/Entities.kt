package com.avsp.pro.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val projectId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val duration: Long?,
    val aspectRatio: String,
    val language: String,
    val outputPath: String?,
    val metadataJson: String
)

@Entity(tableName = "module_status")
data class ModuleStatusEntity(
    @PrimaryKey val moduleId: String,
    val displayName: String,
    val version: String,
    val status: String,
    val lastUpdated: Long,
    val error: String?
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val level: String,
    val module: String,
    val message: String,
    val details: String?,
    val projectId: String?
)

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val assetId: String,
    val projectId: String,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val createdAt: Long,
    val tagsJson: String,
    val metadataJson: String
)
