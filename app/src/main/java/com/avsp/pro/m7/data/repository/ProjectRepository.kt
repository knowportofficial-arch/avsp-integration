package com.avsp.pro.m7.data.repository

import com.avsp.pro.m7.core.common.Resource
import com.avsp.pro.m7.database.dao.ProjectDao
import com.avsp.pro.m7.database.entity.ProjectEntity
import com.avsp.pro.m7.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class ProjectRepository(private val projectDao: ProjectDao) {

    fun observeProjects(): Flow<Resource<List<Project>>> {
        return projectDao.observeProjects()
            .map { entities ->
                Resource.Success(entities.map { it.toDomainModel() }) as Resource<List<Project>>
            }
            .catch { e ->
                emit(Resource.Error("Failed to load projects from Room database", e))
            }
    }

    fun observeProjectById(id: String): Flow<Resource<Project?>> {
        return projectDao.observeProjectById(id)
            .map { entity ->
                Resource.Success(entity?.toDomainModel()) as Resource<Project?>
            }
            .catch { e ->
                emit(Resource.Error("Failed to fetch project details", e))
            }
    }

    suspend fun getProjectById(id: String): Resource<Project?> {
        return try {
            val entity = projectDao.getProjectById(id)
            Resource.Success(entity?.toDomainModel())
        } catch (e: Exception) {
            Resource.Error("Error retrieving project $id", e)
        }
    }

    suspend fun saveProject(project: Project): Resource<Unit> {
        return try {
            if (project.title.isBlank()) {
                return Resource.Error("Project title cannot be empty")
            }
            val entity = ProjectEntity.fromDomainModel(project)
            projectDao.insertProject(entity)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Database error while saving project", e)
        }
    }

    suspend fun updateProject(project: Project): Resource<Unit> {
        return try {
            if (project.title.isBlank()) {
                return Resource.Error("Project title cannot be empty")
            }
            val entity = ProjectEntity.fromDomainModel(project)
            projectDao.updateProject(entity)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Database error while updating project", e)
        }
    }

    suspend fun deleteProject(project: Project): Resource<Unit> {
        return try {
            val entity = ProjectEntity.fromDomainModel(project)
            projectDao.deleteProject(entity)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Database error while deleting project", e)
        }
    }
}
