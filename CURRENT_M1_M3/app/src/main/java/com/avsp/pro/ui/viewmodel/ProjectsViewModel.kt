package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.repository.ProjectRepository
import com.avsp.pro.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Project>>>(UiState.Idle)
    val state: StateFlow<UiState<List<Project>>> = _state.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                _state.value = UiState.Success(projectRepository.listProjects())
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to list projects"
                logger.error("M1", "Project list failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }

    fun createProject(name: String, description: String = "") {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                projectRepository.createProject(
                    name = name,
                    description = description,
                    aspectRatio = settings.defaultAspectRatio,
                    language = settings.defaultLanguage
                )
                _actionError.value = null
                refresh()
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Create failed"
                logger.error("M1", "Create project failed", details = msg)
                _actionError.value = msg
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            try {
                projectRepository.deleteProject(projectId)
                refresh()
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Delete failed"
                _actionError.value = msg
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun createWithDefaults(
        name: String,
        aspectRatio: AspectRatio,
        language: ProjectLanguage
    ) {
        viewModelScope.launch {
            try {
                projectRepository.createProject(
                    name = name,
                    aspectRatio = aspectRatio,
                    language = language
                )
                refresh()
            } catch (e: Exception) {
                _actionError.value =
                    (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Create failed"
            }
        }
    }
}
