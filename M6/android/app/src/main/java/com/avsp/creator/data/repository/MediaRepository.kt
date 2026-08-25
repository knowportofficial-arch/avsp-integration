package com.avsp.creator.data.repository

import android.content.Context
import android.net.Uri
import com.avsp.creator.database.dao.MediaDao
import com.avsp.creator.database.entity.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context, private val mediaDao: MediaDao) {

    fun getMediaForProjectFlow(projectId: String): Flow<List<MediaEntity>> {
        return mediaDao.getMediaForProjectFlow(projectId)
    }

    suspend fun getMediaForProject(projectId: String): List<MediaEntity> = withContext(Dispatchers.IO) {
        mediaDao.getMediaForProject(projectId)
    }

    suspend fun saveCapturedMedia(
        projectId: String,
        uriString: String,
        mediaType: String,
        displayName: String,
        shotType: String = "WIDE",
        missionId: String? = null,
        missionShotId: String? = null,
        durationSeconds: Long = 0L,
        qualityScore: Int = 85,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracyMeters: Float? = null
    ): MediaEntity = withContext(Dispatchers.IO) {
        val entity = MediaEntity(
            projectId = projectId,
            uriString = uriString,
            mediaType = mediaType,
            displayName = displayName,
            shotType = shotType,
            missionId = missionId,
            missionShotId = missionShotId,
            durationSeconds = durationSeconds,
            qualityScore = qualityScore,
            latitude = latitude,
            longitude = longitude,
            locationAccuracyMeters = locationAccuracyMeters
        )
        mediaDao.insertMedia(entity)
        entity
    }

    suspend fun deleteMedia(id: String) = withContext(Dispatchers.IO) {
        val media = mediaDao.getMediaById(id)
        if (media != null) {
            try {
                context.contentResolver.delete(Uri.parse(media.uriString), null, null)
            } catch (_: Exception) {
                // Keep database cleanup resilient even if the MediaStore item was already removed.
            }
        }
        mediaDao.deleteMediaById(id)
    }
}
