package com.avsp.pro.video.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.video.contract.VideoRenderPlan
import com.avsp.pro.video.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface VideoUiState {
    data object Idle : VideoUiState
    data class Ready(val plan: VideoRenderPlan) : VideoUiState
    data class Rendering(val progress: Int, val plan: VideoRenderPlan) : VideoUiState
    data class Success(val outputPath: String, val durationMs: Long, val width: Int, val height: Int) : VideoUiState
    data class Error(val message: String) : VideoUiState
}

class VideoViewModel(private val repository: VideoRepository) : ViewModel() {
    private val _state = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val state: StateFlow<VideoUiState> = _state

    fun prepare(projectId: String) {
        viewModelScope.launch {
            runCatching { repository.buildPlan(projectId) }
                .onSuccess { _state.value = VideoUiState.Ready(it) }
                .onFailure { _state.value = VideoUiState.Error(it.message ?: "Unable to prepare M4") }
        }
    }

    fun render(projectId: String) {
        viewModelScope.launch {
            runCatching {
                val plan = repository.buildPlan(projectId)
                _state.value = VideoUiState.Rendering(0, plan)
                repository.render(projectId)
            }.onSuccess {
                _state.value = VideoUiState.Success(it.outputAbsolutePath, it.durationMs, it.width, it.height)
            }.onFailure {
                _state.value = VideoUiState.Error(it.message ?: "M4 render failed")
            }
        }
    }
}
