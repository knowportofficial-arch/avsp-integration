package com.avsp.pro.avsp20.adapters

import com.avsp.pro.audio.contract.*
import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3AudioContractAdapterTest {

    private fun audio() = AudioPackage(
        projectId = "project-1",
        scriptId = "script-1",
        audioPackageId = "aud-1",
        language = "bn",
        provider = "mock",
        voice = VoiceSettings(
            language = "bn",
            voiceId = "voice-bn",
            providerId = "mock"
        ),
        segments = listOf(
            AudioSegment(
                segmentId = "aseg_00",
                sceneId = "scene-1",
                order = 0,
                sourceText = "প্রথম",
                relativeAudioPath = "audio/aud-1/aseg_00.wav",
                durationMs = 3000L,
                startMs = 0L,
                endMs = 3000L,
                provider = "mock",
                language = "bn"
            )
        ),
        totalDurationMs = 3000L,
        validation = AudioValidation(
            isValid = true,
            status = AudioValidationStatus.VALID
        ),
        metadata = AudioPackageMetadata(
            createdAt = 100L,
            updatedAt = 200L,
            scriptVersion = "1.0",
            format = AudioFormatInfo(
                sampleRateHz = 16000,
                channels = 1
            ),
            voice = VoiceSettings(
                language = "bn",
                voiceId = "voice-bn",
                providerId = "mock"
            )
        )
    )

    @Test
    fun mapsCoreIdentityAndLanguage() {
        val result = M3AudioContractAdapter.toAvsp20(audio())

        assertEquals(ContractType.AUDIO, result.header.contractType)
        assertEquals(AvspId("aud-1"), result.header.id)
        assertEquals(AvspId("project-1"), result.header.projectId)
        assertEquals(AvspId("script-1"), result.sourceScriptId)
        assertEquals(ScriptLanguage.BN, result.language)
        assertEquals("voice-bn", result.voiceId)
    }

    @Test
    fun mapsDurationFormatAndAudioPath() {
        val result = M3AudioContractAdapter.toAvsp20(audio())

        assertEquals(3000L, result.durationMs)
        assertEquals(16000, result.sampleRateHz)
        assertEquals(1, result.channels)
        assertEquals("audio/aud-1/aseg_00.wav", result.uri)
        assertTrue(result.uri.endsWith(".wav"))
    }
}
