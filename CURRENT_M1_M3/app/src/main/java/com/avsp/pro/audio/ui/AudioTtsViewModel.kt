package com.avsp.pro.audio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.repository.AudioRepository
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.settings.ConfigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioTtsForm(
    val providerId: String = "mock",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
)

class AudioTtsViewModel(
    private val audioRepository: AudioRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _form = MutableStateFlow(AudioTtsForm())
    val form: StateFlow<AudioTtsForm> = _form.asStateFlow()

    private val _state = MutableStateFlow<UiState<AudioPackage>>(UiState.Idle)
    val state: StateFlow<UiState<AudioPackage>> = _state.asStateFlow()

    private val _providers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val providers: StateFlow<List<Pair<String, String>>> = _providers.asStateFlow()

    private val _aiConfig = MutableStateFlow(ConfigState.NOT_CONFIGURED)
    val aiConfig: StateFlow<ConfigState> = _aiConfig.asStateFlow()

    private val _scriptReady = MutableStateFlow(false)
    val scriptReady: StateFlow<Boolean> = _scriptReady.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _playPath = MutableStateFlow<String?>(null)
    val playPath: StateFlow<String?> = _playPath.asStateFlow()

    private var projectId: String = ""

    fun start(projectId: String) {
        this.projectId = projectId
        viewModelScope.launch {
            _providers.value = audioRepository.listProviders()
            _aiConfig.value = if (audioRepository.isRemoteConfigured()) {
                ConfigState.CONFIGURED
            } else {
                ConfigState.NOT_CONFIGURED
            }
            val handoff = audioRepository.loadScriptHandoff(projectId)
            _scriptReady.value = handoff != null && handoff.segments.isNotEmpty()
            if (_providers.value.isNotEmpty()) {
                _form.value = _form.value.copy(providerId = _providers.value.first().first)
            }
            val existing = audioRepository.load(projectId)
            if (existing != null) {
                _state.value = UiState.Success(existing)
            } else {
                _state.value = UiState.Idle
            }
        }
    }

    fun updateProvider(id: String) {
        _form.value = _form.value.copy(providerId = id)
    }

    fun generate() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _message.value = null
            _playPath.value = null
            try {
                if (!_scriptReady.value) {
                    throw AudioException(
                        com.avsp.pro.audio.error.AudioErrorCode.SCRIPT_REQUIRED,
                        "SCRIPT_REQUIRED: save an M2 script before generating audio"
                    )
                }
                val f = _form.value
                val handoff = audioRepository.loadScriptHandoff(projectId)
                val voice = VoiceSettings(
                    language = handoff?.language ?: "en",
                    speechRate = f.speechRate,
                    pitch = f.pitch,
                    providerId = f.providerId
                )
                val audio = audioRepository.generate(
                    AudioGenerationRequest(
                        projectId = projectId,
                        preferredProviderId = f.providerId,
                        voice = voice
                    )
                )
                _state.value = UiState.Success(audio)
                _message.value = "Audio package generated (${audio.provider})"
            } catch (e: Exception) {
                val msg = when (e) {
                    is AudioException -> "${e.errorCode.code}: ${e.message}"
                    else -> e.message ?: "Audio generation failed"
                }
                logger.error("M3", msg, projectId = projectId)
                _state.value = UiState.Error(msg, code = (e as? AudioException)?.errorCode?.code, recoverable = true)
            }
        }
    }

    fun regenerate() = generate()

    fun previewSegment(relativePath: String) {
        try {
            val abs = audioRepository.resolveAbsolutePath(projectId, relativePath)
            _playPath.value = abs
            _message.value = "Preview ready"
        } catch (e: Exception) {
            _message.value = "AUDIO_PLAYBACK_FAILED: ${e.message}"
            logger.error("M3", "Playback path resolve failed", details = e.message, projectId = projectId)
        }
    }

    fun clearPlayPath() {
        _playPath.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}
