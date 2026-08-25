package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.module.ModuleStatus
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.repository.ModuleStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModulesViewModel(
    private val moduleStatusRepository: ModuleStatusRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ModuleStatus>>>(UiState.Idle)
    val state: StateFlow<UiState<List<ModuleStatus>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                moduleStatusRepository.ensureDefaults()
                _state.value = UiState.Success(moduleStatusRepository.getAll())
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to load modules"
                logger.error("M1", "Module status load failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }
}
