"""Caption burn must succeed (drawtext OR ass_subtitles) or hard-fail — never silent PASS."""
from __future__ import annotations
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path = [x for x in sys.path if "vendor/m4" not in str(x)]

from app.engines.effects_composer import EffectsComposer
from app.m8.errors import M8Error, ErrorCode
from app.schemas.timeline import Timeline, VisualEvent, CaptionEvent

VALID_METHODS = ("drawtext", "ass_subtitles")


class TestCaptionBurn(unittest.TestCase):
    def test_caption_burn_succeeds(self):
        """Either drawtext or ass_subtitles is a valid successful burn method."""
        out_dir = ROOT / "projects" / "_caption_qa"
        out_dir.mkdir(parents=True, exist_ok=True)
        tl = Timeline(
            project_id="cap_ok",
            format="9:16", width=1080, height=1920, fps=30, duration=3.0,
            visual=[VisualEvent(start=0, end=3, path="", media_type="color")],
            captions=[CaptionEvent(start=0.2, end=2.8, text="BELDA RAILWAY STATION")],
        )
        out = out_dir / "captions_ok.mp4"
        result = EffectsComposer(work_dir=out_dir / "work").compose(tl, out)
        self.assertEqual(result["status"], "success")
        self.assertTrue(result["effects"]["caption_burn_ok"])
        self.assertIn(
            result["effects"]["caption_burn_method"],
            VALID_METHODS,
            msg=f"unexpected method: {result['effects'].get('caption_burn_method')}",
        )
        self.assertGreater(result["effects"]["caption_count"], 0)
        self.assertTrue(out.exists() and out.stat().st_size > 1000)
        print(f"TEST caption burn success — PASS (method={result['effects']['caption_burn_method']})")

    def test_no_silent_skip_when_captions_planned(self):
        """Composer must set caption_burn_ok explicitly; no silent uncaptioned copy."""
        out_dir = ROOT / "projects" / "_caption_qa"
        tl = Timeline(
            project_id="cap_flag",
            format="9:16", width=640, height=360, fps=24, duration=2.0,
            visual=[VisualEvent(start=0, end=2, path="", media_type="color")],
            captions=[CaptionEvent(start=0.1, end=1.9, text="QA CAPTION")],
        )
        out = out_dir / "captions_flag.mp4"
        result = EffectsComposer(work_dir=out_dir / "work2").compose(tl, out)
        self.assertIn("caption_burn_ok", result["effects"])
        self.assertTrue(result["effects"]["caption_burn_ok"])
        self.assertIn(result["effects"]["caption_burn_method"], VALID_METHODS)
        # Source must raise CAPTION_ERROR on total failure (not silent copy)
        src = (ROOT / "app" / "engines" / "effects_composer.py").read_text()
        self.assertIn("CAPTION_ERROR", src)
        self.assertIn("Caption burn failed on all methods", src)
        self.assertNotIn('logger.warning("subtitle burn failed', src)
        print("TEST caption_burn_ok flag + hard-fail present — PASS")


if __name__ == "__main__":
    unittest.main(verbosity=2)
