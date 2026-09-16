package com.avsp.pro.script.language

import com.avsp.pro.core.model.ProjectLanguage

/**
 * Language abstraction for M2 — avoids hard-coding EN/BN logic across the engine.
 */
interface ScriptLanguagePack {
    val code: String
    val displayName: String
    fun titleFor(topic: String): String
    fun hookFor(topic: String): String
    fun introFor(topic: String): String
    fun sectionNarration(topic: String, sectionIndex: Int, sectionCount: Int): String
    fun onScreenFor(topic: String, sectionIndex: Int): String
    fun visualFor(topic: String, sectionIndex: Int): String
    fun bRollFor(topic: String, sectionIndex: Int): String
    fun ctaFor(topic: String): String
    fun endingFor(topic: String): String
    fun cameraDirection(sectionIndex: Int): String
}

object ScriptLanguageRegistry {
    private val packs: Map<String, ScriptLanguagePack> = listOf(
        EnglishLanguagePack,
        BengaliLanguagePack,
        HindiLanguagePack
    ).associateBy { it.code.lowercase() }

    fun supportedCodes(): Set<String> = packs.keys

    fun isSupported(code: String): Boolean {
        val normalized = code.trim().lowercase()
        if (packs.containsKey(normalized)) return true
        return runCatching {
            packs.containsKey(ProjectLanguage.fromCode(code).code)
        }.getOrDefault(false)
    }

    fun resolve(code: String): ScriptLanguagePack {
        val normalized = code.trim().lowercase()
        packs[normalized]?.let { return it }
        val fromProject = runCatching { ProjectLanguage.fromCode(code).code }.getOrNull()
        return packs[fromProject]
            ?: throw IllegalArgumentException("Unsupported script language: $code")
    }
}

object EnglishLanguagePack : ScriptLanguagePack {
    override val code = "en"
    override val displayName = "English"
    override fun titleFor(topic: String) = topic.trim().replaceFirstChar { it.uppercase() }
    override fun hookFor(topic: String) = "Here's what you need to know about $topic."
    override fun introFor(topic: String) =
        "Today we break down $topic in a clear, practical way."
    override fun sectionNarration(topic: String, sectionIndex: Int, sectionCount: Int): String {
        val labels = listOf("the key point", "the practical detail", "why it matters", "what to do next")
        val label = labels.getOrElse(sectionIndex) { "the next point" }
        return "Section ${sectionIndex + 1} of $sectionCount: $label about $topic."
    }
    override fun onScreenFor(topic: String, sectionIndex: Int) =
        "${topic.take(28)} · ${sectionIndex + 1}"
    override fun visualFor(topic: String, sectionIndex: Int) =
        "Clean vertical frame showing $topic context for beat ${sectionIndex + 1}."
    override fun bRollFor(topic: String, sectionIndex: Int) =
        "Relevant B-roll for $topic, beat ${sectionIndex + 1}."
    override fun ctaFor(topic: String) = "Follow for more on $topic."
    override fun endingFor(topic: String) = "That's the essential takeaway on $topic."
    override fun cameraDirection(sectionIndex: Int) = when (sectionIndex) {
        0 -> "Hold steady, face camera"
        else -> "Slight push-in, keep subject centered"
    }
}

object BengaliLanguagePack : ScriptLanguagePack {
    override val code = "bn"
    override val displayName = "Bengali"
    override fun titleFor(topic: String) = topic.trim()
    override fun hookFor(topic: String) = "$topic নিয়ে আজকের গুরুত্বপূর্ণ আপডেট।"
    override fun introFor(topic: String) =
        "আজ আমরা সহজ ভাষায় $topic বুঝিয়ে দিচ্ছি।"
    override fun sectionNarration(topic: String, sectionIndex: Int, sectionCount: Int): String =
        "পর্ব ${sectionIndex + 1}/$sectionCount: $topic সম্পর্কে গুরুত্বপূর্ণ তথ্য।"
    override fun onScreenFor(topic: String, sectionIndex: Int) =
        "${topic.take(24)} · ${sectionIndex + 1}"
    override fun visualFor(topic: String, sectionIndex: Int) =
        "$topic বিষয়ে স্পষ্ট ভিজ্যুয়াল, সিন ${sectionIndex + 1}।"
    override fun bRollFor(topic: String, sectionIndex: Int) =
        "$topic সম্পর্কিত বি-রোল, সিন ${sectionIndex + 1}।"
    override fun ctaFor(topic: String) = "আরও আপডেট পেতে ফলো করুন।"
    override fun endingFor(topic: String) = "এই ছিল $topic নিয়ে আজকের মূল কথা।"
    override fun cameraDirection(sectionIndex: Int) = when (sectionIndex) {
        0 -> "ক্যামেরার দিকে স্থিরভাবে তাকান"
        else -> "হালকা জুম-ইন, বিষয় মাঝখানে রাখুন"
    }
}

object HindiLanguagePack : ScriptLanguagePack {
    override val code = "hi"
    override val displayName = "Hindi"
    override fun titleFor(topic: String) = topic.trim()
    override fun hookFor(topic: String) = "$topic के बारे में जरूरी बातें।"
    override fun introFor(topic: String) =
        "आज हम आसान भाषा में $topic समझाएंगे।"
    override fun sectionNarration(topic: String, sectionIndex: Int, sectionCount: Int): String =
        "भाग ${sectionIndex + 1}/$sectionCount: $topic की महत्वपूर्ण जानकारी।"
    override fun onScreenFor(topic: String, sectionIndex: Int) =
        "${topic.take(24)} · ${sectionIndex + 1}"
    override fun visualFor(topic: String, sectionIndex: Int) =
        "$topic से जुड़ा साफ विज़ुअल, सीन ${sectionIndex + 1}।"
    override fun bRollFor(topic: String, sectionIndex: Int) =
        "$topic संबंधित बी-रोल, सीन ${sectionIndex + 1}।"
    override fun ctaFor(topic: String) = "और अपडेट के लिए फॉलो करें।"
    override fun endingFor(topic: String) = "यही था $topic का मुख्य निष्कर्ष।"
    override fun cameraDirection(sectionIndex: Int) = when (sectionIndex) {
        0 -> "कैमरे की ओर स्थिर देखें"
        else -> "हल्का पुश-इन, सब्जेक्ट केंद्र में"
    }
}
