package com.avsp.pro.m7.capture.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.avsp.pro.m7.data.repository.MediaRepository
import com.avsp.pro.m7.data.repository.ProjectRepository

class CameraViewModelFactory(
    private val mediaRepository: MediaRepository,
    private val projectRepository: ProjectRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            return CameraViewModel(mediaRepository, projectRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
