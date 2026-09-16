package com.avsp.pro.database

import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.core.module.ModuleStatus
import com.avsp.pro.database.entity.LogEntity
import com.avsp.pro.database.entity.MediaAssetEntity
import com.avsp.pro.database.entity.ModuleStatusEntity
import com.avsp.pro.database.entity.ProjectEntity
import com.avsp.pro.logs.LogEntry
import com.avsp.pro.logs.LogLevel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object EntityMappers {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type
    private val listType = object : TypeToken<List<String>>() {}.type

    fun toDomain(entity: ProjectEntity): Project = Project(
        projectId = entity.projectId,
        name = entity.name,
        description = entity.description,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        status = ProjectStatus.fromRaw(entity.status),
        duration = entity.duration,
        aspectRatio = AspectRatio.fromLabel(entity.aspectRatio),
        language = ProjectLanguage.fromCode(entity.language),
        outputPath = entity.outputPath,
        metadata = gson.fromJson(entity.metadataJson, mapType) ?: emptyMap()
    )

    fun toEntity(project: Project): ProjectEntity = ProjectEntity(
        projectId = project.projectId,
        name = project.name,
        description = project.description,
        createdAt = project.createdAt,
        updatedAt = project.updatedAt,
        status = project.status.name,
        duration = project.duration,
        aspectRatio = project.aspectRatio.label,
        language = project.language.code,
        outputPath = project.outputPath,
        metadataJson = gson.toJson(project.metadata)
    )

    fun toDomain(entity: ModuleStatusEntity): ModuleStatus = ModuleStatus(
        moduleId = entity.moduleId,
        displayName = entity.displayName,
        version = entity.version,
        status = ModuleRunStatus.fromRaw(entity.status),
        lastUpdated = entity.lastUpdated,
        error = entity.error
    )

    fun toEntity(status: ModuleStatus): ModuleStatusEntity = ModuleStatusEntity(
        moduleId = status.moduleId,
        displayName = status.displayName,
        version = status.version,
        status = status.status.name,
        lastUpdated = status.lastUpdated,
        error = status.error
    )

    fun toDomain(entity: LogEntity): LogEntry = LogEntry(
        id = entity.id,
        timestamp = entity.timestamp,
        level = LogLevel.fromRaw(entity.level),
        module = entity.module,
        message = entity.message,
        details = entity.details,
        projectId = entity.projectId
    )

    fun toEntity(entry: LogEntry): LogEntity = LogEntity(
        id = entry.id,
        timestamp = entry.timestamp,
        level = entry.level.name,
        module = entry.module,
        message = entry.message,
        details = entry.details,
        projectId = entry.projectId
    )

    fun toDomain(entity: MediaAssetEntity): MediaAsset = MediaAsset(
        assetId = entity.assetId,
        projectId = entity.projectId,
        fileName = entity.fileName,
        relativePath = entity.relativePath,
        mimeType = entity.mimeType,
        sizeBytes = entity.sizeBytes,
        durationMs = entity.durationMs,
        width = entity.width,
        height = entity.height,
        createdAt = entity.createdAt,
        tags = gson.fromJson(entity.tagsJson, listType) ?: emptyList(),
        metadata = gson.fromJson(entity.metadataJson, mapType) ?: emptyMap()
    )

    fun toEntity(asset: MediaAsset): MediaAssetEntity = MediaAssetEntity(
        assetId = asset.assetId,
        projectId = asset.projectId,
        fileName = asset.fileName,
        relativePath = asset.relativePath,
        mimeType = asset.mimeType,
        sizeBytes = asset.sizeBytes,
        durationMs = asset.durationMs,
        width = asset.width,
        height = asset.height,
        createdAt = asset.createdAt,
        tagsJson = gson.toJson(asset.tags),
        metadataJson = gson.toJson(asset.metadata)
    )
}
