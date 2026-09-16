package com.avsp.pro.avsp20.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20DataContractsTest {

    private fun header(type: ContractType, id: String = "id-1") =
        ContractHeader(
            contractType = type,
            id = AvspId(id),
            projectId = AvspId("project-1"),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

    @Test
    fun allTenCanonicalContractTypesAreRegistered() {
        assertEquals(10, Avsp20ContractRegistry.supportedTypes.size)
        assertTrue(Avsp20ContractRegistry.supportedTypes.containsAll(ContractType.entries))
    }

    @Test
    fun canonicalHeaderIsVersioned() {
        val h = header(ContractType.PROJECT)
        assertEquals("2.0", h.contractVersion)
        assertEquals(1, h.schemaVersion)
        assertTrue(Avsp20ContractRegistry.isSupported(h))
    }

    @Test
    fun projectScriptAudioSceneAssetContractsCarryStableIds() {
        val p = ProjectContract(header(ContractType.PROJECT), "Demo", ScriptLanguage.BN, "9:16", "DRAFT")
        val s = ScriptContract(header(ContractType.SCRIPT, "script-1"), "Demo", ScriptLanguage.BN, emptyList())
        val a = AudioContract(header(ContractType.AUDIO, "audio-1"), AvspId("script-1"), "file://a.wav", ScriptLanguage.BN, 1000, 24000, 1)
        val scene = SceneContract(header(ContractType.SCENE, "scene-1"), AvspId("scene-1"), 0, SceneRole.HOOK)
        val asset = AssetContract(header(ContractType.ASSET, "asset-1"), "file://a.mp4", "a.mp4", MediaKind.VIDEO)

        assertEquals("project-1", p.header.projectId.value)
        assertEquals("script-1", a.sourceScriptId.value)
        assertEquals("scene-1", scene.sceneId.value)
        assertEquals("asset-1", asset.header.id.value)
        assertEquals("Demo", s.title)
    }

    @Test
    fun timelineRenderQcPublishAnalyticsLinkByIds() {
        val timeline = TimelineContract(header(ContractType.TIMELINE, "timeline-1"), emptyList(), 1000, 30.0, 1080, 1920)
        val render = RenderContract(header(ContractType.RENDER, "render-1"), AvspId("timeline-1"), widthPx = 1080, heightPx = 1920, frameRate = 30.0, codec = "H264")
        val qc = QcContract(header(ContractType.QC, "qc-1"), AvspId("render-1"), QcStatus.PASS, emptyList())
        val publish = PublishContract(header(ContractType.PUBLISH, "publish-1"), AvspId("render-1"), PublishTarget(PublishPlatform.YOUTUBE), "Demo")
        val analytics = AnalyticsContract(header(ContractType.ANALYTICS, "analytics-1"), AvspId("publish-1"), PublishPlatform.YOUTUBE, 1L)

        assertEquals("timeline-1", render.timelineId.value)
        assertEquals("render-1", qc.renderId.value)
        assertEquals("render-1", publish.renderId.value)
        assertEquals("publish-1", analytics.publishId.value)
    }

    @Test
    fun invalidVersionIsRejected() {
        val h = header(ContractType.PROJECT).copy(contractVersion = "1.x")
        assertTrue(!Avsp20ContractRegistry.isSupported(h))
    }
}

