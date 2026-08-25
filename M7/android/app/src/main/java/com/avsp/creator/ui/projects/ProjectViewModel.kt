package com.avsp.creator.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.creator.core.common.Resource
import com.avsp.creator.data.repository.ProjectRepository
import com.avsp.creator.domain.model.Project
import com.avsp.creator.domain.model.ProjectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ProjectListUiState(
    val isLoading: Boolean = true,
    val projects: List<Project> = emptyList(),
    val errorMessage: String? = null
)

class ProjectViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    val projectListState: StateFlow<ProjectListUiState> = repository.observeProjects()
        .map { resource ->
            when (resource) {
                is Resource.Loading -> ProjectListUiState(isLoading = true)
                is Resource.Success -> ProjectListUiState(
                    isLoading = false,
                    projects = resource.data,
                    errorMessage = null
                )
                is Resource.Error -> ProjectListUiState(
                    isLoading = false,
                    errorMessage = resource.message
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProjectListUiState(isLoading = true)
        )

    private val _selectedProjectState = MutableStateFlow<Resource<Project?>>(Resource.Loading)
    val selectedProjectState: StateFlow<Resource<Project?>> = _selectedProjectState.asStateFlow()

    fun loadProject(id: String) {
        viewModelScope.launch {
            _selectedProjectState.value = Resource.Loading
            repository.observeProjectById(id).collect { resource ->
                _selectedProjectState.value = resource
            }
        }
    }

    suspend fun createProject(
        title: String,
        topic: String,
        category: String,
        targetAudience: String,
        targetPlatform: String,
        targetLanguage: String,
        status: ProjectStatus
    ): Resource<String> {
        if (title.isBlank()) {
            return Resource.Error("Project title cannot be empty")
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = Project(
            id = id,
            title = title.trim(),
            topic = topic.trim(),
            category = if (category.isBlank()) "General" else category.trim(),
            targetAudience = if (targetAudience.isBlank()) "General Audience" else targetAudience.trim(),
            targetPlatform = if (targetPlatform.isBlank()) "YouTube" else targetPlatform.trim(),
            targetLanguage = if (targetLanguage.isBlank()) "English" else targetLanguage.trim(),
            status = status,
            createdAt = now,
            updatedAt = now
        )

        return when (val result = repository.saveProject(project)) {
            is Resource.Success -> Resource.Success(id)
            is Resource.Error -> Resource.Error(result.message, result.cause)
            is Resource.Loading -> Resource.Loading
        }
    }

    fun updateProject(project: Project, onComplete: (Resource<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            onComplete(result)
        }
    }

    fun deleteProject(project: Project, onComplete: (Resource<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteProject(project)
            onComplete(result)
        }
    }
}
