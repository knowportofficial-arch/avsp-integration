package com.avsp.pro.avsp20.integration

import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20IntegrationValidatorTest {

    private fun header(type: ContractType, id: String, project: String = "project-1") =
        ContractHeader(
            contractType = type,
            id = AvspId(id),
            projectId = AvspId(project),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

    @Test
    fun validatesCanonicalHeaders() {
        val r = Avsp20IntegrationValidator.validateHeader(
            header(ContractType.PROJECT, "project-1"),
            ContractType.PROJECT
        )
        assertTrue(r.valid)
    }

    @Test
    fun rejectsWrongContractType() {
        val r = Avsp20IntegrationValidator.validateHeader(
            header(ContractType.PROJECT, "project-1"),
            ContractType.SCRIPT
        )
        assertTrue(!r.valid)
        assertTrue(r.errors.contains("HEADER_TYPE_MISMATCH"))
    }

    @Test
    fun rejectsInvalidTimeline() {
        val c = TimelineContract(
            header(ContractType.TIMELINE, "timeline-1"),
            emptyList(), 1000L, 30.0, 0, 1920
        )
        val r = Avsp20IntegrationValidator.validateTimeline(c)
        assertTrue(!r.valid)
        assertTrue(r.errors.contains("TIMELINE_DIMENSIONS_INVALID"))
    }

    @Test
    fun validatesPipelineLinks() {
        val p = ProjectContract(
            header(ContractType.PROJECT, "project-1"),
            "Demo", ScriptLanguage.BN, "9:16", "DRAFT"
        )
        val s = ScriptContract(
            header(ContractType.SCRIPT, "script-1"),
            "Demo", ScriptLanguage.BN, emptyList()
        )
        val a = AudioContract(
            header(ContractType.AUDIO, "audio-1"),
            AvspId("script-1"), "file://a.wav", ScriptLanguage.BN,
            1000L, 24000, 1
        )
        val t = TimelineContract(
            header(ContractType.TIMELINE, "timeline-1"),
            emptyList(), 1000L, 30.0, 1080, 1920
        )
        val r = RenderContract(
            header(ContractType.RENDER, "render-1"),
            AvspId("timeline-1"),
            widthPx = 1080, heightPx = 1920,
            frameRate = 30.0, codec = "H264"
        )
        val q = QcContract(
            header(ContractType.QC, "qc-1"),
            AvspId("render-1"), QcStatus.PASS, emptyList()
        )
        val pu = PublishContract(
            header(ContractType.PUBLISH, "publish-1"),
            AvspId("render-1"),
            PublishTarget(PublishPlatform.YOUTUBE), "Demo"
        )
        val an = AnalyticsContract(
            header(ContractType.ANALYTICS, "analytics-1"),
            AvspId("publish-1"), PublishPlatform.YOUTUBE, 1L
        )

        val result = Avsp20IntegrationValidator.validatePipeline(
            p, s, a, emptyList(), emptyList(), t, r, q, pu, an
        )

        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun detectsBrokenAudioScriptLink() {
        val p = ProjectContract(
            header(ContractType.PROJECT, "project-1"),
            "Demo", ScriptLanguage.BN, "9:16", "DRAFT"
        )
        val s = ScriptContract(
            header(ContractType.SCRIPT, "script-1"),
            "Demo", ScriptLanguage.BN, emptyList()
        )
        val a = AudioContract(
            header(ContractType.AUDIO, "audio-1"),
            AvspId("wrong-script"), "file://a.wav", ScriptLanguage.BN,
            1000L, 24000, 1
        )
        val t = TimelineContract(
            header(ContractType.TIMELINE, "timeline-1"),
            emptyList(), 1000L, 30.0, 1080, 1920
        )
        val r = RenderContract(
            header(ContractType.RENDER, "render-1"),
            AvspId("timeline-1"),
            widthPx = 1080, heightPx = 1920,
            frameRate = 30.0, codec = "H264"
        )
        val q = QcContract(
            header(ContractType.QC, "qc-1"),
            AvspId("render-1"), QcStatus.PASS, emptyList()
        )
        val pu = PublishContract(
            header(ContractType.PUBLISH, "publish-1"),
            AvspId("render-1"),
            PublishTarget(PublishPlatform.YOUTUBE), "Demo"
        )
        val an = AnalyticsContract(
            header(ContractType.ANALYTICS, "analytics-1"),
            AvspId("publish-1"), PublishPlatform.YOUTUBE, 1L
        )

        val result = Avsp20IntegrationValidator.validatePipeline(
            p, s, a, emptyList(), emptyList(), t, r, q, pu, an
        )

        assertTrue(!result.valid)
        assertTrue(result.errors.contains("PIPELINE_AUDIO_SCRIPT_LINK_MISMATCH"))
    }
}
