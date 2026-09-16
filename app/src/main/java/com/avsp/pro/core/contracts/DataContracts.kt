package com.avsp.pro.core.contracts

import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus

/**
 * Stable M1 project contract. Future modules attach artifacts via references — not by mutating internals.
 */
data class Project(
    val projectId: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val duration: Long? = null,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val language: ProjectLanguage = ProjectLanguage.ENGLISH,
    val outputPath: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Generic media asset reference used by future camera/dataset/video modules.
 */
data class MediaAsset(
    val assetId: String,
    val projectId: String,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Script artifact reference. M2 produces the actual script.json content.
 */
data class ScriptReference(
    val scriptId: String,
    val projectId: String,
    val relativePath: String,
    val language: ProjectLanguage,
    val version: Int = 1,
    val createdAt: Long,
    val status: ArtifactStatus = ArtifactStatus.PENDING
)

/**
 * Audio artifact reference. M3 produces voice.mp3 / voice.json.
 */
data class AudioAsset(
    val audioId: String,
    val projectId: String,
    val relativePath: String,
    val language: ProjectLanguage,
    val durationMs: Long? = null,
    val voiceProfile: String? = null,
    val createdAt: Long,
    val status: ArtifactStatus = ArtifactStatus.PENDING
)

/**
 * Video artifact reference. M4 produces final.mp4.
 */
data class VideoAsset(
    val videoId: String,
    val projectId: String,
    val relativePath: String,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val durationMs: Long? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val createdAt: Long,
    val status: ArtifactStatus = ArtifactStatus.PENDING
)

/**
 * Publishing target reference. M9 consumes final video + metadata.
 */
data class PublishingReference(
    val publishId: String,
    val projectId: String,
    val platform: String,
    val videoRelativePath: String,
    val title: String,
    val description: String = "",
    val language: ProjectLanguage,
    val status: ArtifactStatus = ArtifactStatus.PENDING,
    val remoteId: String? = null,
    val publishedAt: Long? = null
)

enum class ArtifactStatus {
    PENDING,
    READY,
    PROCESSING,
    SUCCESS,
    FAILED
}
