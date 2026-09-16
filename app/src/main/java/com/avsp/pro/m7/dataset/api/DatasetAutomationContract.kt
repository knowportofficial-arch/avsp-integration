package com.avsp.pro.m7.dataset.api

import com.avsp.pro.m7.dataset.model.DatasetCategory
import com.avsp.pro.m7.dataset.model.DatasetMedia
import com.avsp.pro.m7.dataset.model.QualityResult
import com.avsp.pro.m7.dataset.model.Recommendation
import com.avsp.pro.m7.dataset.repository.DatasetRepository

/**
 * M8 integration contract — structured models and APIs that automation can call later.
 * This module does NOT implement the automation engine.
 */
class DatasetAutomationContract(
    private val datasetRepository: DatasetRepository,
    private val mediaSelectionApi: MediaSelectionApi
) {

    data class MediaSummary(
        val clipId: String,
        val category: String,
        val mediaType: String,
        val qualityScore: Double,
        val recommendation: String,
        val isBestShot: Boolean,
        val tags: List<String>,
        val orientation: String,
        val fileUri: String
    )

    data class DatasetSnapshot(
        val projectId: String,
        val totalCount: Int,
        val byCategory: Map<String, Int>,
        val keepCount: Int,
        val retakeCount: Int,
        val reviewCount: Int,
        val bestShotCount: Int,
        val items: List<MediaSummary>
    )

    suspend fun snapshot(projectId: String): DatasetSnapshot {
        val all = datasetRepository.getProjectMedia(projectId)
        val byCat = all.groupingBy { it.category }.eachCount()
        return DatasetSnapshot(
            projectId = projectId,
            totalCount = all.size,
            byCategory = byCat,
            keepCount = all.count { it.qualityResult?.recommendation == Recommendation.KEEP },
            retakeCount = all.count { it.qualityResult?.recommendation == Recommendation.RETAKE },
            reviewCount = all.count { it.qualityResult?.recommendation == Recommendation.REVIEW },
            bestShotCount = all.count { it.isBestShot },
            items = all.map {
                MediaSummary(
                    clipId = it.clipId,
                    category = it.category,
                    mediaType = it.mediaType,
                    qualityScore = it.qualityScore,
                    recommendation = it.qualityResult?.recommendation?.name ?: "REVIEW",
                    isBestShot = it.isBestShot,
                    tags = it.tags,
                    orientation = it.orientation,
                    fileUri = it.fileUri
                )
            }
        )
    }

    suspend fun listCategories(): List<String> =
        DatasetCategory.allKnown().map { it.key }

    suspend fun findKeepable(
        projectId: String,
        category: String? = null,
        minQuality: Double = 0.7
    ): List<DatasetMedia> {
        return mediaSelectionApi.findBestMedia(
            projectId = projectId,
            category = category,
            minimumQuality = minQuality,
            preferBestShot = true
        ).items.filter {
            it.qualityResult?.recommendation != Recommendation.RETAKE
        }
    }

    suspend fun reanalyzeAll(projectId: String): Int {
        val all = datasetRepository.getProjectMedia(projectId)
        var count = 0
        for (item in all) {
            datasetRepository.analyzeExisting(item.clipId)
            count++
        }
        return count
    }
}
