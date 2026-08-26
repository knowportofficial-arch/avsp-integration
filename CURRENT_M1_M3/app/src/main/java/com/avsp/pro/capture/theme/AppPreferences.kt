package com.avsp.pro.capture.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small offline-first preference store for global AVSP appearance and field guidance. */
object AppPreferences {
    private const val PREFS = "avsp_preferences"
    private const val KEY_THEME = "theme"
    private const val KEY_ACCENT = "accent"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_VOICE = "voice_guidance"

    var theme by mutableStateOf(AppThemeOption.DARK)
        private set
    var accent by mutableStateOf(AppAccentOption.BLUE)
        private set
    var language by mutableStateOf(AppLanguageOption.ENGLISH)
        private set
    var voiceGuidanceEnabled by mutableStateOf(false)
        private set

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        theme = AppThemeOption.from(prefs.getString(KEY_THEME, AppThemeOption.DARK.name))
        accent = AppAccentOption.from(prefs.getString(KEY_ACCENT, AppAccentOption.BLUE.name))
        language = AppLanguageOption.from(prefs.getString(KEY_LANGUAGE, AppLanguageOption.ENGLISH.code))
        voiceGuidanceEnabled = prefs.getBoolean(KEY_VOICE, false)
        initialized = true
    }

    fun setTheme(context: Context, value: AppThemeOption) {
        theme = value
        persist(context, KEY_THEME, value.name)
    }

    fun setAccent(context: Context, value: AppAccentOption) {
        accent = value
        persist(context, KEY_ACCENT, value.name)
    }

    fun setLanguage(context: Context, value: AppLanguageOption) {
        language = value
        persist(context, KEY_LANGUAGE, value.code)
    }

    fun setVoiceGuidance(context: Context, enabled: Boolean) {
        voiceGuidanceEnabled = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE, enabled).apply()
    }

    private fun persist(context: Context, key: String, value: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }
}

enum class AppThemeOption {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun from(value: String?): AppThemeOption = entries.firstOrNull { it.name == value } ?: DARK
    }
}

enum class AppAccentOption(val displayName: String) {
    BLUE("Electric Blue"),
    CYAN("Cyan"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    ROSE("Rose");

    companion object {
        fun from(value: String?): AppAccentOption = values().firstOrNull { it.name == value } ?: BLUE
    }
}

enum class AppLanguageOption(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    BENGALI("bn", "বাংলা (Bengali)"),
    HINDI("hi", "हिंदी (Hindi)");

    companion object {
        fun from(value: String?): AppLanguageOption = values().firstOrNull { it.code == value } ?: ENGLISH
    }
}
