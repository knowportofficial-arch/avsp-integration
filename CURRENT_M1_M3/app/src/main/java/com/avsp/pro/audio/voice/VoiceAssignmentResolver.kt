package com.avsp.pro.audio.voice

import com.avsp.pro.audio.contract.AssignmentScope
import com.avsp.pro.audio.contract.SegmentRole
import com.avsp.pro.audio.contract.SegmentVoiceAssignment
import com.avsp.pro.audio.contract.VoiceConfiguration
import com.avsp.pro.audio.contract.VoiceMode
import com.avsp.pro.audio.engine.MockTtsEngine
import com.avsp.pro.audio.engine.VoiceCloneTtsEngine
import com.avsp.pro.audio.engine.AndroidTtsEngine
import com.avsp.pro.audio.integration.M3AudioPlanItem

object VoiceAssignmentResolver {
    fun resolve(item: M3AudioPlanItem, config: VoiceConfiguration): SegmentVoiceAssignment {
        val sceneSpecific = config.sceneAssignments[item.segmentKey]
        if (config.assignmentScope == AssignmentScope.PER_SCENE && sceneSpecific != null) {
            return sceneSpecific
        }
        return when (item.role) {
            SegmentRole.INTRO -> config.introAssignment ?: defaultForRole(item, config)
            SegmentRole.HOOK -> when (config.assignmentScope) {
                AssignmentScope.INTRO_BODY_OUTRO -> config.introAssignment ?: defaultForRole(item, config)
                else -> defaultForRole(item, config)
            }
            SegmentRole.OUTRO -> config.outroAssignment ?: defaultForRole(item, config)
            SegmentRole.BODY, SegmentRole.SCENE -> when (config.assignmentScope) {
                AssignmentScope.INTRO_BODY_OUTRO -> config.bodyAssignment ?: defaultForRole(item, config)
                else -> defaultForRole(item, config)
            }
        }
    }

    private fun defaultForRole(item: M3AudioPlanItem, config: VoiceConfiguration): SegmentVoiceAssignment {
        val provider = when (config.voiceMode) {
            VoiceMode.MY_VOICE_CLONE -> VoiceCloneTtsEngine.PROVIDER_ID
            VoiceMode.SIMPLE_LOCAL -> config.defaultProviderId.ifBlank { AndroidTtsEngine.PROVIDER_ID }
        }
        return SegmentVoiceAssignment(
            segmentKey = item.segmentKey,
            role = item.role,
            voiceMode = config.voiceMode,
            voiceId = config.defaultVoiceId,
            language = item.language.ifBlank { config.defaultLanguage },
            providerId = provider
        )
    }

    fun defaultProviderForMode(mode: VoiceMode): String = when (mode) {
        VoiceMode.MY_VOICE_CLONE -> VoiceCloneTtsEngine.PROVIDER_ID
        VoiceMode.SIMPLE_LOCAL -> MockTtsEngine.PROVIDER_ID
    }

    fun assignmentHash(assignment: SegmentVoiceAssignment): String {
        val raw = listOf(
            assignment.voiceMode.name,
            assignment.providerId,
            assignment.voiceId,
            assignment.language
        ).joinToString("|")
        return raw.hashCode().toUInt().toString(16)
    }
}
