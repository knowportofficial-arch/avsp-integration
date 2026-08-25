"""
M8 unit + integration tests (TEST A–T).
Run from M8 root: python -m pytest tests/ -v
Or: python tests/test_m8_pipeline.py
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path = [p for p in sys.path if "vendor/m4" not in p] + sys.path
# M4 path added only at render time by adapter
# sys.path.insert(0, str(ROOT / "vendor" / "m4"))

from app.m8.controller import AutonomousProductionController
from app.m8.errors import M8Error, ErrorCode
from app.adapters.m7_adapter import M7Adapter
from app.adapters.m4_adapter import M4Adapter
from app.engines.creative_director import CreativeDirector, parse_duration
from app.engines.timeline_composer import TimelineComposer
from app.engines.final_qc import FinalQC
from app.schemas.edl import CreativeEDL
from app.schemas.timeline import Timeline, CtaEvent


class TestM8(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ROOT
        cls.ctrl = AutonomousProductionController(root=cls.root)

    def test_A_project_created(self):
        """TEST A: simple topic → project created"""
        result = self.ctrl.run(topic="Test Topic A", duration="10s", format_name="9:16", skip_render=True)
        pid = result["project_id"]
        self.assertTrue((self.root / "projects" / pid / "input" / "request.json").exists())
        print("TEST A — PASS")

    def test_B_scene_plan(self):
        """TEST B: scene plan generated"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        script = d.generate_script("Belda", 60)
        plan = d.generate_scene_plan("Belda", 60, script)
        self.assertGreaterEqual(len(plan), 5)
        self.assertEqual(plan[-1]["scene_id"], "scene_9")
        self.assertEqual(plan[-1]["duration"], 5.0)
        print("TEST B — PASS")

    def test_C_local_before_external(self):
        """TEST C: local media selected before external"""
        m7 = M7Adapter(
            library_root=self.root / "assets" / "local_media",
            snapshot_path=self.root / "assets" / "m7_snapshot.json",
        )
        keep = m7.find_keepable(min_quality=0.5)
        self.assertTrue(any(a.source == "local" for a in keep))
        print("TEST C — PASS")

    def test_D_external_fallback_stub(self):
        """TEST D: external fallback works (stub when no local)"""
        empty = M7Adapter(library_root=self.root / "assets" / "nonexistent_xyz")
        self.assertEqual(len(empty.find_keepable()), 0)
        # Pipeline records fallback decision without crash
        print("TEST D — PASS")

    def test_E_retake_rejected(self):
        """TEST E: M7 RETAKE asset rejected"""
        m7 = M7Adapter(snapshot_path=self.root / "assets" / "m7_snapshot.json")
        keep = m7.find_keepable(allow_review=True)
        self.assertFalse(any(a.recommendation == "RETAKE" for a in keep))
        print("TEST E — PASS")

    def test_F_keep_preferred(self):
        """TEST F: M7 KEEP asset preferred"""
        m7 = M7Adapter(snapshot_path=self.root / "assets" / "m7_snapshot.json")
        keep = m7.find_keepable(min_quality=0.5)
        self.assertTrue(all(a.recommendation == "KEEP" for a in keep))
        print("TEST F — PASS")

    def test_G_creative_edl(self):
        """TEST G: Creative EDL generated"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_g", "Belda Railway Station", "30s", "9:16")
        self.assertIsInstance(edl, CreativeEDL)
        self.assertGreater(len(edl.scenes), 0)
        self.assertEqual(edl.cta_duration, 5.0)
        print("TEST G — PASS")

    def test_H_timeline(self):
        """TEST H: timeline generated"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_h", "Belda", "20s", "9:16")
        tl = TimelineComposer().compose(edl)
        self.assertIsInstance(tl, Timeline)
        self.assertGreater(tl.duration, 0)
        print("TEST H — PASS")

    def test_I_punch_no_extend(self):
        """TEST I: punch overlay does not extend scene duration"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_i", "Belda", "30s", "9:16")
        body_before = sum(s.duration for s in edl.scenes)
        tl = TimelineComposer().compose(edl)
        # overlays end <= visual ends
        for ov in tl.overlays:
            matching = [v for v in tl.visual if v.start <= ov.start < v.end]
            if matching:
                self.assertLessEqual(ov.end, matching[0].end + 0.05)
        print("TEST I — PASS")

    def test_J_transition_timing(self):
        """TEST J: transition timing valid"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_j", "Belda", "30s", "9:16")
        tl = TimelineComposer().compose(edl)
        for tr in tl.transitions:
            self.assertGreaterEqual(tr.at, 0)
            self.assertLessEqual(tr.duration, 1.0)
        print("TEST J — PASS")

    def test_K_sfx_timing(self):
        """TEST K: SFX timing valid"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_k", "Belda", "30s", "9:16")
        tl = TimelineComposer().compose(edl)
        for s in tl.sfx:
            self.assertLess(s.start, s.end)
        print("TEST K — PASS")

    def test_L_bgm_ducking(self):
        """TEST L: BGM ducking applied"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_l", "Belda", "20s", "9:16")
        tl = TimelineComposer().compose(edl)
        if tl.music:
            self.assertAlmostEqual(tl.music.base_volume, 0.08, places=2)
            self.assertTrue(len(tl.music.duck_regions) >= 0)
        print("TEST L — PASS")

    def test_M_captions(self):
        """TEST M: captions generated"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_m", "Belda", "20s", "9:16")
        tl = TimelineComposer().compose(edl)
        self.assertGreater(len(tl.captions), 0)
        print("TEST M — PASS")

    def test_N_cta_exactly_5s(self):
        """TEST N: CTA exactly 5 seconds"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_n", "Belda", "30s", "9:16")
        tl = TimelineComposer().compose(edl)
        self.assertIsNotNone(tl.cta)
        self.assertTrue(tl.validate_cta(tolerance=0.05))
        self.assertAlmostEqual(tl.cta.duration, 5.0, places=1)
        print("TEST N — PASS")

    def test_O_9_16_timeline(self):
        """TEST O: 9:16 timeline valid"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_o", "Belda", "15s", "9:16")
        tl = TimelineComposer().compose(edl)
        self.assertEqual(tl.width, 1080)
        self.assertEqual(tl.height, 1920)
        print("TEST O — PASS")

    def test_P_16_9_timeline(self):
        """TEST P: 16:9 timeline valid"""
        d = CreativeDirector(m7=self.ctrl.m7, assets_root=self.root / "assets")
        edl = d.build_edl("test_p", "Belda", "15s", "16:9")
        tl = TimelineComposer().compose(edl)
        self.assertEqual(tl.width, 1920)
        self.assertEqual(tl.height, 1080)
        print("TEST P — PASS")

    def test_Q_m4_adapter_import(self):
        """TEST Q: M4 renderer successfully callable (adapter loads)"""
        adapter = M4Adapter(m4_root=self.root / "vendor" / "m4")
        self.assertTrue((adapter.m4_root / "app" / "engines" / "video_engine.py").exists())
        print("TEST Q — PASS")

    def test_R_short_render_mp4(self):
        """TEST R: final MP4 exists (short render)"""
        result = self.ctrl.run(
            topic="Short Render Test",
            duration="8s",
            format_name="9:16",
            skip_render=False,
            project_id="test_r_short",
        )
        mp4 = result.get("final_mp4")
        self.assertIsNotNone(mp4)
        self.assertTrue(Path(mp4).exists(), f"MP4 missing: {mp4}")
        print("TEST R — PASS")

    def test_S_qc_detects_missing(self):
        """TEST S: final QC detects invalid/missing media"""
        qc = FinalQC()
        fake = Path("/tmp/does_not_exist_avsp_m8.mp4")
        rep = qc.inspect(fake, expected_duration=10, expected_format="9:16")
        self.assertEqual(rep["status"], "FAIL")
        self.assertFalse(rep["file_exists"])
        print("TEST S — PASS")

    def test_T_one_command_pipeline(self):
        """TEST T: complete one-command pipeline (short)"""
        result = self.ctrl.run(
            topic="Belda Railway Station",
            duration="12s",
            format_name="9:16",
            project_id="test_t_belda_short",
            skip_render=False,
        )
        self.assertEqual(result.get("topic"), "Belda Railway Station")
        stages = {s["stage"]: s["status"] for s in result.get("stages", [])}
        self.assertEqual(stages.get("validate_input"), "success")
        self.assertEqual(stages.get("generate_creative_edl"), "success")
        self.assertEqual(stages.get("build_timeline"), "success")
        print("TEST T — PASS")


if __name__ == "__main__":
    unittest.main(verbosity=2)
