package com.avsp.pro.capture.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.avsp.pro.capture.database.dao.MediaDao
import com.avsp.pro.capture.database.entity.MediaEntity
import com.avsp.pro.core.contracts.MediaAsset
import com.avsp.pro.core.integration.ProjectPaths
import com.avsp.pro.dataset.analyzer.BestShotSelector
import com.avsp.pro.dataset.analyzer.DuplicateDetector
import com.avsp.pro.dataset.analyzer.LocalQualityAnalyzer
import com.avsp.pro.dataset.analyzer.RecommendationPolicy
import com.avsp.pro.dataset.analyzer.ThumbnailGenerator
import com.avsp.pro.dataset.model.Recommendation
import com.avsp.pro.repository.ProjectRepository as ProProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * M6-facing media repository.
 *
 * On every capture, media is passed through M7 [LocalQualityAnalyzer].
 * The resulting QualityResult (score, blur, exposure, composition,
 * recommendation, etc.) is persisted on [MediaEntity].
 *
 * Pro [projectId] is the authoritative project identity. Capture metadata lives in
 * [CaptureDatabase]; a mirrored [MediaAsset] row is written into Pro storage inventory.
 *
 * Original files are never auto-deleted by M7 analysis.
 */
class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val proProjectRepository: ProProjectRepository? = null
) {

    private val qualityAnalyzer by lazy { LocalQualityAnalyzer(context) }
    private val duplicateDetector by lazy { DuplicateDetector(context) }
    private val thumbnailGenerator by lazy { ThumbnailGenerator(context) }
    private val bestShotSelector by lazy { BestShotSelector() }

    fun getMediaForProjectFlow(projectId: String): Flow<List<MediaEntity>> {
        return mediaDao.getMediaForProjectFlow(projectId)
    }

    suspend fun getMediaForProject(projectId: String): List<MediaEntity> = withContext(Dispatchers.IO) {
        mediaDao.getMediaForProject(projectId)
    }

    /**
     * Persist a newly captured M6 asset after running full M7 local analysis.
     *
     * The caller-supplied [qualityScore] is treated only as a soft hint when
     * analysis fails; the authoritative score and recommendation always come
     * from [LocalQualityAnalyzer] when the file can be decoded.
     */
    suspend fun saveCapturedMedia(
        projectId: String,
        uriString: String,
        mediaType: String,
        displayName: String,
        shotType: String = "WIDE",
        missionId: String? = null,
        missionShotId: String? = null,
        durationSeconds: Long = 0L,
        qualityScore: Int = 0,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracyMeters: Float? = null,
        category: String = "Uncategorized"
    ): MediaEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val existing = mediaDao.getMediaForProject(projectId)
        val fileSize = probeFileSize(uriString)

        val isDup = duplicateDetector.isDuplicateOf(
            candidateUri = uriString,
            candidateType = mediaType,
            candidateSize = fileSize,
            existing = existing,
            missionShotId = missionShotId
        )

        // M7 analysis — authoritative quality + recommendation
        val quality = qualityAnalyzer.analyze(
            uriString = uriString,
            mediaType = mediaType,
            isDuplicate = isDup
        )

        val dims = probeDimensions(uriString, mediaType)
        val now = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val date = Date(now)

        val thumb = thumbnailGenerator.generate(id, uriString, mediaType)
        val hash = duplicateDetector.computeHash(uriString, mediaType, fileSize)?.hash

        // Prefer analyzer score; if analysis failed (score 0 + REVIEW with no defects),
        // fall back to M6-supplied percent for display only — recommendation stays REVIEW.
        val analysisFailed = quality.recommendation == Recommendation.REVIEW &&
            quality.score == 0.0 &&
            !quality.blur &&
            quality.exposure

        val effectiveScore: Double
        val effectivePercent: Int
        val effectiveRec: String

        if (analysisFailed && qualityScore in 1..100) {
            effectiveScore = (qualityScore / 100.0).coerceIn(0.0, 1.0)
            effectivePercent = qualityScore.coerceIn(0, 100)
            effectiveRec = RecommendationPolicy.decide(
                score = effectiveScore,
                analysisFailed = true // force REVIEW on analysis failure
            ).name
        } else {
            effectiveScore = quality.score
            effectivePercent = (quality.score * 100).toInt().coerceIn(0, 100)
            effectiveRec = quality.recommendation.name
        }

        val orientation = when {
            dims.first > 0 && dims.second > dims.first -> "PORTRAIT"
            dims.first > 0 && dims.first >= dims.second -> "LANDSCAPE"
            else -> "UNKNOWN"
        }

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
            qualityScore = effectivePercent,
            qualityScoreNormalized = effectiveScore,
            latitude = latitude,
            longitude = longitude,
            locationAccuracyMeters = locationAccuracyMeters,
            createdAt = now,
            category = category.ifBlank { "Uncategorized" },
            tagsCsv = "",
            thumbnailPath = thumb,
            width = dims.first,
            height = dims.second,
            fps = dims.third,
            orientation = orientation,
            dateString = dateFmt.format(date),
            timeString = timeFmt.format(date),
            blurDetected = quality.blur,
            exposureOk = quality.exposure,
            compositionOk = quality.composition,
            faceQuality = quality.faceQuality,
            closedEye = quality.closedEye,
            isDuplicate = quality.duplicate || isDup,
            recommendation = effectiveRec,
            isBestShot = false,
            perceptualHash = hash
        )
        mediaDao.insertMedia(entity)
        syncProMediaAsset(entity)

        if (!missionShotId.isNullOrBlank()) {
            recomputeBestShot(projectId, missionShotId)
        }

        mediaDao.getMediaById(id) ?: entity
    }

    /**
     * Mirror capture metadata into Pro [MediaAsset] inventory (FileAvspStorage layout).
     * URI remains the MediaStore/content authority; relativePath is a stable camera slot.
     */
    private suspend fun syncProMediaAsset(entity: MediaEntity) {
        val pro = proProjectRepository ?: return
        val ext = if (entity.mediaType.equals("VIDEO", ignoreCase = true)) "mp4" else "jpg"
        val mime = if (entity.mediaType.equals("VIDEO", ignoreCase = true)) "video/mp4" else "image/jpeg"
        val relative = "${ProjectPaths.CAMERA}/${entity.id}.$ext"
        runCatching {
            pro.addMediaAsset(
                MediaAsset(
                    assetId = entity.id,
                    projectId = entity.projectId,
                    fileName = entity.displayName.ifBlank { "capture_${entity.id}.$ext" },
                    relativePath = relative,
                    mimeType = mime,
                    sizeBytes = entity.fileSizeBytes,
                    durationMs = entity.durationSeconds.takeIf { it > 0 }?.times(1000),
                    width = entity.width.takeIf { it > 0 },
                    height = entity.height.takeIf { it > 0 },
                    createdAt = entity.createdAt,
                    tags = entity.tagsList() + listOfNotNull(
                        entity.shotType.takeIf { it.isNotBlank() },
                        entity.recommendation.takeIf { it.isNotBlank() }
                    ),
                    metadata = mapOf(
                        "uri" to entity.uriString,
                        "mediaType" to entity.mediaType,
                        "qualityScore" to entity.qualityScore.toString(),
                        "qualityScoreNormalized" to entity.qualityScoreNormalized.toString(),
                        "recommendation" to entity.recommendation,
                        "isBestShot" to entity.isBestShot.toString(),
                        "orientation" to entity.orientation,
                        "thumbnailPath" to (entity.thumbnailPath ?: ""),
                        "source" to "M7_CAPTURE"
                    )
                )
            )
        }
    }

    private suspend fun recomputeBestShot(projectId: String, missionShotId: String) {
        val group = mediaDao.getByMissionShot(projectId, missionShotId)
        if (group.isEmpty()) return
        mediaDao.clearBestShotFlags(projectId, missionShotId)
        val bestId = bestShotSelector.selectBestId(group) ?: return
        mediaDao.markBestShot(bestId)
    }

    suspend fun deleteMedia(id: String) = withContext(Dispatchers.IO) {
        val media = mediaDao.getMediaById(id)
        if (media != null) {
            try {
                context.contentResolver.delete(Uri.parse(media.uriString), null, null)
            } catch (_: Exception) {
                // Keep database cleanup resilient even if the MediaStore item was already removed.
            }
            media.thumbnailPath?.let { path ->
                try {
                    java.io.File(path).delete()
                } catch (_: Exception) {
                }
            }
        }
        mediaDao.deleteMediaById(id)
    }

    private fun probeFileSize(uriString: String): Long {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: return 0L
                return java.io.File(path).takeIf { it.exists() }?.length() ?: 0L
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
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
                    val uri = Uri.parse(uriString)
                    if (uri.scheme.equals("file", ignoreCase = true) && uri.path != null) {
                        r.setDataSource(uri.path)
                    } else {
                        r.setDataSource(context, uri)
                    }
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toFloatOrNull()?.toInt() ?: 0
                    Triple(w, h, fps)
                } finally {
                    r.release()
                }
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val uri = Uri.parse(uriString)
                if (uri.scheme.equals("file", ignoreCase = true) && uri.path != null) {
                    BitmapFactory.decodeFile(uri.path, opts)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
                Triple(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), 0)
            }
        } catch (_: Exception) {
            Triple(0, 0, 0)
        }
    }
}
