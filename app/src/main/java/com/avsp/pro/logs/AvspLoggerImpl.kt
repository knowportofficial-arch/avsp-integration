package com.avsp.pro.logs

import android.util.Log
import com.avsp.pro.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * M1 logging implementation: Android Log + persisted LogRepository.
 */
class AvspLoggerImpl(
    private val logRepository: LogRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private var minLevel: LogLevel = LogLevel.DEBUG
) : AvspLogger {

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    override fun debug(module: String, message: String, details: String?, projectId: String?) =
        log(LogLevel.DEBUG, module, message, details, projectId)

    override fun info(module: String, message: String, details: String?, projectId: String?) =
        log(LogLevel.INFO, module, message, details, projectId)

    override fun warning(module: String, message: String, details: String?, projectId: String?) =
        log(LogLevel.WARNING, module, message, details, projectId)

    override fun error(module: String, message: String, details: String?, projectId: String?) =
        log(LogLevel.ERROR, module, message, details, projectId)

    override fun log(
        level: LogLevel,
        module: String,
        message: String,
        details: String?,
        projectId: String?
    ) {
        if (level.ordinal < minLevel.ordinal) return
        val safeMessage = SecretRedactor.redact(message) ?: message
        val safeDetails = SecretRedactor.redact(details)
        val tag = "AVSP/$module"
        val line = if (safeDetails != null) "$safeMessage | $safeDetails" else safeMessage
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, line)
            LogLevel.INFO -> Log.i(tag, line)
            LogLevel.WARNING -> Log.w(tag, line)
            LogLevel.ERROR -> Log.e(tag, line)
        }
        scope.launch {
            runCatching {
                logRepository.append(level, module, safeMessage, safeDetails, projectId)
            }
        }
    }

    override suspend fun recent(limit: Int): List<LogEntry> = logRepository.recent(limit)

    override suspend fun clear() = logRepository.clear()
}
