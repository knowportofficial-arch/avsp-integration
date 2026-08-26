package com.avsp.pro.script.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.repository.ProjectRepository
import com.avsp.pro.repository.SettingsRepository
import com.avsp.pro.script.contract.ContentType
import com.avsp.pro.script.contract.DurationRequest
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.repository.ScriptEditHelpers
import com.avsp.pro.script.repository.ScriptRepository
import com.avsp.pro.settings.ConfigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScriptAiForm(
    val topic: String = "",
    val languageCode: String = "en",
    val durationMode: String = "medium",
    val explicitDurationSec: String = "60",
    val contentType: ContentType = ContentType.SHORTS,
    val instructions: String = ""
)

class ScriptAiViewModel(
    private val scriptRepository: ScriptRepository,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _form = MutableStateFlow(ScriptAiForm())
    val form: StateFlow<ScriptAiForm> = _form.asStateFlow()

    private val _scriptState = MutableStateFlow<UiState<ScriptPackage>>(UiState.Idle)
    val scriptState: StateFlow<UiState<ScriptPackage>> = _scriptState.asStateFlow()

    private val _aiConfig = MutableStateFlow(ConfigState.NOT_CONFIGURED)
    val aiConfig: StateFlow<ConfigState> = _aiConfig.asStateFlow()

    private val _activeGenerator = MutableStateFlow("Mock Script Generator (mock/MOCK)")
    val activeGenerator: StateFlow<String> = _activeGenerator.asStateFlow()

    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage: StateFlow<String?> = _savedMessage.asStateFlow()

    private var projectId: String = ""

    fun start(projectId: String) {
        this.projectId = projectId
        viewModelScope.launch {
            _aiConfig.value = if (scriptRepository.isAiConfigured()) {
                ConfigState.CONFIGURED
            } else {
                ConfigState.NOT_CONFIGURED
            }
            _activeGenerator.value = scriptRepository.activeGeneratorLabel()
            val settings = runCatching { settingsRepository.getSettings() }.getOrNull()
            if (settings != null) {
                _form.value = _form.value.copy(languageCode = settings.defaultLanguage.code)
            }
            val existing = scriptRepository.load(projectId)
            if (existing != null) {
                _scriptState.value = UiState.Success(existing)
                _form.value = _form.value.copy(
                    topic = existing.topic,
                    languageCode = existing.language
                )
            } else {
                _scriptState.value = UiState.Idle
            }
        }
    }

    fun updateTopic(value: String) {
        _form.value = _form.value.copy(topic = value)
    }

    fun updateLanguage(code: String) {
        _form.value = _form.value.copy(languageCode = code)
    }

    fun updateDurationMode(mode: String) {
        _form.value = _form.value.copy(durationMode = mode)
    }

    fun updateExplicitDurationSec(value: String) {
        _form.value = _form.value.copy(explicitDurationSec = value.filter { it.isDigit() })
    }

    fun updateInstructions(value: String) {
        _form.value = _form.value.copy(instructions = value)
    }

    fun generate() {
        viewModelScope.launch {
            _scriptState.value = UiState.Loading
            _savedMessage.value = null
            try {
                projectRepository.openProject(projectId)
                val f = _form.value
                val duration = when (f.durationMode.lowercase()) {
                    "short" -> DurationRequest.ShortForm
                    "long" -> DurationRequest.LongForm
                    "explicit" -> {
                        val sec = f.explicitDurationSec.toLongOrNull()
                            ?: throw IllegalArgumentException("Explicit duration must be a number of seconds")
                        DurationRequest.Explicit(sec * 1000L)
                    }
                    else -> DurationRequest.MediumForm
                }
                val request = ScriptGenerationRequest(
                    projectId = projectId,
                    topic = f.topic,
                    languageCode = f.languageCode,
                    duration = duration,
                    contentType = f.contentType,
                    userInstructions = f.instructions.ifBlank { null }
                )
                val script = scriptRepository.generate(request)
                _activeGenerator.value = scriptRepository.activeGeneratorLabel()
                if (!script.validation.isValid) {
                    _scriptState.value = UiState.Error(
                        message = script.validation.errors.joinToString("\n"),
                        code = "SCRIPT_VALIDATION",
                        recoverable = true
                    )
                } else {
                    _scriptState.value = UiState.Success(script)
                    _savedMessage.value =
                        "Script generated via ${script.metadata.generatorId}/${script.metadata.generatorMode} and saved"
                }
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Generation failed"
                logger.error("M2", "Script generate failed", details = msg, projectId = projectId)
                _scriptState.value = UiState.Error(msg, recoverable = true)
            }
        }
    }

    fun updateTitle(title: String) = mutateSuccess { ScriptEditHelpers.updateTitle(it, title) }
    fun updateHook(hook: String) = mutateSuccess { ScriptEditHelpers.updateHook(it, hook) }
    fun updateCta(cta: String) = mutateSuccess { ScriptEditHelpers.updateCta(it, cta) }
    fun updateSceneNarration(sceneId: String, narration: String) =
        mutateSuccess { ScriptEditHelpers.updateSceneNarration(it, sceneId, narration) }

    fun saveEdits() {
        viewModelScope.launch {
            val current = (_scriptState.value as? UiState.Success)?.data ?: return@launch
            _scriptState.value = UiState.Loading
            try {
                val saved = scriptRepository.updateEdited(current)
                _scriptState.value = UiState.Success(saved)
                _savedMessage.value = "Script saved"
            } catch (e: Exception) {
                val msg = (e as? AvspException)?.errorInfo?.message ?: e.message ?: "Save failed"
                _scriptState.value = UiState.Error(msg, recoverable = true)
            }
        }
    }

    fun clearSavedMessage() {
        _savedMessage.value = null
    }

    private fun mutateSuccess(block: (ScriptPackage) -> ScriptPackage) {
        val current = (_scriptState.value as? UiState.Success)?.data ?: return
        _scriptState.value = UiState.Success(block(current))
    }
}
