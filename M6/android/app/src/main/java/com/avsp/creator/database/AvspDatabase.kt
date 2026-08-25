package com.avsp.creator.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.avsp.creator.database.dao.MediaDao
import com.avsp.creator.database.dao.ProjectDao
import com.avsp.creator.database.entity.MediaEntity
import com.avsp.creator.database.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        MediaEntity::class
    ],
    version = 2,
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
                    "avsp_creator_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
