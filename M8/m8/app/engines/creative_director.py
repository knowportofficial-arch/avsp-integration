"""
P2 — AI Creative Director.
Produces structured Creative EDL. Uses deterministic logic when AI unavailable.
"""
from __future__ import annotations

import json
import logging
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from app.schemas.edl import (
    CreativeEDL, SceneDecision, MediaCandidate, PunchDecision,
    TransitionDecision, SfxDecision, EmojiDecision, BgmDecision, CaptionStyle,
)
from app.adapters.m7_adapter import M7Adapter, MediaAsset
from app.m8.errors import M8Error, ErrorCode

logger = logging.getLogger("avsp.m8.creative_director")

# Deterministic scene template for ~5 min educational/local topic
DEFAULT_SCENE_PLAN = [
    {"id": "scene_1", "title": "Hook / Title", "ratio": 0.05, "goal": "establishing wide shot"},
    {"id": "scene_2", "title": "Context", "ratio": 0.12, "goal": "location context"},
    {"id": "scene_3", "title": "History / Background", "ratio": 0.15, "goal": "historical or background visuals"},
    {"id": "scene_4", "title": "Main Feature 1", "ratio": 0.15, "goal": "primary subject detail"},
    {"id": "scene_5", "title": "Main Feature 2", "ratio": 0.15, "goal": "secondary feature"},
    {"id": "scene_6", "title": "Details / Atmosphere", "ratio": 0.12, "goal": "atmosphere and details"},
    {"id": "scene_7", "title": "People / Activity", "ratio": 0.10, "goal": "activity or people"},
    {"id": "scene_8", "title": "Summary", "ratio": 0.08, "goal": "summary visuals"},
    {"id": "scene_9", "title": "CTA", "ratio": 0.0, "goal": "call to action", "fixed_duration": 5.0},
]


def parse_duration(s: str) -> float:
    s = str(s).strip().lower()
    if s.endswith("m"):
        return float(s[:-1]) * 60.0
    if s.endswith("s"):
        return float(s[:-1])
    try:
        return float(s)
    except ValueError:
        return 300.0


class CreativeDirector:
    def __init__(
        self,
        m7: Optional[M7Adapter] = None,
        assets_root: Optional[Path] = None,
        use_ai: bool = False,
    ):
        self.m7 = m7 or M7Adapter()
        self.assets_root = Path(assets_root or "assets")
        self.use_ai = use_ai
        self.punch_lib = self._load_punch_library()
        self.sfx_lib = self._scan_dir(self.assets_root / "sfx", {".mp3", ".wav", ".aac", ".m4a"})
        self.music_lib = self._scan_dir(self.assets_root / "music", {".mp3", ".wav", ".aac", ".m4a"})

    def _scan_dir(self, d: Path, exts: set) -> List[str]:
        if not d.exists():
            return []
        return [str(p) for p in d.rglob("*") if p.suffix.lower() in exts]

    def _load_punch_library(self) -> List[Dict[str, Any]]:
        p = self.assets_root / "punch_library" / "punch_library.json"
        if p.exists():
            try:
                data = json.loads(p.read_text(encoding="utf-8"))
                return data if isinstance(data, list) else data.get("clips", [])
            except Exception:
                pass
        clips_dir = self.assets_root / "punch_library" / "clips"
        if clips_dir.exists():
            return [{"path": str(c), "duration": 1.0} for c in clips_dir.glob("*.mp4")]
        return []

    def generate_script(self, topic: str, duration_sec: float) -> Dict[str, Any]:
        """Deterministic script skeleton (AI can replace later)."""
        segments = []
        n = max(5, int(duration_sec / 40))
        words_per = max(40, int(duration_sec * 2.2 / n))  # ~130 wpm approx
        titles = [s["title"] for s in DEFAULT_SCENE_PLAN if s["id"] != "scene_9"]
        for i, title in enumerate(titles[:n]):
            text = (
                f"{topic}. {title}. "
                f"This segment covers important aspects of {topic} "
                f"with clear narration for scene {i+1}."
            )
            segments.append({"index": i, "title": title, "text": text})
        segments.append({
            "index": len(segments),
            "title": "CTA",
            "text": f"Thanks for watching. Follow for more about {topic} and local places.",
        })
        return {
            "topic": topic,
            "duration_target": duration_sec,
            "language": "en",
            "segments": segments,
            "full_text": " ".join(s["text"] for s in segments),
        }

    def generate_scene_plan(self, topic: str, duration_sec: float, script: Dict[str, Any]) -> List[Dict[str, Any]]:
        cta_dur = 5.0
        body = max(10.0, duration_sec - cta_dur)
        plan = []
        t = 0.0
        segs = script.get("segments", [])
        body_scenes = [s for s in DEFAULT_SCENE_PLAN if s["id"] != "scene_9"]
        ratios = [s["ratio"] for s in body_scenes]
        total_r = sum(ratios) or 1.0
        for i, sc in enumerate(body_scenes):
            dur = body * (sc["ratio"] / total_r)
            narr = segs[i]["text"] if i < len(segs) else f"{topic} — {sc['title']}"
            plan.append({
                "scene_id": sc["id"],
                "title": sc["title"],
                "start": round(t, 3),
                "duration": round(dur, 3),
                "narration_segment": narr,
                "visual_goal": sc["goal"],
            })
            t += dur
        plan.append({
            "scene_id": "scene_9",
            "title": "CTA",
            "start": round(t, 3),
            "duration": cta_dur,
            "narration_segment": segs[-1]["text"] if segs else "Thanks for watching.",
            "visual_goal": "call to action",
        })
        return plan

    def _pick_media(self, goal: str, format_name: str) -> Optional[MediaCandidate]:
        orient = "PORTRAIT" if format_name in ("9:16", "shorts") else "LANDSCAPE"
        asset = self.m7.select_for_scene(goal, orientation=orient)
        if not asset:
            return None
        return MediaCandidate(
            asset_id=asset.asset_id,
            path=asset.path,
            source="local",
            quality_score=asset.quality_score,
            recommendation=asset.recommendation,
            type="video" if "VIDEO" in asset.type.upper() else "image",
            duration=asset.duration,
            tags=asset.tags,
            selection_reason=f"M7 preferred {asset.recommendation} score={asset.quality_score:.2f}",
        )

    def build_edl(
        self,
        project_id: str,
        topic: str,
        duration: str = "5m",
        format_name: str = "9:16",
        script: Optional[Dict[str, Any]] = None,
    ) -> CreativeEDL:
        duration_sec = parse_duration(duration)
        script = script or self.generate_script(topic, duration_sec)
        plan = self.generate_scene_plan(topic, duration_sec, script)

        scenes: List[SceneDecision] = []
        for i, p in enumerate(plan):
            media = self._pick_media(p["visual_goal"], format_name)
            candidates = [media] if media else []
            punch = None
            sfx = None
            emoji = None
            trans = None

            # Sparse effects — only when justified
            is_cta = p["scene_id"] == "scene_9"
            if not is_cta and i > 0 and i % 3 == 0 and self.punch_lib:
                punch = PunchDecision(
                    punch_asset=self.punch_lib[i % len(self.punch_lib)].get("path", ""),
                    start_time=p["start"] + min(1.0, p["duration"] * 0.3),
                    duration=0.9,
                    reason="emphasis on key beat",
                    confidence=0.65,
                )
            if not is_cta and i > 0 and i % 2 == 0 and self.sfx_lib:
                sfx = SfxDecision(
                    asset=self.sfx_lib[i % len(self.sfx_lib)],
                    start=p["start"],
                    duration=0.4,
                    volume=0.35,
                    reason="scene open whoosh",
                )
            if i == 0:
                emoji = EmojiDecision(emoji="📍", start=p["start"] + 0.5, duration=2.0, reason="location marker")
            if i > 0 and not is_cta:
                # Always include at least one real fade (i==2); others hard_cut for stability
                ttype = "fade" if i in (2, 5) else "hard_cut"
                trans = TransitionDecision(
                    from_scene=plan[i - 1]["scene_id"],
                    to_scene=p["scene_id"],
                    type=ttype,
                    duration=0.4 if ttype == "fade" else 0.0,
                    reason="creative fade" if ttype == "fade" else "clean cut",
                )

            scenes.append(SceneDecision(
                scene_id=p["scene_id"],
                start=p["start"],
                duration=p["duration"],
                narration_segment=p["narration_segment"],
                visual_goal=p["visual_goal"],
                media_candidates=candidates,
                selected_media=media,
                selection_reason=media.selection_reason if media else "no local media; external fallback later",
                cut_style="standard",
                transition=trans,
                punch=punch,
                sfx=sfx,
                emoji=emoji,
                caption_style=CaptionStyle(emphasis=is_cta),
                caption_emphasis=[],
                confidence=0.75 if media else 0.5,
            ))

        bgm = None
        if self.music_lib:
            bgm = BgmDecision(
                music_asset=self.music_lib[0],
                start=0.0,
                duration=duration_sec,
                base_volume=0.08,
                duck_volume=0.03,
                reason="local BGM under narration",
            )

        decision_source = "DETERMINISTIC_FALLBACK"
        notes = "Deterministic Creative Director"
        if self.use_ai:
            ai_edl = self._try_ai_edl(project_id, topic, duration_sec, format_name, script, plan)
            if ai_edl is not None:
                ai_edl.decision_source = "AI"
                ai_edl.notes = "AI Creative Director"
                return ai_edl
            notes = "AI requested but unavailable/invalid — DETERMINISTIC_FALLBACK"
        edl = CreativeEDL(
            project_id=project_id,
            format=format_name,
            duration_target=duration_sec,
            topic=topic,
            scenes=scenes,
            bgm=bgm,
            cta_scene_id="scene_9",
            cta_duration=5.0,
            confidence=0.72,
            notes=notes,
            decision_source=decision_source,
        )
        return edl

    def _try_ai_edl(self, project_id, topic, duration_sec, format_name, script, plan):
        """Attempt Gemini structured EDL. Returns CreativeEDL or None."""
        import os
        import json
        import urllib.request
        keys = []
        for env in ("GEMINI_API_KEY", "GOOGLE_API_KEY", "GEMINI_API_KEYS"):
            v = os.environ.get(env)
            if v:
                keys.extend([k.strip() for k in v.split(",") if k.strip()])
        if not keys:
            return None
        prompt = {
            "role": "You are AVSP Creative Director. Return ONLY valid JSON matching the EDL schema.",
            "topic": topic,
            "duration_sec": duration_sec,
            "format": format_name,
            "scene_plan": plan,
            "rules": [
                "CTA scene_9 duration must be exactly 5.0",
                "Prefer sparse effects with reason",
                "punch duration 0.7-1.2",
                "bgm base_volume ~0.08",
            ],
        }
        body = {
            "contents": [{"parts": [{"text": json.dumps(prompt) + "\nReturn CreativeEDL JSON with scenes array."}]}],
            "generationConfig": {"temperature": 0.4, "responseMimeType": "application/json"},
        }
        for key in keys[:3]:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={key}"
            try:
                req = urllib.request.Request(
                    url,
                    data=json.dumps(body).encode("utf-8"),
                    headers={"Content-Type": "application/json"},
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=45) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                text_out = data["candidates"][0]["content"]["parts"][0]["text"]
                start = text_out.find("{")
                end = text_out.rfind("}") + 1
                if start < 0:
                    continue
                raw = json.loads(text_out[start:end])
                raw.setdefault("project_id", project_id)
                raw.setdefault("topic", topic)
                raw.setdefault("format", format_name)
                raw.setdefault("duration_target", duration_sec)
                raw.setdefault("cta_duration", 5.0)
                from app.schemas.edl import CreativeEDL
                edl = CreativeEDL.from_dict(raw)
                for sc in edl.scenes:
                    if sc.scene_id == "scene_9":
                        sc.duration = 5.0
                edl.cta_duration = 5.0
                edl.notes = "AI"
                return edl
            except Exception:
                continue
        return None

