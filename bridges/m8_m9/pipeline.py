"""
Phase 2A orchestration: Topic → M8 → final.mp4 → M9 mock publish → analytics.

Records production timing (T0–T4) without embedding credentials.
"""

from __future__ import annotations

import json
import os
import sys
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from .adapter import M8ProjectBundle, M8ToM9Adapter


def _repo_root() -> Path:
    """Locate repo root containing M8/ and M9/ (bridge lives at bridges/m8_m9/)."""
    here = Path(__file__).resolve().parent
    for cand in (here.parents[1], here.parents[2], Path.cwd()):
        if (cand / "M8" / "m8").is_dir() and (cand / "M9" / "m9").is_dir():
            return cand
    return here.parents[1]


REPO_ROOT = _repo_root()
M8_ROOT = REPO_ROOT / "M8" / "m8"
M9_ROOT = REPO_ROOT / "M9" / "m9"


def _purge_app_modules() -> None:
    """M8 and M9 both ship top-level `app` packages — clear before switching."""
    for k in list(sys.modules):
        if k == "app" or k.startswith("app."):
            del sys.modules[k]


def _prefer_path(root: Path) -> None:
    root_s = str(root.resolve())
    drop = {str(M8_ROOT.resolve()), str(M9_ROOT.resolve())}
    cleaned = [p for p in sys.path if p not in drop]
    sys.path[:] = [root_s] + cleaned


def _import_m8_controller(m8_root: Path):
    _purge_app_modules()
    _prefer_path(m8_root)
    from app.m8.controller import AutonomousProductionController  # type: ignore

    return AutonomousProductionController


def _import_m9():
    _purge_app_modules()
    _prefer_path(M9_ROOT)
    from app.schemas.publishing import PublishingJobCreate  # type: ignore
    from app.m9.controller import PublishingController  # type: ignore
    from app.m9.queue import PublishingQueue  # type: ignore
    from app.m9.analytics import PublishingAnalytics  # type: ignore

    return PublishingJobCreate, PublishingController, PublishingQueue, PublishingAnalytics


def _ensure_paths() -> None:
    root_s = str(REPO_ROOT)
    if root_s not in sys.path:
        sys.path.insert(0, root_s)


@dataclass
class ProductionTiming:
    t0_start: float
    t1_m8_start: float
    t2_final_mp4: float
    t3_m9_request: float
    t4_mock_publish_complete: float

    @property
    def m8_render_sec(self) -> float:
        return max(0.0, self.t2_final_mp4 - self.t1_m8_start)

    @property
    def m9_processing_sec(self) -> float:
        return max(0.0, self.t4_mock_publish_complete - self.t3_m9_request)

    @property
    def total_automated_sec(self) -> float:
        return max(0.0, self.t4_mock_publish_complete - self.t0_start)

    def to_dict(self) -> Dict[str, float]:
        return {
            "t0_start": self.t0_start,
            "t1_m8_start": self.t1_m8_start,
            "t2_final_mp4": self.t2_final_mp4,
            "t3_m9_request": self.t3_m9_request,
            "t4_mock_publish_complete": self.t4_mock_publish_complete,
            "m8_render_sec": round(self.m8_render_sec, 3),
            "m9_processing_sec": round(self.m9_processing_sec, 3),
            "total_automated_sec": round(self.total_automated_sec, 3),
        }


@dataclass
class Phase2AResult:
    ok: bool
    project_id: str
    final_mp4: Optional[str]
    qc_status: Optional[str]
    job_id: Optional[str]
    job_status: Optional[str]
    platform_statuses: Dict[str, Any] = field(default_factory=dict)
    analytics: Dict[str, Any] = field(default_factory=dict)
    seo: Dict[str, Any] = field(default_factory=dict)
    timing: Dict[str, float] = field(default_factory=dict)
    m8_report: Dict[str, Any] = field(default_factory=dict)
    errors: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


def run_m8_to_m9_mock(
    topic: str,
    duration: str = "30s",
    format_name: str = "9:16",
    project_id: Optional[str] = None,
    platforms: Optional[List[str]] = None,
    m8_root: Optional[Path] = None,
    force_mock: bool = True,
    skip_m8: bool = False,
    existing_project_dir: Optional[Path] = None,
    queue_db: Optional[Path] = None,
    analytics_path: Optional[Path] = None,
) -> Phase2AResult:
    """
    End-to-end mock production chain.

    force_mock=True ensures no real platform credentials are used.
    """
    if not force_mock:
        raise ValueError("Phase 2A requires force_mock=True (no real credentials)")

    platforms = platforms or ["youtube", "telegram"]
    t0 = time.time()
    errors: List[str] = []

    _ensure_paths()
    m8_root = Path(m8_root or M8_ROOT)

    # --- M8 ---
    t1 = time.time()
    report: Dict[str, Any] = {}

    if existing_project_dir is not None:
        project_dir = Path(existing_project_dir)
        bundle = M8ProjectBundle.from_project_dir(project_dir)
        report = bundle.pipeline_report or {"project_id": bundle.project_id, "topic": topic}
        t2 = time.time() if bundle.final_mp4.exists() else t1
    else:
        AutonomousProductionController = _import_m8_controller(m8_root)
        ctrl = AutonomousProductionController(root=m8_root)
        report = ctrl.run(
            topic=topic,
            duration=duration,
            format_name=format_name,
            project_id=project_id,
            skip_render=skip_m8,
        )
        project_dir = m8_root / "projects" / report["project_id"]
        bundle = M8ProjectBundle.from_pipeline_report(report, project_dir=project_dir)
        t2 = time.time()

    if not bundle.final_mp4.exists():
        errors.append(f"final MP4 missing: {bundle.final_mp4}")
        return Phase2AResult(
            ok=False,
            project_id=bundle.project_id,
            final_mp4=None,
            qc_status=bundle.qc_status,
            job_id=None,
            job_status=None,
            timing=ProductionTiming(t0, t1, t2, t2, t2).to_dict(),
            m8_report=report,
            errors=errors,
        )

    # --- Adapter + M9 ---
    t3 = time.time()
    adapter = M8ToM9Adapter(default_platforms=platforms)
    seo = adapter.build_seo(bundle)

    PublishingJobCreate, PublishingController, PublishingQueue, PublishingAnalytics = _import_m9()

    qkwargs: Dict[str, Any] = {}
    if queue_db is not None:
        qkwargs["db_path"] = str(queue_db)
    akwargs: Dict[str, Any] = {}
    if analytics_path is not None:
        akwargs["path"] = str(analytics_path)

    queue = PublishingQueue(**qkwargs) if qkwargs else PublishingQueue()
    analytics = PublishingAnalytics(**akwargs) if akwargs else PublishingAnalytics()
    pub = PublishingController(queue=queue, analytics=analytics, force_mock=True)

    create = PublishingJobCreate(**adapter.to_job_create_kwargs(bundle, platforms=platforms))
    job = pub.create_job(create)
    job = pub.process_job(job.job_id)
    t4 = time.time()

    status = pub.get_status(job.job_id) or {}
    analytics_snap = pub.get_analytics()

    for env_key in ("YOUTUBE_CLIENT_SECRET", "TELEGRAM_BOT_TOKEN", "GOOGLE_API_KEY", "GEMINI_API_KEY"):
        val = os.environ.get(env_key)
        if val and len(val) > 8 and val in json.dumps(status, default=str):
            errors.append(f"credential leak detected for {env_key}")

    ok = job.status in ("PUBLISHED", "PARTIAL") and bundle.final_mp4.exists() and not errors
    if bundle.qc_status == "FAIL":
        ok = False
        errors.append("M8 QC status FAIL")

    norm_ps: Dict[str, Any] = {}
    for k, v in (job.platform_statuses or {}).items():
        if hasattr(v, "to_dict"):
            norm_ps[k] = v.to_dict()
        elif isinstance(v, dict):
            norm_ps[k] = v
        else:
            norm_ps[k] = {"status": str(v)}

    return Phase2AResult(
        ok=ok,
        project_id=bundle.project_id,
        final_mp4=str(bundle.final_mp4),
        qc_status=bundle.qc_status,
        job_id=job.job_id,
        job_status=job.status,
        platform_statuses=norm_ps,
        analytics=analytics_snap if isinstance(analytics_snap, dict) else {},
        seo=seo.to_dict(),
        timing=ProductionTiming(t0, t1, t2, t3, t4).to_dict(),
        m8_report={
            "project_id": report.get("project_id"),
            "topic": report.get("topic"),
            "format": report.get("format"),
            "final_mp4": report.get("final_mp4"),
            "decision_source": report.get("decision_source"),
        },
        errors=errors,
    )


def main(argv: Optional[List[str]] = None) -> int:
    import argparse

    p = argparse.ArgumentParser(description="AVSP Phase 2A M8→M9 mock production bridge")
    p.add_argument("--topic", required=True)
    p.add_argument("--duration", default="30s")
    p.add_argument("--format", default="9:16", dest="format_name")
    p.add_argument("--project-id", default=None)
    p.add_argument("--platforms", default="youtube,telegram")
    p.add_argument("--existing-project", default=None, help="Reuse an existing M8 project dir")
    args = p.parse_args(argv)

    platforms = [x.strip() for x in args.platforms.split(",") if x.strip()]
    result = run_m8_to_m9_mock(
        topic=args.topic,
        duration=args.duration,
        format_name=args.format_name,
        project_id=args.project_id,
        platforms=platforms,
        existing_project_dir=Path(args.existing_project) if args.existing_project else None,
        force_mock=True,
    )
    print(json.dumps(result.to_dict(), indent=2, default=str))
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
