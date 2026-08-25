package com.avsp.creator.capture.camera

import android.content.Context
import com.avsp.creator.capture.camera.engine.CinematographerDecision
import com.avsp.creator.capture.camera.analyzer.ShotReadinessStatus
import com.avsp.creator.core.theme.AppLanguageOption
import java.util.Locale

/**
 * Short, non-repetitive spoken instructions for the field cinematographer.
 * It speaks only meaningful decision changes, never every ML frame.
 */
class VoiceGuidanceController(
    context: Context,
    private val languageProvider: () -> AppLanguageOption
) : android.speech.tts.TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: android.speech.tts.TextToSpeech? = android.speech.tts.TextToSpeech(appContext, this)
    private var ready = false
    private var lastSpokenKey: String? = null
    private var lastSpokenAt = 0L
    private var lastStatus = ShotReadinessStatus.NOT_READY

    override fun onInit(status: Int) {
        ready = status == android.speech.tts.TextToSpeech.SUCCESS
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val locale = when (languageProvider()) {
            AppLanguageOption.BENGALI -> Locale("bn", "IN")
            AppLanguageOption.HINDI -> Locale("hi", "IN")
            AppLanguageOption.ENGLISH -> Locale("en", "IN")
        }
        tts?.language = locale
    }

    fun speakDecision(decision: CinematographerDecision, enabled: Boolean) {
        if (!enabled || !ready) return
        val text = decision.guidance.trim()
        if (text.isBlank()) return

        val normalized = text.lowercase(Locale.ROOT)
        val meaningful = when {
            normalized.contains("awaiting") -> false
            normalized.contains("hold steady") && decision.status == ShotReadinessStatus.NOT_READY -> false
            else -> true
        }
        if (!meaningful) return

        val key = "${decision.shotType.name}|$normalized|${decision.status.name}"
        val now = System.currentTimeMillis()
        val statusChanged = decision.status != lastStatus
        val changed = key != lastSpokenKey
        if (!changed && !statusChanged) return
        if (now - lastSpokenAt < 1400L) return

        applyLanguage()
        val spokenText = if (normalized.contains("target not detected")) {
            "Point the camera toward ${decision.targetSubject}."
        } else text
        val spoken = localize(spokenText, languageProvider(), decision.targetSubject)
        tts?.speak(spoken, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "avsp-guidance")
        lastSpokenKey = key
        lastSpokenAt = now
        lastStatus = decision.status
    }

    private fun localize(text: String, language: AppLanguageOption, targetSubject: String): String {
        if (language == AppLanguageOption.ENGLISH) return text
        val normalized = text.lowercase(Locale.ROOT)
        return when (language) {
            AppLanguageOption.BENGALI -> when {
                normalized.contains("move slightly left") -> "একটু বাঁদিকে যান।"
                normalized.contains("move slightly right") -> "একটু ডানদিকে যান।"
                normalized.contains("move back slightly") -> "একটু পিছিয়ে যান।"
                normalized == "move back." -> "একটু পিছিয়ে যান।"
                normalized == "move closer." -> "একটু কাছে যান।"
                normalized.contains("move closer") -> "আরও কাছে যান।"
                normalized.contains("tilt slightly up") -> "ফোনটি একটু উপরে তুলুন।"
                normalized.contains("tilt slightly down") -> "ফোনটি একটু নিচে নামান।"
                normalized.contains("level the phone") -> "ফোনটি সোজা করুন।"
                normalized.contains("good shot") -> "ভালো শট। স্থির রাখুন।"
                normalized.contains("hold steady") -> "স্থির রাখুন।"
                normalized.contains("point the camera toward") -> "ক্যামেরাটি ${targetSubject}-এর দিকে ধরুন।"
                normalized.contains("wide establishing") -> "ওয়াইড এস্টাবলিশিং শট নিন।"
                normalized.contains("switch to wide") -> "ওয়াইডে পরিবর্তন করুন।"
                normalized.contains("switch to medium") -> "মিডিয়ামে পরিবর্তন করুন।"
                normalized.contains("switch to close") -> "ক্লোজ শটে পরিবর্তন করুন।"
                else -> text
            }
            AppLanguageOption.HINDI -> when {
                normalized.contains("move slightly left") -> "थोड़ा बाईं ओर जाएँ।"
                normalized.contains("move slightly right") -> "थोड़ा दाईं ओर जाएँ।"
                normalized.contains("move back slightly") -> "थोड़ा पीछे जाएँ।"
                normalized == "move back." -> "थोड़ा पीछे जाएँ।"
                normalized == "move closer." -> "थोड़ा पास जाएँ।"
                normalized.contains("move closer") -> "थोड़ा और पास जाएँ।"
                normalized.contains("tilt slightly up") -> "फोन थोड़ा ऊपर करें।"
                normalized.contains("tilt slightly down") -> "फोन थोड़ा नीचे करें।"
                normalized.contains("level the phone") -> "फोन सीधा करें।"
                normalized.contains("good shot") -> "अच्छा शॉट। स्थिर रखें।"
                normalized.contains("hold steady") -> "स्थिर रखें।"
                normalized.contains("point the camera toward") -> "कैमरा ${targetSubject} की ओर करें।"
                normalized.contains("wide establishing") -> "वाइड एस्टैब्लिशिंग शॉट लें।"
                normalized.contains("switch to wide") -> "वाइड पर जाएँ।"
                normalized.contains("switch to medium") -> "मीडियम पर जाएँ।"
                normalized.contains("switch to close") -> "क्लोज शॉट पर जाएँ।"
                else -> text
            }
            AppLanguageOption.ENGLISH -> text
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
