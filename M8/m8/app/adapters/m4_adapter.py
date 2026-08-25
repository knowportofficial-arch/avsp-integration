"""
M4 Video Engine adapter — load via importlib to avoid app package shadowing.
"""
from __future__ import annotations

import importlib.util
import sys
import types
from pathlib import Path
from typing import Any, Dict, List, Optional, Callable

from app.m8.errors import M8Error, ErrorCode
from app.schemas.timeline import Timeline


def _load_video_engine_module(m4_root: Path):
    ve_path = m4_root / "app" / "engines" / "video_engine.py"
    if not ve_path.exists():
        raise M8Error(ErrorCode.M4_ADAPTER_ERROR, f"Missing {ve_path}", stage="m4_load")

    m4_str = str(m4_root.resolve())
    if m4_str not in sys.path:
        sys.path.append(m4_str)

    pkg_name = "m4_frozen"
    if pkg_name not in sys.modules:
        pkg = types.ModuleType(pkg_name)
        pkg.__path__ = [str(m4_root / "app")]
        sys.modules[pkg_name] = pkg
        eng = types.ModuleType(pkg_name + ".engines")
        eng.__path__ = [str(m4_root / "app" / "engines")]
        sys.modules[pkg_name + ".engines"] = eng

    mod_name = pkg_name + ".engines.video_engine"
    if mod_name in sys.modules:
        return sys.modules[mod_name]

    spec = importlib.util.spec_from_file_location(mod_name, ve_path)
    if spec is None or spec.loader is None:
        raise M8Error(ErrorCode.M4_ADAPTER_ERROR, "spec_from_file_location failed", stage="m4_load")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[mod_name] = mod
    try:
        spec.loader.exec_module(mod)
    except Exception as e:
        raise M8Error(ErrorCode.M4_ADAPTER_ERROR, f"exec_module failed: {e}", stage="m4_load") from e
    return mod


class M4Adapter:
    def __init__(self, m4_root: Optional[Path] = None):
        self.m4_root = Path(
            m4_root
            or Path(__file__).resolve().parents[2] / "vendor" / "m4"
        )
        if not (self.m4_root / "app" / "engines" / "video_engine.py").exists():
            raise M8Error(ErrorCode.M4_ADAPTER_ERROR, f"M4 not found at {self.m4_root}", stage="m4_adapter_init")
        self._mod = None

    def _engine_class(self):
        if self._mod is None:
            self._mod = _load_video_engine_module(self.m4_root)
        return self._mod.VideoEngine

    def timeline_to_m4_media(self, timeline: Timeline) -> List[Dict[str, Any]]:
        media: List[Dict[str, Any]] = []
        for v in timeline.visual:
            if not v.path or v.media_type == "color":
                media.append({"color": "black", "duration": max(0.1, v.end - v.start)})
            else:
                item: Dict[str, Any] = {"path": v.path, "duration": max(0.1, v.end - v.start)}
                if v.trim_start:
                    item["start"] = v.trim_start
                if v.trim_end is not None:
                    item["end"] = v.trim_end
                media.append(item)
        return media

    def timeline_to_subtitles(self, timeline: Timeline) -> List[Dict[str, Any]]:
        return [{"start": c.start, "end": c.end, "text": c.text} for c in timeline.captions]

    def render(
        self,
        timeline: Timeline,
        audio_path: Optional[str] = None,
        output_path: Optional[str] = None,
        project_id: Optional[str] = None,
        progress_callback: Optional[Callable[[float, str], None]] = None,
        force_duration: Optional[float] = None,
    ) -> Dict[str, Any]:
        VE = self._engine_class()
        template = "default_shorts" if timeline.format in ("9:16", "shorts") else "default_landscape"
        media = self.timeline_to_m4_media(timeline)
        subtitles = self.timeline_to_subtitles(timeline)
        music_path = timeline.music.path if timeline.music else None
        if not audio_path and timeline.narration:
            for n in timeline.narration:
                if n.audio_path and Path(n.audio_path).exists():
                    audio_path = n.audio_path
                    break
        engine = VE(root=str(self.m4_root), progress_callback=progress_callback)
        result = engine.render(
            script=None,
            audio_path=audio_path,
            media=media if media else [{"color": "black", "duration": max(1.0, timeline.duration or 5.0)}],
            template=template,
            project_id=project_id or timeline.project_id,
            output_path=output_path,
            subtitles=subtitles or None,
            music_path=music_path if music_path and Path(str(music_path)).exists() else None,
            force_duration=force_duration or (timeline.duration if timeline.duration > 0 else None),
        )
        return result.to_dict() if hasattr(result, "to_dict") else dict(result)
