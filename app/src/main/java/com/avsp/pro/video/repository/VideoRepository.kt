package com.avsp.pro.video.repository

import com.avsp.pro.audio.integration.AudioToVideoContract
import com.avsp.pro.audio.repository.AudioRepository
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.repository.ProjectRepository
import com.avsp.pro.script.repository.ScriptRepository
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea
import com.avsp.pro.video.contract.VideoRenderPlan
import com.avsp.pro.video.contract.VideoRenderResult
import com.avsp.pro.video.contract.VideoRenderPlanFactory
import com.avsp.pro.video.engine.VideoEngine
import com.avsp.pro.media.BundledMediaSeeder
import com.avsp.pro.media.MediaAssetSelector

interface VideoRepository {
    suspend fun buildPlan(projectId: String): VideoRenderPlan
    suspend fun render(projectId: String): VideoRenderResult
}

class VideoRepositoryImpl(
    private val projectRepository: ProjectRepository,
    private val audioRepository: AudioRepository,
    private val scriptRepository: ScriptRepository,
    private val storage: AvspStorage,
    private val engine: VideoEngine,
    private val logger: AvspLogger,
    private val bundledMediaSeeder: BundledMediaSeeder
) : VideoRepository {
    override suspend fun buildPlan(projectId: String): VideoRenderPlan {
        val project = projectRepository.openProject(projectId)
        bundledMediaSeeder.ensureForProject(projectId)
        val audio = audioRepository.load(projectId)
            ?: error("M3 audio package not found. Generate Audio/TTS first.")
        val handoff = AudioToVideoContract.fromPackage(audio)
        val script = scriptRepository.load(projectId, handoff.scriptId)
        val assets = MediaAssetSelector.videoAssetsForRender(projectRepository.listMediaAssets(projectId))
        val visuals = handoff.segments.sortedBy { it.order }.mapIndexedNotNull { index, segment ->
            assets.getOrNull(index)?.let { segment.sceneId to it.relativePath }
        }.toMap()
        val mime = handoff.segments.sortedBy { it.order }.mapIndexedNotNull { index, segment ->
            assets.getOrNull(index)?.let { segment.sceneId to it.mimeType }
        }.toMap()
        // Output format is a project property. Source clips (including bundled QA
        // clips) must never override it. M4 adapts every source to this canvas.
        val (width, height) = project.aspectRatio.width to project.aspectRatio.height
        val plan = VideoRenderPlanFactory.fromAudio(handoff, visuals, mime, width, height)
        val overlayByScene = script?.scenes?.associate { it.sceneId to it.onScreenText.trim() }.orEmpty()
        return plan.copy(scenes = plan.scenes.map { it.copy(overlayText = overlayByScene[it.sceneId].takeUnless { text -> text.isNullOrBlank() }) })
    }

    override suspend fun render(projectId: String): VideoRenderResult {
        val plan = buildPlan(projectId)
        val result = engine.render(plan)
        val project = projectRepository.openProject(projectId)
        projectRepository.updateProject(
            project.copy(
                duration = result.durationMs,
                outputPath = result.outputRelativePath
            )
        )
        logger.info("M4", "Video rendered", details = result.outputRelativePath, projectId = projectId)
        return result
    }
}
