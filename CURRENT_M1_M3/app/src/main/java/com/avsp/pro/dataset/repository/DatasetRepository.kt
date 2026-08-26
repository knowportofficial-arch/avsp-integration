package com.avsp.pro.dataset.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.avsp.pro.capture.database.dao.MediaDao
import com.avsp.pro.capture.database.entity.MediaEntity
import com.avsp.pro.dataset.analyzer.BestShotSelector
import com.avsp.pro.dataset.analyzer.DuplicateDetector
import com.avsp.pro.dataset.analyzer.LocalQualityAnalyzer
import com.avsp.pro.dataset.analyzer.ThumbnailGenerator
import com.avsp.pro.dataset.model.DatasetCategory
import com.avsp.pro.dataset.model.DatasetMedia
import com.avsp.pro.dataset.model.QualityResult
import com.avsp.pro.dataset.model.Recommendation
import com.avsp.pro.dataset.analyzer.RecommendationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * M7 Personal Dataset repository.
 * Consumes M6 media, enriches with local analysis, never deletes originals.
 */
class DatasetRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val qualityAnalyzer: LocalQualityAnalyzer = LocalQualityAnalyzer(context),
    private val duplicateDetector: DuplicateDetector = DuplicateDetector(context),
    private val thumbnailGenerator: ThumbnailGenerator = ThumbnailGenerator(context),
    private val bestShotSelector: BestShotSelector = BestShotSelector()
) {

    fun observeProjectMedia(projectId: String): Flow<List<DatasetMedia>> {
        return mediaDao.getMediaForProjectFlow(projectId).map { list ->
            list.map { it.toDatasetMedia() }
        }
    }

    suspend fun getProjectMedia(projectId: String): List<DatasetMedia> = withContext(Dispatchers.IO) {
        mediaDao.getMediaForProject(projectId).map { it.toDatasetMedia() }
    }

    /**
     * Import / register media produced by M6 (or external). Runs local analysis pipeline.
     */
    suspend fun ingestMedia(
        projectId: String,
        uriString: String,
        mediaType: String,
        displayName: String,
        category: String = DatasetCategory.UNCATEGORIZED.key,
        tags: List<String> = emptyList(),
        shotType: String = "WIDE",
        missionId: String? = null,
        missionShotId: String? = null,
        durationSeconds: Long = 0L,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracyMeters: Float? = null
    ): DatasetMedia = withContext(Dispatchers.IO) {
        val existing = mediaDao.getMediaForProject(projectId)
        val fileSize = probeFileSize(uriString)
        val isDup = duplicateDetector.isDuplicateOf(
            candidateUri = uriString,
            candidateType = mediaType,
            candidateSize = fileSize,
            existing = existing,
            missionShotId = missionShotId
        )
        val quality = qualityAnalyzer.analyze(uriString, mediaType, isDuplicate = isDup)
        val dims = probeDimensions(uriString, mediaType)
        val now = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val date = Date(now)

        val id = UUID.randomUUID().toString()
        val thumb = thumbnailGenerator.generate(id, uriString, mediaType)
        val hash = duplicateDetector.computeHash(uriString, mediaType, fileSize)?.hash

        val entity = MediaEntity(
            id = id,
            projectId = projectId,
            uriString = uriString,
            mediaType = mediaType.uppercase(Locale.US),
            displayName = displayName,
            shotType = shotType,
            missionId = missionId,
            missionShotId = missionShotId,
            durationSeconds = durationSeconds,
            fileSizeBytes = fileSize,
            qualityScore = (quality.score * 100).toInt().coerceIn(0, 100),
            latitude = latitude,
            longitude = longitude,
            locationAccuracyMeters = locationAccuracyMeters,
            createdAt = now,
            category = category.ifBlank { DatasetCategory.UNCATEGORIZED.key },
            tagsCsv = tags.joinToString(","),
            thumbnailPath = thumb,
            width = dims.first,
            height = dims.second,
            fps = dims.third,
            orientation = dims.let { (w, h, _) ->
                when {
                    w > 0 && h > 0 && h > w -> "PORTRAIT"
                    w > 0 && h > 0 && w >= h -> "LANDSCAPE"
                    else -> "UNKNOWN"
                }
            },
            dateString = dateFmt.format(date),
            timeString = timeFmt.format(date),
            qualityScoreNormalized = quality.score,
            blurDetected = quality.blur,
            exposureOk = quality.exposure,
            compositionOk = quality.composition,
            faceQuality = quality.faceQuality,
            closedEye = quality.closedEye,
            isDuplicate = quality.duplicate,
            recommendation = quality.recommendation.name,
            isBestShot = false,
            perceptualHash = hash
        )
        mediaDao.insertMedia(entity)

        // Re-rank best shot within mission group if applicable
        if (!missionShotId.isNullOrBlank()) {
            recomputeBestShot(projectId, missionShotId)
        }

        entity.toDatasetMedia()
    }

    /**
     * Enrich an existing M6 MediaEntity that was inserted without full M7 analysis.
     */
    suspend fun analyzeExisting(mediaId: String): DatasetMedia? = withContext(Dispatchers.IO) {
        val entity = mediaDao.getMediaById(mediaId) ?: return@withContext null
        val existing = mediaDao.getMediaForProject(entity.projectId)
        val isDup = duplicateDetector.isDuplicateOf(
            candidateUri = entity.uriString,
            candidateType = entity.mediaType,
            candidateSize = entity.fileSizeBytes,
            existing = existing.filter { it.id != entity.id },
            missionShotId = entity.missionShotId
        )
        val quality = qualityAnalyzer.analyze(entity.uriString, entity.mediaType, isDuplicate = isDup)
        val dims = if (entity.width > 0) {
            Triple(entity.width, entity.height, entity.fps)
        } else {
            probeDimensions(entity.uriString, entity.mediaType)
        }
        val thumb = entity.thumbnailPath
            ?: thumbnailGenerator.generate(entity.id, entity.uriString, entity.mediaType)
        val hash = entity.perceptualHash
            ?: duplicateDetector.computeHash(entity.uriString, entity.mediaType, entity.fileSizeBytes)?.hash

        val updated = entity.copy(
            qualityScore = (quality.score * 100).toInt().coerceIn(0, 100),
            qualityScoreNormalized = quality.score,
            blurDetected = quality.blur,
            exposureOk = quality.exposure,
            compositionOk = quality.composition,
            faceQuality = quality.faceQuality,
            closedEye = quality.closedEye,
            isDuplicate = quality.duplicate,
            recommendation = quality.recommendation.name,
            thumbnailPath = thumb,
            width = dims.first,
            height = dims.second,
            fps = dims.third,
            orientation = when {
                dims.first > 0 && dims.second > dims.first -> "PORTRAIT"
                dims.first > 0 && dims.first >= dims.second -> "LANDSCAPE"
                else -> entity.orientation
            },
            perceptualHash = hash
        )
        mediaDao.updateMedia(updated)
        if (!updated.missionShotId.isNullOrBlank()) {
            recomputeBestShot(updated.projectId, updated.missionShotId)
        }
        updated.toDatasetMedia()
    }

    suspend fun recomputeBestShot(projectId: String, missionShotId: String) = withContext(Dispatchers.IO) {
        val group = mediaDao.getByMissionShot(projectId, missionShotId)
        if (group.isEmpty()) return@withContext
        mediaDao.clearBestShotFlags(projectId, missionShotId)
        val bestId = bestShotSelector.selectBestId(group) ?: return@withContext
        mediaDao.markBestShot(bestId)
    }

    suspend fun search(projectId: String, query: String): List<DatasetMedia> = withContext(Dispatchers.IO) {
        mediaDao.search(projectId, query.trim()).map { it.toDatasetMedia() }
    }

    suspend fun filter(
        projectId: String,
        category: String? = null,
        mediaType: String? = null,
        minQuality: Double? = null,
        onlyBest: Boolean = false
    ): List<DatasetMedia> = withContext(Dispatchers.IO) {
        mediaDao.queryFiltered(
            projectId = projectId,
            category = category,
            mediaType = mediaType,
            minQuality = minQuality,
            onlyBest = if (onlyBest) 1 else 0
        ).map { it.toDatasetMedia() }
    }

    suspend fun updateCategory(mediaId: String, category: String) = withContext(Dispatchers.IO) {
        val entity = mediaDao.getMediaById(mediaId) ?: return@withContext
        mediaDao.updateMedia(entity.copy(category = category))
    }

    suspend fun updateTags(mediaId: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val entity = mediaDao.getMediaById(mediaId) ?: return@withContext
        mediaDao.updateMedia(entity.copy(tagsCsv = tags.joinToString(",")))
    }

    /**
     * Database-only removal of the media record. Does NOT delete the original file
     * unless explicitly requested (M7 default: preserve original).
     */
    suspend fun removeFromDataset(mediaId: String, deleteOriginalFile: Boolean = false) =
        withContext(Dispatchers.IO) {
            val media = mediaDao.getMediaById(mediaId) ?: return@withContext
            if (deleteOriginalFile) {
                try {
                    context.contentResolver.delete(Uri.parse(media.uriString), null, null)
                } catch (_: Exception) {
                    // Best-effort
                }
            }
            // Always remove private thumbnail if present
            media.thumbnailPath?.let { path ->
                try {
                    java.io.File(path).delete()
                } catch (_: Exception) {
                }
            }
            mediaDao.deleteMediaById(mediaId)
        }

    // ---- helpers ----

    private fun probeFileSize(uriString: String): Long {
        return try {
            context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")?.use {
                it.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun probeDimensions(uriString: String, mediaType: String): Triple<Int, Int, Int> {
        return try {
            if (mediaType.equals("VIDEO", ignoreCase = true)) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, Uri.parse(uriString))
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toFloatOrNull()?.toInt() ?: 0
                    Triple(w, h, fps)
                } finally {
                    r.release()
                }
            } else {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
                Triple(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), 0)
            }
        } catch (_: Exception) {
            Triple(0, 0, 0)
        }
    }

    private fun MediaEntity.toDatasetMedia(): DatasetMedia {
        // Re-derive recommendation from stored metrics so legacy rows that were
        // written with the old KEEP default cannot surface low-score KEEP.
        val derived = RecommendationPolicy.decide(
            score = qualityScoreNormalized,
            blur = blurDetected,
            exposureOk = exposureOk,
            isDuplicate = isDuplicate,
            analysisFailed = false
        )
        val qr = QualityResult(
            score = qualityScoreNormalized,
            blur = blurDetected,
            exposure = exposureOk,
            composition = compositionOk,
            faceQuality = faceQuality,
            closedEye = closedEye,
            duplicate = isDuplicate,
            recommendation = derived
        )
        return DatasetMedia(
            clipId = id,
            fileUri = uriString,
            mediaType = mediaType,
            category = category,
            date = dateString,
            time = timeString,
            locationLat = latitude,
            locationLng = longitude,
            durationMs = durationSeconds * 1000,
            width = width,
            height = height,
            fps = fps,
            orientation = orientation,
            tags = tagsList(),
            thumbnailUri = thumbnailPath,
            qualityScore = qualityScoreNormalized,
            qualityResult = qr,
            isBestShot = isBestShot,
            missionId = missionId,
            missionShotId = missionShotId,
            projectId = projectId,
            displayName = displayName,
            createdAt = createdAt,
            fileSizeBytes = fileSizeBytes
        )
    }
}
