"""Canonical timeline schema for M8 → M4 handoff."""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional
import json


@dataclass
class NarrationEvent:
    start: float
    end: float
    text: str
    audio_path: Optional[str] = None


@dataclass
class VisualEvent:
    start: float
    end: float
    path: str
    media_type: str = "video"  # video | image | color
    fit_mode: str = "cover"
    source: str = "local"
    asset_id: str = ""
    trim_start: float = 0.0
    trim_end: Optional[float] = None


@dataclass
class OverlayEvent:
    start: float
    end: float
    kind: str  # punch | emoji | text
    path: Optional[str] = None
    text: Optional[str] = None
    position: str = "center"
    scale: float = 1.0
    reason: str = ""


@dataclass
class TransitionEvent:
    at: float
    type: str
    duration: float
    from_scene: str
    to_scene: str
    reason: str = ""


@dataclass
class SfxEvent:
    start: float
    end: float
    path: str
    volume: float = 0.4
    reason: str = ""


@dataclass
class MusicEvent:
    path: str
    start: float = 0.0
    duration: float = 0.0
    base_volume: float = 0.08
    duck_volume: float = 0.03
    duck_regions: List[Dict[str, float]] = field(default_factory=list)


@dataclass
class CaptionEvent:
    start: float
    end: float
    text: str
    style: str = "default"
    emphasis: bool = False
    position: str = "bottom"


@dataclass
class CtaEvent:
    start: float
    end: float
    text: str = "Subscribe / Follow"
    duration: float = 5.0


@dataclass
class Timeline:
    project_id: str
    format: str = "9:16"
    width: int = 1080
    height: int = 1920
    fps: float = 30.0
    duration: float = 0.0
    narration: List[NarrationEvent] = field(default_factory=list)
    visual: List[VisualEvent] = field(default_factory=list)
    overlays: List[OverlayEvent] = field(default_factory=list)
    transitions: List[TransitionEvent] = field(default_factory=list)
    sfx: List[SfxEvent] = field(default_factory=list)
    music: Optional[MusicEvent] = None
    captions: List[CaptionEvent] = field(default_factory=list)
    cta: Optional[CtaEvent] = None
    version: str = "1.0"

    def to_dict(self) -> Dict[str, Any]:
        def _c(o: Any) -> Any:
            if hasattr(o, "__dataclass_fields__"):
                return {k: _c(v) for k, v in asdict(o).items()}
            if isinstance(o, list):
                return [_c(x) for x in o]
            return o
        return _c(self)

    def to_json(self, path: Optional[str] = None, indent: int = 2) -> str:
        data = json.dumps(self.to_dict(), indent=indent, ensure_ascii=False)
        if path:
            with open(path, "w", encoding="utf-8") as f:
                f.write(data)
        return data

    def validate_cta(self, tolerance: float = 0.15) -> bool:
        if not self.cta:
            return False
        actual = self.cta.end - self.cta.start
        return abs(actual - 5.0) <= tolerance
