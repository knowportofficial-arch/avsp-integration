package com.avsp.pro.m7.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.avsp.pro.m7.database.dao.MediaDao
import com.avsp.pro.m7.database.dao.ProjectDao
import com.avsp.pro.m7.database.entity.MediaEntity
import com.avsp.pro.m7.database.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        MediaEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AvspDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: AvspDatabase? = null

        fun getDatabase(context: Context): AvspDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AvspDatabase::class.java,
                    "avsp_m67_db"
                )
                // M6 → M7 schema expansion; destructive is acceptable for pre-release modules.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
