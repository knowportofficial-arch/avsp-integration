package com.avsp.pro.repository

import com.avsp.pro.core.module.AvspModules
import com.avsp.pro.core.module.ModuleRunStatus
import com.avsp.pro.core.module.ModuleStatus
import com.avsp.pro.database.EntityMappers
import com.avsp.pro.database.dao.ModuleStatusDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModuleStatusRepositoryImpl(
    private val dao: ModuleStatusDao
) : ModuleStatusRepository {

    override suspend fun getAll(): List<ModuleStatus> =
        dao.getAll().map(EntityMappers::toDomain)

    override suspend fun get(moduleId: String): ModuleStatus? =
        dao.getById(moduleId)?.let(EntityMappers::toDomain)

    override suspend fun registerOrUpdate(status: ModuleStatus) {
        val guarded = when {
            // Protect accepted frozen modules from casual status mutation.
            status.moduleId in AvspModules.FROZEN_MODULE_IDS &&
                status.status != ModuleRunStatus.FROZEN ->
                status.copy(status = ModuleRunStatus.FROZEN)

            // Do not invent SUCCESS for unimplemented modules at version 0.0.0.
            status.moduleId == AvspModules.M3_AUDIO_TTS &&
                status.status == ModuleRunStatus.SUCCESS &&
                status.version == "0.0.0" ->
                status.copy(status = ModuleRunStatus.NOT_STARTED)

            else -> status
        }
        dao.upsert(EntityMappers.toEntity(guarded.copy(lastUpdated = System.currentTimeMillis())))
    }

    override fun observeAll(): Flow<List<ModuleStatus>> =
        dao.observeAll().map { list -> list.map(EntityMappers::toDomain) }

    override suspend fun ensureDefaults() {
        val now = System.currentTimeMillis()
        AvspModules.ALL.forEach { def ->
            dao.upsert(
                EntityMappers.toEntity(
                    ModuleStatus(
                        moduleId = def.moduleId,
                        displayName = def.displayName,
                        version = def.version,
                        status = def.defaultStatus,
                        lastUpdated = now,
                        error = null
                    )
                )
            )
        }
    }
}
