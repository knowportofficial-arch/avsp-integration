package com.avsp.pro.repository

import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.error.DatabaseException
import com.avsp.pro.core.error.InvalidInputException
import com.avsp.pro.core.error.ProjectNotFoundException
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.avsp.pro.database.EntityMappers
import com.avsp.pro.database.dao.MediaAssetDao
import com.avsp.pro.database.dao.ProjectDao
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.storage.AvspStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao,
    private val mediaAssetDao: MediaAssetDao,
    private val storage: AvspStorage,
    private val logger: AvspLogger
) : ProjectRepository {

    override suspend fun createProject(
        name: String,
        description: String,
        aspectRatio: AspectRatio,
        language: ProjectLanguage,
        metadata: Map<String, String>
    ): Project {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw InvalidInputException("Project name must not be empty")
        }
        if (trimmed.length > 120) {
            throw InvalidInputException("Project name must be 120 characters or fewer")
        }
        val now = System.currentTimeMillis()
        val project = Project(
            projectId = "prj_" + UUID.randomUUID().toString().replace("-", "").take(16),
            name = trimmed,
            description = description.trim(),
            createdAt = now,
            updatedAt = now,
            status = ProjectStatus.DRAFT,
            duration = null,
            aspectRatio = aspectRatio,
            language = language,
            outputPath = null,
            metadata = metadata
        )
        try {
            projectDao.insert(EntityMappers.toEntity(project))
            storage.ensureProjectLayout(project.projectId)
            logger.info("M1", "Project created", details = project.projectId, projectId = project.projectId)
            return project
        } catch (e: InvalidInputException) {
            throw e
        } catch (e: Exception) {
            logger.error("M1", "Failed to create project", details = e.message)
            throw DatabaseException("Failed to create project", cause = e)
        }
    }

    override suspend fun openProject(projectId: String): Project {
        val entity = projectDao.getById(projectId)
            ?: throw ProjectNotFoundException(projectId)
        logger.info("M1", "Project opened", projectId = projectId)
        return EntityMappers.toDomain(entity)
    }

    override suspend fun renameProject(projectId: String, newName: String): Project {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) throw InvalidInputException("Project name must not be empty")
        val existing = openProject(projectId)
        return updateProject(existing.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun updateProject(project: Project): Project {
        if (project.name.isBlank()) throw InvalidInputException("Project name must not be empty")
        // Enforce controlled status enum
        ProjectStatus.fromRaw(project.status.name)
        if (projectDao.getById(project.projectId) == null) {
            throw ProjectNotFoundException(project.projectId)
        }
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        try {
            projectDao.update(EntityMappers.toEntity(updated))
            logger.info("M1", "Project updated", projectId = project.projectId)
            return updated
        } catch (e: Exception) {
            logger.error("M1", "Failed to update project", details = e.message, projectId = project.projectId)
            throw DatabaseException("Failed to update project", cause = e)
        }
    }

    override suspend fun deleteProject(projectId: String) {
        if (projectDao.getById(projectId) == null) {
            throw ProjectNotFoundException(projectId)
        }
        try {
            mediaAssetDao.deleteForProject(projectId)
            projectDao.delete(projectId)
            logger.info("M1", "Project deleted", projectId = projectId)
        } catch (e: Exception) {
            logger.error("M1", "Failed to delete project", details = e.message, projectId = projectId)
            throw DatabaseException("Failed to delete project", cause = e)
        }
    }

    override suspend fun listProjects(): List<Project> =
        projectDao.getAll().map(EntityMappers::toDomain)

    override fun observeProjects(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map(EntityMappers::toDomain) }

    override suspend fun listMediaAssets(projectId: String): List<MediaAsset> =
        mediaAssetDao.forProject(projectId).map(EntityMappers::toDomain)

    override suspend fun addMediaAsset(asset: MediaAsset) {
        mediaAssetDao.upsert(EntityMappers.toEntity(asset))
    }
}
