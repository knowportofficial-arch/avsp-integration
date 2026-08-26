package com.avsp.pro.repository

import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.database.dao.SettingsDao
import com.avsp.pro.database.entity.SettingsEntity
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.settings.AppSettings
import com.avsp.pro.settings.ConfigState
import com.avsp.pro.settings.SecureConfigKeys
import com.avsp.pro.settings.SecureConfigStore
import com.avsp.pro.settings.ThemePreference

class SettingsRepositoryImpl(
    private val settingsDao: SettingsDao,
    private val secureConfigStore: SecureConfigStore
) : SettingsRepository {

    private object Keys {
        const val LANGUAGE = "default_language"
        const val ASPECT = "default_aspect_ratio"
        const val OUTPUT = "default_output_directory_ref"
        const val THEME = "theme_preference"
        const val LOG_LEVEL = "logging_level"
        const val AI_REF = "ai_config_reference"
        const val PUB_REF = "publishing_config_reference"
    }

    override suspend fun getSettings(): AppSettings {
        val map = settingsDao.getAll().associate { it.key to it.value }
        return AppSettings(
            defaultLanguage = map[Keys.LANGUAGE]?.let {
                runCatching { ProjectLanguage.fromCode(it) }.getOrDefault(ProjectLanguage.ENGLISH)
            } ?: ProjectLanguage.ENGLISH,
            defaultAspectRatio = map[Keys.ASPECT]?.let {
                runCatching { AspectRatio.fromLabel(it) }.getOrDefault(AspectRatio.RATIO_9_16)
            } ?: AspectRatio.RATIO_9_16,
            defaultOutputDirectoryRef = map[Keys.OUTPUT] ?: "generated/video",
            themePreference = map[Keys.THEME]?.let {
                runCatching { ThemePreference.valueOf(it) }.getOrDefault(ThemePreference.SYSTEM)
            } ?: ThemePreference.SYSTEM,
            loggingLevel = map[Keys.LOG_LEVEL]?.let { LogLevel.fromRaw(it) } ?: LogLevel.INFO,
            aiConfigState = secureConfigStore.configState(SecureConfigKeys.AI_API),
            publishingConfigState = if (
                secureConfigStore.hasSecret(SecureConfigKeys.YOUTUBE) ||
                secureConfigStore.hasSecret(SecureConfigKeys.META) ||
                secureConfigStore.hasSecret(SecureConfigKeys.TELEGRAM) ||
                secureConfigStore.hasSecret(SecureConfigKeys.WEB)
            ) ConfigState.CONFIGURED else ConfigState.NOT_CONFIGURED,
            aiConfigReference = map[Keys.AI_REF],
            publishingConfigReference = map[Keys.PUB_REF]
        )
    }

    override suspend fun saveSettings(settings: AppSettings) {
        settingsDao.upsert(SettingsEntity(Keys.LANGUAGE, settings.defaultLanguage.code))
        settingsDao.upsert(SettingsEntity(Keys.ASPECT, settings.defaultAspectRatio.label))
        settingsDao.upsert(SettingsEntity(Keys.OUTPUT, settings.defaultOutputDirectoryRef))
        settingsDao.upsert(SettingsEntity(Keys.THEME, settings.themePreference.name))
        settingsDao.upsert(SettingsEntity(Keys.LOG_LEVEL, settings.loggingLevel.name))
        settings.aiConfigReference?.let {
            settingsDao.upsert(SettingsEntity(Keys.AI_REF, it))
        }
        settings.publishingConfigReference?.let {
            settingsDao.upsert(SettingsEntity(Keys.PUB_REF, it))
        }
    }

    override suspend fun updateTheme(theme: ThemePreference) {
        settingsDao.upsert(SettingsEntity(Keys.THEME, theme.name))
    }

    override suspend fun updateLoggingLevel(level: LogLevel) {
        settingsDao.upsert(SettingsEntity(Keys.LOG_LEVEL, level.name))
    }

    override suspend fun refreshCredentialStates() {
        // Credential states are derived from SecureConfigStore on each getSettings().
        // No plaintext secrets are persisted in the settings table.
    }

    override suspend fun setAiApiCredential(secret: String) {
        val trimmed = secret.trim()
        require(trimmed.isNotBlank()) { "AI API credential must not be blank" }
        secureConfigStore.putSecret(SecureConfigKeys.AI_API, trimmed)
    }

    override suspend fun clearAiApiCredential() {
        secureConfigStore.clearSecret(SecureConfigKeys.AI_API)
    }
}
