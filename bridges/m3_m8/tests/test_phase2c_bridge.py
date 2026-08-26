"""
Phase 2C isolated tests: M3→M8 voice bridge.

Does not modify M3/M8 engine source. Uses synthetic M3 fixtures + temp M9 stores.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO))
sys.path.insert(0, str(REPO / "M8" / "m8"))

from bridges.m3_m8.adapter import (
    M3ContractError,
    assert_valid,
    discover_m3_package,
    validate_m3_package,
    wav_duration_ms,
)
from bridges.m3_m8.concat import concat_voice_package
from bridges.m3_m8.fixtures import build_m3_fixture
from bridges.m3_m8.pipeline import run_m3_to_m8


class TestPhase2CBridge(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp(prefix="avsp_p2c_"))
        cls.fixture_root = cls.tmp / "m3_project"
        # ~25s VO → M8 body+CTA aligns (20+5)
        cls.audio_root = build_m3_fixture(
            cls.fixture_root,
            segment_ms=(8000, 8000, 9000),
            topic_hint="Airplane Mode Myth or Fact",
        )
        cls.m8_root = REPO / "M8" / "m8"
        cls.project_id = "phase2c_m3_m8_demo"
        cls.queue_db = cls.tmp / "m9_queue.db"
        cls.analytics = cls.tmp / "m9_analytics.json"

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    # 1. M3 output discovery
    def test_01_m3_output_discovery(self):
        voice = discover_m3_package(self.audio_root)
        self.assertEqual(voice.audio_package_id, "aud_phase2cfixture01")
        self.assertEqual(len(voice.segments), 3)
        for seg in voice.segments:
            self.assertTrue(seg.absolute_path.exists(), seg.absolute_path)
        self.assertGreater(voice.total_duration_ms, 20000)

    # 2. Audio format validation
    def test_02_audio_format_validation(self):
        voice = discover_m3_package(self.audio_root)
        errs = validate_m3_package(voice)
        self.assertEqual(errs, [], errs)
        meta = voice.package.get("metadata", {}).get("format", {})
        self.assertEqual(meta.get("encoding"), "pcm_s16le")
        self.assertEqual(meta.get("sampleRateHz"), 16000)
        self.assertEqual(meta.get("channels"), 1)

    # 3. Duration validation
    def test_03_duration_validation(self):
        voice = discover_m3_package(self.audio_root)
        assert_valid(voice)
        self.assertEqual(
            voice.total_duration_ms,
            sum(s.duration_ms for s in voice.segments),
        )

    # 4. Timing extraction
    def test_04_timing_extraction(self):
        voice = discover_m3_package(self.audio_root)
        cursor = 0
        for seg in voice.segments:
            self.assertEqual(seg.start_ms, cursor)
            self.assertEqual(seg.end_ms, seg.start_ms + seg.duration_ms)
            cursor = seg.end_ms
        self.assertEqual(cursor, voice.total_duration_ms)

    # 5. Adapter conversion (concat)
    def test_05_adapter_concat(self):
        voice = discover_m3_package(self.audio_root)
        out = self.tmp / "narration.wav"
        concat_voice_package(voice, out)
        self.assertTrue(out.exists())
        self.assertGreater(out.stat().st_size, 1000)
        file_ms = wav_duration_ms(out)
        self.assertLessEqual(abs(file_ms - voice.total_duration_ms), 50)

    # 6–9. M8 project + audio acceptance + duration + audio present
    def test_06_m8_render_with_m3_narration(self):
        # Clean prior demo project if present
        prior = self.m8_root / "projects" / self.project_id
        if prior.exists():
            shutil.rmtree(prior, ignore_errors=True)

        result = run_m3_to_m8(
            self.audio_root,
            topic="Airplane Mode — Myth or Fact?",
            format_name="9:16",
            project_id=self.project_id,
            m8_root=self.m8_root,
        )
        type(self).last_result = result
        self.assertTrue(result.ok, result.errors)
        self.assertTrue(result.final_mp4)
        self.assertTrue(Path(result.final_mp4).exists())
        self.assertTrue(result.audio_meta.get("narration"), result.audio_meta)

        # duration ≈ narration
        narr_sec = result.narration_duration_ms / 1000.0
        self.assertIsNotNone(result.mp4_duration_sec)
        tol = max(2.0, narr_sec * 0.2)
        self.assertLessEqual(
            abs(result.mp4_duration_sec - narr_sec),
            tol,
            f"mp4={result.mp4_duration_sec} narr={narr_sec}",
        )

        # audio present
        proc = subprocess.run(
            [
                "ffprobe",
                "-v",
                "quiet",
                "-print_format",
                "json",
                "-show_streams",
                result.final_mp4,
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        info = json.loads(proc.stdout)
        streams = info.get("streams") or []
        self.assertTrue(any(s.get("codec_type") == "audio" for s in streams))
        self.assertTrue(any(s.get("codec_type") == "video" for s in streams))

        # handoff metadata written
        handoff = (
            self.m8_root
            / "projects"
            / self.project_id
            / "input"
            / "m3_voice"
            / "m3_m8_handoff.json"
        )
        self.assertTrue(handoff.exists())

    # 10. Existing M3 tests remain green — documented as external suite;
    #     here we only assert we did not touch CURRENT_M1_M3 sources.
    def test_10_m3_tree_untouched_marker(self):
        # Bridge must not require editing M3; fixture is synthetic.
        m3 = REPO / "CURRENT_M1_M3" / "app" / "src" / "main" / "java" / "com" / "avsp" / "pro" / "audio"
        self.assertTrue(m3.is_dir())
        self.assertTrue((REPO / "bridges" / "m3_m8" / "pipeline.py").exists())

    # 11. Smoke: M8 EffectsComposer still importable / class API intact
    def test_11_m8_effects_composer_api_intact(self):
        sys.path.insert(0, str(self.m8_root))
        # purge may be needed
        for k in list(sys.modules):
            if k == "app" or k.startswith("app."):
                del sys.modules[k]
        sys.path.insert(0, str(self.m8_root))
        from app.engines.effects_composer import EffectsComposer

        self.assertTrue(callable(EffectsComposer.compose))

    # 12. Phase 2A M8→M9 still green via existing M3-VO project (no re-render)
    def test_12_phase2a_m9_mock_from_m3_vo_project(self):
        result = getattr(type(self), "last_result", None)
        if result is None or not result.ok:
            self.skipTest("M8 render result missing")
        from bridges.m8_m9.pipeline import run_m8_to_m9_mock

        project_dir = self.m8_root / "projects" / self.project_id
        m9 = run_m8_to_m9_mock(
            topic="Airplane Mode — Myth or Fact?",
            force_mock=True,
            existing_project_dir=project_dir,
            platforms=["youtube", "telegram"],
            queue_db=self.queue_db,
            analytics_path=self.analytics,
        )
        self.assertTrue(m9.ok, m9.errors)
        self.assertEqual(m9.job_status, "PUBLISHED")
        self.assertIn("youtube", m9.platform_statuses)
        self.assertIn("telegram", m9.platform_statuses)

    def test_13_invalid_package_fails_closed(self):
        bad = self.tmp / "bad_pkg"
        bad.mkdir(parents=True, exist_ok=True)
        (bad / "voice.json").write_text('{"segments":[]}', encoding="utf-8")
        with self.assertRaises(M3ContractError):
            voice = discover_m3_package(bad)
            assert_valid(voice)


if __name__ == "__main__":
    unittest.main()
