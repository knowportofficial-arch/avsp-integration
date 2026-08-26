package com.avsp.pro.dataset.api

import com.avsp.pro.capture.database.dao.MediaDao
import com.avsp.pro.capture.database.entity.MediaEntity
import com.avsp.pro.dataset.model.Recommendation

/**
 * Bridges capture Room rows to the future M4-A editor selection contract.
 */
class DefaultEditorSelectionProvider(
    private val mediaDao: MediaDao
) : EditorSelectionProvider {

    override suspend fun listSelectableMedia(projectId: String): List<EditorMediaSelection> =
        mediaDao.getMediaForProject(projectId).map { it.toSelection(userSelected = false) }

    override suspend fun listBestKeepMedia(projectId: String): List<EditorMediaSelection> =
        mediaDao.getMediaForProject(projectId)
            .filter {
                it.isBestShot ||
                    it.recommendation.equals(Recommendation.KEEP.name, ignoreCase = true)
            }
            .map { it.toSelection(userSelected = it.isBestShot) }

    private fun MediaEntity.toSelection(userSelected: Boolean) = EditorMediaSelection(
        mediaId = id,
        projectId = projectId,
        uriString = uriString,
        mediaType = mediaType,
        durationSeconds = durationSeconds,
        width = width,
        height = height,
        qualityScoreNormalized = qualityScoreNormalized,
        recommendation = recommendation,
        isBestShot = isBestShot,
        userSelected = userSelected,
        displayName = displayName,
        thumbnailPath = thumbnailPath
    )
}
