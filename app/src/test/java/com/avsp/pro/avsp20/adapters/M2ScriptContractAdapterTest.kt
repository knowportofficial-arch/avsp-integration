package com.avsp.pro.avsp20.adapters

import com.avsp.pro.avsp20.contracts.AvspId
import com.avsp.pro.avsp20.contracts.ContractType
import com.avsp.pro.avsp20.contracts.ScriptLanguage
import com.avsp.pro.script.contract.ScriptMetadata
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene
import com.avsp.pro.script.contract.ScriptValidation
import com.avsp.pro.script.contract.ValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M2ScriptContractAdapterTest {

    private fun script() = ScriptPackage(
        projectId = "project-1",
        scriptId = "script-1",
        topic = "Test topic",
        language = "bn",
        title = "বাংলা শিরোনাম",
        hook = "Hook",
        introduction = "Intro",
        scenes = listOf(
            ScriptScene(
                sceneId = "scene-2",
                order = 1,
                durationMs = 2000L,
                narration = "দ্বিতীয়"
            ),
            ScriptScene(
                sceneId = "scene-1",
                order = 0,
                durationMs = 3000L,
                narration = "প্রথম"
            )
        ),
        cta = "CTA",
        ending = "End",
        estimatedDurationMs = 5000L,
        targetDurationMs = 6000L,
        estimatedNarrationDurationMs = 5000L,
        validation = ScriptValidation(
            isValid = true,
            status = ValidationStatus.VALID
        ),
        metadata = ScriptMetadata(
            contentType = com.avsp.pro.script.contract.ContentType.SHORTS,
            audience = com.avsp.pro.script.contract.AudienceType.GENERAL,
            platform = com.avsp.pro.script.contract.TargetPlatform.YOUTUBE_SHORTS,
            aspectRatio = "9:16",
            generatorId = "mock",
            generatorMode = "test",
            createdAt = 100L,
            updatedAt = 200L
        )
    )

    @Test
    fun mapsCoreIdentityAndLanguage() {
        val result = M2ScriptContractAdapter.toAvsp20(script())

        assertEquals(ContractType.SCRIPT, result.header.contractType)
        assertEquals(AvspId("script-1"), result.header.id)
        assertEquals(AvspId("project-1"), result.header.projectId)
        assertEquals(ScriptLanguage.BN, result.language)
        assertEquals("বাংলা শিরোনাম", result.title)
    }

    @Test
    fun preservesSceneOrderDurationAndNarration() {
        val result = M2ScriptContractAdapter.toAvsp20(script())

        assertEquals(2, result.segments.size)
        assertEquals("প্রথম", result.segments[0].text)
        assertEquals(3000L, result.segments[0].durationMs)
        assertEquals("দ্বিতীয়", result.segments[1].text)
        assertEquals(2000L, result.segments[1].durationMs)
        assertTrue(result.segments.all { it.sceneId != null })
        assertEquals(5000L, result.totalDurationMs)
    }
}
