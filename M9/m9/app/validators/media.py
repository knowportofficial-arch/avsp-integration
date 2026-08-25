"""
AVSP M9 — Media validation (video + thumbnail).
Uses ffprobe when available; falls back to basic file checks.
"""

from __future__ import annotations

import os
import json
import subprocess
import shutil
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


SUPPORTED_VIDEO_EXTS = {".mp4", ".mov", ".mkv", ".webm", ".avi"}
SUPPORTED_IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}


@dataclass
class ValidationResult:
    ok: bool
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    info: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ok": self.ok,
            "errors": self.errors,
            "warnings": self.warnings,
            "info": self.info,
        }


class MediaValidator:
    """Validate video and thumbnail files before publishing."""

    def __init__(self, require_ffprobe: bool = False):
        self.ffprobe = shutil.which("ffprobe")
        self.require_ffprobe = require_ffprobe

    def validate_video(self, path: str) -> ValidationResult:
        errors: List[str] = []
        warnings: List[str] = []
        info: Dict[str, Any] = {}

        if not path:
            errors.append("video_path is empty")
            return ValidationResult(ok=False, errors=errors)

        if not os.path.exists(path):
            errors.append(f"video file does not exist: {path}")
            return ValidationResult(ok=False, errors=errors)

        if not os.path.isfile(path):
            errors.append(f"video_path is not a file: {path}")
            return ValidationResult(ok=False, errors=errors)

        if not os.access(path, os.R_OK):
            errors.append(f"video file is not readable: {path}")
            return ValidationResult(ok=False, errors=errors)

        size = os.path.getsize(path)
        info["size_bytes"] = size
        if size == 0:
            errors.append("video file is empty (0 bytes)")
            return ValidationResult(ok=False, errors=errors, info=info)

        ext = os.path.splitext(path)[1].lower()
        info["extension"] = ext
        if ext not in SUPPORTED_VIDEO_EXTS:
            errors.append(
                f"unsupported video extension '{ext}'. Supported: {sorted(SUPPORTED_VIDEO_EXTS)}"
            )

        # ffprobe for stream validation
        if self.ffprobe:
            probe = self._ffprobe(path)
            if probe is None:
                warnings.append("ffprobe failed; basic checks only")
            else:
                info["probe"] = probe
                streams = probe.get("streams", [])
                has_video = any(s.get("codec_type") == "video" for s in streams)
                has_audio = any(s.get("codec_type") == "audio" for s in streams)
                info["has_video_stream"] = has_video
                info["has_audio_stream"] = has_audio
                if not has_video:
                    errors.append("no video stream found in file")
                # duration
                fmt = probe.get("format", {})
                duration = float(fmt.get("duration") or 0)
                info["duration_sec"] = duration
                if duration <= 0:
                    warnings.append("could not determine positive duration")
                # codecs
                for s in streams:
                    if s.get("codec_type") == "video":
                        info["video_codec"] = s.get("codec_name")
                        info["width"] = s.get("width")
                        info["height"] = s.get("height")
                    if s.get("codec_type") == "audio":
                        info["audio_codec"] = s.get("codec_name")
        else:
            if self.require_ffprobe:
                errors.append("ffprobe not found and require_ffprobe=True")
            else:
                warnings.append("ffprobe not available; skipped stream validation")

        return ValidationResult(ok=len(errors) == 0, errors=errors, warnings=warnings, info=info)

    def validate_thumbnail(self, path: Optional[str]) -> ValidationResult:
        if not path:
            return ValidationResult(ok=True, warnings=["no thumbnail provided"])

        errors: List[str] = []
        warnings: List[str] = []
        info: Dict[str, Any] = {}

        if not os.path.exists(path):
            errors.append(f"thumbnail does not exist: {path}")
            return ValidationResult(ok=False, errors=errors)

        if not os.path.isfile(path):
            errors.append(f"thumbnail is not a file: {path}")
            return ValidationResult(ok=False, errors=errors)

        if not os.access(path, os.R_OK):
            errors.append(f"thumbnail not readable: {path}")
            return ValidationResult(ok=False, errors=errors)

        size = os.path.getsize(path)
        info["size_bytes"] = size
        if size == 0:
            errors.append("thumbnail is empty")

        ext = os.path.splitext(path)[1].lower()
        info["extension"] = ext
        if ext not in SUPPORTED_IMAGE_EXTS:
            errors.append(
                f"unsupported thumbnail extension '{ext}'. Supported: {sorted(SUPPORTED_IMAGE_EXTS)}"
            )

        return ValidationResult(ok=len(errors) == 0, errors=errors, warnings=warnings, info=info)

    def _ffprobe(self, path: str) -> Optional[Dict[str, Any]]:
        try:
            cmd = [
                self.ffprobe,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                path,
            ]
            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=30
            )
            if result.returncode != 0:
                return None
            return json.loads(result.stdout)
        except Exception:
            return None
