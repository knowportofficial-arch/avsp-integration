package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.module.ModuleStatus
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.repository.ModuleStatusRepository
import com.avsp.pro.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectDashboard(
    val project: Project,
    val assets: List<MediaAsset>,
    val modules: List<ModuleStatus>
)

class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val moduleStatusRepository: ModuleStatusRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProjectDashboard>>(UiState.Idle)
    val state: StateFlow<UiState<ProjectDashboard>> = _state.asStateFlow()

    fun load(projectId: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val project = projectRepository.openProject(projectId)
                val assets = projectRepository.listMediaAssets(projectId)
                val modules = moduleStatusRepository.getAll()
                _state.value = UiState.Success(
                    ProjectDashboard(project = project, assets = assets, modules = modules)
                )
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to open project"
                logger.error("M1", "Project detail failed", details = msg, projectId = projectId)
                _state.value = UiState.Error(msg, recoverable = true)
            }
        }
    }

    fun rename(projectId: String, newName: String) {
        viewModelScope.launch {
            try {
                projectRepository.renameProject(projectId, newName)
                load(projectId)
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Rename failed"
                _state.value = UiState.Error(msg)
            }
        }
    }
}
