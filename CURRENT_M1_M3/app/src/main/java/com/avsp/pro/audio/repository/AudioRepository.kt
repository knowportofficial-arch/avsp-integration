package com.avsp.pro.audio.repository

import com.avsp.pro.audio.contract.AudioFormatInfo
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioPackageMetadata
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.ClipRole
import com.avsp.pro.audio.contract.DiscoveredVoice
import com.avsp.pro.audio.contract.VoiceAssignment
import com.avsp.pro.audio.contract.VoiceCloneProfile
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.TtsEngine
import com.avsp.pro.audio.engine.TtsEngineRegistry
import com.avsp.pro.audio.engine.TtsSynthesisRequest
import com.avsp.pro.audio.engine.VOICE_CLONE_NOT_CONFIGURED
import com.avsp.pro.audio.engine.VoiceCloneProvider
import com.avsp.pro.audio.engine.VoiceCloneTtsEngine
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.plan.AudioPlanClip
import com.avsp.pro.audio.plan.ScriptAudioPlanner
import com.avsp.pro.audio.util.NarrationHash
import com.avsp.pro.audio.validation.AudioValidator
import com.avsp.pro.audio.wav.WavEncoder
import com.avsp.pro.core.integration.ArtifactNames
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.script.integration.ScriptNarrationHandoff
import com.avsp.pro.script.integration.ScriptToTtsContract
import com.avsp.pro.script.repository.ScriptRepository
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.UUID
import kotlin.math.abs

interface AudioRepository {
    suspend fun loadScriptHandoff(projectId: String): ScriptNarrationHandoff?
    suspend fun generate(request: AudioGenerationRequest): AudioPackage
    suspend fun save(audio: AudioPackage): AudioPackage
    suspend fun load(projectId: String, audioPackageId: String? = null): AudioPackage?
    suspend fun regenerate(projectId: String, preferredProviderId: String? = null): AudioPackage
    suspend fun regenerateClip(projectId: String, sceneId: String, assignment: VoiceAssignment? = null): AudioPackage
    suspend fun refreshStaleFlags(projectId: String): AudioPackage?
    fun listProviders(): List<Pair<String, String>>
    fun listVoices(languageCode: String? = null): List<DiscoveredVoice>
    fun voiceCloneProfile(): VoiceCloneProfile?
    fun isVoiceCloneConfigured(): Boolean
    fun isRemoteConfigured(): Boolean
    fun resolveAbsolutePath(projectId: String, relativePath: String): String
}

class AudioRepositoryImpl(
    private val storage: AvspStorage,
    private val scriptRepository: ScriptRepository,
    private val ttsRegistry: TtsEngineRegistry,
    private val logger: AvspLogger,
    private val voiceCloneProvider: VoiceCloneProvider,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : AudioRepository {

    override suspend fun loadScriptHandoff(projectId: String): ScriptNarrationHandoff? {
        val script = scriptRepository.load(projectId) ?: return null
        return ScriptToTtsContract.fromPackage(script)
    }

    override suspend fun generate(request: AudioGenerationRequest): AudioPackage {
        val script = scriptRepository.load(request.projectId)
            ?: throw AudioException(AudioErrorCode.SCRIPT_REQUIRED, "SCRIPT_REQUIRED: no M2 script")
        val plan = ScriptAudioPlanner.fromScript(script)
        if (plan.isEmpty()) {
            throw AudioException(AudioErrorCode.SCRIPT_REQUIRED, "SCRIPT_REQUIRED: narration is empty")
        }
        val handoff = ScriptToTtsContract.fromPackage(script)
        val handoffValidation = AudioValidator.validateHandoff(handoff)
        if (!handoffValidation.isValid) {
            throw AudioException(
                AudioErrorCode.SCRIPT_REQUIRED,
                handoffValidation.errors.firstOrNull() ?: "SCRIPT_REQUIRED",
                details = handoffValidation.errors.joinToString("; ")
            )
        }

        val assignment = request.assignment ?: VoiceAssignment(
            voiceMode = if (request.preferredProviderId == VoiceCloneTtsEngine.PROVIDER_ID) {
                VoiceMode.MY_VOICE_CLONE
            } else {
                VoiceMode.SIMPLE_LOCAL
            },
            providerId = request.preferredProviderId
                ?: request.voice?.providerId
                ?: ttsRegistry.resolve().providerId,
            voiceId = request.voice?.voiceId ?: "default",
            language = script.language
        )

        if (assignment.voiceMode == VoiceMode.MY_VOICE_CLONE && !voiceCloneProvider.isConfigured()) {
            throw AudioException(AudioErrorCode.VOICE_CLONE_UNAVAILABLE, VOICE_CLONE_NOT_CONFIGURED)
        }

        val existing = if (request.reuseExistingPackage) load(request.projectId) else null
        val audioPackageId = existing?.audioPackageId
            ?: ("aud_" + UUID.randomUUID().toString().replace("-", "").take(16))
        storage.ensureProjectLayout(request.projectId)

        val onlyScene = request.clipSceneId
        val clipsToGenerate = if (onlyScene == null) plan else plan.filter { it.sceneId == onlyScene }
        if (clipsToGenerate.isEmpty()) {
            throw AudioException(AudioErrorCode.INVALID_INPUT, "No matching clip for regeneration")
        }

        val priorByScene = existing?.segments?.associateBy { it.sceneId }.orEmpty()
        val driftWarnings = existing?.metadata?.timingDriftWarnings.orEmpty().toMutableList()
        val generatedByScene = priorByScene.toMutableMap()

        clipsToGenerate.forEach { clip ->
            generatedByScene[clip.sceneId] = synthesizeClip(
                projectId = request.projectId,
                audioPackageId = audioPackageId,
                clip = clip,
                assignment = assignment,
                requestVoice = request.voice,
                driftWarnings = driftWarnings
            )
        }

        val ordered = plan.map { clip ->
            generatedByScene[clip.sceneId] ?: placeholder(clip, assignment)
        }
        val withTimeline = applyTimeline(ordered)
        val now = System.currentTimeMillis()
        val provider = assignment.providerId
        val voice = VoiceSettings(
            language = assignment.language,
            voiceId = assignment.voiceId,
            speechRate = request.voice?.speechRate ?: 1.0f,
            pitch = request.voice?.pitch ?: 1.0f,
            providerId = provider
        )
        val draft = AudioPackage(
            projectId = request.projectId,
            scriptId = script.scriptId,
            audioPackageId = audioPackageId,
            language = script.language,
            provider = provider,
            voice = voice,
            segments = withTimeline,
            totalDurationMs = withTimeline.sumOf { it.durationMs },
            validation = AudioValidationPlaceholder,
            metadata = AudioPackageMetadata(
                createdAt = existing?.metadata?.createdAt ?: now,
                updatedAt = now,
                scriptVersion = script.version,
                format = AudioFormatInfo(
                    format = "wav",
                    sampleRateHz = WavEncoder.SAMPLE_RATE,
                    channels = WavEncoder.CHANNELS,
                    encoding = "pcm_s16le",
                    bitsPerSample = WavEncoder.BITS_PER_SAMPLE
                ),
                voice = voice,
                timingDriftWarnings = driftWarnings.distinct()
            ),
            assignment = assignment,
            generatedAt = now
        )
        val validated = draft.copy(validation = AudioValidator.validatePackage(draft))
        logger.info("M3", "Audio package generated via $provider", projectId = request.projectId)
        return save(validated)
    }

    override suspend fun save(audio: AudioPackage): AudioPackage {
        val validated = audio.copy(
            validation = AudioValidator.validatePackage(audio),
            metadata = audio.metadata.copy(updatedAt = System.currentTimeMillis())
        )
        if (!validated.validation.isValid) {
            throw AudioException(
                AudioErrorCode.INVALID_AUDIO_TIMELINE,
                validated.validation.errors.firstOrNull() ?: "Invalid audio package"
            )
        }
        val packagePath = "${ProjectPaths.AUDIO}/${validated.audioPackageId}/package.json"
        storage.save(
            StorageArea.PROJECT_DATA,
            packagePath,
            gson.toJson(validated),
            validated.projectId
        )
        storage.save(
            StorageArea.PROJECT_DATA,
            "${ProjectPaths.AUDIO}/${ArtifactNames.VOICE_JSON}",
            gson.toJson(validated),
            validated.projectId
        )
        logger.info("M3", "Audio package saved", details = validated.audioPackageId, projectId = validated.projectId)
        return validated
    }

    override suspend fun load(projectId: String, audioPackageId: String?): AudioPackage? {
        val relative = if (audioPackageId != null) {
            "${ProjectPaths.AUDIO}/$audioPackageId/package.json"
        } else {
            "${ProjectPaths.AUDIO}/${ArtifactNames.VOICE_JSON}"
        }
        if (!storage.exists(StorageArea.PROJECT_DATA, relative, projectId)) return null
        val json = storage.readText(StorageArea.PROJECT_DATA, relative, projectId)
        val loaded = gson.fromJson(json, AudioPackage::class.java) ?: return null
        return markStaleAgainstScript(loaded)
    }

    override suspend fun regenerate(projectId: String, preferredProviderId: String?): AudioPackage {
        val existing = load(projectId)
        return generate(
            AudioGenerationRequest(
                projectId = projectId,
                preferredProviderId = preferredProviderId ?: existing?.assignment?.providerId,
                assignment = existing?.assignment,
                voice = existing?.voice,
                reuseExistingPackage = false
            )
        )
    }

    override suspend fun regenerateClip(
        projectId: String,
        sceneId: String,
        assignment: VoiceAssignment?
    ): AudioPackage {
        val existing = load(projectId)
        return generate(
            AudioGenerationRequest(
                projectId = projectId,
                preferredProviderId = assignment?.providerId ?: existing?.assignment?.providerId,
                assignment = assignment ?: existing?.assignment,
                voice = existing?.voice,
                clipSceneId = sceneId
            )
        )
    }

    override suspend fun refreshStaleFlags(projectId: String): AudioPackage? {
        val loaded = load(projectId) ?: return null
        return save(loaded)
    }

    override fun listProviders(): List<Pair<String, String>> =
        ttsRegistry.available().map { it.providerId to it.displayName }

    override fun listVoices(languageCode: String?): List<DiscoveredVoice> {
        val all = ttsRegistry.available().flatMap { it.listVoices() }
        val lang = languageCode?.trim()?.lowercase().orEmpty()
        if (lang.isBlank()) return all
        return all.filter { it.languageCode.equals(lang, ignoreCase = true) || it.locale.startsWith(lang) }
    }

    override fun voiceCloneProfile(): VoiceCloneProfile? = voiceCloneProvider.profile()

    override fun isVoiceCloneConfigured(): Boolean = voiceCloneProvider.isConfigured()

    override fun isRemoteConfigured(): Boolean = ttsRegistry.isRemoteConfigured()

    override fun resolveAbsolutePath(projectId: String, relativePath: String): String =
        storage.resolve(StorageArea.PROJECT_DATA, relativePath, projectId)

    private suspend fun synthesizeClip(
        projectId: String,
        audioPackageId: String,
        clip: AudioPlanClip,
        assignment: VoiceAssignment,
        requestVoice: VoiceSettings?,
        driftWarnings: MutableList<String>
    ): AudioSegment {
        val providerId = assignment.resolveProviderId(clip.role, clip.sceneId)
        val voiceId = assignment.resolveVoiceId(clip.role, clip.sceneId)
        if (assignment.voiceMode == VoiceMode.MY_VOICE_CLONE ||
            providerId == VoiceCloneTtsEngine.PROVIDER_ID
        ) {
            if (!voiceCloneProvider.isConfigured()) {
                return failedClip(
                    clip, assignment, providerId, voiceId,
                    AudioErrorCode.VOICE_CLONE_UNAVAILABLE, VOICE_CLONE_NOT_CONFIGURED
                )
            }
        }
        val engine = try {
            resolveEngine(providerId)
        } catch (e: AudioException) {
            return failedClip(clip, assignment, providerId, voiceId, e.errorCode, e.message ?: e.errorCode.code)
        }
        val language = clip.language
        if (!engine.supportsLanguage(language) && engine.providerId != VoiceCloneTtsEngine.PROVIDER_ID) {
            return failedClip(
                clip, assignment, providerId, voiceId,
                AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE,
                "Language pack unavailable locally: $language"
            )
        }
        val voice = VoiceSettings(
            language = language,
            voiceId = voiceId,
            speechRate = requestVoice?.speechRate ?: 1.0f,
            pitch = requestVoice?.pitch ?: 1.0f,
            providerId = engine.providerId
        )
        return try {
            val result = engine.synthesize(
                TtsSynthesisRequest(
                    text = clip.narration,
                    language = language,
                    voice = voice,
                    targetDurationMs = clip.plannedDurationMs
                )
            )
            val relative = "${ProjectPaths.AUDIO}/$audioPackageId/${ScriptAudioPlanner.relativeWavName(clip)}"
            storage.save(StorageArea.PROJECT_DATA, relative, result.audioBytes, projectId)
            val durationMs = result.durationMs.coerceAtLeast(1L)
            val planned = clip.plannedDurationMs
            val delta = durationMs - planned
            if (planned > 0 && abs(delta).toDouble() / planned > AudioValidator.TIMING_DRIFT_WARN_RATIO) {
                driftWarnings += "${clip.sceneId}: actual ${durationMs}ms vs planned ${planned}ms"
            }
            AudioSegment(
                segmentId = clip.clipId,
                sceneId = clip.sceneId,
                order = clip.order,
                sourceText = clip.narration,
                relativeAudioPath = relative,
                durationMs = durationMs,
                startMs = 0L,
                endMs = durationMs,
                provider = engine.providerId,
                language = language,
                status = AudioSegmentStatus.READY,
                plannedDurationMs = planned,
                durationDeltaMs = delta,
                role = clip.role,
                title = clip.title,
                voiceId = result.voiceId.ifBlank { voiceId },
                sourceTextHash = NarrationHash.sha256(clip.narration),
                generatedAt = System.currentTimeMillis()
            )
        } catch (e: AudioException) {
            logger.error("M3", e.message ?: "clip failed", details = e.errorCode.code, projectId = projectId)
            failedClip(clip, assignment, providerId, voiceId, e.errorCode, e.message ?: e.errorCode.code)
        } catch (e: Exception) {
            failedClip(
                clip, assignment, providerId, voiceId,
                AudioErrorCode.TTS_GENERATION_FAILED,
                e.message ?: "TTS generation failed"
            )
        }
    }

    private fun resolveEngine(providerId: String): TtsEngine {
        return try {
            ttsRegistry.resolve(providerId)
        } catch (e: Exception) {
            throw AudioException(
                AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                "TTS provider unavailable: $providerId",
                details = e.message,
                cause = e
            )
        }
    }

    private fun failedClip(
        clip: AudioPlanClip,
        assignment: VoiceAssignment,
        providerId: String,
        voiceId: String,
        code: AudioErrorCode,
        message: String
    ) = AudioSegment(
        segmentId = clip.clipId,
        sceneId = clip.sceneId,
        order = clip.order,
        sourceText = clip.narration,
        relativeAudioPath = "",
        durationMs = 0L,
        startMs = 0L,
        endMs = 0L,
        provider = providerId,
        language = clip.language,
        status = AudioSegmentStatus.FAILED,
        plannedDurationMs = clip.plannedDurationMs,
        errorCode = code.code,
        errorMessage = message,
        role = clip.role,
        title = clip.title,
        voiceId = voiceId,
        sourceTextHash = NarrationHash.sha256(clip.narration)
    )

    private fun placeholder(clip: AudioPlanClip, assignment: VoiceAssignment) = AudioSegment(
        segmentId = clip.clipId,
        sceneId = clip.sceneId,
        order = clip.order,
        sourceText = clip.narration,
        relativeAudioPath = "",
        durationMs = 0L,
        startMs = 0L,
        endMs = 0L,
        provider = assignment.resolveProviderId(clip.role, clip.sceneId),
        language = clip.language,
        status = AudioSegmentStatus.NOT_GENERATED,
        plannedDurationMs = clip.plannedDurationMs,
        role = clip.role,
        title = clip.title,
        voiceId = assignment.resolveVoiceId(clip.role, clip.sceneId),
        sourceTextHash = NarrationHash.sha256(clip.narration)
    )

    private fun applyTimeline(segments: List<AudioSegment>): List<AudioSegment> {
        var cursor = 0L
        return segments.sortedBy { it.order }.map { seg ->
            val dur = if (seg.isPlayable()) seg.durationMs else 0L
            val start = cursor
            val end = start + dur
            cursor = end
            seg.copy(startMs = start, endMs = end, durationMs = dur, order = seg.order)
        }
    }

    private suspend fun markStaleAgainstScript(audio: AudioPackage): AudioPackage {
        val script = scriptRepository.load(audio.projectId) ?: return audio
        val plan = ScriptAudioPlanner.fromScript(script).associateBy { it.sceneId }
        val updated = audio.segments.map { seg ->
            val current = plan[seg.sceneId] ?: return@map seg
            val hash = NarrationHash.sha256(current.narration)
            val voiceChanged = seg.voiceId != audio.assignment.resolveVoiceId(seg.role, seg.sceneId) &&
                seg.isPlayable()
            val textChanged = seg.sourceTextHash.isNotBlank() &&
                seg.sourceTextHash != hash &&
                seg.isPlayable()
            val textPlainChanged = current.narration.trim() != seg.sourceText.trim() && seg.isPlayable()
            if (textChanged || textPlainChanged || voiceChanged) {
                seg.copy(
                    status = AudioSegmentStatus.STALE,
                    sourceText = current.narration,
                    sourceTextHash = hash,
                    title = current.title.ifBlank { seg.title }
                )
            } else {
                seg.copy(sourceText = current.narration, title = current.title.ifBlank { seg.title })
            }
        }
        val timed = applyTimeline(updated)
        return audio.copy(
            segments = timed,
            totalDurationMs = timed.sumOf { it.durationMs },
            scriptId = script.scriptId
        )
    }

    private companion object {
        val AudioValidationPlaceholder = com.avsp.pro.audio.contract.AudioValidation(
            isValid = true,
            status = com.avsp.pro.audio.contract.AudioValidationStatus.VALID
        )
    }
}
