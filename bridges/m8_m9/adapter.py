"""
M8 → M9 adapter.

Converts an M8 project directory / pipeline_report into M9 PublishingJobCreate
fields without modifying M8 or M9 internals.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from .seo import SEOPackage, build_seo_package


def _load_json(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


@dataclass
class M8ProjectBundle:
    """Actual M8 on-disk / report fields used by the adapter (no invented schema)."""

    project_id: str
    topic: str
    final_mp4: Path
    format: str = "9:16"
    duration_target: Optional[float] = None
    qc_status: Optional[str] = None
    pipeline_report: Dict[str, Any] = field(default_factory=dict)
    render: Dict[str, Any] = field(default_factory=dict)
    script: Dict[str, Any] = field(default_factory=dict)
    request: Dict[str, Any] = field(default_factory=dict)
    thumbnail_path: Optional[Path] = None
    project_dir: Optional[Path] = None

    @classmethod
    def from_project_dir(cls, project_dir: Path) -> "M8ProjectBundle":
        project_dir = Path(project_dir)
        report = _load_json(project_dir / "logs" / "pipeline_report.json")
        render = _load_json(project_dir / "render" / "render.json")
        script = _load_json(project_dir / "research" / "script.json")
        request = _load_json(project_dir / "input" / "request.json")
        qc = _load_json(project_dir / "qc" / "final_qc.json")

        project_id = (
            report.get("project_id")
            or request.get("project_id")
            or project_dir.name
        )
        topic = report.get("topic") or request.get("topic") or script.get("topic") or project_id
        fmt = report.get("format") or request.get("format") or "9:16"

        mp4 = project_dir / "render" / "final.mp4"
        if report.get("final_mp4"):
            cand = Path(report["final_mp4"])
            if cand.exists():
                mp4 = cand
        if not mp4.exists() and render.get("file"):
            cand = Path(render["file"])
            if cand.exists():
                mp4 = cand

        thumb: Optional[Path] = None
        frames_dir = project_dir / "qc" / "frames"
        if frames_dir.is_dir():
            frames = sorted(frames_dir.glob("*.jpg"))
            if frames:
                thumb = frames[0]

        return cls(
            project_id=str(project_id),
            topic=str(topic),
            final_mp4=mp4,
            format=str(fmt),
            duration_target=report.get("duration_target"),
            qc_status=(qc.get("status") if qc else None),
            pipeline_report=report,
            render=render,
            script=script,
            request=request,
            thumbnail_path=thumb,
            project_dir=project_dir,
        )

    @classmethod
    def from_pipeline_report(
        cls,
        report: Dict[str, Any],
        project_dir: Optional[Path] = None,
    ) -> "M8ProjectBundle":
        if project_dir is None and report.get("final_mp4"):
            # derive project dir from final_mp4 .../render/final.mp4
            mp4 = Path(report["final_mp4"])
            if mp4.parent.name == "render":
                project_dir = mp4.parent.parent
        if project_dir is not None:
            bundle = cls.from_project_dir(project_dir)
            # Prefer live report when provided
            bundle.pipeline_report = report
            if report.get("final_mp4"):
                bundle.final_mp4 = Path(report["final_mp4"])
            if report.get("qc"):
                bundle.qc_status = (report.get("qc") or {}).get("status")
            return bundle
        raise ValueError("project_dir required when final_mp4 path cannot derive it")


class M8ToM9Adapter:
    """
    Smallest adapter: M8ProjectBundle → dict suitable for PublishingJobCreate.

    Does not import M9 at module import time (path injected by caller/pipeline).
    """

    def __init__(
        self,
        default_platforms: Optional[List[str]] = None,
        default_privacy: str = "private",
        default_category: Optional[str] = None,
    ):
        self.default_platforms = default_platforms or ["youtube", "telegram"]
        self.default_privacy = default_privacy
        self.default_category = default_category

    def build_seo(self, bundle: M8ProjectBundle) -> SEOPackage:
        return build_seo_package(
            topic=bundle.topic,
            script=bundle.script,
            thumbnail_path=str(bundle.thumbnail_path) if bundle.thumbnail_path else None,
            language=(bundle.script.get("language") or "en"),
            privacy=self.default_privacy,
            category=self.default_category,
        )

    def to_job_create_kwargs(
        self,
        bundle: M8ProjectBundle,
        platforms: Optional[List[str]] = None,
        privacy: Optional[str] = None,
    ) -> Dict[str, Any]:
        if not bundle.final_mp4.exists():
            raise FileNotFoundError(f"M8 final MP4 missing: {bundle.final_mp4}")

        seo = self.build_seo(bundle)
        return {
            "project_id": bundle.project_id,
            "video_path": str(bundle.final_mp4.resolve()),
            "title": seo.title,
            "description": seo.description,
            "tags": list(seo.tags),
            "hashtags": list(seo.hashtags),
            "thumbnail_path": seo.thumbnail_path,
            "category": seo.category,
            "privacy": privacy or seo.privacy,
            "scheduled_time": None,
            "target_platforms": list(platforms or self.default_platforms),
            "platform_metadata": {},
            "language": seo.language,
        }

    def to_publishing_job_create(self, bundle: M8ProjectBundle, **kwargs: Any):
        """Return M9 PublishingJobCreate (requires M9 on sys.path)."""
        from bridges.m8_m9.pipeline import _import_m9

        PublishingJobCreate, *_ = _import_m9()
        data = self.to_job_create_kwargs(bundle, **kwargs)
        return PublishingJobCreate(**data)
