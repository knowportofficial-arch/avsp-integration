"""
AVSP M9 — Instagram (Meta) Reels / video publisher.
Uses Graph API container → publish flow. Default mock-safe.
"""

from __future__ import annotations

import time
from typing import Optional

from app.publishers.base import Publisher, PublisherConfig
from app.schemas.publishing import PublishResult, PublishingJob, PlatformPublishStatus
from app.m9.errors import (
    AuthError,
    ConfigurationError,
    UploadFailedError,
    ProcessingFailedError,
)


class InstagramPublisher(Publisher):
    platform = "instagram"
    is_mock = False

    def validate_config(self) -> None:
        if self.config.use_mock:
            return
        if not self.config.access_token or not self.config.ig_user_id:
            raise ConfigurationError(
                "Instagram real mode requires AVSP_INSTAGRAM_ACCESS_TOKEN and AVSP_INSTAGRAM_IG_USER_ID",
                platform=self.platform,
            )

    def publish(self, job: PublishingJob) -> PublishResult:
        try:
            self.validate_config()
        except ConfigurationError as e:
            return self._make_error_result(e)

        if self.config.use_mock:
            from app.publishers.mock import MockInstagramPublisher
            return MockInstagramPublisher(self.config).publish(job)

        try:
            return self._real_upload(job)
        except Exception as e:
            return self._make_error_result(
                UploadFailedError(str(e), platform=self.platform, job_id=job.job_id)
            )

    def _real_upload(self, job: PublishingJob) -> PublishResult:
        """
        Instagram Content Publishing API (Reels):
        1. Create media container (video_url or resumable)
        2. Poll status
        3. Publish container
        Note: For local files, a publicly reachable URL or resumable upload is required.
        This implementation expects a public video_url in platform_metadata or fails clearly.
        """
        try:
            import requests
        except ImportError:
            raise ConfigurationError("requests required for Instagram real mode")

        ig_user_id = self.config.ig_user_id
        token = self.config.access_token
        meta = self._meta(job)
        video_url = meta.get("video_url")  # must be publicly accessible for container

        if not video_url:
            # Local file path alone is not sufficient for standard Graph API without resumable session.
            raise ConfigurationError(
                "Instagram real mode requires platform_metadata['instagram']['video_url'] "
                "(publicly accessible URL). Local path-only upload needs resumable upload session.",
                platform=self.platform,
            )

        caption = job.description or job.title
        if job.hashtags:
            caption = caption + " " + " ".join(f"#{h.lstrip('#')}" for h in job.hashtags)

        # Step 1: create container
        create_url = f"https://graph.facebook.com/v19.0/{ig_user_id}/media"
        payload = {
            "media_type": "REELS",
            "video_url": video_url,
            "caption": caption[:2200],
            "access_token": token,
        }
        resp = requests.post(create_url, data=payload, timeout=60)
        if resp.status_code in (401, 403):
            raise AuthError(f"Instagram auth failed: {resp.text[:200]}", platform=self.platform)
        if resp.status_code >= 400:
            raise UploadFailedError(
                f"Instagram container create failed: {resp.text[:300]}",
                platform=self.platform,
            )
        container_id = resp.json().get("id")
        if not container_id:
            raise UploadFailedError("No container id from Instagram", platform=self.platform)

        # Step 2: poll status (simplified)
        status_url = f"https://graph.facebook.com/v19.0/{container_id}"
        for _ in range(30):
            sresp = requests.get(status_url, params={"fields": "status_code", "access_token": token}, timeout=30)
            code = sresp.json().get("status_code")
            if code == "FINISHED":
                break
            if code in ("ERROR", "EXPIRED"):
                raise ProcessingFailedError(f"Instagram container status={code}", platform=self.platform)
            time.sleep(2)
        else:
            raise ProcessingFailedError("Instagram container processing timeout", platform=self.platform)

        # Step 3: publish
        publish_url = f"https://graph.facebook.com/v19.0/{ig_user_id}/media_publish"
        presp = requests.post(
            publish_url,
            data={"creation_id": container_id, "access_token": token},
            timeout=60,
        )
        if presp.status_code >= 400:
            raise UploadFailedError(f"Instagram publish failed: {presp.text[:300]}", platform=self.platform)
        media_id = presp.json().get("id")
        if not media_id:
            raise UploadFailedError("Instagram returned no media id", platform=self.platform)

        return PublishResult(
            success=True,
            platform=self.platform,
            platform_id=str(media_id),
            status=PlatformPublishStatus.PUBLISHED.value,
            message="REAL Instagram publish succeeded",
            is_mock=False,
            raw=presp.json(),
        )
