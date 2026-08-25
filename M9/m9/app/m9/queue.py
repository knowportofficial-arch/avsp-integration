"""
AVSP M9 — Persistent local publishing queue (SQLite).
Survives application restart. No cloud required.
"""

from __future__ import annotations

import os
import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone, timedelta
from typing import Any, Dict, List, Optional
from pathlib import Path

from app.schemas.publishing import (
    PublishingJob,
    JobStatus,
    PlatformStatus,
    PlatformPublishStatus,
)
from app.m9.errors import StateTransitionError, M9Error


DEFAULT_DB_PATH = os.environ.get(
    "AVSP_M9_QUEUE_DB",
    str(Path(__file__).resolve().parents[2] / "data" / "m9_queue.db"),
)


class PublishingQueue:
    """SQLite-backed publishing job queue with state machine enforcement."""

    def __init__(self, db_path: Optional[str] = None):
        self.db_path = db_path or DEFAULT_DB_PATH
        Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    @contextmanager
    def _conn(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def _init_db(self) -> None:
        with self._conn() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS jobs (
                    job_id TEXT PRIMARY KEY,
                    data TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    next_retry_at TEXT,
                    idempotency_key TEXT UNIQUE
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS job_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id TEXT NOT NULL,
                    event TEXT NOT NULL,
                    from_status TEXT,
                    to_status TEXT,
                    message TEXT,
                    timestamp TEXT NOT NULL
                )
                """
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status)"
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_jobs_retry ON jobs(next_retry_at)"
            )

    def _log(
        self,
        conn: sqlite3.Connection,
        job_id: str,
        event: str,
        from_status: Optional[str] = None,
        to_status: Optional[str] = None,
        message: Optional[str] = None,
    ) -> None:
        conn.execute(
            """
            INSERT INTO job_log (job_id, event, from_status, to_status, message, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                job_id,
                event,
                from_status,
                to_status,
                message,
                datetime.now(timezone.utc).isoformat(),
            ),
        )

    def enqueue(self, job: PublishingJob) -> PublishingJob:
        """Add job to queue (DRAFT → QUEUED). Idempotent on job_id / idempotency_key."""
        with self._conn() as conn:
            # check existing by job_id or idempotency_key
            row = conn.execute(
                "SELECT data FROM jobs WHERE job_id = ? OR idempotency_key = ?",
                (job.job_id, job.idempotency_key),
            ).fetchone()
            if row:
                existing = PublishingJob.from_dict(json.loads(row["data"]))
                return existing

            if job.status == JobStatus.DRAFT.value:
                job.transition(JobStatus.QUEUED)

            conn.execute(
                """
                INSERT INTO jobs (job_id, data, status, created_at, updated_at, next_retry_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    job.job_id,
                    job.to_json(),
                    job.status,
                    job.created_at,
                    job.updated_at,
                    job.next_retry_at,
                    job.idempotency_key,
                ),
            )
            self._log(conn, job.job_id, "ENQUEUE", None, job.status, "Job enqueued")
            return job

    def get(self, job_id: str) -> Optional[PublishingJob]:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT data FROM jobs WHERE job_id = ?", (job_id,)
            ).fetchone()
            if not row:
                return None
            return PublishingJob.from_dict(json.loads(row["data"]))

    def update(self, job: PublishingJob) -> PublishingJob:
        job.updated_at = datetime.now(timezone.utc).isoformat()
        with self._conn() as conn:
            old = conn.execute(
                "SELECT status FROM jobs WHERE job_id = ?", (job.job_id,)
            ).fetchone()
            old_status = old["status"] if old else None
            conn.execute(
                """
                UPDATE jobs SET data = ?, status = ?, updated_at = ?, next_retry_at = ?
                WHERE job_id = ?
                """,
                (
                    job.to_json(),
                    job.status,
                    job.updated_at,
                    job.next_retry_at,
                    job.job_id,
                ),
            )
            if old_status != job.status:
                self._log(
                    conn,
                    job.job_id,
                    "STATUS_CHANGE",
                    old_status,
                    job.status,
                    f"{old_status} → {job.status}",
                )
            return job

    def list_jobs(
        self,
        status: Optional[str] = None,
        limit: int = 100,
    ) -> List[PublishingJob]:
        with self._conn() as conn:
            if status:
                rows = conn.execute(
                    "SELECT data FROM jobs WHERE status = ? ORDER BY created_at DESC LIMIT ?",
                    (status, limit),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT data FROM jobs ORDER BY created_at DESC LIMIT ?",
                    (limit,),
                ).fetchall()
            return [PublishingJob.from_dict(json.loads(r["data"])) for r in rows]

    def list_queued_ready(self, limit: int = 50) -> List[PublishingJob]:
        """Jobs that are QUEUED or READY or RETRYING (and retry time passed)."""
        now = datetime.now(timezone.utc).isoformat()
        with self._conn() as conn:
            rows = conn.execute(
                """
                SELECT data FROM jobs
                WHERE status IN ('QUEUED', 'READY', 'RETRYING')
                  AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ORDER BY created_at ASC
                LIMIT ?
                """,
                (now, limit),
            ).fetchall()
            return [PublishingJob.from_dict(json.loads(r["data"])) for r in rows]

    def cancel(self, job_id: str) -> Optional[PublishingJob]:
        job = self.get(job_id)
        if not job:
            return None
        if job.status in (
            JobStatus.PUBLISHED.value,
            JobStatus.CANCELLED.value,
        ):
            return job
        try:
            job.transition(JobStatus.CANCELLED)
        except ValueError:
            # force cancel for intermediate states if needed
            job.status = JobStatus.CANCELLED.value
            job.updated_at = datetime.now(timezone.utc).isoformat()
        return self.update(job)

    def get_log(self, job_id: str, limit: int = 50) -> List[Dict[str, Any]]:
        with self._conn() as conn:
            rows = conn.execute(
                """
                SELECT event, from_status, to_status, message, timestamp
                FROM job_log WHERE job_id = ?
                ORDER BY id DESC LIMIT ?
                """,
                (job_id, limit),
            ).fetchall()
            return [dict(r) for r in rows]

    def count_by_status(self) -> Dict[str, int]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT status, COUNT(*) as cnt FROM jobs GROUP BY status"
            ).fetchall()
            return {r["status"]: r["cnt"] for r in rows}
