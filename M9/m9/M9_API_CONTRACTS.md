# M9 API Contracts

## 1. Create job (Python)

```python
from app.schemas.publishing import PublishingJobCreate
from app.m9.controller import PublishingController

ctrl = PublishingController(force_mock=True)

job = ctrl.create_job(PublishingJobCreate(
    project_id="project001",
    video_path="/path/to/final.mp4",
    title="Title",
    description="...",
    tags=["a", "b"],
    hashtags=["shorts"],
    thumbnail_path="/path/to/thumb.jpg",  # optional
    privacy="private",                    # private|unlisted|public
    scheduled_time=None,                  # ISO-8601 or None
    target_platforms=["youtube", "telegram"],
    platform_metadata={
        "instagram": {"video_url": "https://..."}  # when real IG
    },
    language="bn",
))
```

## 2. Process job

```python
result = ctrl.process_job(job.job_id)
# result.status ∈ {PUBLISHED, PARTIAL, FAILED, RETRYING, SCHEDULED, ...}
```

## 3. Status

```python
st = ctrl.get_status(job.job_id)
# {
#   "job_id": "...",
#   "status": "PUBLISHED",
#   "platform_statuses": {
#     "youtube": {
#       "platform": "youtube",
#       "status": "PUBLISHED",
#       "platform_id": "mock_youtube_abc",
#       "attempt": 1,
#       "published_at": "2026-...",
#       "error": null,
#       "error_code": null,
#       "retryable": false,
#       "message": "MOCKED publish to youtube succeeded",
#       "extra": {"is_mock": true}
#     }
#   },
#   ...
# }
```

## 4. Retry / Cancel

```python
ctrl.retry_job(job_id)
ctrl.cancel_job(job_id)
```

## 5. Queue batch

```python
jobs = ctrl.process_queue(limit=20)
```

## 6. Analytics

```python
ctrl.get_analytics()
# {
#   "jobs_created": N,
#   "jobs_published": N,
#   "jobs_failed": N,
#   "jobs_retried": N,
#   "platform_success": {"youtube": N, ...},
#   "platform_failure": {"youtube": N, ...},
#   "average_upload_time_sec": float,
#   ...
# }
```

## 7. CLI

```
python main.py publish --project-id ID --video PATH --title TEXT --platforms youtube,telegram [--mock]
python main.py process-queue [--limit N] [--mock]
python main.py status --job-id UUID
python main.py retry --job-id UUID [--mock]
python main.py cancel --job-id UUID
python main.py analytics
python main.py list [--status STATUS]
```

## 8. Error payload

```json
{
  "error_code": "AUTH_ERROR",
  "message": "...",
  "platform": "youtube",
  "job_id": "...",
  "retryable": false,
  "attempt": 1,
  "timestamp": "..."
}
```

Error codes: `AUTH_ERROR`, `MEDIA_NOT_FOUND`, `INVALID_METADATA`, `INVALID_MEDIA`, `RATE_LIMIT`, `NETWORK_ERROR`, `UPLOAD_FAILED`, `PROCESSING_FAILED`, `PERMISSION_DENIED`, `CONFIG_ERROR`, `INVALID_STATE`, `JOB_NOT_FOUND`, `UNKNOWN_ERROR`.

## 9. Upstream (M8) handoff

M9 expects only:

- path to accepted `final.mp4`
- title / description / tags / language
- optional thumbnail
- list of target platforms

M9 does **not** import or modify M8 packages.
