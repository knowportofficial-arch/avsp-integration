package com.avsp.pro.audio.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.avsp.pro.audio.contract.DiscoveredVoice
import com.avsp.pro.audio.duration.AudioDurationReader
import com.avsp.pro.audio.error.AudioErrorCode
import com.avsp.pro.audio.error.AudioException
import com.avsp.pro.audio.language.AudioLanguageRegistry
import com.avsp.pro.audio.wav.WavEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android platform TTS provider.
 * Detects availability and language support; never crashes when TTS is missing.
 * Synthesizes to a WAV file via [TextToSpeech.synthesizeToFile] when available.
 */
class AndroidTtsEngine(
    context: Context
) : TtsEngine {

    private val appContext = context.applicationContext
    private val ready = AtomicBoolean(false)
    private var initFailed = false
    private var tts: TextToSpeech? = null

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "Android Local TTS"
    override val mode: TtsProviderMode = TtsProviderMode.ANDROID_LOCAL

    @Synchronized
    private fun ensureInitialized() {
        if (tts != null || initFailed) return
        try {
            tts = TextToSpeech(appContext) { status ->
                ready.set(status == TextToSpeech.SUCCESS)
                if (status != TextToSpeech.SUCCESS) {
                    initFailed = true
                }
            }
            // Brief wait — callers should use isAvailable() which re-checks.
            Thread.sleep(150)
        } catch (_: Exception) {
            initFailed = true
            tts = null
            ready.set(false)
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            ensureInitialized()
            !initFailed && tts != null && ready.get()
        } catch (_: Exception) {
            false
        }
    }

    override fun supportedLanguages(): Set<String> {
        if (!isAvailable()) return emptySet()
        val engine = tts ?: return emptySet()
        return AudioLanguageRegistry.supportedCodes().filter { code ->
            val locale = localeFor(code)
            val result = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(-2)
            result >= TextToSpeech.LANG_AVAILABLE
        }.toSet()
    }

    override fun supportsLanguage(languageCode: String): Boolean {
        if (!AudioLanguageRegistry.isSupported(languageCode)) return false
        if (!isAvailable()) return false
        val engine = tts ?: return false
        val locale = localeFor(languageCode)
        val result = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(-2)
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    override fun listVoices(): List<DiscoveredVoice> {
        if (!isAvailable()) return emptyList()
        val engine = tts ?: return emptyList()
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        return voices.map { voice ->
            val locale = voice.locale
            val features = voice.features.orEmpty()
            val notInstalled = features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            val network = features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)
            DiscoveredVoice(
                voiceId = voice.name,
                name = voice.name,
                locale = locale.toLanguageTag(),
                languageCode = locale.language.lowercase(),
                gender = inferGender(voice.name),
                installed = !notInstalled,
                providerId = providerId,
                requiresNetwork = network
            )
        }.sortedBy { it.locale + it.name }
    }

    override suspend fun synthesize(request: TtsSynthesisRequest): TtsSynthesisResult =
        withContext(Dispatchers.IO) {
            if (!isAvailable()) {
                throw AudioException(
                    AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                    "Android Local TTS is unavailable on this device"
                )
            }
            if (!supportsLanguage(request.language)) {
                throw AudioException(
                    AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE,
                    "Language unavailable for Android TTS: ${request.language}"
                )
            }
            if (request.text.isBlank()) {
                throw AudioException(AudioErrorCode.INVALID_INPUT, "TTS text must not be blank")
            }

            val engine = tts
                ?: throw AudioException(
                    AudioErrorCode.TTS_PROVIDER_UNAVAILABLE,
                    "Android Local TTS is unavailable on this device"
                )

            val locale = localeFor(request.language)
            val langResult = engine.setLanguage(locale)
            if (langResult < TextToSpeech.LANG_AVAILABLE) {
                throw AudioException(
                    AudioErrorCode.TTS_LANGUAGE_UNAVAILABLE,
                    "Language unavailable for Android TTS: ${request.language}"
                )
            }
            engine.setSpeechRate(request.voice.speechRate.coerceIn(0.5f, 2.0f))
            engine.setPitch(request.voice.pitch.coerceIn(0.5f, 2.0f))
            applyVoice(engine, request.voice.voiceId)

            val outFile = File(appContext.cacheDir, "avsp_tts_${UUID.randomUUID()}.wav")
            try {
                synthesizeToFile(engine, request.text, outFile)
                if (!outFile.exists() || outFile.length() < 44L) {
                    // Some devices write non-WAV or empty files — fall back to timed mock tone
                    // only as generation failure signal (not silent language substitution).
                    throw AudioException(
                        AudioErrorCode.TTS_GENERATION_FAILED,
                        "Android TTS produced empty or invalid audio output"
                    )
                }
                val bytes = outFile.readBytes()
                val durationMs = AudioDurationReader.fromBytes(bytes, outFile).takeIf { it > 0L }
                    ?: request.targetDurationMs
                    ?: maxOf(500L, request.text.length * 60L)
                if (bytes.size < 44L) {
                    throw AudioException(
                        AudioErrorCode.AUDIO_CORRUPT,
                        "Generated audio file is corrupt or empty"
                    )
                }
                TtsSynthesisResult(
                    audioBytes = bytes,
                    durationMs = durationMs.coerceAtLeast(1L),
                    mimeType = "audio/wav",
                    providerId = providerId,
                    language = request.language.lowercase(),
                    voiceId = request.voice.voiceId.ifBlank { "android-default" }
                )
            } catch (e: AudioException) {
                throw e
            } catch (e: Exception) {
                throw AudioException(
                    AudioErrorCode.TTS_GENERATION_FAILED,
                    "Android TTS generation failed",
                    details = e.message,
                    cause = e
                )
            } finally {
                outFile.delete()
            }
        }

    private suspend fun synthesizeToFile(
        engine: TextToSpeech,
        text: String,
        outFile: File
    ) = suspendCancellableCoroutine { cont ->
        val utteranceId = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (cont.isActive) cont.resume(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        AudioException(AudioErrorCode.TTS_GENERATION_FAILED, "Android TTS utterance error")
                    )
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        AudioException(
                            AudioErrorCode.TTS_GENERATION_FAILED,
                            "Android TTS utterance error code=$errorCode"
                        )
                    )
                }
            }
        })
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
        @Suppress("DEPRECATION")
        val result = engine.synthesizeToFile(text, params, outFile.absolutePath)
        if (result != TextToSpeech.SUCCESS) {
            cont.resumeWithException(
                AudioException(AudioErrorCode.TTS_GENERATION_FAILED, "synthesizeToFile rejected")
            )
        }
        cont.invokeOnCancellation {
            runCatching { engine.stop() }
        }
    }

    private fun applyVoice(engine: TextToSpeech, voiceId: String) {
        if (voiceId.isBlank() || voiceId == "default" || voiceId == "android-default") return
        val match = runCatching { engine.voices }.getOrNull()?.find { it.name == voiceId }
        if (match == null) {
            throw AudioException(
                AudioErrorCode.VOICE_UNAVAILABLE,
                "Local voice is not installed: $voiceId. Install the language pack in Android TTS settings."
            )
        }
        val features = match.features.orEmpty()
        if (features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
            throw AudioException(
                AudioErrorCode.VOICE_UNAVAILABLE,
                "Local voice is not installed: $voiceId. Install the language pack in Android TTS settings."
            )
        }
        engine.voice = match
    }

    private fun inferGender(name: String): String? {
        val n = name.lowercase()
        return when {
            n.contains("female") || n.contains("woman") || n.contains("girl") -> "FEMALE"
            n.contains("male") || n.contains("man") || n.contains("boy") -> "MALE"
            else -> null
        }
    }

    private fun localeFor(languageCode: String): Locale {
        val tag = AudioLanguageRegistry.resolve(languageCode).localeTag
        return Locale.forLanguageTag(tag)
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready.set(false)
    }

    companion object {
        const val PROVIDER_ID = "android_local"
    }
}
