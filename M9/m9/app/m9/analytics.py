"""
AVSP M9 — Basic publishing analytics (local counters).
Does NOT invent platform views/likes. Only tracks job outcomes.
"""

from __future__ import annotations

import os
import json
from pathlib import Path
from typing import Any, Dict
from datetime import datetime, timezone
from threading import Lock


DEFAULT_ANALYTICS_PATH = os.environ.get(
    "AVSP_M9_ANALYTICS_PATH",
    str(Path(__file__).resolve().parents[2] / "data" / "m9_analytics.json"),
)


class PublishingAnalytics:
    """Thread-safe local analytics store."""

    def __init__(self, path: str | None = None):
        self.path = path or DEFAULT_ANALYTICS_PATH
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        self._data = self._load()

    def _default(self) -> Dict[str, Any]:
        return {
            "jobs_created": 0,
            "jobs_published": 0,
            "jobs_failed": 0,
            "jobs_retried": 0,
            "jobs_cancelled": 0,
            "jobs_partial": 0,
            "platform_success": {
                "youtube": 0,
                "facebook": 0,
                "instagram": 0,
                "telegram": 0,
                "web": 0,
            },
            "platform_failure": {
                "youtube": 0,
                "facebook": 0,
                "instagram": 0,
                "telegram": 0,
                "web": 0,
            },
            "total_upload_time_sec": 0.0,
            "upload_count": 0,
            "last_updated": None,
        }

    def _load(self) -> Dict[str, Any]:
        if os.path.exists(self.path):
            try:
                with open(self.path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                # merge missing keys
                base = self._default()
                for k, v in base.items():
                    if k not in data:
                        data[k] = v
                return data
            except Exception:
                return self._default()
        return self._default()

    def _save(self) -> None:
        self._data["last_updated"] = datetime.now(timezone.utc).isoformat()
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self._data, f, indent=2)

    def record_created(self) -> None:
        with self._lock:
            self._data["jobs_created"] += 1
            self._save()

    def record_published(self) -> None:
        with self._lock:
            self._data["jobs_published"] += 1
            self._save()

    def record_failed(self) -> None:
        with self._lock:
            self._data["jobs_failed"] += 1
            self._save()

    def record_retried(self) -> None:
        with self._lock:
            self._data["jobs_retried"] += 1
            self._save()

    def record_cancelled(self) -> None:
        with self._lock:
            self._data["jobs_cancelled"] += 1
            self._save()

    def record_partial(self) -> None:
        with self._lock:
            self._data["jobs_partial"] += 1
            self._save()

    def record_platform_success(self, platform: str) -> None:
        with self._lock:
            if platform in self._data["platform_success"]:
                self._data["platform_success"][platform] += 1
            self._save()

    def record_platform_failure(self, platform: str) -> None:
        with self._lock:
            if platform in self._data["platform_failure"]:
                self._data["platform_failure"][platform] += 1
            self._save()

    def record_upload_time(self, seconds: float) -> None:
        with self._lock:
            self._data["total_upload_time_sec"] += seconds
            self._data["upload_count"] += 1
            self._save()

    def snapshot(self) -> Dict[str, Any]:
        with self._lock:
            data = dict(self._data)
            if data["upload_count"] > 0:
                data["average_upload_time_sec"] = (
                    data["total_upload_time_sec"] / data["upload_count"]
                )
            else:
                data["average_upload_time_sec"] = 0.0
            return data

    def reset(self) -> None:
        with self._lock:
            self._data = self._default()
            self._save()
