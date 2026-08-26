package com.avsp.pro.audio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.DiscoveredVoice
import com.avsp.pro.audio.contract.VoiceAssignment
import com.avsp.pro.audio.contract.VoiceCloneProfile
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.VoiceCloneTtsEngine
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.repository.AudioRepository
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioTtsForm(
    val voiceMode: VoiceMode = VoiceMode.SIMPLE_LOCAL,
    val language: String = "en",
    val providerId: String = "mock",
    val voiceId: String = "default",
    val introVoiceId: String = "",
    val bodyVoiceId: String = "",
    val outroVoiceId: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
)

class AudioTtsViewModel(
    private val audioRepository: AudioRepository,
    private val projectRepository: ProjectRepository,
    private val logger: AvspLogger
) : ViewModel() {

    private val _form = MutableStateFlow(AudioTtsForm())
    val form: StateFlow<AudioTtsForm> = _form.asStateFlow()

    private val _state = MutableStateFlow<UiState<AudioPackage>>(UiState.Idle)
    val state: StateFlow<UiState<AudioPackage>> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<DiscoveredVoice>>(emptyList())
    val voices: StateFlow<List<DiscoveredVoice>> = _voices.asStateFlow()

    private val _scriptReady = MutableStateFlow(false)
    val scriptReady: StateFlow<Boolean> = _scriptReady.asStateFlow()

    private val _projectName = MutableStateFlow("")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _cloneProfile = MutableStateFlow<VoiceCloneProfile?>(null)
    val cloneProfile: StateFlow<VoiceCloneProfile?> = _cloneProfile.asStateFlow()

    private val _cloneConfigured = MutableStateFlow(false)
    val cloneConfigured: StateFlow<Boolean> = _cloneConfigured.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _playPath = MutableStateFlow<String?>(null)
    val playPath: StateFlow<String?> = _playPath.asStateFlow()

    private val _playlist = MutableStateFlow<List<String>>(emptyList())
    val playlist: StateFlow<List<String>> = _playlist.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private var projectId: String = ""
    private var playlistIndex = 0

    fun start(projectId: String) {
        this.projectId = projectId
        viewModelScope.launch {
            refreshVoices()
            _cloneConfigured.value = audioRepository.isVoiceCloneConfigured()
            _cloneProfile.value = audioRepository.voiceCloneProfile()
            val handoff = audioRepository.loadScriptHandoff(projectId)
            _scriptReady.value = handoff != null && handoff.segments.isNotEmpty()
            _projectName.value = runCatching { projectRepository.openProject(projectId).name }.getOrElse { projectId }
            val existing = audioRepository.load(projectId)
            if (existing != null) {
                val assignment = existing.assignment
                _form.value = _form.value.copy(
                    voiceMode = assignment.voiceMode,
                    language = assignment.language.ifBlank { existing.language },
                    providerId = assignment.providerId,
                    voiceId = assignment.voiceId,
                    introVoiceId = assignment.introVoiceId.orEmpty(),
                    bodyVoiceId = assignment.bodyVoiceId.orEmpty(),
                    outroVoiceId = assignment.outroVoiceId.orEmpty()
                )
                refreshVoices()
                _state.value = UiState.Success(existing)
            } else {
                _state.value = UiState.Idle
            }
        }
    }

    fun updateVoiceMode(mode: VoiceMode) {
        _form.value = _form.value.copy(
            voiceMode = mode,
            providerId = if (mode == VoiceMode.MY_VOICE_CLONE) {
                VoiceCloneTtsEngine.PROVIDER_ID
            } else {
                audioRepository.listProviders().firstOrNull { it.first != VoiceCloneTtsEngine.PROVIDER_ID }?.first
                    ?: "mock"
            }
        )
        refreshVoices()
        markVoiceStale()
    }

    fun updateLanguage(code: String) {
        _form.value = _form.value.copy(language = code)
        refreshVoices()
    }

    fun updateVoice(voiceId: String) {
        _form.value = _form.value.copy(voiceId = voiceId)
        markVoiceStale()
    }

    fun updateIntroVoice(voiceId: String) {
        _form.value = _form.value.copy(introVoiceId = voiceId)
        markVoiceStale()
    }

    fun updateBodyVoice(voiceId: String) {
        _form.value = _form.value.copy(bodyVoiceId = voiceId)
        markVoiceStale()
    }

    fun updateOutroVoice(voiceId: String) {
        _form.value = _form.value.copy(outroVoiceId = voiceId)
        markVoiceStale()
    }

    fun updateSceneVoice(sceneId: String, voiceId: String) {
        viewModelScope.launch {
            val current = (_state.value as? UiState.Success)?.data ?: return@launch
            val assignment = current.assignment.copy(
                sceneVoiceIds = current.assignment.sceneVoiceIds + (sceneId to voiceId)
            )
            val saved = audioRepository.save(current.copy(assignment = assignment))
            val refreshed = audioRepository.refreshStaleFlags(projectId) ?: saved
            _state.value = UiState.Success(refreshed)
        }
    }

    fun generate() = runGeneration { assignment ->
        audioRepository.generate(
            AudioGenerationRequest(
                projectId = projectId,
                preferredProviderId = assignment.providerId,
                assignment = assignment,
                voice = VoiceSettings(
                    language = assignment.language,
                    voiceId = assignment.voiceId,
                    providerId = assignment.providerId
                )
            )
        )
    }

    fun regenerateAll() = generate()

    fun regenerateClip(sceneId: String) = runGeneration { assignment ->
        audioRepository.regenerateClip(projectId, sceneId, assignment)
    }

    fun previewSegment(relativePath: String) {
        stopInternal()
        try {
            val abs = audioRepository.resolveAbsolutePath(projectId, relativePath)
            _playlist.value = emptyList()
            _playPath.value = abs
            _paused.value = false
            _message.value = "Preview ready"
        } catch (e: Exception) {
            _message.value = "AUDIO_PLAYBACK_FAILED: ${e.message}"
        }
    }

    fun previewEntire() {
        val audio = (_state.value as? UiState.Success)?.data ?: return
        val paths = audio.segments.filter { it.isPlayable() }.sortedBy { it.order }.map {
            audioRepository.resolveAbsolutePath(projectId, it.relativeAudioPath)
        }
        if (paths.isEmpty()) {
            _message.value = "No playable audio yet"
            return
        }
        stopInternal()
        playlistIndex = 0
        _playlist.value = paths
        _playPath.value = paths.first()
        _paused.value = false
    }

    fun pauseOrResume() {
        _paused.value = !_paused.value
    }

    fun stopPlayback() {
        stopInternal()
    }

    fun onClipCompleted() {
        val list = _playlist.value
        if (list.isEmpty()) {
            _playPath.value = null
            return
        }
        playlistIndex += 1
        if (playlistIndex < list.size) {
            _playPath.value = list[playlistIndex]
        } else {
            stopInternal()
        }
    }

    fun clearPlayPath() {
        if (_playlist.value.isNotEmpty()) {
            onClipCompleted()
        } else {
            _playPath.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopInternal()
    }

    private fun currentAssignment(): VoiceAssignment {
        val f = _form.value
        return VoiceAssignment(
            voiceMode = f.voiceMode,
            providerId = f.providerId,
            voiceId = f.voiceId.ifBlank { "default" },
            language = f.language,
            introVoiceId = f.introVoiceId.ifBlank { null },
            bodyVoiceId = f.bodyVoiceId.ifBlank { null },
            outroVoiceId = f.outroVoiceId.ifBlank { null }
        )
    }

    private fun runGeneration(block: suspend (VoiceAssignment) -> AudioPackage) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _message.value = null
            stopInternal()
            try {
                if (!_scriptReady.value) {
                    throw AudioException(
                        com.avsp.pro.audio.error.AudioErrorCode.SCRIPT_REQUIRED,
                        "SCRIPT_REQUIRED: save an M2 script before generating audio"
                    )
                }
                val assignment = currentAssignment()
                if (assignment.voiceMode == VoiceMode.MY_VOICE_CLONE && !audioRepository.isVoiceCloneConfigured()) {
                    throw AudioException(
                        com.avsp.pro.audio.error.AudioErrorCode.VOICE_CLONE_UNAVAILABLE,
                        "My Voice Clone is not configured."
                    )
                }
                val audio = block(assignment)
                _state.value = UiState.Success(audio)
                _message.value = "Audio package saved (${audio.provider})"
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

    private fun refreshVoices() {
        val lang = _form.value.language
        _voices.value = audioRepository.listVoices(lang)
        val first = _voices.value.firstOrNull()
        if (_form.value.voiceMode == VoiceMode.SIMPLE_LOCAL &&
            _voices.value.none { it.voiceId == _form.value.voiceId } &&
            first != null
        ) {
            _form.value = _form.value.copy(voiceId = first.voiceId, providerId = first.providerId)
        }
    }

    private fun markVoiceStale() {
        viewModelScope.launch {
            runCatching { audioRepository.refreshStaleFlags(projectId) }.getOrNull()?.let {
                _state.value = UiState.Success(it)
            }
        }
    }

    private fun stopInternal() {
        _playPath.value = null
        _playlist.value = emptyList()
        _paused.value = false
        playlistIndex = 0
    }
}
