"""
AVSP M9 — YouTube publisher (official API abstraction).
Real mode uses Google YouTube Data API v3 when credentials are configured.
Default is mock-safe.
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
    InvalidMetadataError,
)


class YouTubePublisher(Publisher):
    platform = "youtube"
    is_mock = False

    def validate_config(self) -> None:
        if self.config.use_mock:
            return
        # Real YouTube requires OAuth client + token or service account.
        # We accept access_token or the presence of a credentials file path via extra.
        if not self.config.access_token and not self.config.client_id:
            raise ConfigurationError(
                "YouTube real mode requires AVSP_YOUTUBE_ACCESS_TOKEN or OAuth client credentials",
                platform=self.platform,
            )

    def publish(self, job: PublishingJob) -> PublishResult:
        try:
            self.validate_config()
        except ConfigurationError as e:
            return self._make_error_result(e)

        if self.config.use_mock:
            # Should not reach here if factory used correctly
            from app.publishers.mock import MockYouTubePublisher
            return MockYouTubePublisher(self.config).publish(job)

        # --- Real API path (skeleton; requires google-api-python-client + credentials) ---
        try:
            return self._real_upload(job)
        except AuthError as e:
            return self._make_error_result(e)
        except NetworkError as e:
            return self._make_error_result(e)
        except Exception as e:
            return self._make_error_result(
                UploadFailedError(str(e), platform=self.platform, job_id=job.job_id)
            )

    def _real_upload(self, job: PublishingJob) -> PublishResult:
        """
        Attempt real YouTube upload via Google API.
        This will raise if libraries/credentials are missing.
        """
        try:
            from googleapiclient.discovery import build
            from googleapiclient.http import MediaFileUpload
            from google.oauth2.credentials import Credentials
        except ImportError:
            raise ConfigurationError(
                "google-api-python-client / google-auth not installed. "
                "Install or set AVSP_YOUTUBE_USE_MOCK=true",
                platform=self.platform,
            )

        if not self.config.access_token:
            raise AuthError("No access_token for YouTube", platform=self.platform)

        creds = Credentials(token=self.config.access_token)
        youtube = build("youtube", "v3", credentials=creds)

        body = {
            "snippet": {
                "title": job.title[:100],
                "description": job.description or "",
                "tags": (job.tags or [])[:30],
                "categoryId": self._meta(job).get("category_id", "22"),
            },
            "status": {
                "privacyStatus": job.privacy if job.privacy in ("public", "private", "unlisted") else "private",
                "selfDeclaredMadeForKids": False,
            },
        }

        if job.scheduled_time:
            body["status"]["publishAt"] = job.scheduled_time
            body["status"]["privacyStatus"] = "private"

        media = MediaFileUpload(job.video_path, chunksize=-1, resumable=True, mimetype="video/*")

        request = youtube.videos().insert(
            part="snippet,status",
            body=body,
            media_body=media,
        )
        response = None
        while response is None:
            status, response = request.next_chunk()
            # progress ignored for simplicity

        video_id = response.get("id")
        if not video_id:
            raise UploadFailedError("YouTube API returned no video id", platform=self.platform)

        # optional thumbnail
        if job.thumbnail_path and os.path.exists(job.thumbnail_path):
            try:
                youtube.thumbnails().set(
                    videoId=video_id,
                    media_body=MediaFileUpload(job.thumbnail_path),
                ).execute()
            except Exception:
                pass  # non-fatal

        return PublishResult(
            success=True,
            platform=self.platform,
            platform_id=video_id,
            status=PlatformPublishStatus.PUBLISHED.value
            if not job.scheduled_time
            else PlatformPublishStatus.SCHEDULED.value,
            message="REAL YouTube upload succeeded",
            is_mock=False,
            raw={"id": video_id, "response_keys": list(response.keys())},
        )
