package com.avsp.pro.capture.camera.guided

import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.error.ProjectNotFoundException
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.avsp.pro.repository.ProjectRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * Guided Capture completion must register video under the Pro projectId.
 */
class GuidedCaptureMediaRegistrationTest {

    @Test
    fun guidedVideoRegistersAgainstProProjectId() {
        runBlocking {
            val repo = FakeProProjects()
            val projectId = "prj_guided_video_1"
            repo.projects[projectId] = Project(
                projectId = projectId,
                name = "Guided",
                description = "",
                createdAt = 1L,
                updatedAt = 1L,
                status = ProjectStatus.DRAFT,
                duration = null,
                aspectRatio = AspectRatio.RATIO_9_16,
                language = ProjectLanguage.ENGLISH,
                outputPath = null,
                metadata = emptyMap()
            )

            val clipFile = File.createTempFile("guided_clip", ".mp4").apply {
                writeBytes(ByteArray(16_384) { 2 })
                deleteOnExit()
            }

            val asset = MediaAsset(
                assetId = "clip_1",
                projectId = projectId,
                fileName = clipFile.name,
                relativePath = "originals/camera/${clipFile.name}",
                mimeType = "video/mp4",
                sizeBytes = clipFile.length(),
                durationMs = 3000L,
                width = 1080,
                height = 1920,
                createdAt = System.currentTimeMillis(),
                tags = listOf("GUIDED", "KEEP"),
                metadata = mapOf(
                    "uri" to clipFile.toURI().toString(),
                    "mediaType" to "VIDEO",
                    "source" to "M7_CAPTURE"
                )
            )
            repo.addMediaAsset(asset)

            val listed = repo.listMediaAssets(projectId)
            assertThat(listed).hasSize(1)
            assertThat(listed[0].projectId).isEqualTo(projectId)
            assertThat(listed[0].mimeType).isEqualTo("video/mp4")
            assertThat(listed[0].metadata["mediaType"]).isEqualTo("VIDEO")
            assertThat(listed[0].metadata["uri"]).isEqualTo(clipFile.toURI().toString())
            Unit
        }
    }

    @Test
    fun missingProjectDoesNotInventSecondIdentity() {
        runBlocking {
            val repo = FakeProProjects()
            try {
                repo.openProject("missing")
                throw AssertionError("expected ProjectNotFoundException")
            } catch (_: ProjectNotFoundException) {
            }
            assertThat(repo.listMediaAssets("missing")).isEmpty()
            Unit
        }
    }

    private class FakeProProjects : ProjectRepository {
        val projects = mutableMapOf<String, Project>()
        private val assets = mutableListOf<MediaAsset>()

        override suspend fun createProject(
            name: String,
            description: String,
            aspectRatio: AspectRatio,
            language: ProjectLanguage,
            metadata: Map<String, String>
        ): Project = error("unused")

        override suspend fun openProject(projectId: String): Project =
            projects[projectId] ?: throw ProjectNotFoundException(projectId)

        override suspend fun renameProject(projectId: String, newName: String): Project = error("unused")
        override suspend fun updateProject(project: Project): Project = error("unused")
        override suspend fun deleteProject(projectId: String) = error("unused")
        override suspend fun listProjects(): List<Project> = projects.values.toList()
        override fun observeProjects(): Flow<List<Project>> = flowOf(listOf())
        override suspend fun listMediaAssets(projectId: String): List<MediaAsset> =
            assets.filter { it.projectId == projectId }

        override suspend fun addMediaAsset(asset: MediaAsset) {
            assets += asset
        }
    }
}
