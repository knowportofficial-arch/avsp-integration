package com.avsp.pro.dataset

import com.avsp.pro.capture.database.entity.MediaEntity
import com.avsp.pro.dataset.api.DefaultEditorSelectionProvider
import com.avsp.pro.dataset.model.Recommendation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EditorSelectionContractTest {

    @Test
    fun bestKeepSurfaceExposesM4ReadyFields() {
        runBlocking {
            val dao = FakeMediaDao(
                listOf(
                    MediaEntity(
                        id = "m1",
                        projectId = "prj_1",
                        uriString = "content://media/1",
                        mediaType = "PHOTO",
                        displayName = "keep.jpg",
                        durationSeconds = 0,
                        width = 1080,
                        height = 1920,
                        qualityScoreNormalized = 0.82,
                        recommendation = Recommendation.KEEP.name,
                        isBestShot = true
                    ),
                    MediaEntity(
                        id = "m2",
                        projectId = "prj_1",
                        uriString = "content://media/2",
                        mediaType = "VIDEO",
                        displayName = "retake.mp4",
                        durationSeconds = 4,
                        width = 1080,
                        height = 1920,
                        qualityScoreNormalized = 0.2,
                        recommendation = Recommendation.RETAKE.name,
                        isBestShot = false
                    )
                )
            )
            val provider = DefaultEditorSelectionProvider(dao)
            val best = provider.listBestKeepMedia("prj_1")
            assertThat(best).hasSize(1)
            assertThat(best[0].mediaId).isEqualTo("m1")
            assertThat(best[0].projectId).isEqualTo("prj_1")
            assertThat(best[0].uriString).isEqualTo("content://media/1")
            assertThat(best[0].qualityScoreNormalized).isEqualTo(0.82)
            assertThat(best[0].recommendation).isEqualTo("KEEP")
            assertThat(best[0].isBestShot).isTrue()

            val all = provider.listSelectableMedia("prj_1")
            assertThat(all).hasSize(2)
            assertThat(all.map { it.mediaId }).containsExactly("m1", "m2")
            Unit
        }
    }

    private class FakeMediaDao(
        private val rows: List<MediaEntity>
    ) : com.avsp.pro.capture.database.dao.MediaDao {
        override fun getMediaForProjectFlow(projectId: String): Flow<List<MediaEntity>> =
            flowOf(rows.filter { it.projectId == projectId })

        override suspend fun getMediaForProject(projectId: String): List<MediaEntity> =
            rows.filter { it.projectId == projectId }

        override suspend fun getMediaById(id: String): MediaEntity? = rows.find { it.id == id }
        override fun getMediaCountForProject(projectId: String): Flow<Int> =
            flowOf(rows.count { it.projectId == projectId })

        override suspend fun insertMedia(media: MediaEntity) = Unit
        override suspend fun updateMedia(media: MediaEntity) = Unit
        override suspend fun deleteMedia(media: MediaEntity) = Unit
        override suspend fun deleteMediaById(id: String) = Unit
        override suspend fun queryFiltered(
            projectId: String,
            category: String?,
            mediaType: String?,
            minQuality: Double?,
            onlyBest: Int
        ): List<MediaEntity> = emptyList()

        override suspend fun search(projectId: String, query: String): List<MediaEntity> = emptyList()
        override suspend fun getByMissionShot(projectId: String, missionShotId: String): List<MediaEntity> =
            emptyList()

        override suspend fun clearBestShotFlags(projectId: String, missionShotId: String) = Unit
        override suspend fun markBestShot(id: String) = Unit
        override suspend fun getBestShots(projectId: String): List<MediaEntity> =
            rows.filter { it.projectId == projectId && it.isBestShot }

        override suspend fun deleteAllForProject(projectId: String) = Unit
    }
}
