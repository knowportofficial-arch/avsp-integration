package com.avsp.pro.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.avsp.pro.database.dao.LogDao
import com.avsp.pro.database.dao.MediaAssetDao
import com.avsp.pro.database.dao.ModuleStatusDao
import com.avsp.pro.database.dao.ProjectDao
import com.avsp.pro.database.dao.SettingsDao
import com.avsp.pro.database.entity.LogEntity
import com.avsp.pro.database.entity.MediaAssetEntity
import com.avsp.pro.database.entity.ModuleStatusEntity
import com.avsp.pro.database.entity.ProjectEntity
import com.avsp.pro.database.entity.SettingsEntity

@Database(
    entities = [
        ProjectEntity::class,
        ModuleStatusEntity::class,
        SettingsEntity::class,
        LogEntity::class,
        MediaAssetEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AvspDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun moduleStatusDao(): ModuleStatusDao
    abstract fun settingsDao(): SettingsDao
    abstract fun logDao(): LogDao
    abstract fun mediaAssetDao(): MediaAssetDao

    companion object {
        const val NAME = "avsp_m1.db"

        /**
         * v1 → v2: add media_assets table for project asset inventory.
         * Non-destructive migration (normal strategy — not fallbackToDestructiveMigration).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_assets (
                        assetId TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        durationMs INTEGER,
                        width INTEGER,
                        height INTEGER,
                        createdAt INTEGER NOT NULL,
                        tagsJson TEXT NOT NULL,
                        metadataJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_assets_projectId ON media_assets(projectId)"
                )
            }
        }
    }
}
