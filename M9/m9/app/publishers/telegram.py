"""
AVSP M9 — Telegram Bot API video publisher.
"""

from __future__ import annotations

from app.publishers.base import Publisher, PublisherConfig
from app.schemas.publishing import PublishResult, PublishingJob, PlatformPublishStatus
from app.m9.errors import (
    AuthError,
    ConfigurationError,
    NetworkError,
    UploadFailedError,
)


class TelegramPublisher(Publisher):
    platform = "telegram"
    is_mock = False

    def validate_config(self) -> None:
        if self.config.use_mock:
            return
        if not self.config.bot_token or not self.config.chat_id:
            raise ConfigurationError(
                "Telegram real mode requires AVSP_TELEGRAM_BOT_TOKEN and AVSP_TELEGRAM_CHAT_ID",
                platform=self.platform,
            )

    def publish(self, job: PublishingJob) -> PublishResult:
        try:
            self.validate_config()
        except ConfigurationError as e:
            return self._make_error_result(e)

        if self.config.use_mock:
            from app.publishers.mock import MockTelegramPublisher
            return MockTelegramPublisher(self.config).publish(job)

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
            raise ConfigurationError("requests required for Telegram real mode")

        token = self.config.bot_token
        chat_id = self.config.chat_id
        url = f"https://api.telegram.org/bot{token}/sendVideo"

        caption = job.title
        if job.description:
            caption = f"{job.title}\n\n{job.description}"
        if job.hashtags:
            caption += "\n" + " ".join(f"#{h.lstrip('#')}" for h in job.hashtags)
        caption = caption[:1024]

        with open(job.video_path, "rb") as f:
            files = {"video": f}
            data = {
                "chat_id": chat_id,
                "caption": caption,
                "supports_streaming": "true",
            }
            resp = requests.post(url, data=data, files=files, timeout=300)

        if resp.status_code == 401:
            raise AuthError("Telegram bot token invalid", platform=self.platform)
        if resp.status_code >= 400:
            raise UploadFailedError(
                f"Telegram sendVideo failed HTTP {resp.status_code}: {resp.text[:300]}",
                platform=self.platform,
            )

        result = resp.json()
        if not result.get("ok"):
            raise UploadFailedError(
                f"Telegram API error: {result.get('description', 'unknown')}",
                platform=self.platform,
            )

        msg = result.get("result", {})
        message_id = msg.get("message_id")
        file_id = None
        if "video" in msg:
            file_id = msg["video"].get("file_id")

        return PublishResult(
            success=True,
            platform=self.platform,
            platform_id=str(message_id) if message_id else str(file_id),
            status=PlatformPublishStatus.PUBLISHED.value,
            message="REAL Telegram publish succeeded",
            is_mock=False,
            raw={"message_id": message_id, "file_id": file_id},
        )
