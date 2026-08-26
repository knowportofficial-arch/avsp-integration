package com.avsp.pro.capture

import com.avsp.pro.capture.common.Resource
import com.avsp.pro.capture.data.CaptureProjectInfo
import com.avsp.pro.capture.data.ProjectRepository
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.error.ProjectNotFoundException
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Pro project identity is authoritative for capture; capture lookup maps name → title.
 */
class CaptureProjectIdentityTest {

    @Test
    fun mapsProProjectNameToCaptureTitle() {
        runBlocking {
            val pro = FakeProProjects(
                Project(
                    projectId = "prj_abc",
                    name = "Station B-roll",
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
            )
            val capture = ProjectRepository(pro)
            val result = capture.getProjectById("prj_abc")
            assertThat(result).isInstanceOf(Resource.Success::class.java)
            val info = (result as Resource.Success<CaptureProjectInfo?>).data
            assertThat(info).isEqualTo(CaptureProjectInfo(id = "prj_abc", title = "Station B-roll"))
            Unit
        }
    }

    @Test
    fun missingProProjectReturnsNullSuccess() {
        runBlocking {
            val capture = ProjectRepository(FakeProProjects(null))
            val result = capture.getProjectById("missing")
            assertThat(result).isInstanceOf(Resource.Success::class.java)
            assertThat((result as Resource.Success<CaptureProjectInfo?>).data).isNull()
            Unit
        }
    }

    private class FakeProProjects(
        private val project: Project?
    ) : com.avsp.pro.repository.ProjectRepository {
        override suspend fun createProject(
            name: String,
            description: String,
            aspectRatio: AspectRatio,
            language: ProjectLanguage,
            metadata: Map<String, String>
        ): Project = error("unused")

        override suspend fun openProject(projectId: String): Project =
            project?.takeIf { it.projectId == projectId }
                ?: throw ProjectNotFoundException(projectId)

        override suspend fun renameProject(projectId: String, newName: String): Project = error("unused")
        override suspend fun updateProject(project: Project): Project = error("unused")
        override suspend fun deleteProject(projectId: String) = error("unused")
        override suspend fun listProjects(): List<Project> = emptyList()
        override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
        override suspend fun listMediaAssets(projectId: String): List<MediaAsset> = emptyList()
        override suspend fun addMediaAsset(asset: MediaAsset) = Unit
    }
}
