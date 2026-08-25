package com.avsp.creator.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.creator.core.common.Resource
import com.avsp.creator.data.repository.ProjectRepository
import com.avsp.creator.domain.model.Project
import com.avsp.creator.domain.model.ProjectStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalProjectsCount: Int = 0,
    val activeProjectsCount: Int = 0,
    val pendingCaptureCount: Int = 0,
    val pendingEditingCount: Int = 0,
    val readyProjectsCount: Int = 0,
    val recentProjects: List<Project> = emptyList(),
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = repository.observeProjects()
        .map { resource ->
            when (resource) {
                is Resource.Loading -> DashboardUiState(isLoading = true)
                is Resource.Success -> {
                    val projects = resource.data
                    DashboardUiState(
                        isLoading = false,
                        totalProjectsCount = projects.size,
                        activeProjectsCount = projects.count { it.status != ProjectStatus.PUBLISHED },
                        pendingCaptureCount = projects.count { it.status == ProjectStatus.CAPTURE },
                        pendingEditingCount = projects.count { it.status == ProjectStatus.EDITING },
                        readyProjectsCount = projects.count { it.status == ProjectStatus.READY || it.status == ProjectStatus.PUBLISHED },
                        recentProjects = projects.take(5),
                        errorMessage = null
                    )
                }
                is Resource.Error -> {
                    DashboardUiState(
                        isLoading = false,
                        errorMessage = resource.message
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )
}
