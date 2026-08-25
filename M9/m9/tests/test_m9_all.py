"""
AVSP M9 — Comprehensive test suite (TEST A–Z).
All tests run in MOCK mode. No real platform credentials required.
"""

from __future__ import annotations

import os
import sys
import tempfile
import shutil
from pathlib import Path
from datetime import datetime, timezone, timedelta

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.schemas.publishing import (
    PublishingJobCreate,
    PublishingJob,
    JobStatus,
    PlatformPublishStatus,
    Platform,
)
from app.validators.media import MediaValidator
from app.publishers.mock import (
    MockYouTubePublisher,
    MockFacebookPublisher,
    MockInstagramPublisher,
    MockTelegramPublisher,
    MockWebPublisher,
    get_mock_publisher,
    _BaseMockPublisher,
)
from app.m9.queue import PublishingQueue
from app.m9.analytics import PublishingAnalytics
from app.m9.controller import PublishingController
from app.m9.errors import (
    InvalidMetadataError,
    InvalidMediaError,
    MediaNotFoundError,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def tmp_dir():
    d = tempfile.mkdtemp(prefix="m9test_")
    yield Path(d)
    shutil.rmtree(d, ignore_errors=True)


@pytest.fixture
def sample_video(tmp_dir):
    """Create a minimal valid-looking MP4 (not a real video, but non-empty)."""
    path = tmp_dir / "final.mp4"
    # Minimal ftyp box-ish bytes so extension + size checks pass
    path.write_bytes(b"\x00\x00\x00\x18ftypmp42\x00\x00\x00\x00mp42isom" + b"\x00" * 200)
    return str(path)


@pytest.fixture
def sample_thumb(tmp_dir):
    path = tmp_dir / "thumb.jpg"
    path.write_bytes(b"\xff\xd8\xff\xe0" + b"\x00" * 100)  # fake JPEG header
    return str(path)


@pytest.fixture
def queue(tmp_dir):
    db = str(tmp_dir / "queue.db")
    return PublishingQueue(db_path=db)


@pytest.fixture
def analytics(tmp_dir):
    path = str(tmp_dir / "analytics.json")
    a = PublishingAnalytics(path=path)
    a.reset()
    return a


@pytest.fixture
def controller(queue, analytics):
    return PublishingController(
        queue=queue,
        analytics=analytics,
        force_mock=True,
        max_attempts=3,
        retry_base_seconds=1,
    )


def _make_create(video_path, **kwargs):
    defaults = dict(
        project_id="proj_test_001",
        video_path=video_path,
        title="Test AVSP Short",
        description="Automated test description",
        tags=["test", "avsp"],
        hashtags=["shorts"],
        target_platforms=["youtube", "telegram"],
        privacy="private",
        language="en",
    )
    defaults.update(kwargs)
    return PublishingJobCreate(**defaults)


# ---------------------------------------------------------------------------
# TEST A — job creation
# ---------------------------------------------------------------------------

def test_a_job_creation(controller, sample_video):
    create = _make_create(sample_video)
    job = controller.create_job(create)
    assert job.job_id
    assert job.status == JobStatus.QUEUED.value
    assert job.project_id == "proj_test_001"
    assert "youtube" in job.target_platforms
    assert job.platform_statuses["youtube"].status == PlatformPublishStatus.PENDING.value


# ---------------------------------------------------------------------------
# TEST B — input validation
# ---------------------------------------------------------------------------

def test_b_input_validation_empty_title(controller, sample_video):
    create = _make_create(sample_video, title="")
    with pytest.raises(InvalidMetadataError) as exc:
        controller.create_job(create)
    assert "title" in str(exc.value).lower()


def test_b_input_validation_no_platforms(controller, sample_video):
    create = _make_create(sample_video, target_platforms=[])
    with pytest.raises(InvalidMetadataError):
        controller.create_job(create)


def test_b_input_validation_bad_platform(controller, sample_video):
    create = _make_create(sample_video, target_platforms=["myspace"])
    with pytest.raises(InvalidMetadataError):
        controller.create_job(create)


# ---------------------------------------------------------------------------
# TEST C — missing media
# ---------------------------------------------------------------------------

def test_c_missing_media(controller, tmp_dir):
    create = _make_create(str(tmp_dir / "does_not_exist.mp4"))
    with pytest.raises(InvalidMediaError) as exc:
        controller.create_job(create)
    assert "does not exist" in str(exc.value).lower() or "MEDIA" in str(exc.value)


# ---------------------------------------------------------------------------
# TEST D — metadata validation
# ---------------------------------------------------------------------------

def test_d_metadata_validation(controller, sample_video):
    create = _make_create(sample_video, project_id="")
    with pytest.raises(InvalidMetadataError):
        controller.create_job(create)


# ---------------------------------------------------------------------------
# TEST E — queue enqueue
# ---------------------------------------------------------------------------

def test_e_queue_enqueue(controller, sample_video):
    create = _make_create(sample_video)
    job = controller.create_job(create)
    fetched = controller.queue.get(job.job_id)
    assert fetched is not None
    assert fetched.job_id == job.job_id


# ---------------------------------------------------------------------------
# TEST F — queue persistence
# ---------------------------------------------------------------------------

def test_f_queue_persistence(tmp_dir, sample_video):
    db = str(tmp_dir / "persist.db")
    q1 = PublishingQueue(db_path=db)
    ctrl1 = PublishingController(queue=q1, force_mock=True)
    job = ctrl1.create_job(_make_create(sample_video))
    job_id = job.job_id

    # New queue instance same DB
    q2 = PublishingQueue(db_path=db)
    loaded = q2.get(job_id)
    assert loaded is not None
    assert loaded.title == "Test AVSP Short"


# ---------------------------------------------------------------------------
# TEST G — queue processing
# ---------------------------------------------------------------------------

def test_g_queue_processing(controller, sample_video):
    job = controller.create_job(_make_create(sample_video))
    processed = controller.process_job(job.job_id)
    assert processed.status in (
        JobStatus.PUBLISHED.value,
        JobStatus.PARTIAL.value,
    )
    for p in processed.target_platforms:
        assert processed.platform_statuses[p].status == PlatformPublishStatus.PUBLISHED.value
        assert processed.platform_statuses[p].platform_id is not None
        assert processed.platform_statuses[p].extra.get("is_mock") is True


# ---------------------------------------------------------------------------
# TEST H — retry
# ---------------------------------------------------------------------------

def test_h_retry(controller, sample_video):
    # Force mock failure once
    _BaseMockPublisher.force_fail = True
    _BaseMockPublisher.force_error_code = "NETWORK_ERROR"
    _BaseMockPublisher.force_retryable = True
    try:
        job = controller.create_job(_make_create(sample_video, target_platforms=["youtube"]))
        processed = controller.process_job(job.job_id)
        assert processed.status in (JobStatus.RETRYING.value, JobStatus.FAILED.value)
        assert processed.attempt >= 1

        # Clear force fail and retry
        _BaseMockPublisher.force_fail = False
        _BaseMockPublisher.force_error_code = None
        _BaseMockPublisher.force_retryable = False
        retried = controller.retry_job(job.job_id)
        assert retried.status == JobStatus.PUBLISHED.value
    finally:
        _BaseMockPublisher.force_fail = False
        _BaseMockPublisher.force_error_code = None
        _BaseMockPublisher.force_retryable = False


# ---------------------------------------------------------------------------
# TEST I — non-retryable error
# ---------------------------------------------------------------------------

def test_i_non_retryable_error(controller, sample_video):
    _BaseMockPublisher.force_fail = True
    _BaseMockPublisher.force_error_code = "AUTH_ERROR"
    _BaseMockPublisher.force_retryable = False
    try:
        job = controller.create_job(_make_create(sample_video, target_platforms=["youtube"]))
        processed = controller.process_job(job.job_id)
        # Should go to FAILED (not RETRYING) because non-retryable
        assert processed.status == JobStatus.FAILED.value
        assert processed.platform_statuses["youtube"].retryable is False
    finally:
        _BaseMockPublisher.force_fail = False
        _BaseMockPublisher.force_error_code = None
        _BaseMockPublisher.force_retryable = False


# ---------------------------------------------------------------------------
# TEST J — idempotency
# ---------------------------------------------------------------------------

def test_j_idempotency(controller, sample_video):
    create = _make_create(sample_video, target_platforms=["youtube"])
    job1 = controller.create_job(create)
    # Same idempotency key → same job
    job2 = controller.create_job(create)
    assert job1.job_id == job2.job_id

    # Process once
    p1 = controller.process_job(job1.job_id)
    platform_id_1 = p1.platform_statuses["youtube"].platform_id

    # Process again — should not change published platform_id (idempotent skip)
    p2 = controller.process_job(job1.job_id)
    assert p2.platform_statuses["youtube"].platform_id == platform_id_1
    assert p2.status == JobStatus.PUBLISHED.value


# ---------------------------------------------------------------------------
# TEST K–O — individual mock publishers
# ---------------------------------------------------------------------------

def test_k_youtube_mock(sample_video):
    pub = MockYouTubePublisher()
    job = PublishingJob(
        job_id="j1", project_id="p", video_path=sample_video, title="T",
        target_platforms=["youtube"],
    )
    result = pub.publish(job)
    assert result.success
    assert result.is_mock
    assert result.platform == "youtube"
    assert result.platform_id.startswith("mock_youtube_")


def test_l_facebook_mock(sample_video):
    pub = MockFacebookPublisher()
    job = PublishingJob(
        job_id="j1", project_id="p", video_path=sample_video, title="T",
        target_platforms=["facebook"],
    )
    result = pub.publish(job)
    assert result.success and result.is_mock and result.platform == "facebook"


def test_m_instagram_mock(sample_video):
    pub = MockInstagramPublisher()
    job = PublishingJob(
        job_id="j1", project_id="p", video_path=sample_video, title="T",
        target_platforms=["instagram"],
    )
    result = pub.publish(job)
    assert result.success and result.is_mock and result.platform == "instagram"


def test_n_telegram_mock(sample_video):
    pub = MockTelegramPublisher()
    job = PublishingJob(
        job_id="j1", project_id="p", video_path=sample_video, title="T",
        target_platforms=["telegram"],
    )
    result = pub.publish(job)
    assert result.success and result.is_mock and result.platform == "telegram"


def test_o_web_mock(sample_video):
    pub = MockWebPublisher()
    job = PublishingJob(
        job_id="j1", project_id="p", video_path=sample_video, title="T",
        target_platforms=["web"],
    )
    result = pub.publish(job)
    assert result.success and result.is_mock and result.platform == "web"


# ---------------------------------------------------------------------------
# TEST P — multi-platform publishing
# ---------------------------------------------------------------------------

def test_p_multi_platform(controller, sample_video):
    platforms = ["youtube", "facebook", "instagram", "telegram", "web"]
    job = controller.create_job(
        _make_create(sample_video, target_platforms=platforms)
    )
    processed = controller.process_job(job.job_id)
    assert processed.status == JobStatus.PUBLISHED.value
    for p in platforms:
        assert processed.platform_statuses[p].status == PlatformPublishStatus.PUBLISHED.value
        assert processed.platform_statuses[p].extra.get("is_mock") is True


# ---------------------------------------------------------------------------
# TEST Q — partial platform failure
# ---------------------------------------------------------------------------

def test_q_partial_failure(controller, sample_video):
    # Fail only facebook
    original_publish = MockFacebookPublisher.publish

    def fail_fb(self, job):
        from app.schemas.publishing import PublishResult
        return PublishResult(
            success=False,
            platform="facebook",
            status=PlatformPublishStatus.FAILED.value,
            message="Mock FB fail",
            error_code="UPLOAD_FAILED",
            retryable=True,
            is_mock=True,
        )

    MockFacebookPublisher.publish = fail_fb
    try:
        job = controller.create_job(
            _make_create(sample_video, target_platforms=["youtube", "facebook"])
        )
        processed = controller.process_job(job.job_id)
        assert processed.status in (JobStatus.PARTIAL.value, JobStatus.RETRYING.value)
        assert processed.platform_statuses["youtube"].status == PlatformPublishStatus.PUBLISHED.value
        assert processed.platform_statuses["facebook"].status == PlatformPublishStatus.FAILED.value
    finally:
        MockFacebookPublisher.publish = original_publish


# ---------------------------------------------------------------------------
# TEST R — status tracking
# ---------------------------------------------------------------------------

def test_r_status_tracking(controller, sample_video):
    job = controller.create_job(_make_create(sample_video))
    st = controller.get_status(job.job_id)
    assert st is not None
    assert st["job_id"] == job.job_id
    assert st["status"] == JobStatus.QUEUED.value
    controller.process_job(job.job_id)
    st2 = controller.get_status(job.job_id)
    assert st2["status"] == JobStatus.PUBLISHED.value
    assert "youtube" in st2["platform_statuses"]


# ---------------------------------------------------------------------------
# TEST S — scheduling
# ---------------------------------------------------------------------------

def test_s_scheduling(controller, sample_video):
    future = (datetime.now(timezone.utc) + timedelta(hours=2)).isoformat()
    job = controller.create_job(
        _make_create(sample_video, scheduled_time=future, target_platforms=["youtube"])
    )
    processed = controller.process_job(job.job_id)
    assert processed.status == JobStatus.SCHEDULED.value


# ---------------------------------------------------------------------------
# TEST T — cancellation
# ---------------------------------------------------------------------------

def test_t_cancellation(controller, sample_video):
    job = controller.create_job(_make_create(sample_video))
    cancelled = controller.cancel_job(job.job_id)
    assert cancelled is not None
    assert cancelled.status == JobStatus.CANCELLED.value
    st = controller.get_status(job.job_id)
    assert st["status"] == JobStatus.CANCELLED.value


# ---------------------------------------------------------------------------
# TEST U — thumbnail validation
# ---------------------------------------------------------------------------

def test_u_thumbnail_validation(controller, sample_video, sample_thumb):
    job = controller.create_job(
        _make_create(sample_video, thumbnail_path=sample_thumb)
    )
    assert job.thumbnail_path == sample_thumb
    processed = controller.process_job(job.job_id)
    assert processed.status == JobStatus.PUBLISHED.value


def test_u_bad_thumbnail(controller, sample_video, tmp_dir):
    bad = str(tmp_dir / "bad.bmp")
    Path(bad).write_bytes(b"BM" + b"\x00" * 20)
    with pytest.raises(InvalidMediaError):
        controller.create_job(
            _make_create(sample_video, thumbnail_path=bad)
        )


# ---------------------------------------------------------------------------
# TEST V — analytics counters
# ---------------------------------------------------------------------------

def test_v_analytics(controller, sample_video, analytics):
    analytics.reset()
    job = controller.create_job(_make_create(sample_video))
    controller.process_job(job.job_id)
    snap = controller.get_analytics()
    assert snap["jobs_created"] >= 1
    assert snap["jobs_published"] >= 1
    assert snap["platform_success"]["youtube"] >= 1
    assert "average_upload_time_sec" in snap


# ---------------------------------------------------------------------------
# TEST W — authentication error (mock)
# ---------------------------------------------------------------------------

def test_w_auth_error(controller, sample_video):
    _BaseMockPublisher.force_fail = True
    _BaseMockPublisher.force_error_code = "AUTH_ERROR"
    _BaseMockPublisher.force_retryable = False
    try:
        job = controller.create_job(
            _make_create(sample_video, target_platforms=["youtube"])
        )
        processed = controller.process_job(job.job_id)
        assert processed.status == JobStatus.FAILED.value
        assert processed.platform_statuses["youtube"].error_code == "AUTH_ERROR"
    finally:
        _BaseMockPublisher.force_fail = False
        _BaseMockPublisher.force_error_code = None
        _BaseMockPublisher.force_retryable = False


# ---------------------------------------------------------------------------
# TEST X — network / transient error
# ---------------------------------------------------------------------------

def test_x_network_error(controller, sample_video):
    _BaseMockPublisher.force_fail = True
    _BaseMockPublisher.force_error_code = "NETWORK_ERROR"
    _BaseMockPublisher.force_retryable = True
    try:
        job = controller.create_job(
            _make_create(sample_video, target_platforms=["web"])
        )
        processed = controller.process_job(job.job_id)
        assert processed.status in (JobStatus.RETRYING.value, JobStatus.FAILED.value)
        assert processed.platform_statuses["web"].retryable is True
    finally:
        _BaseMockPublisher.force_fail = False
        _BaseMockPublisher.force_error_code = None
        _BaseMockPublisher.force_retryable = False


# ---------------------------------------------------------------------------
# TEST Y — invalid media
# ---------------------------------------------------------------------------

def test_y_invalid_media_empty_file(controller, tmp_dir):
    empty = tmp_dir / "empty.mp4"
    empty.write_bytes(b"")
    with pytest.raises(InvalidMediaError):
        controller.create_job(_make_create(str(empty)))


def test_y_invalid_media_bad_ext(controller, tmp_dir):
    bad = tmp_dir / "video.txt"
    bad.write_bytes(b"not a video")
    with pytest.raises(InvalidMediaError):
        controller.create_job(_make_create(str(bad)))


# ---------------------------------------------------------------------------
# TEST Z — complete end-to-end mock publishing flow
# ---------------------------------------------------------------------------

def test_z_e2e_mock_flow(controller, sample_video, sample_thumb, analytics):
    analytics.reset()
    create = _make_create(
        sample_video,
        thumbnail_path=sample_thumb,
        target_platforms=["youtube", "facebook", "instagram", "telegram", "web"],
        tags=["avsp", "test"],
        hashtags=["shorts", "ai"],
        description="Full e2e mock publish",
    )
    job = controller.create_job(create)
    assert job.status == JobStatus.QUEUED.value

    processed = controller.process_job(job.job_id)
    assert processed.status == JobStatus.PUBLISHED.value

    for p in create.target_platforms:
        ps = processed.platform_statuses[p]
        assert ps.status == PlatformPublishStatus.PUBLISHED.value
        assert ps.platform_id
        assert ps.extra.get("is_mock") is True
        assert "MOCK" in (ps.message or "").upper() or ps.extra.get("is_mock")

    st = controller.get_status(job.job_id)
    assert st["status"] == JobStatus.PUBLISHED.value

    snap = controller.get_analytics()
    assert snap["jobs_created"] >= 1
    assert snap["jobs_published"] >= 1
    for p in create.target_platforms:
        assert snap["platform_success"][p] >= 1

    # Log exists
    logs = controller.queue.get_log(job.job_id)
    assert len(logs) > 0


# ---------------------------------------------------------------------------
# Media validator unit tests
# ---------------------------------------------------------------------------

def test_media_validator_missing():
    v = MediaValidator()
    r = v.validate_video("/nonexistent/path/video.mp4")
    assert not r.ok
    assert any("exist" in e.lower() for e in r.errors)


def test_media_validator_ok(sample_video):
    v = MediaValidator()
    r = v.validate_video(sample_video)
    # Without real ffprobe streams, may warn but size/ext ok
    assert r.info.get("size_bytes", 0) > 0
    # ok depends on ffprobe; at minimum no hard file errors
    if not r.ok:
        # only stream-related allowed if ffprobe present and complains
        assert all("stream" in e.lower() or "duration" in e.lower() for e in r.errors) or r.ok


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])
