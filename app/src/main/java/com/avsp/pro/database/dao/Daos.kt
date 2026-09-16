package com.avsp.pro.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.avsp.pro.database.entity.LogEntity
import com.avsp.pro.database.entity.MediaAssetEntity
import com.avsp.pro.database.entity.ModuleStatusEntity
import com.avsp.pro.database.entity.ProjectEntity
import com.avsp.pro.database.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    suspend fun delete(projectId: String)

    @Query("SELECT * FROM projects WHERE projectId = :projectId LIMIT 1")
    suspend fun getById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int
}

@Dao
interface ModuleStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: ModuleStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statuses: List<ModuleStatusEntity>)

    @Query("SELECT * FROM module_status WHERE moduleId = :moduleId LIMIT 1")
    suspend fun getById(moduleId: String): ModuleStatusEntity?

    @Query("SELECT * FROM module_status ORDER BY moduleId ASC")
    suspend fun getAll(): List<ModuleStatusEntity>

    @Query("SELECT * FROM module_status ORDER BY moduleId ASC")
    fun observeAll(): Flow<List<ModuleStatusEntity>>
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingsEntity)

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SettingsEntity?

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingsEntity>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntity): Long

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LogEntity>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntity>>

    @Query("DELETE FROM logs")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun count(): Int
}

@Dao
interface MediaAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: MediaAssetEntity)

    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun forProject(projectId: String): List<MediaAssetEntity>

    @Query("DELETE FROM media_assets WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Query("DELETE FROM media_assets WHERE assetId = :assetId")
    suspend fun delete(assetId: String)
}
