package com.avsp.pro.logs

/**
 * Centralized logging levels for all AVSP modules.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR;

    companion object {
        fun fromRaw(raw: String): LogLevel =
            entries.find { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: INFO
    }
}

data class LogEntry(
    val id: Long = 0L,
    val timestamp: Long,
    val level: LogLevel,
    val module: String,
    val message: String,
    val details: String? = null,
    val projectId: String? = null
)

/**
 * Logging interface every module should use. Secrets must never be logged.
 */
interface AvspLogger {
    fun debug(module: String, message: String, details: String? = null, projectId: String? = null)
    fun info(module: String, message: String, details: String? = null, projectId: String? = null)
    fun warning(module: String, message: String, details: String? = null, projectId: String? = null)
    fun error(module: String, message: String, details: String? = null, projectId: String? = null)
    fun log(level: LogLevel, module: String, message: String, details: String? = null, projectId: String? = null)
    suspend fun recent(limit: Int = 100): List<LogEntry>
    suspend fun clear()
}

/**
 * Redacts common secret patterns before logging.
 */
object SecretRedactor {
    private val patterns = listOf(
        Regex("""(?i)(api[_-]?key|token|password|secret|authorization)\s*[:=]\s*\S+"""),
        Regex("""(?i)bearer\s+[a-z0-9\-._~+/]+=*"""),
        Regex("""(?i)AIza[0-9A-Za-z\-_]{20,}"""),
        Regex("""(?i)sk-[A-Za-z0-9]{10,}""")
    )

    fun redact(input: String?): String? {
        if (input.isNullOrBlank()) return input
        var result = input
        patterns.forEach { regex ->
            result = result!!.replace(regex) { match ->
                val prefix = match.value.substringBefore(":")
                    .substringBefore("=")
                    .trim()
                if (prefix.equals(match.value, ignoreCase = true)) {
                    "[REDACTED]"
                } else {
                    "$prefix=[REDACTED]"
                }
            }
        }
        return result
    }
}
