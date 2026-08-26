package com.avsp.pro.dataset.api

/**
 * Future M4-A editor selection surface.
 * This phase exposes the contract only — M4-A is not implemented.
 */
data class EditorMediaSelection(
    val mediaId: String,
    val projectId: String,
    val uriString: String,
    val mediaType: String,
    val durationSeconds: Long,
    val width: Int,
    val height: Int,
    val qualityScoreNormalized: Double,
    val recommendation: String,
    val isBestShot: Boolean,
    val userSelected: Boolean = false,
    val displayName: String = "",
    val thumbnailPath: String? = null
)

interface EditorSelectionProvider {
    suspend fun listSelectableMedia(projectId: String): List<EditorMediaSelection>
    suspend fun listBestKeepMedia(projectId: String): List<EditorMediaSelection>
}
