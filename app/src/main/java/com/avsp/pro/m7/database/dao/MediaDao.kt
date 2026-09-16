package com.avsp.pro.m7.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.avsp.pro.m7.database.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM project_media WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getMediaForProjectFlow(projectId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM project_media WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getMediaForProject(projectId: String): List<MediaEntity>

    @Query("SELECT * FROM project_media WHERE id = :id")
    suspend fun getMediaById(id: String): MediaEntity?

    @Query("SELECT COUNT(*) FROM project_media WHERE projectId = :projectId")
    fun getMediaCountForProject(projectId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Update
    suspend fun updateMedia(media: MediaEntity)

    @Delete
    suspend fun deleteMedia(media: MediaEntity)

    @Query("DELETE FROM project_media WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    // ---- M7 query surface ----

    @Query(
        """
        SELECT * FROM project_media
        WHERE projectId = :projectId
          AND (:category IS NULL OR category = :category)
          AND (:mediaType IS NULL OR mediaType = :mediaType)
          AND (:minQuality IS NULL OR qualityScoreNormalized >= :minQuality)
          AND (:onlyBest = 0 OR isBestShot = 1)
        ORDER BY createdAt DESC
        """
    )
    suspend fun queryFiltered(
        projectId: String,
        category: String? = null,
        mediaType: String? = null,
        minQuality: Double? = null,
        onlyBest: Int = 0
    ): List<MediaEntity>

    @Query(
        """
        SELECT * FROM project_media
        WHERE projectId = :projectId
          AND (
            displayName LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR tagsCsv LIKE '%' || :query || '%'
            OR shotType LIKE '%' || :query || '%'
          )
        ORDER BY createdAt DESC
        """
    )
    suspend fun search(projectId: String, query: String): List<MediaEntity>

    @Query(
        """
        SELECT * FROM project_media
        WHERE projectId = :projectId
          AND missionShotId = :missionShotId
        ORDER BY qualityScoreNormalized DESC, createdAt DESC
        """
    )
    suspend fun getByMissionShot(projectId: String, missionShotId: String): List<MediaEntity>

    @Query(
        """
        UPDATE project_media SET isBestShot = 0
        WHERE projectId = :projectId AND missionShotId = :missionShotId
        """
    )
    suspend fun clearBestShotFlags(projectId: String, missionShotId: String)

    @Query(
        """
        UPDATE project_media SET isBestShot = 1 WHERE id = :id
        """
    )
    suspend fun markBestShot(id: String)

    @Query("SELECT * FROM project_media WHERE projectId = :projectId AND isBestShot = 1")
    suspend fun getBestShots(projectId: String): List<MediaEntity>
}
