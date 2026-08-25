#!/usr/bin/env python3
"""
AVSP M9 — Publishing & Web CLI.

Usage examples:
  # Create + process a mock publish job
  PYTHONPATH=. python main.py publish \\
      --project-id demo001 \\
      --video /path/to/final.mp4 \\
      --title "Test Short" \\
      --platforms youtube,telegram \\
      --mock

  # Process queue
  PYTHONPATH=. python main.py process-queue --mock

  # Status
  PYTHONPATH=. python main.py status --job-id <uuid>

  # Analytics
  PYTHONPATH=. python main.py analytics
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

# Ensure package root on path
ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.schemas.publishing import PublishingJobCreate
from app.m9.controller import PublishingController


def cmd_publish(args: argparse.Namespace) -> int:
    platforms = [p.strip() for p in args.platforms.split(",") if p.strip()]
    create = PublishingJobCreate(
        project_id=args.project_id,
        video_path=args.video,
        title=args.title,
        description=args.description or "",
        tags=[t.strip() for t in (args.tags or "").split(",") if t.strip()],
        hashtags=[h.strip() for h in (args.hashtags or "").split(",") if h.strip()],
        thumbnail_path=args.thumbnail,
        privacy=args.privacy,
        scheduled_time=args.schedule,
        target_platforms=platforms,
        language=args.language or "en",
    )

    ctrl = PublishingController(force_mock=args.mock)
    try:
        job = ctrl.create_job(create)
        print(f"[M9] Created job {job.job_id} status={job.status}")
        if not args.enqueue_only:
            job = ctrl.process_job(job.job_id)
            print(f"[M9] Processed job {job.job_id} status={job.status}")
            for p, ps in job.platform_statuses.items():
                mock_tag = " [MOCK]" if ps.extra.get("is_mock") else " [REAL]"
                print(f"  {p}: {ps.status} id={ps.platform_id}{mock_tag}")
                if ps.error_code:
                    print(f"    error={ps.error_code}: {ps.message}")
        print(json.dumps(ctrl.get_status(job.job_id), indent=2, default=str))
        return 0
    except Exception as e:
        print(f"[M9] ERROR: {e}", file=sys.stderr)
        return 1


def cmd_process_queue(args: argparse.Namespace) -> int:
    ctrl = PublishingController(force_mock=args.mock)
    results = ctrl.process_queue(limit=args.limit)
    print(f"[M9] Processed {len(results)} jobs")
    for job in results:
        print(f"  {job.job_id} → {job.status}")
    return 0


def cmd_status(args: argparse.Namespace) -> int:
    ctrl = PublishingController(force_mock=True)
    st = ctrl.get_status(args.job_id)
    if not st:
        print(f"Job not found: {args.job_id}", file=sys.stderr)
        return 1
    print(json.dumps(st, indent=2, default=str))
    return 0


def cmd_retry(args: argparse.Namespace) -> int:
    ctrl = PublishingController(force_mock=args.mock)
    try:
        job = ctrl.retry_job(args.job_id)
        print(f"[M9] Retry result: {job.job_id} → {job.status}")
        print(json.dumps(ctrl.get_status(job.job_id), indent=2, default=str))
        return 0
    except Exception as e:
        print(f"[M9] ERROR: {e}", file=sys.stderr)
        return 1


def cmd_cancel(args: argparse.Namespace) -> int:
    ctrl = PublishingController(force_mock=True)
    job = ctrl.cancel_job(args.job_id)
    if not job:
        print(f"Job not found: {args.job_id}", file=sys.stderr)
        return 1
    print(f"[M9] Cancelled: {job.job_id} → {job.status}")
    return 0


def cmd_analytics(args: argparse.Namespace) -> int:
    ctrl = PublishingController(force_mock=True)
    print(json.dumps(ctrl.get_analytics(), indent=2))
    return 0


def cmd_list(args: argparse.Namespace) -> int:
    ctrl = PublishingController(force_mock=True)
    jobs = ctrl.queue.list_jobs(status=args.status, limit=args.limit)
    for j in jobs:
        print(f"{j.job_id}  {j.status:12}  {j.title[:40]}  platforms={j.target_platforms}")
    print(f"Total: {len(jobs)}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="AVSP M9 Publishing & Web")
    sub = parser.add_subparsers(dest="command", required=True)

    p_pub = sub.add_parser("publish", help="Create and optionally process a publish job")
    p_pub.add_argument("--project-id", required=True)
    p_pub.add_argument("--video", required=True)
    p_pub.add_argument("--title", required=True)
    p_pub.add_argument("--description", default="")
    p_pub.add_argument("--tags", default="")
    p_pub.add_argument("--hashtags", default="")
    p_pub.add_argument("--thumbnail", default=None)
    p_pub.add_argument("--platforms", required=True, help="comma-separated: youtube,facebook,instagram,telegram,web")
    p_pub.add_argument("--privacy", default="private")
    p_pub.add_argument("--schedule", default=None, help="ISO-8601 scheduled time")
    p_pub.add_argument("--language", default="en")
    p_pub.add_argument("--mock", action="store_true", default=True)
    p_pub.add_argument("--no-mock", action="store_false", dest="mock")
    p_pub.add_argument("--enqueue-only", action="store_true")
    p_pub.set_defaults(func=cmd_publish)

    p_pq = sub.add_parser("process-queue", help="Process pending queue jobs")
    p_pq.add_argument("--limit", type=int, default=20)
    p_pq.add_argument("--mock", action="store_true", default=True)
    p_pq.add_argument("--no-mock", action="store_false", dest="mock")
    p_pq.set_defaults(func=cmd_process_queue)

    p_st = sub.add_parser("status", help="Show job status")
    p_st.add_argument("--job-id", required=True)
    p_st.set_defaults(func=cmd_status)

    p_rt = sub.add_parser("retry", help="Retry a failed/partial job")
    p_rt.add_argument("--job-id", required=True)
    p_rt.add_argument("--mock", action="store_true", default=True)
    p_rt.add_argument("--no-mock", action="store_false", dest="mock")
    p_rt.set_defaults(func=cmd_retry)

    p_ca = sub.add_parser("cancel", help="Cancel a job")
    p_ca.add_argument("--job-id", required=True)
    p_ca.set_defaults(func=cmd_cancel)

    p_an = sub.add_parser("analytics", help="Show local analytics")
    p_an.set_defaults(func=cmd_analytics)

    p_ls = sub.add_parser("list", help="List jobs")
    p_ls.add_argument("--status", default=None)
    p_ls.add_argument("--limit", type=int, default=50)
    p_ls.set_defaults(func=cmd_list)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
