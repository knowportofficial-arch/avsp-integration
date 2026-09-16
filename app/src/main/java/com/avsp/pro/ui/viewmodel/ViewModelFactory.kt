package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.avsp.pro.di.AppContainer

class ViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(
                    projectRepository = container.projectRepository,
                    moduleStatusRepository = container.moduleStatusRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(ProjectsViewModel::class.java) ->
                ProjectsViewModel(
                    projectRepository = container.projectRepository,
                    settingsRepository = container.settingsRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(ProjectDetailViewModel::class.java) ->
                ProjectDetailViewModel(
                    projectRepository = container.projectRepository,
                    moduleStatusRepository = container.moduleStatusRepository,
                    logger = container.logger,
                    bundledMediaSeeder = container.bundledMediaSeeder
                ) as T
            modelClass.isAssignableFrom(MediaViewModel::class.java) ->
                MediaViewModel(
                    projectRepository = container.projectRepository,
                    logger = container.logger,
                    bundledMediaSeeder = container.bundledMediaSeeder,
                    m7MediaRepository = container.m7Services.mediaRepository
                ) as T
            modelClass.isAssignableFrom(ModulesViewModel::class.java) ->
                ModulesViewModel(
                    moduleStatusRepository = container.moduleStatusRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(LogsViewModel::class.java) ->
                LogsViewModel(
                    logRepository = container.logRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(com.avsp.pro.script.ui.ScriptAiViewModel::class.java) ->
                com.avsp.pro.script.ui.ScriptAiViewModel(
                    scriptRepository = container.scriptRepository,
                    projectRepository = container.projectRepository,
                    settingsRepository = container.settingsRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(com.avsp.pro.audio.ui.AudioTtsViewModel::class.java) ->
                com.avsp.pro.audio.ui.AudioTtsViewModel(
                    audioRepository = container.audioRepository,
                    logger = container.logger
                ) as T
            modelClass.isAssignableFrom(com.avsp.pro.video.ui.VideoViewModel::class.java) ->
                com.avsp.pro.video.ui.VideoViewModel(container.videoRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}

