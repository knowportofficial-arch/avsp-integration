"""Structured error types for M8 pipeline."""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Any, Dict, Optional


class ErrorCode(str, Enum):
    INPUT_INVALID = "INPUT_INVALID"
    AI_ERROR = "AI_ERROR"
    AI_INVALID_JSON = "AI_INVALID_JSON"
    MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND"
    MEDIA_DECODE_ERROR = "MEDIA_DECODE_ERROR"
    TIMELINE_ERROR = "TIMELINE_ERROR"
    RENDER_ERROR = "RENDER_ERROR"
    AUDIO_ERROR = "AUDIO_ERROR"
    CAPTION_ERROR = "CAPTION_ERROR"
    QC_FAILED = "QC_FAILED"
    EXTERNAL_SOURCE_ERROR = "EXTERNAL_SOURCE_ERROR"
    STAGE_FAILED = "STAGE_FAILED"
    M4_ADAPTER_ERROR = "M4_ADAPTER_ERROR"
    M7_ADAPTER_ERROR = "M7_ADAPTER_ERROR"
    CONFIG_ERROR = "CONFIG_ERROR"


@dataclass
class M8Error(Exception):
    code: ErrorCode
    message: str
    stage: str = ""
    details: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "code": self.code.value if isinstance(self.code, ErrorCode) else str(self.code),
            "message": self.message,
            "stage": self.stage,
            "details": self.details,
        }

    def __str__(self) -> str:
        return f"[{self.code}] {self.stage}: {self.message}"
