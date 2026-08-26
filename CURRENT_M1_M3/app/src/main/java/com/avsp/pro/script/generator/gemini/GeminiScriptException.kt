package com.avsp.pro.script.generator.gemini

import com.avsp.pro.core.error.AvspException
import com.avsp.pro.core.error.ErrorCode
import com.avsp.pro.core.error.ErrorInfo
import com.avsp.pro.core.error.ErrorSeverity

/**
 * Typed M2 Gemini failures — never silently swapped for mock success.
 */
class GeminiScriptException(
    message: String,
    code: ErrorCode = ErrorCode.MODULE_ERROR,
    details: String? = null,
    cause: Throwable? = null
) : AvspException(
    ErrorInfo(
        code = code,
        message = message,
        module = "M2",
        severity = ErrorSeverity.ERROR,
        details = details,
        recoverable = true
    ),
    cause
)
