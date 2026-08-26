package com.avsp.pro.repository

import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.contracts.Project
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.model.ProjectStatus
import com.avsp.pro.core.module.ModuleStatus
import com.avsp.pro.logs.LogEntry
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.settings.AppSettings
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    suspend fun createProject(
        name: String,
        description: String = "",
        aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
        language: ProjectLanguage = ProjectLanguage.ENGLISH,
        metadata: Map<String, String> = emptyMap()
    ): Project

    suspend fun openProject(projectId: String): Project
    suspend fun renameProject(projectId: String, newName: String): Project
    suspend fun updateProject(project: Project): Project
    suspend fun deleteProject(projectId: String)
    suspend fun listProjects(): List<Project>
    fun observeProjects(): Flow<List<Project>>
    suspend fun listMediaAssets(projectId: String): List<MediaAsset>
    suspend fun addMediaAsset(asset: MediaAsset)
}

interface SettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)
    suspend fun updateTheme(theme: com.avsp.pro.settings.ThemePreference)
    suspend fun updateLoggingLevel(level: LogLevel)
    suspend fun refreshCredentialStates()
    /** Stores AI API credential in SecureConfigStore — never logs the value. */
    suspend fun setAiApiCredential(secret: String)
    suspend fun clearAiApiCredential()
}

interface ModuleStatusRepository {
    suspend fun getAll(): List<ModuleStatus>
    suspend fun get(moduleId: String): ModuleStatus?
    suspend fun registerOrUpdate(status: ModuleStatus)
    fun observeAll(): Flow<List<ModuleStatus>>
    suspend fun ensureDefaults()
}

interface LogRepository {
    suspend fun append(
        level: LogLevel,
        module: String,
        message: String,
        details: String? = null,
        projectId: String? = null
    )
    suspend fun recent(limit: Int = 100): List<LogEntry>
    fun observeRecent(limit: Int = 100): Flow<List<LogEntry>>
    suspend fun clear()
}
