package com.avsp.pro.repository

import com.avsp.pro.database.EntityMappers
import com.avsp.pro.database.dao.LogDao
import com.avsp.pro.logs.LogEntry
import com.avsp.pro.logs.LogLevel
import com.avsp.pro.logs.SecretRedactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LogRepositoryImpl(
    private val logDao: LogDao
) : LogRepository {

    override suspend fun append(
        level: LogLevel,
        module: String,
        message: String,
        details: String?,
        projectId: String?
    ) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            module = module,
            message = SecretRedactor.redact(message) ?: message,
            details = SecretRedactor.redact(details),
            projectId = projectId
        )
        logDao.insert(EntityMappers.toEntity(entry))
    }

    override suspend fun recent(limit: Int): List<LogEntry> =
        logDao.recent(limit).map(EntityMappers::toDomain)

    override fun observeRecent(limit: Int): Flow<List<LogEntry>> =
        logDao.observeRecent(limit).map { list -> list.map(EntityMappers::toDomain) }

    override suspend fun clear() {
        logDao.clear()
    }
}
