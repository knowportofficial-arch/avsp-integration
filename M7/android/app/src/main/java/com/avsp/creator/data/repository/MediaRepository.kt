package com.avsp.creator.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.avsp.creator.database.dao.MediaDao
import com.avsp.creator.database.entity.MediaEntity
import com.avsp.creator.dataset.analyzer.BestShotSelector
import com.avsp.creator.dataset.analyzer.DuplicateDetector
import com.avsp.creator.dataset.analyzer.LocalQualityAnalyzer
import com.avsp.creator.dataset.analyzer.RecommendationPolicy
import com.avsp.creator.dataset.analyzer.ThumbnailGenerator
import com.avsp.creator.dataset.model.Recommendation
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
 * Original files are never auto-deleted by M7 analysis.
 */
class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao
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

        if (!missionShotId.isNullOrBlank()) {
            recomputeBestShot(projectId, missionShotId)
        }

        mediaDao.getMediaById(id) ?: entity
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
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                Triple(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), 0)
            }
        } catch (_: Exception) {
            Triple(0, 0, 0)
        }
    }
}
