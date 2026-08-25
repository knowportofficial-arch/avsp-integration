"""
P4 — Real Pexels external media fallback with cache.
API key from env: PEXELS_API_KEY
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import time
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

from app.m8.errors import M8Error, ErrorCode

logger = logging.getLogger("avsp.m8.pexels")


class PexelsClient:
    BASE = "https://api.pexels.com/v1"

    def __init__(self, api_key: Optional[str] = None, cache_dir: Optional[Path] = None):
        self.api_key = api_key or os.environ.get("PEXELS_API_KEY") or os.environ.get("PEXELS_KEY")
        self.cache_dir = Path(cache_dir or "cache/pexels")
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        (self.cache_dir / "meta").mkdir(parents=True, exist_ok=True)
        (self.cache_dir / "files").mkdir(parents=True, exist_ok=True)

    @property
    def available(self) -> bool:
        return bool(self.api_key)

    def search_videos(
        self,
        query: str,
        orientation: str = "portrait",
        per_page: int = 8,
        min_duration: float = 3.0,
    ) -> List[Dict[str, Any]]:
        if not self.api_key:
            raise M8Error(
                ErrorCode.EXTERNAL_SOURCE_ERROR,
                "PEXELS_API_KEY not set",
                stage="pexels_search",
            )
        cache_key = hashlib.md5(f"v:{query}:{orientation}:{per_page}".encode()).hexdigest()
        cache_path = self.cache_dir / "meta" / f"{cache_key}.json"
        if cache_path.exists():
            try:
                return json.loads(cache_path.read_text(encoding="utf-8"))
            except Exception:
                pass

        params = urlencode({
            "query": query,
            "orientation": orientation,
            "per_page": per_page,
        })
        url = f"{self.BASE}/videos/search?{params}"
        req = Request(url, headers={"Authorization": self.api_key})
        try:
            with urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except (URLError, HTTPError) as e:
            raise M8Error(
                ErrorCode.EXTERNAL_SOURCE_ERROR,
                f"Pexels search failed: {e}",
                stage="pexels_search",
            ) from e

        results = []
        for v in data.get("videos", []):
            files = v.get("video_files") or []
            # Prefer HD portrait-ish
            files_sorted = sorted(files, key=lambda f: abs((f.get("width") or 0) - 1080))
            best = files_sorted[0] if files_sorted else None
            if not best or not best.get("link"):
                continue
            dur = float(v.get("duration") or 0)
            if dur and dur < min_duration:
                continue
            results.append({
                "id": str(v.get("id")),
                "url": best["link"],
                "width": best.get("width"),
                "height": best.get("height"),
                "duration": dur,
                "query": query,
                "source": "pexels",
                "type": "video",
            })
        cache_path.write_text(json.dumps(results, indent=2), encoding="utf-8")
        return results

    def search_photos(
        self,
        query: str,
        orientation: str = "portrait",
        per_page: int = 8,
    ) -> List[Dict[str, Any]]:
        if not self.api_key:
            raise M8Error(
                ErrorCode.EXTERNAL_SOURCE_ERROR,
                "PEXELS_API_KEY not set",
                stage="pexels_search",
            )
        cache_key = hashlib.md5(f"p:{query}:{orientation}:{per_page}".encode()).hexdigest()
        cache_path = self.cache_dir / "meta" / f"{cache_key}.json"
        if cache_path.exists():
            try:
                return json.loads(cache_path.read_text(encoding="utf-8"))
            except Exception:
                pass

        params = urlencode({
            "query": query,
            "orientation": orientation,
            "per_page": per_page,
        })
        url = f"{self.BASE}/search?{params}"
        req = Request(url, headers={"Authorization": self.api_key})
        try:
            with urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except (URLError, HTTPError) as e:
            raise M8Error(
                ErrorCode.EXTERNAL_SOURCE_ERROR,
                f"Pexels photo search failed: {e}",
                stage="pexels_search",
            ) from e

        results = []
        for p in data.get("photos", []):
            src = (p.get("src") or {})
            link = src.get("large") or src.get("original") or src.get("medium")
            if not link:
                continue
            results.append({
                "id": str(p.get("id")),
                "url": link,
                "width": p.get("width"),
                "height": p.get("height"),
                "duration": 0,
                "query": query,
                "source": "pexels",
                "type": "photo",
            })
        cache_path.write_text(json.dumps(results, indent=2), encoding="utf-8")
        return results

    def download(self, item: Dict[str, Any]) -> Path:
        """Download and cache asset. Returns local path."""
        url = item["url"]
        ext = ".mp4" if item.get("type") == "video" else ".jpg"
        fname = f"{item.get('id', 'x')}_{hashlib.md5(url.encode()).hexdigest()[:10]}{ext}"
        dest = self.cache_dir / "files" / fname
        if dest.exists() and dest.stat().st_size > 1000:
            return dest
        req = Request(url, headers={"User-Agent": "AVSP-M8/2.0"})
        try:
            with urlopen(req, timeout=120) as resp:
                dest.write_bytes(resp.read())
        except (URLError, HTTPError) as e:
            raise M8Error(
                ErrorCode.EXTERNAL_SOURCE_ERROR,
                f"Pexels download failed: {e}",
                stage="pexels_download",
            ) from e
        return dest

    def fetch_for_scene(
        self,
        query: str,
        orientation: str = "portrait",
        prefer_video: bool = True,
    ) -> Optional[Dict[str, Any]]:
        """Search + download best candidate. Returns media candidate dict or None."""
        if not self.available:
            return None
        try:
            items = self.search_videos(query, orientation=orientation) if prefer_video else []
            if not items:
                items = self.search_photos(query, orientation=orientation)
            if not items:
                return None
            best = items[0]
            path = self.download(best)
            return {
                "asset_id": f"pexels_{best['id']}",
                "path": str(path),
                "source": "pexels",
                "quality_score": 0.8,
                "recommendation": "KEEP",
                "type": "video" if best.get("type") == "video" else "image",
                "duration": float(best.get("duration") or 0),
                "tags": [query],
                "selection_reason": f"Pexels fallback for '{query}'",
            }
        except M8Error:
            logger.exception("Pexels fetch failed")
            return None
        except Exception as e:
            logger.warning("Pexels unexpected error: %s", e)
            return None
