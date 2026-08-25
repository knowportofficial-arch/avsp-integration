package com.avsp.pro.script.repository

import com.avsp.pro.core.contracts.ArtifactStatus
import com.avsp.pro.core.contracts.ScriptReference
import com.avsp.pro.core.error.InvalidInputException
import com.avsp.pro.core.integration.ArtifactNames
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.core.model.ProjectLanguage
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.script.contract.ScriptGenerationRequest
import com.avsp.pro.script.contract.ScriptPackage
import com.avsp.pro.script.contract.ScriptScene
import com.avsp.pro.script.contract.ShotType
import com.avsp.pro.script.contract.TransitionIntent
import com.avsp.pro.script.generator.ScriptGeneratorRegistry
import com.avsp.pro.script.validation.ScriptValidator
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea
import com.google.gson.Gson
import com.google.gson.GsonBuilder

interface ScriptRepository {
    suspend fun generate(request: ScriptGenerationRequest): ScriptPackage
    suspend fun save(script: ScriptPackage): ScriptPackage
    suspend fun load(projectId: String, scriptId: String? = null): ScriptPackage?
    suspend fun listReferences(projectId: String): List<ScriptReference>
    suspend fun updateEdited(script: ScriptPackage): ScriptPackage
    fun isAiConfigured(): Boolean
}

class ScriptRepositoryImpl(
    private val storage: AvspStorage,
    private val generatorRegistry: ScriptGeneratorRegistry,
    private val logger: AvspLogger,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : ScriptRepository {

    override suspend fun generate(request: ScriptGenerationRequest): ScriptPackage {
        val pre = ScriptValidator.validateRequest(request)
        if (!pre.isValid) {
            logger.warning("M2", "Script generation rejected", details = pre.errors.joinToString("; "))
            throw InvalidInputException(
                message = pre.errors.firstOrNull() ?: "Invalid script request",
                module = "M2",
                details = pre.errors.joinToString("; ")
            )
        }
        storage.ensureProjectLayout(request.projectId)
        val generator = generatorRegistry.resolve()
        logger.info(
            "M2",
            "Generating script via ${generator.providerId}",
            projectId = request.projectId
        )
        val generated = generator.generate(request)
        if (!generated.validation.isValid) {
            logger.warning(
                "M2",
                "Generated script failed validation",
                details = generated.validation.errors.joinToString("; "),
                projectId = request.projectId
            )
            // Persist validation failure package only if caller wants; still return it.
            return generated
        }
        return save(generated)
    }

    override suspend fun save(script: ScriptPackage): ScriptPackage {
        val validated = script.copy(
            validation = ScriptValidator.validatePackage(script),
            metadata = script.metadata.copy(updatedAt = System.currentTimeMillis())
        )
        if (!validated.validation.isValid) {
            throw InvalidInputException(
                message = validated.validation.errors.firstOrNull() ?: "Invalid script package",
                module = "M2",
                details = validated.validation.errors.joinToString("; ")
            )
        }
        val relative = relativePath(validated.scriptId)
        storage.save(
            area = StorageArea.PROJECT_DATA,
            relativePath = relative,
            content = gson.toJson(validated),
            projectId = validated.projectId
        )
        // Also write canonical script.json pointer for integration contract.
        storage.save(
            area = StorageArea.PROJECT_DATA,
            relativePath = "${ProjectPaths.SCRIPT}/${ArtifactNames.SCRIPT_JSON}",
            content = gson.toJson(validated),
            projectId = validated.projectId
        )
        logger.info("M2", "Script saved", details = validated.scriptId, projectId = validated.projectId)
        return validated
    }

    override suspend fun load(projectId: String, scriptId: String?): ScriptPackage? {
        val relative = if (scriptId != null) {
            relativePath(scriptId)
        } else {
            "${ProjectPaths.SCRIPT}/${ArtifactNames.SCRIPT_JSON}"
        }
        if (!storage.exists(StorageArea.PROJECT_DATA, relative, projectId)) {
            return null
        }
        val json = storage.readText(StorageArea.PROJECT_DATA, relative, projectId)
        return gson.fromJson(json, ScriptPackage::class.java)
    }

    override suspend fun listReferences(projectId: String): List<ScriptReference> {
        val files = storage.list(StorageArea.PROJECT_DATA, ProjectPaths.SCRIPT, projectId)
            .filter { it.endsWith(".json") }
        return files.mapNotNull { fileName ->
            val loaded = runCatching {
                val json = storage.readText(
                    StorageArea.PROJECT_DATA,
                    "${ProjectPaths.SCRIPT}/$fileName",
                    projectId
                )
                gson.fromJson(json, ScriptPackage::class.java)
            }.getOrNull() ?: return@mapNotNull null
            ScriptReference(
                scriptId = loaded.scriptId,
                projectId = loaded.projectId,
                relativePath = "${ProjectPaths.SCRIPT}/$fileName",
                language = runCatching { ProjectLanguage.fromCode(loaded.language) }
                    .getOrDefault(ProjectLanguage.ENGLISH),
                version = 1,
                createdAt = loaded.metadata.createdAt,
                status = if (loaded.validation.isValid) ArtifactStatus.READY else ArtifactStatus.FAILED
            )
        }.distinctBy { it.scriptId }
    }

    override suspend fun updateEdited(script: ScriptPackage): ScriptPackage {
        val scenes = script.scenes
            .sortedBy { it.order }
            .mapIndexed { index, scene -> scene.copy(order = index) }
        val total = scenes.sumOf { it.durationMs }
        val edited = script.copy(
            scenes = scenes,
            estimatedDurationMs = total,
            metadata = script.metadata.copy(updatedAt = System.currentTimeMillis())
        )
        return save(edited)
    }

    override fun isAiConfigured(): Boolean = generatorRegistry.isRemoteConfigured()

    private fun relativePath(scriptId: String): String =
        "${ProjectPaths.SCRIPT}/$scriptId.json"
}

/** Helpers used by UI editors for safe field updates. */
object ScriptEditHelpers {
    fun updateSceneNarration(script: ScriptPackage, sceneId: String, narration: String): ScriptPackage {
        val scenes = script.scenes.map {
            if (it.sceneId == sceneId) it.copy(narration = narration) else it
        }
        return script.copy(scenes = scenes)
    }

    fun updateTitle(script: ScriptPackage, title: String): ScriptPackage =
        script.copy(title = title.trim())

    fun updateHook(script: ScriptPackage, hook: String): ScriptPackage =
        script.copy(hook = hook.trim())

    fun updateCta(script: ScriptPackage, cta: String): ScriptPackage =
        script.copy(cta = cta.trim())

    fun normalizeScene(
        sceneId: String,
        order: Int,
        durationMs: Long,
        narration: String
    ): ScriptScene = ScriptScene(
        sceneId = sceneId,
        order = order,
        durationMs = durationMs,
        narration = narration,
        shotType = ShotType.MEDIUM,
        transition = TransitionIntent.CUT
    )
}
