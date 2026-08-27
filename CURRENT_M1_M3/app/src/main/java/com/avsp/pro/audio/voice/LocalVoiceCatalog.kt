package com.avsp.pro.audio.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.avsp.pro.audio.contract.LocalVoiceInfo
import com.avsp.pro.audio.language.AudioLanguageRegistry
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Discovers voices available through the Android/device TTS engine.
 * Never hard-codes vendor voice names — lists what is actually installed.
 */
class LocalVoiceCatalog(
    context: Context
) {
    private val appContext = context.applicationContext
    private val ready = AtomicBoolean(false)
    private var initFailed = false
    private var tts: TextToSpeech? = null

    @Synchronized
    private fun ensureInitialized() {
        if (tts != null || initFailed) return
        try {
            tts = TextToSpeech(appContext) { status ->
                ready.set(status == TextToSpeech.SUCCESS)
                if (status != TextToSpeech.SUCCESS) initFailed = true
            }
            Thread.sleep(200)
        } catch (_: Exception) {
            initFailed = true
            tts = null
            ready.set(false)
        }
    }

    fun isEngineAvailable(): Boolean {
        return try {
            ensureInitialized()
            !initFailed && tts != null && ready.get()
        } catch (_: Exception) {
            false
        }
    }

    fun listVoices(languageCode: String? = null): List<LocalVoiceInfo> {
        if (!isEngineAvailable()) return emptyList()
        val engine = tts ?: return emptyList()
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        return voices
            .mapNotNull { voice -> voice.toInfo(engine) }
            .filter { info ->
                if (languageCode == null) true
                else matchesLanguage(info.languageTag, languageCode)
            }
            .sortedWith(compareBy({ it.languageTag }, { it.displayName }))
    }

    fun listLanguages(): List<String> =
        listVoices()
            .map { normalizeLanguage(it.languageTag) }
            .distinct()
            .sorted()

    fun defaultVoiceForLanguage(languageCode: String): LocalVoiceInfo? {
        val lang = AudioLanguageRegistry.resolve(languageCode)
        val locale = Locale.forLanguageTag(lang.localeTag)
        val engine = tts
        if (engine != null && isEngineAvailable()) {
            val availability = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(-2)
            if (availability < TextToSpeech.LANG_AVAILABLE) {
                return LocalVoiceInfo(
                    voiceId = "default",
                    displayName = "Default ($languageCode)",
                    languageTag = lang.localeTag,
                    installed = false,
                    requiresDownload = true
                )
            }
        }
        return listVoices(languageCode).firstOrNull()
            ?: LocalVoiceInfo(
                voiceId = "default",
                displayName = "Default ($languageCode)",
                languageTag = lang.localeTag,
                installed = isEngineAvailable()
            )
    }

    private fun inferGender(voiceName: String, features: Set<String>?): String? {
        val lower = voiceName.lowercase(Locale.US)
        if (lower.contains("female") || lower.contains("#female")) return "female"
        if (lower.contains("male") || lower.contains("#male")) return "male"
        return features?.firstOrNull { it.contains("gender", ignoreCase = true) }
    }

    private fun Voice.toInfo(engine: TextToSpeech): LocalVoiceInfo? {
        val name = this.name ?: return null
        val locale = this.locale ?: Locale.getDefault()
        val availability = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(-2)
        val installed = availability >= TextToSpeech.LANG_AVAILABLE
        return LocalVoiceInfo(
            voiceId = name,
            displayName = name.substringAfterLast('-', name),
            languageTag = locale.toLanguageTag(),
            gender = inferGender(name, features),
            installed = installed,
            requiresDownload = !installed
        )
    }

    private fun matchesLanguage(voiceTag: String, languageCode: String): Boolean {
        val normalized = normalizeLanguage(voiceTag)
        val target = normalizeLanguage(AudioLanguageRegistry.resolve(languageCode).localeTag)
        return normalized == target ||
            normalized.startsWith("$target-") ||
            target.startsWith("$normalized-")
    }

    private fun normalizeLanguage(tag: String): String =
        tag.lowercase(Locale.US).replace('_', '-')

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready.set(false)
    }
}
