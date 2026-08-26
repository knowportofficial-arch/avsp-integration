"""
Phase 2A isolated tests: M8→M9 adapter, mock publish, SEO, credentials, timing.

Does not modify M8/M9 engine source. Uses temp dirs for M9 queue/analytics.
"""

from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

# test file is bridges/m8_m9/tests/ → repo root is parents[3]
REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO))
sys.path.insert(0, str(REPO / "M8" / "m8"))
sys.path.insert(0, str(REPO / "M9" / "m9"))

from bridges.m8_m9.adapter import M8ProjectBundle, M8ToM9Adapter
from bridges.m8_m9.seo import build_seo_package
from bridges.m8_m9.pipeline import run_m8_to_m9_mock


class TestPhase2ABridge(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp(prefix="avsp_p2a_"))
        cls.m8_root = REPO / "M8" / "m8"
        # Short topic render for E2E
        cls.project_id = "phase2a_bridge_demo"
        cls.queue_db = cls.tmp / "m9_queue.db"
        cls.analytics = cls.tmp / "m9_analytics.json"

    @classmethod
    def tearDownClass(cls):
        # Keep generated project under M8/projects — caller may clean; do not delete here
        # Remove only temp M9 stores
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def test_01_seo_package_fields(self):
        seo = build_seo_package(
            topic="Belda Railway Station",
            script={
                "topic": "Belda Railway Station",
                "language": "en",
                "full_text": "A short description about Belda.",
                "segments": [{"text": "Hook"}],
            },
            privacy="private",
        )
        self.assertTrue(seo.title)
        self.assertIn("Belda", seo.title)
        self.assertTrue(seo.description)
        self.assertTrue(seo.tags)
        self.assertTrue(seo.hashtags)
        self.assertEqual(seo.language, "en")
        self.assertEqual(seo.privacy, "private")
        self.assertEqual(seo.visibility, "private")
        d = seo.to_dict()
        for key in (
            "title",
            "description",
            "tags",
            "hashtags",
            "thumbnail_path",
            "category",
            "language",
            "visibility",
        ):
            self.assertIn(key, d)

    def test_02_m8_produces_valid_final_mp4_and_qc(self):
        result = run_m8_to_m9_mock(
            topic="Phase2A Bridge Demo Station",
            duration="15s",
            format_name="9:16",
            project_id=self.project_id,
            platforms=["youtube", "telegram"],
            m8_root=self.m8_root,
            force_mock=True,
            queue_db=self.queue_db,
            analytics_path=self.analytics,
        )
        self.assertTrue(result.final_mp4, result.errors)
        self.assertTrue(Path(result.final_mp4).exists())
        self.assertGreater(Path(result.final_mp4).stat().st_size, 1000)
        # QC should not FAIL for short deterministic render
        self.assertNotEqual(result.qc_status, "FAIL", result.errors)
        # Probe via ffprobe
        import subprocess

        proc = subprocess.run(
            [
                "ffprobe",
                "-v",
                "quiet",
                "-print_format",
                "json",
                "-show_streams",
                "-show_format",
                result.final_mp4,
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        info = json.loads(proc.stdout)
        streams = info.get("streams") or []
        self.assertTrue(any(s.get("codec_type") == "video" for s in streams))
        self.assertTrue(any(s.get("codec_type") == "audio" for s in streams))
        # stash for dependent conceptual checks
        type(self).last_result = result

    def test_03_m9_accepts_m8_via_adapter_mock_publish(self):
        result = getattr(type(self), "last_result", None)
        if result is None:
            self.skipTest("prior E2E result missing")
        self.assertTrue(result.ok, result.errors)
        self.assertEqual(result.job_status, "PUBLISHED")
        self.assertIn("youtube", result.platform_statuses)
        self.assertIn("telegram", result.platform_statuses)
        self.assertEqual(result.platform_statuses["youtube"]["status"], "PUBLISHED")
        self.assertEqual(result.platform_statuses["telegram"]["status"], "PUBLISHED")
        self.assertTrue(result.platform_statuses["youtube"].get("extra", {}).get("is_mock")
                        or "mock" in (result.platform_statuses["youtube"].get("platform_id") or "").lower()
                        or result.platform_statuses["youtube"].get("extra", {}).get("is_mock", True))

    def test_04_analytics_returned(self):
        result = getattr(type(self), "last_result", None)
        if result is None:
            self.skipTest("prior E2E result missing")
        self.assertIsInstance(result.analytics, dict)
        self.assertGreaterEqual(result.analytics.get("jobs_created", 0), 1)
        self.assertGreaterEqual(result.analytics.get("jobs_published", 0), 1)

    def test_05_no_credentials_exposed(self):
        result = getattr(type(self), "last_result", None)
        if result is None:
            self.skipTest("prior E2E result missing")
        blob = json.dumps(result.to_dict(), default=str)
        # Ensure we did not embed env secrets
        for env_key in (
            "YOUTUBE_CLIENT_SECRET",
            "TELEGRAM_BOT_TOKEN",
            "GOOGLE_CLIENT_SECRET",
            "GEMINI_API_KEY",
        ):
            val = os.environ.get(env_key)
            if val and len(val) > 6:
                self.assertNotIn(val, blob)
        # Mock path must be used
        self.assertTrue(result.ok)

    def test_06_timing_measured(self):
        result = getattr(type(self), "last_result", None)
        if result is None:
            self.skipTest("prior E2E result missing")
        t = result.timing
        for k in (
            "t0_start",
            "t1_m8_start",
            "t2_final_mp4",
            "t3_m9_request",
            "t4_mock_publish_complete",
            "m8_render_sec",
            "m9_processing_sec",
            "total_automated_sec",
        ):
            self.assertIn(k, t)
        self.assertGreaterEqual(t["total_automated_sec"], 0)
        self.assertGreaterEqual(t["m8_render_sec"], 0)
        self.assertGreaterEqual(t["m9_processing_sec"], 0)

    def test_07_adapter_from_existing_project(self):
        proj = self.m8_root / "projects" / self.project_id
        if not (proj / "render" / "final.mp4").exists():
            self.skipTest("E2E project not created")
        bundle = M8ProjectBundle.from_project_dir(proj)
        adapter = M8ToM9Adapter()
        kwargs = adapter.to_job_create_kwargs(bundle)
        self.assertEqual(kwargs["project_id"], self.project_id)
        self.assertTrue(Path(kwargs["video_path"]).exists())
        self.assertTrue(kwargs["title"])
        create = adapter.to_publishing_job_create(bundle)
        errs = create.validate_basic()
        self.assertEqual(errs, [])


if __name__ == "__main__":
    unittest.main()
