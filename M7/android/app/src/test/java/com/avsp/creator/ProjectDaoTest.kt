package com.avsp.creator

import com.avsp.creator.database.entity.ProjectEntity
import com.avsp.creator.domain.model.Project
import com.avsp.creator.domain.model.ProjectStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProjectDaoTest {

    @Test
    fun testProjectDomainConversion() {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val project = Project(
            id = id,
            title = "Test Field Capture Project",
            topic = "Railway BSNL Tutorial",
            category = "Education",
            targetAudience = "Tech Enthusiasts",
            targetPlatform = "YouTube",
            targetLanguage = "English",
            status = ProjectStatus.CAPTURE,
            createdAt = now,
            updatedAt = now
        )

        // Convert to Room Entity
        val entity = ProjectEntity.fromDomainModel(project)
        assertEquals(id, entity.id)
        assertEquals("Test Field Capture Project", entity.title)
        assertEquals("CAPTURE", entity.status)

        // Convert back to Domain Model
        val restored = entity.toDomainModel()
        assertEquals(project.id, restored.id)
        assertEquals(project.title, restored.title)
        assertEquals(ProjectStatus.CAPTURE, restored.status)
        assertEquals(project.createdAt, restored.createdAt)
    }

    @Test
    fun testProjectStatusFromString() {
        assertEquals(ProjectStatus.IDEA, ProjectStatus.fromString("IDEA"))
        assertEquals(ProjectStatus.RESEARCH, ProjectStatus.fromString("RESEARCH"))
        assertEquals(ProjectStatus.SCRIPT, ProjectStatus.fromString("SCRIPT"))
        assertEquals(ProjectStatus.VOICE, ProjectStatus.fromString("VOICE"))
        assertEquals(ProjectStatus.CAPTURE, ProjectStatus.fromString("CAPTURE"))
        assertEquals(ProjectStatus.EDITING, ProjectStatus.fromString("EDITING"))
        assertEquals(ProjectStatus.REVIEW, ProjectStatus.fromString("REVIEW"))
        assertEquals(ProjectStatus.READY, ProjectStatus.fromString("READY"))
        assertEquals(ProjectStatus.PUBLISHED, ProjectStatus.fromString("PUBLISHED"))
        assertEquals(ProjectStatus.ANALYZING, ProjectStatus.fromString("ANALYZING"))
        assertEquals(ProjectStatus.IDEA, ProjectStatus.fromString("UNKNOWN_STATUS"))
    }
}
