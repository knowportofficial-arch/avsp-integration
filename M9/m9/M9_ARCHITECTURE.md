# M9 Architecture

## Overview

M9 is a **standalone publishing module**. It does not generate video. It receives a finished MP4 (and optional thumbnail/metadata) and distributes it to configured platforms through a reliable local queue.

```
Upstream (M8 / manual / automation)
            │
            ▼
   PublishingJobCreate  (validated)
            │
            ▼
   PublishingQueue (SQLite)
            │
            ▼
   PublishingController.process_job()
            │
     ┌──────┼──────┬──────┬──────┐
     ▼      ▼      ▼      ▼      ▼
  YouTube Facebook Instagram Telegram Web
     │      │      │      │      │
     └──────┴──────┴──────┴──────┘
            │
            ▼
   PlatformStatus[] + overall JobStatus
            │
            ▼
   Analytics (local counters)
```

## Components

| Component | Path | Responsibility |
|-----------|------|----------------|
| Schemas | `app/schemas/publishing.py` | Job model, state machine, contracts |
| Errors | `app/m9/errors.py` | Structured error codes |
| Queue | `app/m9/queue.py` | Persistent SQLite queue + audit log |
| Analytics | `app/m9/analytics.py` | Local outcome counters |
| Controller | `app/m9/controller.py` | Orchestration |
| MediaValidator | `app/validators/media.py` | File/stream checks |
| Publishers | `app/publishers/*` | Platform adapters + mocks |
| CLI | `main.py` | Operator interface |

## State machine

```
DRAFT → QUEUED → VALIDATING → READY → UPLOADING → PROCESSING
                                              ↓
                    SCHEDULED ←───────────────┤
                                              ↓
                                    PUBLISHED / PARTIAL / FAILED
                                              ↓
                                         RETRYING → UPLOADING
                                              ↓
                                         CANCELLED
```

Invalid transitions raise `StateTransitionError`.

## Multi-platform semantics

- One job can target N platforms.
- Each platform has independent `PlatformStatus`.
- Overall job becomes:
  - `PUBLISHED` if all platforms succeed
  - `PARTIAL` if some succeed and some fail
  - `FAILED` if all fail (or max attempts exhausted)
- Retry only re-attempts **retryable** failed platforms.
- Successful platforms are **not** re-uploaded (idempotency).

## Idempotency

Key: `project_id:video_path:sorted_platforms`

- Re-creating the same job returns the existing job_id.
- Re-processing a published platform skips upload if `platform_id` already present.

## Retry policy

- Configurable `max_attempts` (default 3).
- Exponential backoff: `retry_base_seconds * 2^(attempt-1)`.
- Retryable codes: `NETWORK_ERROR`, `RATE_LIMIT`, `UPLOAD_FAILED`, `PROCESSING_FAILED`.
- Non-retryable: `AUTH_ERROR`, `INVALID_METADATA`, `INVALID_MEDIA`, `PERMISSION_DENIED`, `MEDIA_NOT_FOUND`.

## Mock vs Real

Factory `get_publisher(platform, force_mock=...)`:

1. If `force_mock` or `AVSP_<PLATFORM>_USE_MOCK=true` → Mock*Publisher.
2. Else → real publisher (validates env credentials).

All mock results set `is_mock=True` and messages contain "MOCK".

## Storage

- Queue DB: `data/m9_queue.db` (override via `AVSP_M9_QUEUE_DB`)
- Analytics: `data/m9_analytics.json` (override via `AVSP_M9_ANALYTICS_PATH`)

No cloud database required.

## Extension points

- New platform: implement `Publisher` subclass + register in factory.
- New storage backend: swap `PublishingQueue` implementation.
- Scheduler: M9 respects `scheduled_time`; a future M8/M1 scheduler can call `process_queue` periodically.
