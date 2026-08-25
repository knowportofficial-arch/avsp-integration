"""
AVSP M9 — Facebook (Meta) video publisher.
Uses Graph API. Default mock-safe.
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


class FacebookPublisher(Publisher):
    platform = "facebook"
    is_mock = False

    def validate_config(self) -> None:
        if self.config.use_mock:
            return
        if not self.config.access_token or not self.config.page_id:
            raise ConfigurationError(
                "Facebook real mode requires AVSP_FACEBOOK_ACCESS_TOKEN and AVSP_FACEBOOK_PAGE_ID",
                platform=self.platform,
            )

    def publish(self, job: PublishingJob) -> PublishResult:
        try:
            self.validate_config()
        except ConfigurationError as e:
            return self._make_error_result(e)

        if self.config.use_mock:
            from app.publishers.mock import MockFacebookPublisher
            return MockFacebookPublisher(self.config).publish(job)

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
            raise ConfigurationError("requests library required for Facebook real mode")

        page_id = self.config.page_id
        token = self.config.access_token
        url = f"https://graph.facebook.com/v19.0/{page_id}/videos"

        data = {
            "access_token": token,
            "title": job.title[:255],
            "description": job.description or "",
            "published": "true" if not job.scheduled_time else "false",
        }
        if job.scheduled_time:
            # Graph expects unix timestamp for scheduled_publish_time
            from datetime import datetime
            try:
                dt = datetime.fromisoformat(job.scheduled_time.replace("Z", "+00:00"))
                data["scheduled_publish_time"] = int(dt.timestamp())
            except Exception:
                pass

        with open(job.video_path, "rb") as f:
            files = {"source": f}
            resp = requests.post(url, data=data, files=files, timeout=300)

        if resp.status_code in (401, 403):
            raise AuthError(f"Facebook auth failed: {resp.text[:200]}", platform=self.platform)

        if resp.status_code >= 400:
            raise UploadFailedError(
                f"Facebook upload failed HTTP {resp.status_code}: {resp.text[:300]}",
                platform=self.platform,
            )

        result = resp.json()
        video_id = result.get("id")
        if not video_id:
            raise UploadFailedError("Facebook returned no video id", platform=self.platform)

        return PublishResult(
            success=True,
            platform=self.platform,
            platform_id=str(video_id),
            status=PlatformPublishStatus.PUBLISHED.value
            if not job.scheduled_time
            else PlatformPublishStatus.SCHEDULED.value,
            message="REAL Facebook upload succeeded",
            is_mock=False,
            raw=result,
        )
