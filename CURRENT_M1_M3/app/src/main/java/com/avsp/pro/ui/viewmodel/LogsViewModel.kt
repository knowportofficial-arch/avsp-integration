package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.logs.LogEntry
import com.avsp.pro.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(
    private val logRepository: LogRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<LogEntry>>>(UiState.Idle)
    val state: StateFlow<UiState<List<LogEntry>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                _state.value = UiState.Success(logRepository.recent(200))
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to load logs"
                logger.error("M1", "Logs load failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            try {
                logRepository.clear()
                refresh()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Failed to clear logs")
            }
        }
    }
}
