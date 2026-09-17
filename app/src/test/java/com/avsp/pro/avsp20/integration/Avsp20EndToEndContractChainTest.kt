package com.avsp.pro.avsp20.integration

import com.avsp.pro.avsp20.contracts.*
import org.junit.Assert.assertTrue
import org.junit.Test

class Avsp20EndToEndContractChainTest {

    private fun header(type: ContractType, id: String, projectId: String) =
        ContractHeader(
            contractType = type,
            id = AvspId(id),
            projectId = AvspId(projectId),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

    @Test
    fun validatesCompletePipeline() {
        val project = ProjectContract(
            header = header(ContractType.PROJECT, "project-1", "project-1"),
            name = "S17 Test Project",
            language = ScriptLanguage.EN,
            aspectRatio = "16:9",
            status = "READY",
            sourceTopic = "S17"
        )

        val script = ScriptContract(
            header = header(ContractType.SCRIPT, "script-1", "project-1"),
            title = "Test Script",
            language = ScriptLanguage.EN,
            segments = listOf(
                ScriptSegment(
                    segmentId = AvspId("segment-1"),
                    order = 0,
                    text = "Test segment",
                    language = ScriptLanguage.EN,
                    durationMs = 5000L
                )
            ),
            totalDurationMs = 5000L
        )

        val audio = AudioContract(
            header = header(ContractType.AUDIO, "audio-1", "project-1"),
            sourceScriptId = AvspId("script-1"),
            uri = "audio://test",
            language = ScriptLanguage.EN,
            durationMs = 5000L,
            sampleRateHz = 24000,
            channels = 1,
            voiceId = "test-voice"
        )

        val scene = SceneContract(
            header = header(ContractType.SCENE, "scene-1", "project-1"),
            sceneId = AvspId("scene-1"),
            order = 0,
            role = SceneRole.BODY,
            scriptSegmentId = AvspId("segment-1"),
            narrationStartMs = 0L,
            narrationEndMs = 5000L,
            visualIntent = "Test visual",
            requiredAssetKinds = listOf(MediaKind.VIDEO)
        )

        val asset = AssetContract(
            header = header(ContractType.ASSET, "asset-1", "project-1"),
            uri = "file://test.mp4",
            fileName = "test.mp4",
            mediaKind = MediaKind.VIDEO,
            durationMs = 5000L,
            widthPx = 1920,
            heightPx = 1080,
            source = "S17_TEST",
            tags = listOf("test")
        )

        val timeline = TimelineContract(
            header = header(ContractType.TIMELINE, "timeline-1", "project-1"),
            items = listOf(
                TimelineItem(
                    itemId = AvspId("item-1"),
                    trackType = TimelineTrackType.VIDEO,
                    assetId = AvspId("asset-1"),
                    sceneId = AvspId("scene-1"),
                    startMs = 0L,
                    durationMs = 5000L
                )
            ),
            durationMs = 5000L,
            frameRate = 30.0,
            widthPx = 1920,
            heightPx = 1080
        )

        val render = RenderContract(
            header = header(ContractType.RENDER, "render-1", "project-1"),
            timelineId = AvspId("timeline-1"),
            outputUri = "file://render.mp4",
            widthPx = 1920,
            heightPx = 1080,
            frameRate = 30.0,
            codec = "H264",
            audioCodec = "AAC",
            status = RenderStatus.SUCCESS
        )

        val qc = QcContract(
            header = header(ContractType.QC, "qc-1", "project-1"),
            renderId = AvspId("render-1"),
            status = QcStatus.PASS,
            checks = listOf(
                QcCheck("codec", "Codec", true),
                QcCheck("duration", "Duration", true)
            ),
            durationMs = 5000L
        )

        val publish = PublishContract(
            header = header(ContractType.PUBLISH, "publish-1", "project-1"),
            renderId = AvspId("render-1"),
            target = PublishTarget(platform = PublishPlatform.YOUTUBE),
            title = "S17 Test Publish",
            status = PublishStatus.QUEUED
        )

        val analytics = AnalyticsContract(
            header = header(ContractType.ANALYTICS, "analytics-1", "project-1"),
            publishId = AvspId("publish-1"),
            platform = PublishPlatform.YOUTUBE,
            capturedAtEpochMs = 1L,
            metrics = emptyList()
        )

        val result = Avsp20IntegrationValidator.validatePipeline(
            project = project,
            script = script,
            audio = audio,
            scenes = listOf(scene),
            assets = listOf(asset),
            timeline = timeline,
            render = render,
            qc = qc,
            publish = publish,
            analytics = analytics
        )

        assertTrue("Pipeline validation errors: ${result.errors}", result.valid)
    }
}
