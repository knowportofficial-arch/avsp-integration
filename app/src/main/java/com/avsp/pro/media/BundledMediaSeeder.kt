package com.avsp.pro.media

import android.content.Context
import android.media.MediaMetadataRetriever
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.database.dao.MediaAssetDao
import com.avsp.pro.database.EntityMappers
import com.avsp.pro.logs.AvspLogger
import com.avsp.pro.storage.AvspStorage
import com.avsp.pro.storage.StorageArea

/**
 * Imports the bundled M4 QA clips into the real project media inventory.
 * Idempotent: existing files/assets are reused and never duplicated.
 */
class BundledMediaSeeder(
    private val context: Context,
    private val storage: AvspStorage,
    private val mediaAssetDao: MediaAssetDao,
    private val logger: AvspLogger
) {
    private val clips = listOf(
        "01_INTRO.mp4" to listOf("intro", "m4-test"),
        "02_HOOK.mp4" to listOf("hook", "m4-test"),
        "03_SCENE.mp4" to listOf("scene", "m4-test"),
        "04_OUTRO.mp4" to listOf("outro", "m4-test")
    )

    suspend fun ensureForProject(projectId: String) {
        storage.ensureProjectLayout(projectId)
        clips.forEachIndexed { index, (fileName, tags) ->
            val relative = "${ProjectPaths.MEDIA}/$fileName"
            val assetId = "m4_test_${projectId}_${index + 1}"
            val existingPath = storage.resolve(StorageArea.PROJECT_DATA, relative, projectId)
            if (!java.io.File(existingPath).exists()) {
                context.assets.open("m4_test_clips/$fileName").use { input ->
                    storage.openOutput(StorageArea.PROJECT_DATA, relative, projectId).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val file = java.io.File(existingPath)
            val info = inspect(file)
            mediaAssetDao.upsert(
                EntityMappers.toEntity(
                    MediaAsset(
                        assetId = assetId,
                        projectId = projectId,
                        fileName = fileName,
                        relativePath = relative,
                        mimeType = "video/mp4",
                        sizeBytes = file.length(),
                        durationMs = info.durationMs,
                        width = info.width,
                        height = info.height,
                        createdAt = 1_000L + index,
                        tags = tags,
                        metadata = mapOf("source" to "bundled_m4_qa", "order" to index.toString())
                    )
                )
            )
        }
        logger.info("M4", "Bundled M4 test clips available", details = "${clips.size} clips", projectId = projectId)
    }

    private data class Info(val durationMs: Long?, val width: Int?, val height: Int?)

    private fun inspect(file: java.io.File): Info {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            Info(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            )
        } finally { r.release() }
    }
}
