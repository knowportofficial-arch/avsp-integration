"""
AVSP M9 — Generic web / API publisher.
Configurable endpoint + auth. Default mock-safe.
"""

from __future__ import annotations

import os
from typing import Optional

from app.publishers.base import Publisher, PublisherConfig
from app.schemas.publishing import PublishResult, PublishingJob, PlatformPublishStatus
from app.m9.errors import (
    AuthError,
    ConfigurationError,
    NetworkError,
    UploadFailedError,
)


class WebPublisher(Publisher):
    platform = "web"
    is_mock = False

    def validate_config(self) -> None:
        if self.config.use_mock:
            return
        if not self.config.api_endpoint:
            raise ConfigurationError(
                "Web real mode requires AVSP_WEB_API_ENDPOINT",
                platform=self.platform,
            )

    def publish(self, job: PublishingJob) -> PublishResult:
        try:
            self.validate_config()
        except ConfigurationError as e:
            return self._make_error_result(e)

        if self.config.use_mock:
            from app.publishers.mock import MockWebPublisher
            return MockWebPublisher(self.config).publish(job)

        try:
            return self._real_upload(job)
        except Exception as e:
            return self._make_error_result(
                UploadFailedError(str(e), platform=self.platform, job_id=job.job_id)
            )

    def _real_upload(self, job: PublishingJob) -> PublishResult:
        try:
            import requests
        except ImportError:
            raise ConfigurationError("requests required for Web real mode")

        endpoint = self.config.api_endpoint
        headers = {}
        if self.config.api_key:
            headers["Authorization"] = f"Bearer {self.config.api_key}"
        if self.config.access_token:
            headers["Authorization"] = f"Bearer {self.config.access_token}"

        payload = {
            "project_id": job.project_id,
            "title": job.title,
            "description": job.description,
            "tags": job.tags,
            "hashtags": job.hashtags,
            "language": job.language,
            "privacy": job.privacy,
            "scheduled_time": job.scheduled_time,
        }
        meta = self._meta(job)
        payload.update(meta)

        # Prefer multipart if endpoint expects file upload
        with open(job.video_path, "rb") as f:
            files = {"video": (os.path.basename(job.video_path), f, "video/mp4")}
            if job.thumbnail_path and os.path.exists(job.thumbnail_path):
                files["thumbnail"] = (
                    os.path.basename(job.thumbnail_path),
                    open(job.thumbnail_path, "rb"),
                    "image/jpeg",
                )
            try:
                resp = requests.post(
                    endpoint,
                    data={k: str(v) if not isinstance(v, (list, dict)) else str(v) for k, v in payload.items()},
                    files=files,
                    headers=headers,
                    timeout=300,
                )
            finally:
                # close thumbnail handle if opened
                if "thumbnail" in files and hasattr(files["thumbnail"][1], "close"):
                    try:
                        files["thumbnail"][1].close()
                    except Exception:
                        pass

        if resp.status_code in (401, 403):
            raise AuthError(f"Web endpoint auth failed: {resp.text[:200]}", platform=self.platform)
        if resp.status_code >= 400:
            raise UploadFailedError(
                f"Web publish failed HTTP {resp.status_code}: {resp.text[:300]}",
                platform=self.platform,
            )

        try:
            result = resp.json()
        except Exception:
            result = {"text": resp.text[:500]}

        remote_id = (
            result.get("id")
            or result.get("video_id")
            or result.get("remote_id")
            or result.get("uuid")
            or "web_ok"
        )

        return PublishResult(
            success=True,
            platform=self.platform,
            platform_id=str(remote_id),
            status=PlatformPublishStatus.PUBLISHED.value,
            message="REAL Web publish succeeded",
            is_mock=False,
            raw=result if isinstance(result, dict) else {"raw": str(result)},
        )
