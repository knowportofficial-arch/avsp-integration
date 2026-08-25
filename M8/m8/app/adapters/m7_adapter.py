"""
M7 Personal Dataset / Media Intelligence adapter.
M7 is Android (Kotlin). This adapter consumes exported snapshots / local
media manifests that match the M7 contract shapes, without modifying M7.
"""
from __future__ import annotations

import json
import hashlib
from pathlib import Path
from typing import Any, Dict, List, Optional
from dataclasses import dataclass, field, asdict

from app.m8.errors import M8Error, ErrorCode


@dataclass
class MediaAsset:
    asset_id: str
    path: str
    type: str = "video"  # PHOTO | VIDEO | photo | video | image
    duration: float = 0.0
    resolution: str = ""
    orientation: str = "PORTRAIT"
    category: str = ""
    tags: List[str] = field(default_factory=list)
    quality_score: float = 0.0
    recommendation: str = "KEEP"  # KEEP | REVIEW | RETAKE
    source: str = "local"
    location: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)
    is_duplicate: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class M7Adapter:
    """
    Local-first media selection that mirrors M7 selection rules:
    - Prefer KEEP
    - REVIEW only if necessary
    - Never auto-select RETAKE
    - Avoid duplicates
    """

    def __init__(self, library_root: Optional[Path] = None, snapshot_path: Optional[Path] = None):
        self.library_root = Path(library_root) if library_root else Path("assets/local_media")
        self.snapshot_path = Path(snapshot_path) if snapshot_path else None
        self._assets: List[MediaAsset] = []
        self._load()

    def _load(self) -> None:
        if self.snapshot_path and self.snapshot_path.exists():
            try:
                data = json.loads(self.snapshot_path.read_text(encoding="utf-8"))
                items = data.get("items") or data.get("media") or data.get("summaries") or []
                for it in items:
                    rec = (it.get("recommendation") or it.get("quality", {}).get("recommendation") or "KEEP").upper()
                    score = float(it.get("quality_score") or it.get("score") or it.get("quality", {}).get("score") or 0.5)
                    path = it.get("path") or it.get("filePath") or it.get("uri") or ""
                    if not path:
                        continue
                    self._assets.append(MediaAsset(
                        asset_id=str(it.get("id") or it.get("asset_id") or hashlib.md5(path.encode()).hexdigest()[:12]),
                        path=path,
                        type=(it.get("type") or it.get("mediaType") or "VIDEO").upper(),
                        duration=float(it.get("duration") or 0),
                        resolution=str(it.get("resolution") or f"{it.get('width',0)}x{it.get('height',0)}"),
                        orientation=(it.get("orientation") or "PORTRAIT").upper(),
                        category=it.get("category") or "",
                        tags=it.get("tags") or it.get("tagsList") or [],
                        quality_score=score,
                        recommendation=rec,
                        source="local",
                        location=it.get("location"),
                        metadata=it.get("metadata") or {},
                        is_duplicate=bool(it.get("duplicate") or it.get("is_duplicate")),
                    ))
            except Exception as e:
                raise M8Error(ErrorCode.M7_ADAPTER_ERROR, f"Failed to load M7 snapshot: {e}", stage="m7_load")

        # Also scan library_root for loose files
        if self.library_root.exists():
            for p in self.library_root.rglob("*"):
                if p.suffix.lower() in {".mp4", ".mov", ".jpg", ".jpeg", ".png", ".webp"}:
                    aid = hashlib.md5(str(p).encode()).hexdigest()[:12]
                    if any(a.path == str(p) for a in self._assets):
                        continue
                    self._assets.append(MediaAsset(
                        asset_id=aid,
                        path=str(p),
                        type="VIDEO" if p.suffix.lower() in {".mp4", ".mov"} else "PHOTO",
                        recommendation="KEEP",
                        quality_score=0.7,
                        source="local",
                    ))

    def snapshot(self, project_id: str = "") -> Dict[str, Any]:
        keep = sum(1 for a in self._assets if a.recommendation == "KEEP")
        review = sum(1 for a in self._assets if a.recommendation == "REVIEW")
        retake = sum(1 for a in self._assets if a.recommendation == "RETAKE")
        return {
            "project_id": project_id,
            "total": len(self._assets),
            "keep": keep,
            "review": review,
            "retake": retake,
            "items": [a.to_dict() for a in self._assets],
        }

    def find_keepable(
        self,
        category: Optional[str] = None,
        min_quality: float = 0.7,
        orientation: Optional[str] = None,
        media_type: Optional[str] = None,
        limit: int = 20,
        allow_review: bool = False,
    ) -> List[MediaAsset]:
        """Mirror M7 findKeepable + findBestMedia ranking."""
        out: List[MediaAsset] = []
        for a in self._assets:
            if a.recommendation == "RETAKE":
                continue
            if a.is_duplicate:
                continue
            if a.recommendation == "REVIEW" and not allow_review:
                continue
            if a.quality_score < min_quality and a.recommendation != "KEEP":
                continue
            if category and a.category and category.lower() not in a.category.lower():
                if category.lower() not in " ".join(a.tags).lower():
                    continue
            if orientation and a.orientation and orientation.upper() not in a.orientation.upper():
                continue
            if media_type:
                mt = media_type.upper()
                if mt in ("PHOTO", "IMAGE") and a.type.upper() not in ("PHOTO", "IMAGE"):
                    continue
                if mt == "VIDEO" and a.type.upper() != "VIDEO":
                    continue
            out.append(a)

        # Prefer KEEP, then quality, then avoid REVIEW
        def rank(a: MediaAsset):
            rec_score = 2 if a.recommendation == "KEEP" else 1
            return (rec_score, a.quality_score)

        out.sort(key=rank, reverse=True)
        return out[:limit]

    def select_for_scene(
        self,
        visual_goal: str,
        category: Optional[str] = None,
        orientation: str = "PORTRAIT",
        prefer_keep: bool = True,
    ) -> Optional[MediaAsset]:
        """Select best local asset for a scene; never returns RETAKE."""
        candidates = self.find_keepable(
            category=category,
            min_quality=0.5,
            orientation=orientation,
            limit=10,
            allow_review=False,
        )
        if not candidates:
            candidates = self.find_keepable(
                category=category,
                min_quality=0.3,
                orientation=orientation,
                limit=10,
                allow_review=True,
            )
        if not candidates:
            return None
        # Simple keyword match on tags/category/path
        goal_l = (visual_goal or "").lower()
        scored = []
        for c in candidates:
            score = c.quality_score
            blob = f"{c.category} {' '.join(c.tags)} {c.path}".lower()
            for w in goal_l.split():
                if len(w) > 3 and w in blob:
                    score += 0.1
            scored.append((score, c))
        scored.sort(key=lambda x: x[0], reverse=True)
        return scored[0][1]
