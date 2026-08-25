"""
AVSP M9 structured errors.
"""

from __future__ import annotations
from typing import Any, Dict, Optional
from datetime import datetime, timezone


class M9Error(Exception):
    """Base M9 error with structured payload."""

    def __init__(
        self,
        message: str,
        error_code: str = "UNKNOWN_ERROR",
        platform: Optional[str] = None,
        job_id: Optional[str] = None,
        retryable: bool = False,
        attempt: int = 0,
        details: Optional[Dict[str, Any]] = None,
    ):
        super().__init__(message)
        self.message = message
        self.error_code = error_code
        self.platform = platform
        self.job_id = job_id
        self.retryable = retryable
        self.attempt = attempt
        self.details = details or {}
        self.timestamp = datetime.now(timezone.utc).isoformat()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "error_code": self.error_code,
            "message": self.message,
            "platform": self.platform,
            "job_id": self.job_id,
            "retryable": self.retryable,
            "attempt": self.attempt,
            "timestamp": self.timestamp,
            "details": self.details,
        }


class AuthError(M9Error):
    def __init__(self, message: str = "Authentication failed", **kwargs):
        super().__init__(message, error_code="AUTH_ERROR", retryable=False, **kwargs)


class MediaNotFoundError(M9Error):
    def __init__(self, message: str = "Media file not found", **kwargs):
        super().__init__(message, error_code="MEDIA_NOT_FOUND", retryable=False, **kwargs)


class InvalidMetadataError(M9Error):
    def __init__(self, message: str = "Invalid metadata", **kwargs):
        super().__init__(message, error_code="INVALID_METADATA", retryable=False, **kwargs)


class RateLimitError(M9Error):
    def __init__(self, message: str = "Rate limit exceeded", **kwargs):
        super().__init__(message, error_code="RATE_LIMIT", retryable=True, **kwargs)


class NetworkError(M9Error):
    def __init__(self, message: str = "Network error", **kwargs):
        super().__init__(message, error_code="NETWORK_ERROR", retryable=True, **kwargs)


class UploadFailedError(M9Error):
    def __init__(self, message: str = "Upload failed", **kwargs):
        super().__init__(message, error_code="UPLOAD_FAILED", retryable=True, **kwargs)


class ProcessingFailedError(M9Error):
    def __init__(self, message: str = "Processing failed", **kwargs):
        super().__init__(message, error_code="PROCESSING_FAILED", retryable=True, **kwargs)


class PermissionDeniedError(M9Error):
    def __init__(self, message: str = "Permission denied", **kwargs):
        super().__init__(message, error_code="PERMISSION_DENIED", retryable=False, **kwargs)


class InvalidMediaError(M9Error):
    def __init__(self, message: str = "Invalid or unsupported media", **kwargs):
        super().__init__(message, error_code="INVALID_MEDIA", retryable=False, **kwargs)


class StateTransitionError(M9Error):
    def __init__(self, message: str = "Invalid state transition", **kwargs):
        super().__init__(message, error_code="INVALID_STATE", retryable=False, **kwargs)


class ConfigurationError(M9Error):
    def __init__(self, message: str = "Missing or invalid configuration", **kwargs):
        super().__init__(message, error_code="CONFIG_ERROR", retryable=False, **kwargs)
