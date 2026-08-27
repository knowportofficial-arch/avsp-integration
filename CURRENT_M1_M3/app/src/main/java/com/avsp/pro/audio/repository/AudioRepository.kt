package com.avsp.pro.audio.repository

import com.avsp.pro.audio.contract.AssignmentScope
import com.avsp.pro.audio.contract.AudioFormatInfo
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioPackageStatus
import com.avsp.pro.audio.contract.AudioPackageMetadata
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.LocalVoiceInfo
import com.avsp.pro.audio.contract.SegmentRole
import com.avsp.pro.audio.contract.VoiceCloneProfile
import com.avsp.pro.audio.contract.VoiceConfiguration
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.TtsEngineRegistry
import com.avsp.pro.audio.engine.TtsSynthesisRequest
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.integration.M3AudioPlanItem
import com.avsp.pro.audio.integration.M3ScriptAudioPlan
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.audio.validation.AudioValidator
import com.avsp.pro.audio.voice.LocalVoiceCatalog
import com.avsp.pro.audio.voice.VoiceAssignmentResolver
import com.avsp.pro.audio.voice.VoiceCloneProfileStore
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
    suspend fun loadVoiceConfiguration(projectId: String): VoiceConfiguration
    suspend fun saveVoiceConfiguration(config: VoiceConfiguration): VoiceConfiguration
    suspend fun listLocalVoices(languageCode: String? = null): List<LocalVoiceInfo>
    fun getVoiceCloneProfile(): VoiceCloneProfile
    suspend fun generate(request: AudioGenerationRequest): AudioPackage
    suspend fun regenerateSegment(projectId: String, segmentId: String): AudioPackage
    suspend fun save(audio: AudioPackage): AudioPackage
    suspend fun load(projectId: String, audioPackageId: String? = null): AudioPackage?
    suspend fun refreshStaleState(projectId: String): AudioPackage?
    suspend fun regenerate(projectId: String, preferredProviderId: String? = null): AudioPackage
    fun listProviders(): List<Pair<String, String>>
    fun isRemoteConfigured(): Boolean
    fun resolveAbsolutePath(projectId: String, relativePath: String): String
}

class AudioRepositoryImpl(
    private val storage: AvspStorage,
    private val scriptRepository: ScriptRepository,
    private val ttsRegistry: TtsEngineRegistry,
    private val voiceCatalog: LocalVoiceCatalog,
    private val voiceCloneProfileStore: VoiceCloneProfileStore,
    private val logger: AvspLogger,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : AudioRepository {

    override suspend fun loadScriptHandoff(projectId: String): ScriptNarrationHandoff? {
        val script = scriptRepository.load(projectId) ?: return null
        return ScriptToTtsContract.fromPackage(script)
    }

    override suspend fun loadVoiceConfiguration(projectId: String): VoiceConfiguration {
        val path = voiceConfigPath()
        if (!storage.exists(StorageArea.PROJECT_DATA, path, projectId)) {
            val script = scriptRepository.load(projectId)
            return VoiceConfiguration(
                projectId = projectId,
                defaultLanguage = script?.language ?: "en"
            )
        }
        return gson.fromJson(
            storage.readText(StorageArea.PROJECT_DATA, path, projectId),
            VoiceConfiguration::class.java
        )
    }

    override suspend fun saveVoiceConfiguration(config: VoiceConfiguration): VoiceConfiguration {
        storage.ensureProjectLayout(config.projectId)
        val saved = config.copy(updatedAt = System.currentTimeMillis())
        storage.save(
            StorageArea.PROJECT_DATA,
            voiceConfigPath(),
            gson.toJson(saved),
            config.projectId
        )
        markVoiceAssignmentStaleIfNeeded(config.projectId, saved)
        return saved
    }

    override suspend fun listLocalVoices(languageCode: String?): List<LocalVoiceInfo> =
        voiceCatalog.listVoices(languageCode)

    override fun getVoiceCloneProfile(): VoiceCloneProfile = voiceCloneProfileStore.load()

    override suspend fun generate(request: AudioGenerationRequest): AudioPackage {
        val script = scriptRepository.load(request.projectId)
            ?: throw AudioException(AudioErrorCode.SCRIPT_REQUIRED, "SCRIPT_REQUIRED")
        val plan = M3ScriptAudioPlan.fromScript(script)
        if (plan.items.isEmpty()) {
            throw AudioException(AudioErrorCode.SCRIPT_REQUIRED, "SCRIPT_REQUIRED: no narration items")
        }

        val voiceConfig = request.voiceConfiguration
            ?: loadVoiceConfiguration(request.projectId)
        val audioPackageId = "aud_" + UUID.randomUUID().toString().replace("-", "").take(16)
        storage.ensureProjectLayout(request.projectId)

        val segments = mutableListOf<AudioSegment>()
        val driftWarnings = mutableListOf<String>()
        var cursor = 0L
        val now = System.currentTimeMillis()

        plan.items.forEach { item ->
            val assignment = VoiceAssignmentResolver.resolve(item, voiceConfig)
            val segment = synthesizePlanItem(
                projectId = request.projectId,
                audioPackageId = audioPackageId,
                item = item,
                assignment = assignment,
                voiceConfig = voiceConfig,
                cursorStart = cursor,
                generatedAt = now,
                driftWarnings = driftWarnings
            )
            segments += segment
            cursor = segment.endMs
        }

        return finalizePackage(
            projectId = request.projectId,
            scriptId = plan.scriptId,
            audioPackageId = audioPackageId,
            language = plan.language,
            voiceConfig = voiceConfig,
            scriptVersion = plan.scriptVersion,
            targetDurationMs = plan.targetDurationMs,
            segments = segments,
            driftWarnings = driftWarnings,
            generatedAt = now
        )
    }

    override suspend fun regenerateSegment(projectId: String, segmentId: String): AudioPackage {
        val existing = load(projectId) ?: throw AudioException(
            AudioErrorCode.INVALID_INPUT,
            "No audio package to regenerate"
        )
        val script = scriptRepository.load(projectId)
            ?: throw AudioException(AudioErrorCode.SCRIPT_REQUIRED, "SCRIPT_REQUIRED")
        val plan = M3ScriptAudioPlan.fromScript(script)
        val voiceConfig = existing.voiceConfiguration ?: loadVoiceConfiguration(projectId)
        val target = existing.segments.find { it.segmentId == segmentId }
            ?: throw AudioException(AudioErrorCode.INVALID_INPUT, "Unknown segment: $segmentId")
        val planItem = plan.items.find { it.clipId == segmentId }
            ?: throw AudioException(AudioErrorCode.INVALID_INPUT, "Plan item missing for $segmentId")

        val assignment = VoiceAssignmentResolver.resolve(planItem, voiceConfig)
        val index = existing.segments.indexOf(target)
        val regenerated = synthesizePlanItem(
            projectId = projectId,
            audioPackageId = existing.audioPackageId,
            item = planItem,
            assignment = assignment,
            voiceConfig = voiceConfig,
            cursorStart = target.startMs,
            generatedAt = System.currentTimeMillis(),
            driftWarnings = mutableListOf(),
            relativeAudioPath = target.relativeAudioPath
        )

        val updatedSegments = existing.segments.toMutableList()
        updatedSegments[index] = regenerated.copy(
            segmentId = target.segmentId,
            startMs = target.startMs,
            endMs = target.startMs + regenerated.durationMs
        )
        var timeline = 0L
        val retimed = updatedSegments.map { seg ->
            val retimedSeg = seg.copy(
                startMs = timeline,
                endMs = timeline + seg.durationMs
            )
            timeline = retimedSeg.endMs
            retimedSeg
        }

        return save(
            existing.copy(
                segments = retimed,
                introAudio = retimed.find { it.role == SegmentRole.INTRO },
                outroAudio = retimed.find { it.role == SegmentRole.OUTRO },
                totalDurationMs = retimed.sumOf { it.durationMs },
                actualNarrationDurationMs = retimed.sumOf { it.durationMs },
                durationDeltaMs = existing.targetDurationMs - retimed.sumOf { it.durationMs },
                status = AudioPackageStatus.fromSegments(retimed),
                voiceConfiguration = voiceConfig,
                metadata = existing.metadata.copy(
                    updatedAt = System.currentTimeMillis(),
                    scriptVersion = plan.scriptVersion
                )
            )
        )
    }

    override suspend fun refreshStaleState(projectId: String): AudioPackage? {
        val existing = load(projectId) ?: return null
        val script = scriptRepository.load(projectId) ?: return existing
        val plan = M3ScriptAudioPlan.fromScript(script)
        val planByClipId = plan.items.associateBy { it.clipId }
        val voiceConfig = existing.voiceConfiguration ?: loadVoiceConfiguration(projectId)

        val refreshed = existing.segments.map { seg ->
            val item = planByClipId[seg.segmentId]
            if (item == null) {
                if (seg.status == AudioSegmentStatus.READY) {
                    seg.copy(status = AudioSegmentStatus.STALE)
                } else {
                    seg
                }
            } else {
                val assignment = VoiceAssignmentResolver.resolve(item, voiceConfig)
                val assignmentHash = VoiceAssignmentResolver.assignmentHash(assignment)
                val textStale = seg.sourceTextHash.isNotBlank() &&
                    seg.sourceTextHash != item.sourceTextHash
                val voiceStale = seg.voiceAssignmentHash.isNotBlank() &&
                    seg.voiceAssignmentHash != assignmentHash
                if ((textStale || voiceStale) && seg.status == AudioSegmentStatus.READY) {
                    seg.copy(status = AudioSegmentStatus.STALE)
                } else {
                    seg
                }
            }
        }

        val updated = existing.copy(
            segments = refreshed,
            introAudio = refreshed.find { it.role == SegmentRole.INTRO },
            outroAudio = refreshed.find { it.role == SegmentRole.OUTRO },
            status = AudioPackageStatus.fromSegments(refreshed)
        )
        return if (updated != existing) save(updated) else existing
    }

    override suspend fun save(audio: AudioPackage): AudioPackage {
        val normalized = normalizeLoadedPackage(audio)
        val validated = normalized.copy(
            validation = AudioValidator.validatePackage(normalized),
            metadata = normalized.metadata.copy(updatedAt = System.currentTimeMillis())
        )
        if (!validated.validation.isValid) {
            throw AudioException(
                AudioErrorCode.INVALID_AUDIO_TIMELINE,
                validated.validation.errors.firstOrNull() ?: "Invalid audio package"
            )
        }
        val packagePath = "${ProjectPaths.AUDIO}/${validated.audioPackageId}/package.json"
        storage.save(StorageArea.PROJECT_DATA, packagePath, gson.toJson(validated), validated.projectId)
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
        return normalizeLoadedPackage(gson.fromJson(json, AudioPackage::class.java))
    }

    override suspend fun regenerate(projectId: String, preferredProviderId: String?): AudioPackage {
        val config = loadVoiceConfiguration(projectId).let { cfg ->
            if (preferredProviderId != null) {
                cfg.copy(defaultProviderId = preferredProviderId)
            } else cfg
        }
        return generate(AudioGenerationRequest(projectId, preferredProviderId, voiceConfiguration = config))
    }

    override fun listProviders(): List<Pair<String, String>> =
        ttsRegistry.available().map { it.providerId to it.displayName }

    override fun isRemoteConfigured(): Boolean = ttsRegistry.isRemoteConfigured()

    override fun resolveAbsolutePath(projectId: String, relativePath: String): String =
        storage.resolve(StorageArea.PROJECT_DATA, relativePath, projectId)

    private suspend fun markVoiceAssignmentStaleIfNeeded(projectId: String, config: VoiceConfiguration) {
        val existing = load(projectId) ?: return
        val script = scriptRepository.load(projectId) ?: return
        val plan = M3ScriptAudioPlan.fromScript(script)
        val planByClipId = plan.items.associateBy { it.clipId }
        val refreshed = existing.segments.map { seg ->
            val item = planByClipId[seg.segmentId] ?: return@map seg
            val assignment = VoiceAssignmentResolver.resolve(item, config)
            val assignmentHash = VoiceAssignmentResolver.assignmentHash(assignment)
            if (seg.voiceAssignmentHash.isNotBlank() &&
                seg.voiceAssignmentHash != assignmentHash &&
                seg.status == AudioSegmentStatus.READY
            ) {
                seg.copy(status = AudioSegmentStatus.STALE)
            } else {
                seg
            }
        }
        if (refreshed != existing.segments) {
            save(
                existing.copy(
                    segments = refreshed,
                    introAudio = refreshed.find { it.role == SegmentRole.INTRO },
                    outroAudio = refreshed.find { it.role == SegmentRole.OUTRO },
                    status = AudioPackageStatus.fromSegments(refreshed),
                    voiceConfiguration = config
                )
            )
        }
    }

    private suspend fun synthesizePlanItem(
        projectId: String,
        audioPackageId: String,
        item: M3AudioPlanItem,
        assignment: com.avsp.pro.audio.contract.SegmentVoiceAssignment,
        voiceConfig: VoiceConfiguration,
        cursorStart: Long,
        generatedAt: Long,
        driftWarnings: MutableList<String>,
        relativeAudioPath: String? = null
    ): AudioSegment {
        if (assignment.voiceMode == VoiceMode.MY_VOICE_CLONE && !voiceCloneProfileStore.isConfigured()) {
            throw AudioException(
                AudioErrorCode.VOICE_CLONE_NOT_CONFIGURED,
                "My Voice Clone is not configured."
            )
        }

        val language = assignment.language
        if (!AudioLanguageRegistry.isSupported(language)) {
            throw AudioException(AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE, "Language unavailable: $language")
        }

        val engine = try {
            ttsRegistry.resolve(assignment.providerId)
        } catch (e: Exception) {
            throw AudioException(
                AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                "TTS provider unavailable",
                details = e.message,
                cause = e
            )
        }
        if (!engine.isAvailable()) {
            throw AudioException(
                AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                "TTS provider unavailable: ${engine.providerId}"
            )
        }
        if (!engine.supportsLanguage(language)) {
            throw AudioException(
                AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE,
                "Language unavailable for ${engine.providerId}: $language"
            )
        }

        val voice = VoiceSettings(
            language = language,
            voiceId = assignment.voiceId,
            speechRate = voiceConfig.speechRate,
            pitch = voiceConfig.pitch,
            providerId = engine.providerId,
            voiceMode = assignment.voiceMode
        )

        val clipId = item.clipId
        val relative = relativeAudioPath ?: "${ProjectPaths.AUDIO}/$audioPackageId/$clipId.wav"

        val result = engine.synthesize(
            TtsSynthesisRequest(
                text = item.narration,
                language = language,
                voice = voice,
                targetDurationMs = null
            )
        )
        try {
            storage.save(StorageArea.PROJECT_DATA, relative, result.audioBytes, projectId)
        } catch (e: Exception) {
            throw AudioException(
                AudioErrorCode.AUDIO_STORAGE_FAILED,
                "Failed to store audio segment $clipId",
                details = e.message,
                cause = e
            )
        }

        val durationMs = result.durationMs.coerceAtLeast(1L)
        val planned = item.plannedDurationMs
        val delta = durationMs - planned
        if (planned > 0 && abs(delta).toDouble() / planned > AudioValidator.TIMING_DRIFT_WARN_RATIO) {
            driftWarnings += "${item.segmentKey}: actual ${durationMs}ms vs planned ${planned}ms"
        }

        val assignmentHash = VoiceAssignmentResolver.assignmentHash(assignment)

        return AudioSegment(
            segmentId = clipId,
            sceneId = item.sceneId ?: item.segmentKey,
            order = item.order,
            role = item.role,
            title = item.title,
            sourceText = item.narration,
            sourceTextHash = item.sourceTextHash,
            relativeAudioPath = relative,
            durationMs = durationMs,
            startMs = cursorStart,
            endMs = cursorStart + durationMs,
            provider = engine.providerId,
            language = language,
            voiceId = assignment.voiceId,
            voiceMode = assignment.voiceMode,
            status = AudioSegmentStatus.READY,
            plannedDurationMs = planned,
            durationDeltaMs = delta,
            generatedAt = generatedAt,
            voiceAssignmentHash = assignmentHash
        )
    }

    private suspend fun finalizePackage(
        projectId: String,
        scriptId: String,
        audioPackageId: String,
        language: String,
        voiceConfig: VoiceConfiguration,
        scriptVersion: String,
        targetDurationMs: Long,
        segments: List<AudioSegment>,
        driftWarnings: List<String>,
        generatedAt: Long
    ): AudioPackage {
        if (segments.isEmpty()) {
            throw AudioException(AudioErrorCode.INVALID_AUDIO_TIMELINE, "No audio segments generated")
        }
        val primaryProvider = segments.first().provider
        val voice = VoiceSettings(
            language = language,
            voiceId = voiceConfig.defaultVoiceId,
            speechRate = voiceConfig.speechRate,
            pitch = voiceConfig.pitch,
            providerId = primaryProvider,
            voiceMode = voiceConfig.voiceMode
        )
        val actualNarrationDurationMs = segments.sumOf { it.durationMs }
        val durationDeltaMs = targetDurationMs - actualNarrationDurationMs
        val packageStatus = AudioPackageStatus.fromSegments(segments)
        val draft = AudioPackage(
            projectId = projectId,
            scriptId = scriptId,
            audioPackageId = audioPackageId,
            language = language,
            provider = primaryProvider,
            voice = voice,
            voiceConfiguration = voiceConfig,
            segments = segments,
            introAudio = segments.find { it.role == SegmentRole.INTRO },
            outroAudio = segments.find { it.role == SegmentRole.OUTRO },
            totalDurationMs = actualNarrationDurationMs,
            targetDurationMs = targetDurationMs,
            actualNarrationDurationMs = actualNarrationDurationMs,
            durationDeltaMs = durationDeltaMs,
            generatedAt = generatedAt,
            status = packageStatus,
            validation = PLACEHOLDER_VALIDATION,
            metadata = AudioPackageMetadata(
                createdAt = generatedAt,
                updatedAt = generatedAt,
                scriptVersion = scriptVersion,
                format = AudioFormatInfo(
                    format = "wav",
                    sampleRateHz = WavEncoder.SAMPLE_RATE,
                    channels = WavEncoder.CHANNELS,
                    encoding = "pcm_s16le",
                    bitsPerSample = WavEncoder.BITS_PER_SAMPLE
                ),
                voice = voice,
                timingDriftWarnings = driftWarnings
            )
        )
        return save(draft)
    }

    private fun voiceConfigPath() = "${ProjectPaths.AUDIO}/voice_config.json"

    private fun normalizeLoadedPackage(audio: AudioPackage): AudioPackage {
        val segments = audio.segments.map { seg ->
            seg.copy(
                status = when (seg.status) {
                    AudioSegmentStatus.GENERATED -> AudioSegmentStatus.READY
                    AudioSegmentStatus.PENDING -> AudioSegmentStatus.NOT_GENERATED
                    else -> seg.status
                }
            )
        }
        val actualNarrationDurationMs = segments.sumOf { it.durationMs }
        val targetDurationMs = audio.targetDurationMs
        val durationDeltaMs = if (targetDurationMs > 0) {
            targetDurationMs - actualNarrationDurationMs
        } else {
            audio.durationDeltaMs
        }
        val status = AudioPackageStatus.fromSegments(segments)
        return audio.copy(
            version = audio.version.ifBlank { AudioPackage.CURRENT_VERSION },
            segments = segments,
            introAudio = segments.find { it.role == SegmentRole.INTRO },
            outroAudio = segments.find { it.role == SegmentRole.OUTRO },
            totalDurationMs = actualNarrationDurationMs,
            actualNarrationDurationMs = actualNarrationDurationMs,
            durationDeltaMs = durationDeltaMs,
            status = status
        )
    }

    private companion object {
        val PLACEHOLDER_VALIDATION = com.avsp.pro.audio.contract.AudioValidation(
            isValid = true,
            status = com.avsp.pro.audio.contract.AudioValidationStatus.VALID
        )
    }
}
