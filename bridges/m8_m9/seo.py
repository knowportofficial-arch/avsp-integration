"""
SEO metadata package for M9 PublishingJobCreate fields.

Uses existing M9 schema fields only — no advanced AI SEO engine.
"""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class SEOPackage:
    """Maps 1:1 onto M9 PublishingJobCreate metadata fields."""

    title: str
    description: str = ""
    tags: List[str] = field(default_factory=list)
    hashtags: List[str] = field(default_factory=list)
    thumbnail_path: Optional[str] = None
    category: Optional[str] = None
    language: str = "en"
    privacy: str = "private"  # private | unlisted | public
    visibility: str = "private"  # alias of privacy for SEO wording

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        # visibility mirrors privacy for downstream SEO consumers
        d["visibility"] = self.privacy
        return d


def _slug_tags(topic: str, limit: int = 8) -> List[str]:
    words = re.findall(r"[A-Za-z0-9]+", topic or "")
    tags: List[str] = []
    for w in words:
        lw = w.lower()
        if len(lw) < 2:
            continue
        if lw not in tags:
            tags.append(lw)
        if len(tags) >= limit:
            break
    if topic and topic.strip() and topic.strip().lower() not in tags:
        tags.insert(0, topic.strip()[:40])
    return tags[:limit]


def build_seo_package(
    *,
    topic: str,
    script: Optional[Dict[str, Any]] = None,
    thumbnail_path: Optional[str] = None,
    language: str = "en",
    privacy: str = "private",
    category: Optional[str] = None,
    max_description: int = 4000,
) -> SEOPackage:
    """
    Build SEO fields from M8 research/script artifacts.

    title ← topic (or script topic)
    description ← script full_text or joined segment texts
    tags/hashtags ← lightweight tokenization of topic (not AI)
    """
    script = script or {}
    title = (script.get("topic") or topic or "AVSP Video").strip()
    if not title:
        title = "AVSP Video"

    description = (script.get("full_text") or "").strip()
    if not description:
        segments = script.get("segments") or []
        parts = []
        for seg in segments:
            if isinstance(seg, dict):
                t = (seg.get("text") or seg.get("title") or "").strip()
                if t:
                    parts.append(t)
        description = " ".join(parts).strip()
    if not description:
        description = title
    if len(description) > max_description:
        description = description[: max_description - 1] + "…"

    lang = (script.get("language") or language or "en").strip() or "en"
    tags = _slug_tags(title)
    hashtags = [f"#{t.replace(' ', '')}" for t in tags[:5] if t]

    return SEOPackage(
        title=title[:100],
        description=description,
        tags=tags,
        hashtags=hashtags,
        thumbnail_path=thumbnail_path,
        category=category,
        language=lang,
        privacy=privacy,
        visibility=privacy,
    )
