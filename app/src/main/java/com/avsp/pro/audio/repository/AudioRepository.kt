package com.avsp.pro.audio.repository

import com.avsp.pro.audio.contract.AudioFormatInfo
import com.avsp.pro.audio.contract.AudioGenerationRequest
import com.avsp.pro.audio.contract.AudioPackage
import com.avsp.pro.audio.contract.AudioPackageMetadata
import com.avsp.pro.audio.contract.AudioSegment
import com.avsp.pro.audio.contract.AudioSegmentStatus
import com.avsp.pro.audio.contract.VoiceSettings
import com.avsp.pro.audio.engine.TtsEngineRegistry
import com.avsp.pro.audio.engine.TtsSynthesisRequest
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.language.AudioLanguageRegistry
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
    fun listProviders(): List<Pair<String, String>>
    fun isRemoteConfigured(): Boolean
    fun resolveAbsolutePath(projectId: String, relativePath: String): String
}

class AudioRepositoryImpl(
    private val storage: AvspStorage,
    private val scriptRepository: ScriptRepository,
    private val ttsRegistry: TtsEngineRegistry,
    private val logger: AvspLogger,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : AudioRepository {

    override suspend fun loadScriptHandoff(projectId: String): ScriptNarrationHandoff? {
        val script = scriptRepository.load(projectId) ?: return null
        return ScriptToTtsContract.fromPackage(script)
    }

    override suspend fun generate(request: AudioGenerationRequest): AudioPackage {
        val handoff = loadScriptHandoff(request.projectId)
        val handoffValidation = AudioValidator.validateHandoff(handoff)
        if (!handoffValidation.isValid || handoff == null) {
            throw AudioException(
                AudioErrorCode.SCRIPT_REQUIRED,
                handoffValidation.errors.firstOrNull() ?: "SCRIPT_REQUIRED",
                details = handoffValidation.errors.joinToString("; ")
            )
        }

        val language = handoff.language
        if (!AudioLanguageRegistry.isSupported(language)) {
            throw AudioException(
                AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE,
                "Language unavailable: $language"
            )
        }

        val engine = try {
            ttsRegistry.resolve(request.preferredProviderId)
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
                "Language unavailable for provider ${engine.providerId}: $language"
            )
        }

        val voice = (request.voice ?: VoiceSettings(language = language, providerId = engine.providerId))
            .copy(language = language, providerId = engine.providerId)

        val audioPackageId = "aud_" + UUID.randomUUID().toString().replace("-", "").take(16)
        storage.ensureProjectLayout(request.projectId)

        val driftWarnings = mutableListOf<String>()
        var cursor = 0L
        val segments = mutableListOf<AudioSegment>()

        handoff.segments.sortedBy { it.order }.forEachIndexed { index, narration ->
            val segmentId = "aseg_${index.toString().padStart(2, '0')}"
            try {
                val result = engine.synthesize(
                    TtsSynthesisRequest(
                        text = narration.narration,
                        language = language,
                        voice = voice,
                        targetDurationMs = narration.durationMs
                    )
                )
                val relative = "${ProjectPaths.AUDIO}/$audioPackageId/$segmentId.wav"
                try {
                    storage.save(
                        area = StorageArea.PROJECT_DATA,
                        relativePath = relative,
                        bytes = result.audioBytes,
                        projectId = request.projectId
                    )
                } catch (e: Exception) {
                    throw AudioException(
                        AudioErrorCode.AUDIO_STORAGE_FAILED,
                        "Failed to store audio segment $segmentId",
                        details = e.message,
                        cause = e
                    )
                }

                val durationMs = result.durationMs.coerceAtLeast(1L)
                val planned = narration.durationMs
                val delta = durationMs - planned
                if (planned > 0 && abs(delta).toDouble() / planned > AudioValidator.TIMING_DRIFT_WARN_RATIO) {
                    driftWarnings += "scene ${narration.sceneId}: actual ${durationMs}ms vs planned ${planned}ms"
                }
                val start = cursor
                val end = start + durationMs
                segments += AudioSegment(
                    segmentId = segmentId,
                    sceneId = narration.sceneId,
                    order = narration.order,
                    sourceText = narration.narration,
                    relativeAudioPath = relative,
                    durationMs = durationMs,
                    startMs = start,
                    endMs = end,
                    provider = engine.providerId,
                    language = language,
                    status = AudioSegmentStatus.GENERATED,
                    plannedDurationMs = planned,
                    durationDeltaMs = delta
                )
                // Contiguous timeline for M4; pauseAfter is M2 intent metadata, not a gap.
                cursor = end
            } catch (e: AudioException) {
                logger.error("M3", e.message ?: "segment failed", details = e.errorCode.code, projectId = request.projectId)
                throw e
            } catch (e: Exception) {
                throw AudioException(
                    AudioErrorCode.TTS_GENERATION_FAILED,
                    "TTS generation failed for scene ${narration.sceneId}",
                    details = e.message,
                    cause = e
                )
            }
        }

        if (segments.isEmpty()) {
            throw AudioException(AudioErrorCode.INVALID_AUDIO_TIMELINE, "No audio segments generated")
        }

        // Timeline total equals sum of segment durations (contiguous).
        val totalDurationMs = segments.sumOf { it.durationMs }
        val now = System.currentTimeMillis()
        val draft = AudioPackage(
            projectId = request.projectId,
            scriptId = handoff.scriptId,
            audioPackageId = audioPackageId,
            language = language,
            provider = engine.providerId,
            voice = voice,
            segments = segments,
            totalDurationMs = totalDurationMs,
            validation = AudioValidationPlaceholder,
            metadata = AudioPackageMetadata(
                createdAt = now,
                updatedAt = now,
                scriptVersion = handoff.scriptVersion,
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
        val validated = draft.copy(validation = AudioValidator.validatePackage(draft))
        if (!validated.validation.isValid) {
            throw AudioException(
                AudioErrorCode.INVALID_AUDIO_TIMELINE,
                validated.validation.errors.firstOrNull() ?: "Invalid audio timeline",
                details = validated.validation.errors.joinToString("; ")
            )
        }
        logger.info("M3", "Audio package generated via ${engine.providerId}", projectId = request.projectId)
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
        // Compatibility pointer for integration contract voice.mp3 name — store metadata note only if wav exists.
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
        return gson.fromJson(json, AudioPackage::class.java)
    }

    override suspend fun regenerate(projectId: String, preferredProviderId: String?): AudioPackage {
        return generate(
            AudioGenerationRequest(
                projectId = projectId,
                preferredProviderId = preferredProviderId
            )
        )
    }

    override fun listProviders(): List<Pair<String, String>> =
        ttsRegistry.available().map { it.providerId to it.displayName }

    override fun isRemoteConfigured(): Boolean = ttsRegistry.isRemoteConfigured()

    override fun resolveAbsolutePath(projectId: String, relativePath: String): String =
        storage.resolve(StorageArea.PROJECT_DATA, relativePath, projectId)

    private companion object {
        // Temporary placeholder replaced immediately after timeline build.
        val AudioValidationPlaceholder = com.avsp.pro.audio.contract.AudioValidation(
            isValid = true,
            status = com.avsp.pro.audio.contract.AudioValidationStatus.VALID
        )
    }
}
