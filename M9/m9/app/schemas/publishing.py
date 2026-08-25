"""
AVSP M9 — Publishing schemas and contracts.
"""

from __future__ import annotations

from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Any, Dict, List, Optional
from datetime import datetime, timezone
import uuid
import json


class Platform(str, Enum):
    YOUTUBE = "youtube"
    FACEBOOK = "facebook"
    INSTAGRAM = "instagram"
    TELEGRAM = "telegram"
    WEB = "web"


class JobStatus(str, Enum):
    DRAFT = "DRAFT"
    QUEUED = "QUEUED"
    VALIDATING = "VALIDATING"
    READY = "READY"
    UPLOADING = "UPLOADING"
    PROCESSING = "PROCESSING"
    PUBLISHED = "PUBLISHED"
    SCHEDULED = "SCHEDULED"
    FAILED = "FAILED"
    RETRYING = "RETRYING"
    CANCELLED = "CANCELLED"
    PARTIAL = "PARTIAL"  # multi-platform: some ok, some failed


# Valid transitions (simplified state machine)
ALLOWED_TRANSITIONS: Dict[JobStatus, set] = {
    JobStatus.DRAFT: {JobStatus.QUEUED, JobStatus.CANCELLED},
    JobStatus.QUEUED: {JobStatus.VALIDATING, JobStatus.CANCELLED},
    JobStatus.VALIDATING: {JobStatus.READY, JobStatus.FAILED, JobStatus.CANCELLED},
    JobStatus.READY: {JobStatus.UPLOADING, JobStatus.SCHEDULED, JobStatus.CANCELLED},
    JobStatus.SCHEDULED: {JobStatus.UPLOADING, JobStatus.CANCELLED},
    JobStatus.UPLOADING: {JobStatus.PROCESSING, JobStatus.FAILED, JobStatus.RETRYING},
    JobStatus.PROCESSING: {JobStatus.PUBLISHED, JobStatus.PARTIAL, JobStatus.FAILED, JobStatus.RETRYING},
    JobStatus.RETRYING: {JobStatus.UPLOADING, JobStatus.FAILED, JobStatus.CANCELLED},
    JobStatus.PUBLISHED: set(),
    JobStatus.PARTIAL: {JobStatus.RETRYING, JobStatus.CANCELLED},
    JobStatus.FAILED: {JobStatus.RETRYING, JobStatus.CANCELLED},
    JobStatus.CANCELLED: set(),
}


class PlatformPublishStatus(str, Enum):
    PENDING = "PENDING"
    UPLOADING = "UPLOADING"
    PROCESSING = "PROCESSING"
    PUBLISHED = "PUBLISHED"
    SCHEDULED = "SCHEDULED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


@dataclass
class PlatformStatus:
    platform: str
    status: str = PlatformPublishStatus.PENDING.value
    platform_id: Optional[str] = None
    attempt: int = 0
    published_at: Optional[str] = None
    error: Optional[Dict[str, Any]] = None
    error_code: Optional[str] = None
    retryable: bool = False
    message: Optional[str] = None
    extra: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "PlatformStatus":
        return cls(**{k: v for k, v in d.items() if k in cls.__dataclass_fields__})


@dataclass
class PublishingJobCreate:
    """Input contract for creating a publishing job."""
    project_id: str
    video_path: str
    title: str
    description: str = ""
    tags: List[str] = field(default_factory=list)
    hashtags: List[str] = field(default_factory=list)
    thumbnail_path: Optional[str] = None
    category: Optional[str] = None
    privacy: str = "private"  # private | unlisted | public
    scheduled_time: Optional[str] = None  # ISO-8601
    target_platforms: List[str] = field(default_factory=list)
    platform_metadata: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    language: str = "en"
    created_at: Optional[str] = None

    def validate_basic(self) -> List[str]:
        errors = []
        if not self.project_id:
            errors.append("project_id is required")
        if not self.video_path:
            errors.append("video_path is required")
        if not self.title or not self.title.strip():
            errors.append("title is required and must be non-empty")
        if not self.target_platforms:
            errors.append("at least one target_platform is required")
        for p in self.target_platforms:
            if p not in [e.value for e in Platform]:
                errors.append(f"unsupported platform: {p}")
        return errors


@dataclass
class PublishingJob:
    job_id: str
    project_id: str
    video_path: str
    title: str
    description: str = ""
    tags: List[str] = field(default_factory=list)
    hashtags: List[str] = field(default_factory=list)
    thumbnail_path: Optional[str] = None
    category: Optional[str] = None
    privacy: str = "private"
    scheduled_time: Optional[str] = None
    target_platforms: List[str] = field(default_factory=list)
    platform_metadata: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    language: str = "en"
    status: str = JobStatus.DRAFT.value
    platform_statuses: Dict[str, PlatformStatus] = field(default_factory=dict)
    attempt: int = 0
    max_attempts: int = 3
    created_at: str = ""
    updated_at: str = ""
    next_retry_at: Optional[str] = None
    error: Optional[Dict[str, Any]] = None
    idempotency_key: str = ""

    def __post_init__(self):
        if not self.job_id:
            self.job_id = str(uuid.uuid4())
        now = datetime.now(timezone.utc).isoformat()
        if not self.created_at:
            self.created_at = now
        if not self.updated_at:
            self.updated_at = now
        if not self.idempotency_key:
            self.idempotency_key = f"{self.job_id}"
        # init platform statuses
        for p in self.target_platforms:
            if p not in self.platform_statuses:
                self.platform_statuses[p] = PlatformStatus(platform=p)

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        # platform_statuses already dicts via asdict
        return d

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2, default=str)

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "PublishingJob":
        ps = d.pop("platform_statuses", {})
        # reconstruct PlatformStatus objects
        platform_statuses = {}
        for k, v in ps.items():
            if isinstance(v, dict):
                platform_statuses[k] = PlatformStatus.from_dict(v)
            else:
                platform_statuses[k] = v
        d["platform_statuses"] = platform_statuses
        # filter known fields
        known = {f for f in cls.__dataclass_fields__}
        filtered = {k: v for k, v in d.items() if k in known}
        return cls(**filtered)

    def can_transition(self, new_status: JobStatus) -> bool:
        current = JobStatus(self.status)
        return new_status in ALLOWED_TRANSITIONS.get(current, set())

    def transition(self, new_status: JobStatus) -> None:
        if not self.can_transition(new_status):
            raise ValueError(
                f"Invalid transition {self.status} → {new_status.value}"
            )
        self.status = new_status.value
        self.updated_at = datetime.now(timezone.utc).isoformat()

    def overall_status_from_platforms(self) -> str:
        """Derive overall status from individual platform results."""
        if not self.platform_statuses:
            return self.status
        statuses = [ps.status for ps in self.platform_statuses.values()]
        if all(s == PlatformPublishStatus.PUBLISHED.value for s in statuses):
            return JobStatus.PUBLISHED.value
        if all(s == PlatformPublishStatus.FAILED.value for s in statuses):
            return JobStatus.FAILED.value
        if any(s == PlatformPublishStatus.PUBLISHED.value for s in statuses) and any(
            s == PlatformPublishStatus.FAILED.value for s in statuses
        ):
            return JobStatus.PARTIAL.value
        if any(s == PlatformPublishStatus.SCHEDULED.value for s in statuses):
            return JobStatus.SCHEDULED.value
        return self.status


@dataclass
class PublishResult:
    success: bool
    platform: str
    platform_id: Optional[str] = None
    status: str = PlatformPublishStatus.PUBLISHED.value
    message: Optional[str] = None
    error_code: Optional[str] = None
    retryable: bool = False
    raw: Optional[Dict[str, Any]] = None
    is_mock: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
