package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.media.BundledMediaSeeder
import com.avsp.pro.repository.ProjectRepository
import com.avsp.pro.m7.data.repository.MediaRepository as M7MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaInventory(
    val projects: List<Project>,
    val assetsByProject: Map<String, List<MediaAsset>>
)

class MediaViewModel(
    private val projectRepository: ProjectRepository,
    private val logger: AvspLogger,
    private val bundledMediaSeeder: BundledMediaSeeder,
    private val m7MediaRepository: M7MediaRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<MediaInventory>>(UiState.Idle)
    val state: StateFlow<UiState<MediaInventory>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val projects = projectRepository.listProjects()
                val map = projects.associate { project ->
                    bundledMediaSeeder.ensureForProject(project.projectId)
                    val legacyAssets = projectRepository.listMediaAssets(project.projectId)
                    val m7Assets = m7MediaRepository.getMediaForProject(project.projectId).map { media ->
                        com.avsp.pro.core.contracts.MediaAsset(
                            assetId = media.id,
                            projectId = media.projectId,
                            fileName = media.displayName,
                            relativePath = media.uriString,
                            mimeType = if (media.mediaType.equals("VIDEO", ignoreCase = true)) "video/mp4" else "image/jpeg",
                            sizeBytes = media.fileSizeBytes,
                            durationMs = if (media.durationSeconds > 0L) media.durationSeconds * 1000L else null,
                            width = media.width.takeIf { it > 0 },
                            height = media.height.takeIf { it > 0 },
                            createdAt = media.createdAt,
                            tags = media.tagsList(),
                            metadata = mapOf(
                                "source" to "M7",
                                "shotType" to media.shotType,
                                "category" to media.category,
                                "recommendation" to media.recommendation,
                                "qualityScore" to media.qualityScore.toString()
                            )
                        )
                    }
                    project.projectId to (m7Assets + legacyAssets)
                }
                _state.value = UiState.Success(MediaInventory(projects, map))
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to load media"
                logger.error("M1", "Media load failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }
}

