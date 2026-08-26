package com.avsp.pro.capture.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.avsp.pro.capture.database.dao.MediaDao
import com.avsp.pro.capture.database.entity.MediaEntity

/**
 * Capture/vision Room store for M6/M7 media metadata.
 *
 * Intentionally separate from Pro [com.avsp.pro.database.AvspDatabase] so M1–M3
 * migrations stay non-destructive. Rows are keyed by Pro [projectId] — this is
 * not a second project-root system.
 */
@Database(
    entities = [MediaEntity::class],
    version = 1,
    exportSchema = true
)
abstract class CaptureDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        const val NAME = "avsp_capture_db"

        @Volatile
        private var INSTANCE: CaptureDatabase? = null

        fun get(context: Context): CaptureDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CaptureDatabase::class.java,
                    NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
