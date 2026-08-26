package com.avsp.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.model.AspectRatio
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.repository.SettingsRepository
import com.avsp.pro.settings.AppSettings
import com.avsp.pro.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<AppSettings>>(UiState.Idle)
    val state: StateFlow<UiState<AppSettings>> = _state.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                _state.value = UiState.Success(settingsRepository.getSettings())
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to load settings"
                logger.error("M1", "Settings load failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }

    fun save(settings: AppSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.saveSettings(settings)
                logger.info("M1", "Settings saved")
                _saved.value = true
                _state.value = UiState.Success(settingsRepository.getSettings())
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to save settings"
                logger.error("M1", "Settings save failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }

    fun updateLanguage(language: ProjectLanguage) = mutate { it.copy(defaultLanguage = language) }
    fun updateAspect(aspect: AspectRatio) = mutate { it.copy(defaultAspectRatio = aspect) }
    fun updateTheme(theme: ThemePreference) = mutate { it.copy(themePreference = theme) }
    fun updateLogLevel(level: LogLevel) = mutate { it.copy(loggingLevel = level) }
    fun updateOutputRef(ref: String) = mutate { it.copy(defaultOutputDirectoryRef = ref) }

    fun saveAiApiCredential(secret: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setAiApiCredential(secret)
                logger.info("M1", "AI API credential configured")
                _saved.value = true
                _state.value = UiState.Success(settingsRepository.getSettings())
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to save AI credential"
                logger.error("M1", "AI credential save failed", details = msg)
                _state.value = UiState.Error(msg)
            }
        }
    }

    fun clearAiApiCredential() {
        viewModelScope.launch {
            try {
                settingsRepository.clearAiApiCredential()
                logger.info("M1", "AI API credential cleared")
                _saved.value = true
                _state.value = UiState.Success(settingsRepository.getSettings())
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Failed to clear AI credential"
                _state.value = UiState.Error(msg)
            }
        }
    }

    fun clearSavedFlag() {
        _saved.value = false
    }

    private fun mutate(block: (AppSettings) -> AppSettings) {
        val current = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(block(current))
    }
}
