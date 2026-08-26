package com.avsp.pro.capture.data

import com.avsp.pro.capture.common.Resource
import com.avsp.pro.core.error.ProjectNotFoundException
import com.avsp.pro.repository.ProjectRepository as ProProjectRepository

/**
 * Lightweight project title lookup for capture UI.
 * Authoritative project identity remains Pro [ProProjectRepository] / FileAvspStorage.
 */
data class CaptureProjectInfo(
    val id: String,
    val title: String
)

class ProjectRepository(
    private val proProjects: ProProjectRepository
) {
    suspend fun getProjectById(id: String): Resource<CaptureProjectInfo?> {
        return try {
            val project = proProjects.openProject(id)
            Resource.Success(
                CaptureProjectInfo(
                    id = project.projectId,
                    title = project.name
                )
            )
        } catch (_: ProjectNotFoundException) {
            Resource.Success(null)
        } catch (e: Exception) {
            Resource.Error("Error retrieving project $id", e)
        }
    }
}
