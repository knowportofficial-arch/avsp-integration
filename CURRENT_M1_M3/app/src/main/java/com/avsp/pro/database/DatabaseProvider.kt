package com.avsp.pro.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.database.entity.ModuleStatusEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {

    @Volatile
    private var instance: AvspDatabase? = null

    fun get(context: Context): AvspDatabase {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    fun build(
        context: Context,
        inMemory: Boolean = false,
        name: String = AvspDatabase.NAME
    ): AvspDatabase {
        val builder = if (inMemory) {
            Room.inMemoryDatabaseBuilder(context, AvspDatabase::class.java)
        } else {
            Room.databaseBuilder(context, AvspDatabase::class.java, name)
        }
        return builder
            .addMigrations(AvspDatabase.MIGRATION_1_2)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                }
            })
            .build()
            .also { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    seedModulesIfNeeded(database)
                }
            }
    }

    suspend fun seedModulesIfNeeded(database: AvspDatabase) {
        val dao = database.moduleStatusDao()
        val now = System.currentTimeMillis()
        // Always align with canonical AvspModules defaults (M1/M2 FROZEN, M3 READY, M4–M9 FROZEN).
        val normalized = AvspModules.ALL.map { def ->
            ModuleStatusEntity(
                moduleId = def.moduleId,
                displayName = def.displayName,
                version = def.version,
                status = def.defaultStatus.name,
                lastUpdated = now,
                error = null
            )
        }
        dao.upsertAll(normalized)
    }

    fun clearForTests() {
        instance = null
    }
}
