"""
AVSP M9 — Publisher abstract base.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, Optional
import os

from app.schemas.publishing import PublishResult, PublishingJob, PlatformPublishStatus
from app.m9.errors import M9Error, ConfigurationError


@dataclass
class PublisherConfig:
    """Configuration for a platform publisher. Secrets from env only."""
    platform: str
    enabled: bool = True
    use_mock: bool = True  # default safe: mock unless explicitly real
    # common
    client_id: Optional[str] = None
    client_secret: Optional[str] = None
    access_token: Optional[str] = None
    refresh_token: Optional[str] = None
    # platform specific
    channel_id: Optional[str] = None          # YouTube
    page_id: Optional[str] = None             # Facebook
    ig_user_id: Optional[str] = None          # Instagram
    bot_token: Optional[str] = None           # Telegram
    chat_id: Optional[str] = None             # Telegram
    api_endpoint: Optional[str] = None        # Web
    api_key: Optional[str] = None             # Web
    extra: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_env(cls, platform: str) -> "PublisherConfig":
        """Load config from environment variables. Never hard-code secrets."""
        p = platform.upper()
        use_mock = os.getenv(f"AVSP_{p}_USE_MOCK", "true").lower() in ("1", "true", "yes")
        return cls(
            platform=platform,
            enabled=os.getenv(f"AVSP_{p}_ENABLED", "true").lower() in ("1", "true", "yes"),
            use_mock=use_mock,
            client_id=os.getenv(f"AVSP_{p}_CLIENT_ID"),
            client_secret=os.getenv(f"AVSP_{p}_CLIENT_SECRET"),
            access_token=os.getenv(f"AVSP_{p}_ACCESS_TOKEN"),
            refresh_token=os.getenv(f"AVSP_{p}_REFRESH_TOKEN"),
            channel_id=os.getenv(f"AVSP_{p}_CHANNEL_ID"),
            page_id=os.getenv(f"AVSP_{p}_PAGE_ID"),
            ig_user_id=os.getenv(f"AVSP_{p}_IG_USER_ID"),
            bot_token=os.getenv(f"AVSP_{p}_BOT_TOKEN") or os.getenv("AVSP_TELEGRAM_BOT_TOKEN"),
            chat_id=os.getenv(f"AVSP_{p}_CHAT_ID") or os.getenv("AVSP_TELEGRAM_CHAT_ID"),
            api_endpoint=os.getenv(f"AVSP_{p}_API_ENDPOINT") or os.getenv("AVSP_WEB_API_ENDPOINT"),
            api_key=os.getenv(f"AVSP_{p}_API_KEY") or os.getenv("AVSP_WEB_API_KEY"),
        )


class Publisher(ABC):
    """Common interface for all platform publishers."""

    platform: str = "base"

    def __init__(self, config: Optional[PublisherConfig] = None):
        self.config = config or PublisherConfig(platform=self.platform)
        self.is_mock = getattr(self, "is_mock", False)

    @abstractmethod
    def publish(self, job: PublishingJob) -> PublishResult:
        """Publish the job's video to this platform. Must not raise unhandled exceptions."""
        ...

    def validate_config(self) -> None:
        """Raise ConfigurationError if required credentials missing (for real mode)."""
        if self.is_mock or self.config.use_mock:
            return
        # subclasses override

    def _meta(self, job: PublishingJob) -> Dict[str, Any]:
        return job.platform_metadata.get(self.platform, {})

    def _make_error_result(
        self,
        error: M9Error,
        status: str = PlatformPublishStatus.FAILED.value,
    ) -> PublishResult:
        return PublishResult(
            success=False,
            platform=self.platform,
            status=status,
            message=error.message,
            error_code=error.error_code,
            retryable=error.retryable,
            is_mock=self.is_mock,
            raw=error.to_dict(),
        )
