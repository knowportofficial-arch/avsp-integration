"""
AVSP M9 — Mock publishers for offline / credential-free testing.
All mock results are explicitly labelled is_mock=True.
"""

from __future__ import annotations

import time
import uuid
from typing import Optional

from app.publishers.base import Publisher, PublisherConfig
from app.schemas.publishing import PublishResult, PublishingJob, PlatformPublishStatus
from app.m9.errors import (
    AuthError,
    NetworkError,
    RateLimitError,
    UploadFailedError,
    InvalidMetadataError,
    PermissionDeniedError,
)


class _BaseMockPublisher(Publisher):
    is_mock = True
    # class-level controls for tests
    force_fail: bool = False
    force_error_code: Optional[str] = None
    force_retryable: bool = False
    simulate_delay: float = 0.01

    def publish(self, job: PublishingJob) -> PublishResult:
        time.sleep(self.simulate_delay)

        if self.force_fail or self.force_error_code:
            code = self.force_error_code or "UPLOAD_FAILED"
            retryable = self.force_retryable
            msg = f"Mock forced failure: {code}"
            return PublishResult(
                success=False,
                platform=self.platform,
                status=PlatformPublishStatus.FAILED.value,
                message=msg,
                error_code=code,
                retryable=retryable,
                is_mock=True,
                raw={"mock": True, "forced": True},
            )

        # Simulate success
        platform_id = f"mock_{self.platform}_{uuid.uuid4().hex[:12]}"
        return PublishResult(
            success=True,
            platform=self.platform,
            platform_id=platform_id,
            status=PlatformPublishStatus.PUBLISHED.value,
            message=f"MOCKED publish to {self.platform} succeeded",
            is_mock=True,
            raw={
                "mock": True,
                "title": job.title,
                "video_path": job.video_path,
                "privacy": job.privacy,
            },
        )


class MockYouTubePublisher(_BaseMockPublisher):
    platform = "youtube"


class MockFacebookPublisher(_BaseMockPublisher):
    platform = "facebook"


class MockInstagramPublisher(_BaseMockPublisher):
    platform = "instagram"


class MockTelegramPublisher(_BaseMockPublisher):
    platform = "telegram"


class MockWebPublisher(_BaseMockPublisher):
    platform = "web"


def get_mock_publisher(platform: str, config: Optional[PublisherConfig] = None) -> Publisher:
    mapping = {
        "youtube": MockYouTubePublisher,
        "facebook": MockFacebookPublisher,
        "instagram": MockInstagramPublisher,
        "telegram": MockTelegramPublisher,
        "web": MockWebPublisher,
    }
    cls = mapping.get(platform)
    if not cls:
        raise ValueError(f"No mock publisher for platform: {platform}")
    return cls(config or PublisherConfig(platform=platform, use_mock=True))


def get_publisher(platform: str, force_mock: bool = False) -> Publisher:
    """
    Factory: returns real publisher if credentials present and force_mock=False,
    otherwise mock.
    """
    from app.publishers.youtube import YouTubePublisher
    from app.publishers.facebook import FacebookPublisher
    from app.publishers.instagram import InstagramPublisher
    from app.publishers.telegram import TelegramPublisher
    from app.publishers.web import WebPublisher

    real_map = {
        "youtube": YouTubePublisher,
        "facebook": FacebookPublisher,
        "instagram": InstagramPublisher,
        "telegram": TelegramPublisher,
        "web": WebPublisher,
    }
    cfg = PublisherConfig.from_env(platform)
    if force_mock or cfg.use_mock:
        return get_mock_publisher(platform, cfg)
    cls = real_map.get(platform)
    if not cls:
        raise ValueError(f"Unknown platform: {platform}")
    return cls(cfg)
