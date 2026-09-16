package com.avsp.pro.m7.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.avsp.pro.m7.domain.model.Project
import com.avsp.pro.m7.domain.model.ProjectStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val topic: String,
    val category: String,
    val targetAudience: String,
    val targetPlatform: String,
    val targetLanguage: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): Project {
        return Project(
            id = id,
            title = title,
            topic = topic,
            category = category,
            targetAudience = targetAudience,
            targetPlatform = targetPlatform,
            targetLanguage = targetLanguage,
            status = ProjectStatus.fromString(status),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomainModel(project: Project): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                title = project.title,
                topic = project.topic,
                category = project.category,
                targetAudience = project.targetAudience,
                targetPlatform = project.targetPlatform,
                targetLanguage = project.targetLanguage,
                status = project.status.name,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt
            )
        }
    }
}
