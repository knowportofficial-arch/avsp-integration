package com.avsp.creator.dataset.api

import com.avsp.creator.dataset.model.DatasetMedia
import com.avsp.creator.dataset.repository.DatasetRepository

/**
 * M4 integration contract — clean selection surface for B-roll / media requests.
 *
 * Example:
 *   findBestMedia(category = "Nature", tags = listOf("golden-hour"), orientation = "LANDSCAPE", minimumQuality = 0.7)
 */
class MediaSelectionApi(
    private val datasetRepository: DatasetRepository
) {

    data class SelectionCriteria(
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val orientation: String? = null,
        val minimumQuality: Double = 0.0,
        val mediaType: String? = null,
        val preferBestShot: Boolean = true,
        val limit: Int = 20
    )

    data class SelectionResult(
        val items: List<DatasetMedia>,
        val criteria: SelectionCriteria
    )

    suspend fun findBestMedia(
        projectId: String,
        category: String? = null,
        tags: List<String> = emptyList(),
        orientation: String? = null,
        minimumQuality: Double = 0.0,
        mediaType: String? = null,
        preferBestShot: Boolean = true,
        limit: Int = 20
    ): SelectionResult {
        val criteria = SelectionCriteria(
            category = category,
            tags = tags,
            orientation = orientation,
            minimumQuality = minimumQuality,
            mediaType = mediaType,
            preferBestShot = preferBestShot,
            limit = limit
        )
        var candidates = datasetRepository.filter(
            projectId = projectId,
            category = category,
            mediaType = mediaType,
            minQuality = minimumQuality.takeIf { it > 0.0 },
            onlyBest = preferBestShot
        )
        // If preferBestShot returned empty, fall back to full quality filter
        if (candidates.isEmpty() && preferBestShot) {
            candidates = datasetRepository.filter(
                projectId = projectId,
                category = category,
                mediaType = mediaType,
                minQuality = minimumQuality.takeIf { it > 0.0 },
                onlyBest = false
            )
        }
        if (orientation != null) {
            candidates = candidates.filter {
                it.orientation.equals(orientation, ignoreCase = true)
            }
        }
        if (tags.isNotEmpty()) {
            candidates = candidates.filter { media ->
                tags.any { tag ->
                    media.tags.any { it.equals(tag, ignoreCase = true) } ||
                        media.category.equals(tag, ignoreCase = true)
                }
            }
        }
        val ranked = candidates
            .sortedWith(
                compareByDescending<DatasetMedia> { it.isBestShot }
                    .thenByDescending { it.qualityScore }
                    .thenByDescending { it.createdAt }
            )
            .take(limit)
        return SelectionResult(items = ranked, criteria = criteria)
    }

    suspend fun getBestShots(projectId: String): List<DatasetMedia> {
        return datasetRepository.filter(projectId = projectId, onlyBest = true)
    }
}
