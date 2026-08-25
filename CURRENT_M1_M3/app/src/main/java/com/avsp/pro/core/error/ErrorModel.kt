package com.avsp.pro.core.error

/**
 * Structured application error model. Errors must not be silently swallowed.
 */
enum class ErrorSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

enum class ErrorCode(val code: String) {
    INVALID_INPUT("INVALID_INPUT"),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND"),
    STORAGE_ERROR("STORAGE_ERROR"),
    DATABASE_ERROR("DATABASE_ERROR"),
    MODULE_ERROR("MODULE_ERROR"),
    CONFIG_ERROR("CONFIG_ERROR"),
    UNKNOWN_ERROR("UNKNOWN_ERROR"),
    PERMISSION_REQUIRED("PERMISSION_REQUIRED"),
    DEPENDENCY_MISSING("DEPENDENCY_MISSING"),
    INPUT_NOT_FOUND("INPUT_NOT_FOUND");

    companion object {
        fun fromRaw(raw: String): ErrorCode =
            entries.find { it.code.equals(raw, true) || it.name.equals(raw, true) }
                ?: UNKNOWN_ERROR
    }
}

data class ErrorInfo(
    val code: ErrorCode,
    val message: String,
    val module: String,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    val recoverable: Boolean = true
) {
    fun toLogLine(): String =
        "[${severity.name}] ${code.code} module=$module recoverable=$recoverable :: $message" +
            (details?.let { " | $it" } ?: "")
}

open class AvspException(
    val errorInfo: ErrorInfo,
    cause: Throwable? = null
) : Exception(errorInfo.message, cause)

class InvalidInputException(
    message: String,
    module: String = "M1",
    details: String? = null
) : AvspException(
    ErrorInfo(
        code = ErrorCode.INVALID_INPUT,
        message = message,
        module = module,
        severity = ErrorSeverity.WARNING,
        details = details,
        recoverable = true
    )
)

class ProjectNotFoundException(
    projectId: String,
    module: String = "M1"
) : AvspException(
    ErrorInfo(
        code = ErrorCode.PROJECT_NOT_FOUND,
        message = "Project not found: $projectId",
        module = module,
        severity = ErrorSeverity.ERROR,
        details = "projectId=$projectId",
        recoverable = false
    )
)

class StorageException(
    message: String,
    details: String? = null,
    cause: Throwable? = null
) : AvspException(
    ErrorInfo(
        code = ErrorCode.STORAGE_ERROR,
        message = message,
        module = "storage",
        severity = ErrorSeverity.ERROR,
        details = details,
        recoverable = true
    ),
    cause
)

class DatabaseException(
    message: String,
    details: String? = null,
    cause: Throwable? = null
) : AvspException(
    ErrorInfo(
        code = ErrorCode.DATABASE_ERROR,
        message = message,
        module = "database",
        severity = ErrorSeverity.ERROR,
        details = details,
        recoverable = true
    ),
    cause
)
