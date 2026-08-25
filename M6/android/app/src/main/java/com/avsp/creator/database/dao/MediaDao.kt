package com.avsp.creator.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avsp.creator.database.entity.MediaEntity
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

    @Delete
    suspend fun deleteMedia(media: MediaEntity)

    @Query("DELETE FROM project_media WHERE id = :id")
    suspend fun deleteMediaById(id: String)
}
