package com.avsp.pro.audio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avsp.pro.audio.contract.AssignmentScope
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.LocalVoiceInfo
import com.avsp.pro.audio.contract.SegmentRole
import com.avsp.pro.audio.contract.SegmentVoiceAssignment
import com.avsp.pro.audio.contract.VoiceCloneProfile
import com.avsp.pro.audio.contract.VoiceCloneStatus
import com.avsp.pro.audio.contract.VoiceConfiguration
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.AndroidTtsEngine
import com.avsp.pro.audio.engine.MockTtsEngine
import com.avsp.pro.audio.engine.VoiceCloneTtsEngine
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.repository.AudioRepository
import com.avsp.pro.audio.voice.VoiceAssignmentResolver
import com.avsp.pro.core.ui.UiState
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.script.integration.ScriptNarrationHandoff
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioTtsForm(
    val voiceMode: VoiceMode = VoiceMode.SIMPLE_LOCAL,
    val assignmentScope: AssignmentScope = AssignmentScope.ENTIRE_PROJECT,
    val providerId: String = MockTtsEngine.PROVIDER_ID,
    val language: String = "en",
    val voiceId: String = "default",
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

    private val _localVoices = MutableStateFlow<List<LocalVoiceInfo>>(emptyList())
    val localVoices: StateFlow<List<LocalVoiceInfo>> = _localVoices.asStateFlow()

    private val _cloneProfile = MutableStateFlow(
        VoiceCloneProfile("", "My Voice Clone", VoiceCloneStatus.NOT_CONFIGURED)
    )
    val cloneProfile: StateFlow<VoiceCloneProfile> = _cloneProfile.asStateFlow()

    private val _scriptReady = MutableStateFlow(false)
    val scriptReady: StateFlow<Boolean> = _scriptReady.asStateFlow()

    private val _projectTitle = MutableStateFlow("")
    val projectTitle: StateFlow<String> = _projectTitle.asStateFlow()

    private val _handoff = MutableStateFlow<ScriptNarrationHandoff?>(null)
    val handoff: StateFlow<ScriptNarrationHandoff?> = _handoff.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _playPath = MutableStateFlow<String?>(null)
    val playPath: StateFlow<String?> = _playPath.asStateFlow()

    private val _previewQueue = MutableStateFlow<List<String>>(emptyList())
    val previewQueue: StateFlow<List<String>> = _previewQueue.asStateFlow()

    private var previewJob: Job? = null
    private var projectId: String = ""

    fun start(projectId: String) {
        this.projectId = projectId
        viewModelScope.launch {
            _providers.value = audioRepository.listProviders()
            _cloneProfile.value = audioRepository.getVoiceCloneProfile()
            val handoff = audioRepository.loadScriptHandoff(projectId)
            _handoff.value = handoff
            _scriptReady.value = handoff != null && handoff.segments.isNotEmpty()
            _projectTitle.value = handoff?.title ?: projectId

            val savedConfig = audioRepository.loadVoiceConfiguration(projectId)
            _form.value = AudioTtsForm(
                voiceMode = savedConfig.voiceMode,
                assignmentScope = savedConfig.assignmentScope,
                providerId = savedConfig.defaultProviderId,
                language = savedConfig.defaultLanguage,
                voiceId = savedConfig.defaultVoiceId,
                speechRate = savedConfig.speechRate,
                pitch = savedConfig.pitch
            )
            refreshVoices(savedConfig.defaultLanguage)

            val existing = audioRepository.refreshStaleState(projectId)
            if (existing != null) {
                _state.value = UiState.Success(existing)
            } else {
                _state.value = UiState.Idle
            }
        }
    }

    fun updateVoiceMode(mode: VoiceMode) {
        _form.value = _form.value.copy(
            voiceMode = mode,
            providerId = VoiceAssignmentResolver.defaultProviderForMode(mode)
        )
        persistVoiceConfiguration()
    }

    fun updateAssignmentScope(scope: AssignmentScope) {
        _form.value = _form.value.copy(assignmentScope = scope)
        persistVoiceConfiguration()
    }

    fun updateLanguage(language: String) {
        _form.value = _form.value.copy(language = language)
        refreshVoices(language)
        persistVoiceConfiguration()
    }

    fun updateVoice(voiceId: String) {
        _form.value = _form.value.copy(voiceId = voiceId)
        persistVoiceConfiguration()
    }

    fun updateProvider(id: String) {
        _form.value = _form.value.copy(providerId = id)
        persistVoiceConfiguration()
    }

    fun updateSpeechRate(rate: Float) {
        _form.value = _form.value.copy(speechRate = rate.coerceIn(0.5f, 2.0f))
        persistVoiceConfiguration()
    }

    fun updatePitch(pitch: Float) {
        _form.value = _form.value.copy(pitch = pitch.coerceIn(0.5f, 2.0f))
        persistVoiceConfiguration()
    }

    private fun refreshVoices(language: String) {
        viewModelScope.launch {
            _localVoices.value = audioRepository.listLocalVoices(language)
            if (_localVoices.value.isEmpty()) {
                _localVoices.value = listOf(
                    LocalVoiceInfo(
                        voiceId = "default",
                        displayName = "Default ($language)",
                        languageTag = language,
                        installed = true
                    )
                )
            }
        }
    }

    private fun buildVoiceConfiguration(): VoiceConfiguration {
        val f = _form.value
        val assignment = SegmentVoiceAssignment(
            segmentKey = "project",
            role = SegmentRole.BODY,
            voiceMode = f.voiceMode,
            voiceId = f.voiceId,
            language = f.language,
            providerId = f.providerId
        )
        return VoiceConfiguration(
            projectId = projectId,
            voiceMode = f.voiceMode,
            assignmentScope = f.assignmentScope,
            defaultLanguage = f.language,
            defaultVoiceId = f.voiceId,
            defaultProviderId = f.providerId,
            speechRate = f.speechRate,
            pitch = f.pitch,
            introAssignment = assignment.copy(role = SegmentRole.INTRO, segmentKey = "intro"),
            bodyAssignment = assignment.copy(role = SegmentRole.BODY, segmentKey = "body"),
            outroAssignment = assignment.copy(role = SegmentRole.OUTRO, segmentKey = "outro")
        )
    }

    private fun persistVoiceConfiguration() {
        if (projectId.isBlank()) return
        viewModelScope.launch {
            runCatching { audioRepository.saveVoiceConfiguration(buildVoiceConfiguration()) }
        }
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
                if (_form.value.voiceMode == VoiceMode.MY_VOICE_CLONE &&
                    _cloneProfile.value.status != VoiceCloneStatus.CONFIGURED
                ) {
                    throw AudioException(
                        com.avsp.pro.audio.error.AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED,
                        "My Voice Clone is not configured."
                    )
                }
                val config = audioRepository.saveVoiceConfiguration(buildVoiceConfiguration())
                val audio = audioRepository.generate(
                    AudioGenerationRequest(
                        projectId = projectId,
                        preferredProviderId = config.defaultProviderId,
                        voiceConfiguration = config
                    )
                )
                _state.value = UiState.Success(audio)
                _message.value = "Audio package generated (${audio.segments.count { it.status == AudioSegmentStatus.READY }} clips)"
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

    fun regenerateSegment(segmentId: String) {
        viewModelScope.launch {
            _message.value = null
            try {
                val audio = audioRepository.regenerateSegment(projectId, segmentId)
                _state.value = UiState.Success(audio)
                _message.value = "Regenerated $segmentId"
            } catch (e: Exception) {
                val msg = when (e) {
                    is AudioException -> "${e.errorCode.code}: ${e.message}"
                    else -> e.message ?: "Regeneration failed"
                }
                _message.value = msg
                logger.error("M3", msg, projectId = projectId)
            }
        }
    }

    fun previewSegment(relativePath: String) {
        try {
            val abs = audioRepository.resolveAbsolutePath(projectId, relativePath)
            _previewQueue.value = emptyList()
            _playPath.value = abs
        } catch (e: Exception) {
            _message.value = "AUDIO_PLAYBACK_FAILED: ${e.message}"
        }
    }

    fun previewEntireAudio() {
        val audio = (_state.value as? UiState.Success)?.data ?: return
        val paths = audio.segments
            .sortedBy { it.order }
            .filter { it.status == AudioSegmentStatus.READY || it.status == AudioSegmentStatus.GENERATED }
            .map { audioRepository.resolveAbsolutePath(projectId, it.relativeAudioPath) }
        if (paths.isEmpty()) {
            _message.value = "No ready audio clips to preview"
            return
        }
        _previewQueue.value = paths.drop(1)
        _playPath.value = paths.first()
        _message.value = "Previewing entire audio (${paths.size} clips)"
    }

    fun onPreviewCompleted() {
        val queue = _previewQueue.value
        if (queue.isEmpty()) {
            _playPath.value = null
            return
        }
        _playPath.value = queue.first()
        _previewQueue.value = queue.drop(1)
    }

    fun clearPlayPath() {
        previewJob?.cancel()
        _playPath.value = null
        _previewQueue.value = emptyList()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun segmentStatusLabel(segment: AudioSegment): String = when (segment.status) {
        AudioSegmentStatus.NOT_GENERATED, AudioSegmentStatus.PENDING -> "NOT_GENERATED"
        AudioSegmentStatus.GENERATING -> "GENERATING"
        AudioSegmentStatus.READY, AudioSegmentStatus.GENERATED -> "READY"
        AudioSegmentStatus.FAILED -> "FAILED"
        AudioSegmentStatus.STALE -> "STALE"
        AudioSegmentStatus.SKIPPED -> "SKIPPED"
    }
}
