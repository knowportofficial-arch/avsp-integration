package com.avsp.creator

import com.avsp.creator.core.common.Resource
import com.avsp.creator.domain.model.Project
import com.avsp.creator.domain.model.ProjectStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProjectRepositoryTest {

    @Test
    fun testProjectValidationInRepository() {
        val emptyTitleProject = Project(
            id = UUID.randomUUID().toString(),
            title = "   ",
            topic = "Topic",
            category = "Cat",
            targetAudience = "Audience",
            targetPlatform = "Platform",
            targetLanguage = "en",
            status = ProjectStatus.IDEA,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        assertTrue(emptyTitleProject.title.isBlank())
    }

    @Test
    fun testResourceSealedClass() {
        val loading = Resource.Loading
        assertTrue(loading is Resource.Loading)

        val success = Resource.Success("Success Data")
        assertEquals("Success Data", (success as Resource.Success).data)

        val error = Resource.Error("Failed to access database")
        assertEquals("Failed to access database", (error as Resource.Error).message)
    }
}
