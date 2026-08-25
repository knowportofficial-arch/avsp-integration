"""
AVSP M9 — Publishing Controller.
Orchestrates validation, queue, multi-platform publish, retry, status, analytics.
Does NOT own video generation (M4/M8).
"""

from __future__ import annotations

import time
from datetime import datetime, timezone, timedelta
from typing import Any, Dict, List, Optional

from app.schemas.publishing import (
    PublishingJob,
    PublishingJobCreate,
    JobStatus,
    PlatformPublishStatus,
    Platform,
    PublishResult,
)
from app.validators.media import MediaValidator
from app.m9.queue import PublishingQueue
from app.m9.analytics import PublishingAnalytics
from app.m9.errors import (
    M9Error,
    MediaNotFoundError,
    InvalidMetadataError,
    InvalidMediaError,
    StateTransitionError,
)

def _get_publisher(platform: str, force_mock: bool = False):
    from app.publishers.mock import get_publisher
    return get_publisher(platform, force_mock=force_mock)


class PublishingController:
    """
    Main entry point for M9.
    Accepts final video + metadata from upstream (M8 / automation).
    """

    def __init__(
        self,
        queue: Optional[PublishingQueue] = None,
        analytics: Optional[PublishingAnalytics] = None,
        force_mock: bool = True,
        max_attempts: int = 3,
        retry_base_seconds: int = 30,
    ):
        self.queue = queue or PublishingQueue()
        self.analytics = analytics or PublishingAnalytics()
        self.validator = MediaValidator()
        self.force_mock = force_mock
        self.max_attempts = max_attempts
        self.retry_base_seconds = retry_base_seconds

    # ------------------------------------------------------------------
    # Job creation & validation
    # ------------------------------------------------------------------

    def create_job(self, data: PublishingJobCreate) -> PublishingJob:
        """Validate input and create a DRAFT job, then enqueue."""
        basic_errors = data.validate_basic()
        if basic_errors:
            raise InvalidMetadataError(
                "; ".join(basic_errors),
                details={"validation_errors": basic_errors},
            )

        # Media validation
        vresult = self.validator.validate_video(data.video_path)
        if not vresult.ok:
            raise InvalidMediaError(
                "; ".join(vresult.errors),
                details=vresult.to_dict(),
            )

        if data.thumbnail_path:
            tresult = self.validator.validate_thumbnail(data.thumbnail_path)
            if not tresult.ok:
                raise InvalidMediaError(
                    "Thumbnail invalid: " + "; ".join(tresult.errors),
                    details=tresult.to_dict(),
                )

        job = PublishingJob(
            job_id="",  # auto
            project_id=data.project_id,
            video_path=data.video_path,
            title=data.title,
            description=data.description,
            tags=list(data.tags),
            hashtags=list(data.hashtags),
            thumbnail_path=data.thumbnail_path,
            category=data.category,
            privacy=data.privacy,
            scheduled_time=data.scheduled_time,
            target_platforms=list(data.target_platforms),
            platform_metadata=dict(data.platform_metadata or {}),
            language=data.language,
            status=JobStatus.DRAFT.value,
            max_attempts=self.max_attempts,
            created_at=data.created_at or datetime.now(timezone.utc).isoformat(),
        )

        # Idempotency key based on project + video + platforms
        platforms_key = ",".join(sorted(job.target_platforms))
        job.idempotency_key = f"{job.project_id}:{job.video_path}:{platforms_key}"

        enqueued = self.queue.enqueue(job)
        self.analytics.record_created()
        return enqueued

    # ------------------------------------------------------------------
    # Processing
    # ------------------------------------------------------------------

    def process_job(self, job_id: str) -> PublishingJob:
        """Process a single job: validate → publish to all target platforms."""
        job = self.queue.get(job_id)
        if not job:
            raise M9Error(f"Job not found: {job_id}", error_code="JOB_NOT_FOUND")

        if job.status in (
            JobStatus.PUBLISHED.value,
            JobStatus.CANCELLED.value,
        ):
            return job

        # VALIDATING
        try:
            if job.can_transition(JobStatus.VALIDATING):
                job.transition(JobStatus.VALIDATING)
                self.queue.update(job)
        except ValueError:
            pass

        # Re-validate media (file may have been deleted)
        vresult = self.validator.validate_video(job.video_path)
        if not vresult.ok:
            job.status = JobStatus.FAILED.value
            job.error = {
                "error_code": "INVALID_MEDIA",
                "message": "; ".join(vresult.errors),
            }
            self.queue.update(job)
            self.analytics.record_failed()
            return job

        # READY
        if job.can_transition(JobStatus.READY):
            job.transition(JobStatus.READY)
            self.queue.update(job)

        # Scheduling: if future scheduled_time, mark SCHEDULED and exit
        if job.scheduled_time:
            try:
                sched = datetime.fromisoformat(
                    job.scheduled_time.replace("Z", "+00:00")
                )
                now = datetime.now(timezone.utc)
                if sched > now:
                    if job.can_transition(JobStatus.SCHEDULED):
                        job.transition(JobStatus.SCHEDULED)
                        self.queue.update(job)
                    return job
            except Exception:
                pass  # invalid schedule → treat as immediate

        # UPLOADING
        if job.can_transition(JobStatus.UPLOADING):
            job.transition(JobStatus.UPLOADING)
        job.attempt += 1
        self.queue.update(job)

        start = time.time()
        results: Dict[str, PublishResult] = {}

        for platform in job.target_platforms:
            # Idempotency: skip if already published on this platform
            ps = job.platform_statuses.get(platform)
            if ps and ps.status == PlatformPublishStatus.PUBLISHED.value and ps.platform_id:
                continue

            publisher = _get_publisher(platform, force_mock=self.force_mock)
            result = publisher.publish(job)
            results[platform] = result

            # Update platform status
            status = PlatformPublishStatus.PUBLISHED.value if result.success else PlatformPublishStatus.FAILED.value
            if result.status == PlatformPublishStatus.SCHEDULED.value:
                status = PlatformPublishStatus.SCHEDULED.value

            job.platform_statuses[platform] = type(ps)(
                platform=platform,
                status=status,
                platform_id=result.platform_id,
                attempt=job.attempt,
                published_at=datetime.now(timezone.utc).isoformat() if result.success else None,
                error=result.raw if not result.success else None,
                error_code=result.error_code,
                retryable=result.retryable,
                message=result.message,
                extra={"is_mock": result.is_mock},
            )

            if result.success:
                self.analytics.record_platform_success(platform)
            else:
                self.analytics.record_platform_failure(platform)

        elapsed = time.time() - start
        self.analytics.record_upload_time(elapsed)

        # Derive overall status
        overall = job.overall_status_from_platforms()
        job.status = overall

        if overall == JobStatus.PUBLISHED.value:
            self.analytics.record_published()
        elif overall == JobStatus.PARTIAL.value:
            self.analytics.record_partial()
        elif overall == JobStatus.FAILED.value:
            # decide retry
            any_retryable = any(
                (job.platform_statuses[p].retryable for p in job.target_platforms
                 if job.platform_statuses[p].status == PlatformPublishStatus.FAILED.value)
            )
            if any_retryable and job.attempt < job.max_attempts:
                job.status = JobStatus.RETRYING.value
                delay = self.retry_base_seconds * (2 ** (job.attempt - 1))
                job.next_retry_at = (
                    datetime.now(timezone.utc) + timedelta(seconds=delay)
                ).isoformat()
                self.analytics.record_retried()
            else:
                self.analytics.record_failed()
                job.error = {
                    "error_code": "ALL_PLATFORMS_FAILED",
                    "message": "All target platforms failed or max attempts reached",
                    "attempt": job.attempt,
                }

        self.queue.update(job)
        return job

    def process_queue(self, limit: int = 20) -> List[PublishingJob]:
        """Process next batch of ready/queued/retrying jobs."""
        jobs = self.queue.list_queued_ready(limit=limit)
        results = []
        for job in jobs:
            try:
                results.append(self.process_job(job.job_id))
            except Exception as e:
                # mark failed but continue batch
                job.status = JobStatus.FAILED.value
                job.error = {"error_code": "PROCESSING_EXCEPTION", "message": str(e)}
                self.queue.update(job)
                self.analytics.record_failed()
                results.append(job)
        return results

    def retry_job(self, job_id: str) -> PublishingJob:
        """Manually retry a FAILED or PARTIAL job."""
        job = self.queue.get(job_id)
        if not job:
            raise M9Error(f"Job not found: {job_id}", error_code="JOB_NOT_FOUND")

        if job.status not in (
            JobStatus.FAILED.value,
            JobStatus.PARTIAL.value,
            JobStatus.RETRYING.value,
        ):
            raise StateTransitionError(
                f"Cannot retry job in status {job.status}",
                job_id=job_id,
            )

        # Reset failed platforms to PENDING for retry
        for p, ps in job.platform_statuses.items():
            if ps.status == PlatformPublishStatus.FAILED.value and ps.retryable:
                ps.status = PlatformPublishStatus.PENDING.value
                ps.error = None
                ps.error_code = None

        job.status = JobStatus.RETRYING.value
        job.next_retry_at = None
        self.queue.update(job)
        return self.process_job(job_id)

    def cancel_job(self, job_id: str) -> Optional[PublishingJob]:
        job = self.queue.cancel(job_id)
        if job and job.status == JobStatus.CANCELLED.value:
            self.analytics.record_cancelled()
        return job

    def get_status(self, job_id: str) -> Optional[Dict[str, Any]]:
        job = self.queue.get(job_id)
        if not job:
            return None
        return {
            "job_id": job.job_id,
            "project_id": job.project_id,
            "status": job.status,
            "attempt": job.attempt,
            "max_attempts": job.max_attempts,
            "target_platforms": job.target_platforms,
            "platform_statuses": {
                p: ps.to_dict() for p, ps in job.platform_statuses.items()
            },
            "created_at": job.created_at,
            "updated_at": job.updated_at,
            "next_retry_at": job.next_retry_at,
            "error": job.error,
            "scheduled_time": job.scheduled_time,
        }

    def get_analytics(self) -> Dict[str, Any]:
        return self.analytics.snapshot()
