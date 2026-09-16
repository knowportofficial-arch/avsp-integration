package com.avsp.pro.avsp20.integration

import com.avsp.pro.avsp20.contracts.*

data class IntegrationValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
)

object Avsp20IntegrationValidator {
    fun validateProject(c: ProjectContract) = validateHeader(c.header, ContractType.PROJECT)

    fun validateScript(c: ScriptContract): IntegrationValidationResult {
        val e = mutableListOf<String>()
        e += headerErrors(c.header, ContractType.SCRIPT)
        if (c.segments.map { it.order }.distinct().size != c.segments.size) e += "SCRIPT_SEGMENT_ORDER_DUPLICATE"
        if (c.segments.any { it.text.isBlank() }) e += "SCRIPT_SEGMENT_TEXT_EMPTY"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateAudio(c: AudioContract): IntegrationValidationResult {
        val e = mutableListOf<String>()
        e += headerErrors(c.header, ContractType.AUDIO)
        if (c.sourceScriptId.value.isBlank()) e += "AUDIO_SOURCE_SCRIPT_ID_EMPTY"
        if (c.durationMs < 0) e += "AUDIO_DURATION_NEGATIVE"
        if (c.sampleRateHz <= 0) e += "AUDIO_SAMPLE_RATE_INVALID"
        if (c.channels <= 0) e += "AUDIO_CHANNELS_INVALID"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateScene(c: SceneContract): IntegrationValidationResult =
        IntegrationValidationResult(headerErrors(c.header, ContractType.SCENE).isEmpty(),
            headerErrors(c.header, ContractType.SCENE))

    fun validateAsset(c: AssetContract): IntegrationValidationResult {
        val e = mutableListOf<String>()
        e += headerErrors(c.header, ContractType.ASSET)
        if (c.uri.isBlank()) e += "ASSET_URI_EMPTY"
        if (c.fileName.isBlank()) e += "ASSET_FILENAME_EMPTY"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateTimeline(c: TimelineContract): IntegrationValidationResult {
        val e = mutableListOf<String>()
        e += headerErrors(c.header, ContractType.TIMELINE)
        if (c.durationMs < 0) e += "TIMELINE_DURATION_NEGATIVE"
        if (c.frameRate <= 0) e += "TIMELINE_FRAMERATE_INVALID"
        if (c.widthPx <= 0 || c.heightPx <= 0) e += "TIMELINE_DIMENSIONS_INVALID"
        if (c.items.any { it.startMs < 0 || it.durationMs < 0 }) e += "TIMELINE_ITEM_RANGE_INVALID"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateRender(c: RenderContract): IntegrationValidationResult {
        val e = mutableListOf<String>()
        e += headerErrors(c.header, ContractType.RENDER)
        if (c.timelineId.value.isBlank()) e += "RENDER_TIMELINE_ID_EMPTY"
        if (c.widthPx <= 0 || c.heightPx <= 0) e += "RENDER_DIMENSIONS_INVALID"
        if (c.frameRate <= 0) e += "RENDER_FRAMERATE_INVALID"
        if (c.codec.isBlank()) e += "RENDER_CODEC_EMPTY"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateQc(c: QcContract): IntegrationValidationResult {
        val e = headerErrors(c.header, ContractType.QC).toMutableList()
        if (c.renderId.value.isBlank()) e += "QC_RENDER_ID_EMPTY"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validatePublish(c: PublishContract): IntegrationValidationResult {
        val e = headerErrors(c.header, ContractType.PUBLISH).toMutableList()
        if (c.renderId.value.isBlank()) e += "PUBLISH_RENDER_ID_EMPTY"
        if (c.title.isBlank()) e += "PUBLISH_TITLE_EMPTY"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateAnalytics(c: AnalyticsContract): IntegrationValidationResult {
        val e = headerErrors(c.header, ContractType.ANALYTICS).toMutableList()
        if (c.publishId.value.isBlank()) e += "ANALYTICS_PUBLISH_ID_EMPTY"
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    fun validateHeader(h: ContractHeader, expected: ContractType): IntegrationValidationResult {
        val e = headerErrors(h, expected)
        return IntegrationValidationResult(e.isEmpty(), e)
    }

    private fun headerErrors(h: ContractHeader, expected: ContractType): List<String> {
        val e = mutableListOf<String>()
        if (h.contractType != expected) e += "HEADER_TYPE_MISMATCH"
        if (!Avsp20ContractRegistry.isSupported(h)) e += "HEADER_VERSION_UNSUPPORTED"
        if (h.id.value.isBlank()) e += "HEADER_ID_EMPTY"
        if (h.projectId.value.isBlank()) e += "HEADER_PROJECT_ID_EMPTY"
        if (h.createdAtEpochMs < 0 || h.updatedAtEpochMs < 0) e += "HEADER_TIMESTAMP_INVALID"
        return e
    }

    fun validatePipeline(
        project: ProjectContract,
        script: ScriptContract,
        audio: AudioContract,
        scenes: List<SceneContract>,
        assets: List<AssetContract>,
        timeline: TimelineContract,
        render: RenderContract,
        qc: QcContract,
        publish: PublishContract,
        analytics: AnalyticsContract
    ): IntegrationValidationResult {
        val e = mutableListOf<String>()
        e += validateProject(project).errors
        e += validateScript(script).errors
        e += validateAudio(audio).errors
        scenes.forEach { e += validateScene(it).errors }
        assets.forEach { e += validateAsset(it).errors }
        e += validateTimeline(timeline).errors
        e += validateRender(render).errors
        e += validateQc(qc).errors
        e += validatePublish(publish).errors
        e += validateAnalytics(analytics).errors

        if (audio.sourceScriptId != script.header.id) e += "PIPELINE_AUDIO_SCRIPT_LINK_MISMATCH"
        scenes.forEach { if (it.header.projectId != project.header.id) e += "PIPELINE_SCENE_PROJECT_LINK_MISMATCH" }
        assets.forEach { if (it.header.projectId != project.header.id) e += "PIPELINE_ASSET_PROJECT_LINK_MISMATCH" }
        if (timeline.header.projectId != project.header.id) e += "PIPELINE_TIMELINE_PROJECT_LINK_MISMATCH"
        if (render.timelineId != timeline.header.id) e += "PIPELINE_RENDER_TIMELINE_LINK_MISMATCH"
        if (qc.renderId != render.header.id) e += "PIPELINE_QC_RENDER_LINK_MISMATCH"
        if (publish.renderId != render.header.id) e += "PIPELINE_PUBLISH_RENDER_LINK_MISMATCH"
        if (analytics.publishId != publish.header.id) e += "PIPELINE_ANALYTICS_PUBLISH_LINK_MISMATCH"

        return IntegrationValidationResult(e.isEmpty(), e.distinct())
    }
}
