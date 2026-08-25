package com.avsp.pro.settings

import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.logs.LogLevel

/**
 * Credential/configuration state — never expose raw secrets in UI.
 */
enum class ConfigState {
    CONFIGURED,
    NOT_CONFIGURED
}

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Core application settings. API secrets are never stored as plain fields in source.
 */
data class AppSettings(
    val defaultLanguage: ProjectLanguage = ProjectLanguage.ENGLISH,
    val defaultAspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val defaultOutputDirectoryRef: String = "generated/video",
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val loggingLevel: LogLevel = LogLevel.INFO,
    val aiConfigState: ConfigState = ConfigState.NOT_CONFIGURED,
    val publishingConfigState: ConfigState = ConfigState.NOT_CONFIGURED,
    val aiConfigReference: String? = null,
    val publishingConfigReference: String? = null
)

/**
 * Secure configuration abstraction for future credentials.
 * Implementations must use platform secure storage (e.g. EncryptedSharedPreferences).
 */
interface SecureConfigStore {
    fun putSecret(key: String, value: String)
    fun getSecret(key: String): String?
    fun clearSecret(key: String)
    fun hasSecret(key: String): Boolean
    fun configState(key: String): ConfigState =
        if (hasSecret(key)) ConfigState.CONFIGURED else ConfigState.NOT_CONFIGURED
}

object SecureConfigKeys {
    const val AI_API = "ai_api_credential"
    const val YOUTUBE = "youtube_credential"
    const val META = "meta_credential"
    const val TELEGRAM = "telegram_credential"
    const val WEB = "web_publishing_credential"
}
