"""Creative Edit Decision List (EDL) schema for M8."""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional
import json


@dataclass
class MediaCandidate:
    asset_id: str
    path: str
    source: str = "local"  # local | external
    quality_score: float = 0.0
    recommendation: str = "KEEP"  # KEEP | REVIEW | RETAKE
    type: str = "video"  # video | image
    duration: float = 0.0
    tags: List[str] = field(default_factory=list)
    selection_reason: str = ""


@dataclass
class PunchDecision:
    punch_asset: str
    start_time: float
    duration: float = 1.0
    reason: str = ""
    confidence: float = 0.7


@dataclass
class TransitionDecision:
    from_scene: str
    to_scene: str
    type: str = "hard_cut"  # hard_cut | fade | zoom | whip | slide | flash
    duration: float = 0.3
    reason: str = ""


@dataclass
class SfxDecision:
    asset: str
    start: float
    duration: float = 0.5
    volume: float = 0.4
    reason: str = ""


@dataclass
class EmojiDecision:
    emoji: str
    start: float
    duration: float = 1.5
    position: str = "center"
    scale: float = 1.0
    reason: str = ""


@dataclass
class BgmDecision:
    music_asset: str
    start: float = 0.0
    duration: float = 0.0
    base_volume: float = 0.08
    duck_volume: float = 0.03
    reason: str = ""


@dataclass
class CaptionStyle:
    emphasis: bool = False
    style: str = "default"
    position: str = "bottom"


@dataclass
class SceneDecision:
    scene_id: str
    start: float
    duration: float
    narration_segment: str = ""
    visual_goal: str = ""
    media_candidates: List[MediaCandidate] = field(default_factory=list)
    selected_media: Optional[MediaCandidate] = None
    selection_reason: str = ""
    cut_style: str = "standard"
    transition: Optional[TransitionDecision] = None
    punch: Optional[PunchDecision] = None
    sfx: Optional[SfxDecision] = None
    emoji: Optional[EmojiDecision] = None
    caption_style: CaptionStyle = field(default_factory=CaptionStyle)
    caption_emphasis: List[str] = field(default_factory=list)
    confidence: float = 0.8


@dataclass
class CreativeEDL:
    project_id: str
    format: str = "9:16"
    duration_target: float = 300.0
    topic: str = ""
    scenes: List[SceneDecision] = field(default_factory=list)
    bgm: Optional[BgmDecision] = None
    cta_scene_id: str = "scene_9"
    cta_duration: float = 5.0
    version: str = "1.0"
    confidence: float = 0.8
    notes: str = ""
    decision_source: str = "DETERMINISTIC_FALLBACK"  # AI | DETERMINISTIC_FALLBACK

    def to_dict(self) -> Dict[str, Any]:
        def _conv(obj: Any) -> Any:
            if hasattr(obj, "__dataclass_fields__"):
                return {k: _conv(v) for k, v in asdict(obj).items()}
            if isinstance(obj, list):
                return [_conv(x) for x in obj]
            return obj
        return _conv(self)

    def to_json(self, path: Optional[str] = None, indent: int = 2) -> str:
        data = json.dumps(self.to_dict(), indent=indent, ensure_ascii=False)
        if path:
            with open(path, "w", encoding="utf-8") as f:
                f.write(data)
        return data

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "CreativeEDL":
        scenes = []
        for s in d.get("scenes", []):
            candidates = [MediaCandidate(**c) for c in s.get("media_candidates", [])]
            sel = s.get("selected_media")
            selected = MediaCandidate(**sel) if sel else None
            punch = PunchDecision(**s["punch"]) if s.get("punch") else None
            trans = TransitionDecision(**s["transition"]) if s.get("transition") else None
            sfx = SfxDecision(**s["sfx"]) if s.get("sfx") else None
            emoji = EmojiDecision(**s["emoji"]) if s.get("emoji") else None
            cs = CaptionStyle(**s["caption_style"]) if s.get("caption_style") else CaptionStyle()
            scenes.append(SceneDecision(
                scene_id=s["scene_id"],
                start=float(s["start"]),
                duration=float(s["duration"]),
                narration_segment=s.get("narration_segment", ""),
                visual_goal=s.get("visual_goal", ""),
                media_candidates=candidates,
                selected_media=selected,
                selection_reason=s.get("selection_reason", ""),
                cut_style=s.get("cut_style", "standard"),
                transition=trans,
                punch=punch,
                sfx=sfx,
                emoji=emoji,
                caption_style=cs,
                caption_emphasis=s.get("caption_emphasis", []),
                confidence=float(s.get("confidence", 0.8)),
            ))
        bgm = BgmDecision(**d["bgm"]) if d.get("bgm") else None
        return cls(
            project_id=d["project_id"],
            format=d.get("format", "9:16"),
            duration_target=float(d.get("duration_target", 300)),
            topic=d.get("topic", ""),
            scenes=scenes,
            bgm=bgm,
            cta_scene_id=d.get("cta_scene_id", "scene_9"),
            cta_duration=float(d.get("cta_duration", 5.0)),
            version=d.get("version", "1.0"),
            confidence=float(d.get("confidence", 0.8)),
            notes=d.get("notes", ""),
            decision_source=d.get("decision_source", "DETERMINISTIC_FALLBACK"),
        )
