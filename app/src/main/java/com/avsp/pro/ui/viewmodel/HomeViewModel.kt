package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class HomeDashboard(
    val projectCount: Int,
    val recentProjects: List<Project>,
    val modules: List<ModuleStatus>,
    val m1Status: String
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val moduleStatusRepository: ModuleStatusRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeDashboard>>(UiState.Idle)
    val state: StateFlow<UiState<HomeDashboard>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                moduleStatusRepository.ensureDefaults()
                val projects = projectRepository.listProjects()
                val modules = moduleStatusRepository.getAll()
                val m1 = modules.find { it.moduleId == "M1" }?.status?.name ?: "READY"
                _state.value = UiState.Success(
                    HomeDashboard(
                        projectCount = projects.size,
                        recentProjects = projects.take(5),
                        modules = modules,
                        m1Status = m1
                    )
                )
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to load home"
                logger.error("M1", "Home load failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }
}
