"""
M8 V2 QA Patch tests — FAIL if effects are only planned, not rendered/verified.
"""
from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path = [p for p in sys.path if "vendor/m4" not in str(p)]

from app.engines.effects_composer import EffectsComposer
from app.engines.visual_verifier import VisualVerifier, extract_frames, frame_has_visible_content
from app.m8.errors import M8Error
from app.schemas.timeline import (
    Timeline, VisualEvent, OverlayEvent, SfxEvent, MusicEvent,
    CaptionEvent, TransitionEvent, CtaEvent,
)


def _ffprobe(path: Path):
    p = subprocess.run(
        ["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", str(path)],
        capture_output=True, text=True, timeout=30,
    )
    return json.loads(p.stdout) if p.returncode == 0 else None


class TestQAPatch(unittest.TestCase):
    def setUp(self):
        self.root = ROOT
        self.out_dir = self.root / "projects" / "_qa_patch"
        self.out_dir.mkdir(parents=True, exist_ok=True)

    def test_transition_fade_must_render(self):
        """Requested fade MUST appear; silent hard-cut fallback is forbidden."""
        # Two distinctly different colored clips so fade is meaningful
        tl = Timeline(
            project_id="qa_fade",
            format="9:16", width=1080, height=1920, fps=30, duration=4.0,
            visual=[
                VisualEvent(start=0, end=2.0, path="", media_type="color"),
                VisualEvent(start=2.0, end=4.0, path="", media_type="color"),
            ],
            transitions=[
                TransitionEvent(at=2.0, type="fade", duration=0.4, from_scene="a", to_scene="b", reason="qa"),
            ],
            captions=[CaptionEvent(start=0, end=2, text="Part A"), CaptionEvent(start=2, end=4, text="Part B")],
        )
        out = self.out_dir / "fade_transition.mp4"
        # Use colored clips: make_visual_clip with empty path makes black;
        # override by pre-generating blue/green clips
        work = self.out_dir / "work_fade"
        work.mkdir(exist_ok=True)
        blue = work / "blue.mp4"
        green = work / "green.mp4"
        for color, path, dur in (("blue", blue, 2.0), ("green", green, 2.0)):
            subprocess.run([
                "ffmpeg", "-y", "-f", "lavfi",
                "-i", f"color=c={color}:s=1080x1920:d={dur}:r=30",
                "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
                str(path),
            ], capture_output=True, timeout=30, check=True)
        tl.visual[0].path = str(blue)
        tl.visual[0].media_type = "video"
        tl.visual[1].path = str(green)
        tl.visual[1].media_type = "video"

        composer = EffectsComposer(work_dir=work / "fx")
        result = composer.compose(tl, out)
        self.assertEqual(result["status"], "success")
        self.assertTrue(out.exists())
        rendered = result["effects"].get("transitions_rendered") or []
        self.assertTrue(any(str(t.get("type", "")).lower() == "fade" for t in rendered),
                        f"fade not in transitions_rendered: {rendered}")
        # Must not have silently hard-cut only when fade was requested
        info = _ffprobe(out)
        self.assertIsNotNone(info)
        dur = float(info["format"]["duration"])
        # fade shortens total slightly (overlap) vs pure concat 4.0
        self.assertLess(dur, 4.0, "fade should reduce total duration via overlap")
        self.assertGreater(dur, 3.0)
        print("TEST transition fade actual render — PASS")

    def test_transition_failure_must_raise(self):
        """If clips are too short for xfade, must FAIL (not hard-cut silently)."""
        tl = Timeline(
            project_id="qa_fail",
            format="9:16", width=1080, height=1920, fps=30, duration=0.5,
            visual=[
                VisualEvent(start=0, end=0.2, path="", media_type="color"),
                VisualEvent(start=0.2, end=0.4, path="", media_type="color"),
            ],
            transitions=[
                TransitionEvent(at=0.2, type="fade", duration=0.4, from_scene="a", to_scene="b"),
            ],
        )
        out = self.out_dir / "fade_should_fail.mp4"
        composer = EffectsComposer(work_dir=self.out_dir / "work_fail")
        with self.assertRaises(M8Error) as ctx:
            composer.compose(tl, out)
        self.assertEqual(ctx.exception.stage, "transition_render")
        print("TEST transition failure raises — PASS")

    def test_caption_visual_in_frames(self):
        """Captions must be visible in extracted frames of final MP4."""
        tl = Timeline(
            project_id="qa_cap",
            format="9:16", width=1080, height=1920, fps=30, duration=3.0,
            visual=[VisualEvent(start=0, end=3, path="", media_type="color")],
            captions=[CaptionEvent(start=0.2, end=2.8, text="BELDA RAILWAY STATION QA")],
        )
        out = self.out_dir / "captions_visual.mp4"
        result = EffectsComposer(work_dir=self.out_dir / "work_cap").compose(tl, out)
        self.assertEqual(result["status"], "success")
        frames = extract_frames(out, [1.0, 1.5], self.out_dir / "cap_frames")
        self.assertGreaterEqual(len(frames), 1)
        # Black base + white burned text → non-trivial JPEG and non-zero Y
        for f in frames:
            self.assertTrue(frame_has_visible_content(f), f"frame lacks visible content: {f}")
            self.assertGreater(f.stat().st_size, 3000, "caption frame too small / likely empty")
        v = VisualVerifier().verify(out, timeline=tl, render_meta=result, work_dir=self.out_dir / "cap_v")
        self.assertTrue(v["matrix"]["caption"]["PLANNED"])
        self.assertTrue(v["matrix"]["caption"]["RENDERED"])
        self.assertTrue(v["matrix"]["caption"]["VERIFIED"])
        print("TEST caption visual verification — PASS")

    def test_punch_sfx_emoji_bgm_verified(self):
        punch = self.root / "assets" / "punch_library" / "clips" / "punch_01.mp4"
        sfx = next((self.root / "assets" / "sfx").glob("*.m4a"))
        music = self.root / "assets" / "music" / "bgm_soft.m4a"
        self.assertTrue(punch.exists() and sfx.exists() and music.exists())
        tl = Timeline(
            project_id="qa_fx",
            format="9:16", width=1080, height=1920, fps=30, duration=4.0,
            visual=[VisualEvent(start=0, end=4, path="", media_type="color")],
            overlays=[
                OverlayEvent(start=0.5, end=1.4, kind="punch", path=str(punch), scale=0.4),
                OverlayEvent(start=1.5, end=3.0, kind="emoji", text="📍", position="center"),
            ],
            sfx=[SfxEvent(start=0.5, end=1.0, path=str(sfx), volume=0.4)],
            music=MusicEvent(
                path=str(music), start=0, duration=4.0, base_volume=0.08, duck_volume=0.03,
                duck_regions=[{"start": 0.5, "end": 3.5}],
            ),
            captions=[CaptionEvent(start=0.3, end=3.7, text="Effects QA")],
        )
        out = self.out_dir / "all_effects.mp4"
        result = EffectsComposer(work_dir=self.out_dir / "work_all").compose(tl, out)
        self.assertEqual(result["status"], "success")
        fx = result["effects"]
        am = result["audio_meta"]
        self.assertGreaterEqual(fx["punch_count"], 1)
        self.assertGreaterEqual(fx["emoji_count"], 1)
        self.assertGreaterEqual(fx["sfx_count"], 1)
        self.assertTrue(fx["bgm"])
        self.assertTrue(am.get("ducking_applied"))
        v = VisualVerifier().verify(out, timeline=tl, render_meta=result, work_dir=self.out_dir / "all_v")
        for key in ("punch", "emoji", "sfx", "caption", "bgm", "voice_ducking"):
            self.assertTrue(v["matrix"][key]["PLANNED"], key)
            self.assertTrue(v["matrix"][key]["RENDERED"], key)
            self.assertTrue(v["matrix"][key]["VERIFIED"], f"{key} not VERIFIED: {v['matrix'][key]}")
        self.assertEqual(v["status"], "PASS")
        print("TEST all effects verified — PASS")

    def test_ai_decision_source_honesty(self):
        """Must not claim AI when keys missing."""
        import os
        from app.engines.creative_director import CreativeDirector
        from app.adapters.m7_adapter import M7Adapter
        d = CreativeDirector(
            m7=M7Adapter(snapshot_path=self.root / "assets" / "m7_snapshot.json"),
            assets_root=self.root / "assets",
            use_ai=True,
        )
        edl = d.build_edl("ai_h", "Belda", "20s", "9:16")
        has_key = bool(os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY"))
        if has_key:
            self.assertIn("AI", (edl.notes or "").upper())
            print("TEST AI decision — PASS (live AI)")
        else:
            self.assertIn("FALLBACK", (edl.notes or "").upper())
            print("TEST AI decision — PASS (honest FALLBACK, no key)")


if __name__ == "__main__":
    unittest.main(verbosity=2)
