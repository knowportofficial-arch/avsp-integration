"""
P1 — Autonomous AI Production Controller.
Single orchestration entry point for the full pipeline.
"""
from __future__ import annotations

import json
import logging
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from app.m8.errors import M8Error, ErrorCode
from app.adapters.m4_adapter import M4Adapter
from app.adapters.m7_adapter import M7Adapter
from app.adapters.pexels_client import PexelsClient
from app.engines.creative_director import CreativeDirector, parse_duration
from app.engines.timeline_composer import TimelineComposer
from app.engines.final_qc import FinalQC
from app.engines.visual_verifier import VisualVerifier
from app.engines.effects_composer import EffectsComposer
from app.schemas.edl import CreativeEDL, MediaCandidate
from app.schemas.timeline import Timeline

logger = logging.getLogger("avsp.m8.controller")


@dataclass
class StageResult:
    stage: str
    status: str  # success | error | skipped
    elapsed: float = 0.0
    input_summary: str = ""
    output_summary: str = ""
    error: Optional[Dict[str, Any]] = None
    data: Any = None


class AutonomousProductionController:
    STAGES = [
        "validate_input",
        "create_project",
        "research_planning",
        "generate_script",
        "generate_scene_plan",
        "discover_local_media",
        "filter_media",
        "external_fallback",
        "generate_creative_edl",
        "build_timeline",
        "add_punch_overlays",
        "add_transitions",
        "add_sfx",
        "add_emoji",
        "add_bgm",
        "apply_voice_ducking",
        "add_captions",
        "protect_cta",
        "call_m4_renderer",
        "inspect_final_mp4",
        "run_final_ai_qc",
        "produce_final_report",
    ]

    def __init__(self, root: Optional[Path] = None):
        self.root = Path(root or Path(__file__).resolve().parents[2])
        self.projects_dir = self.root / "projects"
        self.assets_dir = self.root / "assets"
        self.projects_dir.mkdir(parents=True, exist_ok=True)
        self.m7 = M7Adapter(
            library_root=self.assets_dir / "local_media",
            snapshot_path=self.assets_dir / "m7_snapshot.json",
        )
        self.director = CreativeDirector(m7=self.m7, assets_root=self.assets_dir, use_ai=True)
        self.composer = TimelineComposer()
        self.m4 = M4Adapter(m4_root=self.root / "vendor" / "m4")
        self.qc = FinalQC()
        self.verifier = VisualVerifier()
        self.pexels = PexelsClient(cache_dir=self.root / "cache" / "pexels")
        self.stage_log: List[StageResult] = []
        self.last_decision_source = "DETERMINISTIC_FALLBACK"
        self.media_sources_used = []

    def _stage(self, name: str, fn, *args, **kwargs) -> StageResult:
        t0 = time.time()
        try:
            data = fn(*args, **kwargs)
            sr = StageResult(
                stage=name,
                status="success",
                elapsed=time.time() - t0,
                output_summary=str(type(data).__name__),
                data=data,
            )
        except M8Error as e:
            sr = StageResult(
                stage=name,
                status="error",
                elapsed=time.time() - t0,
                error=e.to_dict(),
            )
            self.stage_log.append(sr)
            raise
        except Exception as e:
            sr = StageResult(
                stage=name,
                status="error",
                elapsed=time.time() - t0,
                error={"code": ErrorCode.STAGE_FAILED.value, "message": str(e), "stage": name},
            )
            self.stage_log.append(sr)
            raise M8Error(ErrorCode.STAGE_FAILED, str(e), stage=name) from e
        self.stage_log.append(sr)
        logger.info("STAGE %s OK (%.2fs)", name, sr.elapsed)
        return sr

    def run(
        self,
        topic: str,
        duration: str = "5m",
        format_name: str = "9:16",
        project_id: Optional[str] = None,
        skip_render: bool = False,
    ) -> Dict[str, Any]:
        self.stage_log = []
        project_id = project_id or f"proj_{uuid.uuid4().hex[:10]}"
        duration_sec = parse_duration(duration)

        # 1. Validate
        def _validate():
            if not topic or not topic.strip():
                raise M8Error(ErrorCode.INPUT_INVALID, "topic required", stage="validate_input")
            if format_name not in ("9:16", "16:9", "shorts", "landscape"):
                raise M8Error(ErrorCode.INPUT_INVALID, f"unsupported format {format_name}", stage="validate_input")
            return {"topic": topic, "duration": duration, "format": format_name}

        self._stage("validate_input", _validate)

        # 2. Create project dirs
        def _create():
            base = self.projects_dir / project_id
            for sub in ("input", "research", "media", "cache", "edl", "timeline", "render", "qc", "logs"):
                (base / sub).mkdir(parents=True, exist_ok=True)
            meta = {"project_id": project_id, "topic": topic, "duration": duration, "format": format_name}
            (base / "input" / "request.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
            return base

        proj = self._stage("create_project", _create).data

        # 3–5 Research / script / scene plan
        def _research():
            plan = {"topic": topic, "notes": "local-first planning", "external_ok": True}
            (proj / "research" / "plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
            return plan

        self._stage("research_planning", _research)

        script = self._stage(
            "generate_script",
            lambda: self.director.generate_script(topic, duration_sec),
        ).data
        (proj / "research" / "script.json").write_text(json.dumps(script, indent=2, ensure_ascii=False), encoding="utf-8")

        scene_plan = self._stage(
            "generate_scene_plan",
            lambda: self.director.generate_scene_plan(topic, duration_sec, script),
        ).data
        (proj / "research" / "scene_plan.json").write_text(json.dumps(scene_plan, indent=2, ensure_ascii=False), encoding="utf-8")

        # 6–7 Local media
        snap = self._stage("discover_local_media", lambda: self.m7.snapshot(project_id)).data
        (proj / "media" / "m7_snapshot.json").write_text(json.dumps(snap, indent=2), encoding="utf-8")

        keepable = self._stage(
            "filter_media",
            lambda: [a.to_dict() for a in self.m7.find_keepable(min_quality=0.5, limit=50)],
        ).data

        # 8. External fallback — real Pexels when local insufficient
        def _external():
            result = {"used": False, "reason": "", "assets": []}
            if keepable:
                result["reason"] = "local KEEP/REVIEW media available"
                self.media_sources_used.append("local")
                return result
            # Try Pexels
            if self.pexels.available:
                q = topic
                orient = "portrait" if format_name in ("9:16", "shorts") else "landscape"
                item = self.pexels.fetch_for_scene(q, orientation=orient, prefer_video=True)
                if item:
                    result["used"] = True
                    result["reason"] = "pexels"
                    result["assets"].append(item)
                    self.media_sources_used.append("pexels")
                    # inject into m7-like list for director by writing cache path into snapshot style
                    return result
                result["reason"] = "pexels available but no results"
            else:
                result["reason"] = "PEXELS_API_KEY not set — placeholder fallback"
            self.media_sources_used.append("placeholder")
            return result

        ext_result = self._stage("external_fallback", _external).data

        # 9. Creative EDL
        edl: CreativeEDL = self._stage(
            "generate_creative_edl",
            lambda: self.director.build_edl(project_id, topic, duration, format_name, script),
        ).data
        # Record AI vs deterministic
        if edl.notes and "AI Creative" in edl.notes or (edl.notes or "").strip() == "AI":
            self.last_decision_source = "AI"
        else:
            self.last_decision_source = "DETERMINISTIC_FALLBACK"
        # Fill missing media from Pexels if needed
        if ext_result and ext_result.get("assets"):
            for sc in edl.scenes:
                if sc.selected_media is None and ext_result["assets"]:
                    a = ext_result["assets"][0]
                    sc.selected_media = MediaCandidate(
                        asset_id=a["asset_id"], path=a["path"], source="pexels",
                        quality_score=a.get("quality_score", 0.8),
                        recommendation="KEEP", type=a.get("type", "video"),
                        duration=a.get("duration", 0),
                        selection_reason=a.get("selection_reason", "pexels"),
                    )
                    sc.selection_reason = sc.selected_media.selection_reason
        # Resolve relative asset paths against project root
        for sc in edl.scenes:
            if sc.selected_media and sc.selected_media.path:
                pp = Path(sc.selected_media.path)
                if not pp.is_absolute() and not pp.exists():
                    cand = self.root / sc.selected_media.path
                    if cand.exists():
                        sc.selected_media.path = str(cand)
            if sc.punch and sc.punch.punch_asset:
                pp = Path(sc.punch.punch_asset)
                if not pp.is_absolute() and not pp.exists():
                    cand = self.root / sc.punch.punch_asset
                    if cand.exists():
                        sc.punch.punch_asset = str(cand)
            if sc.sfx and sc.sfx.asset:
                pp = Path(sc.sfx.asset)
                if not pp.is_absolute() and not pp.exists():
                    cand = self.root / sc.sfx.asset
                    if cand.exists():
                        sc.sfx.asset = str(cand)
        if edl.bgm and edl.bgm.music_asset:
            pp = Path(edl.bgm.music_asset)
            if not pp.is_absolute() and not pp.exists():
                cand = self.root / edl.bgm.music_asset
                if cand.exists():
                    edl.bgm.music_asset = str(cand)
        edl_path = proj / "edl" / "creative_edl.json"
        edl.to_json(str(edl_path))
        (proj / "edl" / "decision_source.txt").write_text(self.last_decision_source, encoding="utf-8")

        # 10–18 Timeline (composer folds punch/transitions/sfx/emoji/bgm/captions/CTA)
        timeline: Timeline = self._stage(
            "build_timeline",
            lambda: self.composer.compose(edl),
        ).data
        # Mark sub-stages as success (logic embedded in composer)
        for s in ("add_punch_overlays", "add_transitions", "add_sfx", "add_emoji",
                  "add_bgm", "apply_voice_ducking", "add_captions", "protect_cta"):
            self.stage_log.append(StageResult(stage=s, status="success", elapsed=0.0, output_summary="folded_into_composer"))

        tl_path = proj / "timeline" / "timeline.json"
        timeline.to_json(str(tl_path))

        if not timeline.validate_cta():
            raise M8Error(
                ErrorCode.TIMELINE_ERROR,
                f"CTA duration invalid: {timeline.cta}",
                stage="protect_cta",
            )

        # 19. M4 render
        out_mp4 = proj / "render" / "final.mp4"

        def _render():
            if skip_render:
                return {"status": "skipped", "file": None}
            # M8 Effects Composer produces final MP4 with punch/transitions/SFX/emoji/ducking/captions
            fx = EffectsComposer(work_dir=proj / "render" / "fx_work")
            result = fx.compose(timeline, out_mp4, narration_audio=None)
            # Also write a render.json compatible summary
            (proj / "render" / "render.json").write_text(
                json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8"
            )
            return result

        try:
            render_result = self._stage("call_m4_renderer", _render).data
        except M8Error:
            raise  # preserve CAPTION_ERROR / RENDER_ERROR / stage
        except Exception as e:
            raise M8Error(ErrorCode.RENDER_ERROR, str(e), stage="call_m4_renderer") from e

        # 20–21 QC
        def _inspect():
            if skip_render or not out_mp4.exists():
                return {"file_exists": out_mp4.exists(), "skipped": skip_render}
            return {"file": str(out_mp4), "size": out_mp4.stat().st_size}

        self._stage("inspect_final_mp4", _inspect)

        def _qc():
            if skip_render or not out_mp4.exists():
                rep = {
                    "status": "SKIP",
                    "reason": "render skipped or missing",
                    "file_exists": out_mp4.exists(),
                }
                (proj / "qc" / "final_qc.json").write_text(json.dumps(rep, indent=2), encoding="utf-8")
                return rep
            rep = self.qc.inspect(
                out_mp4,
                timeline=timeline,
                expected_duration=duration_sec,
                expected_format=format_name,
                render_meta=render_result if isinstance(render_result, dict) else None,
            )
            # Visual/audio verification on actual MP4 frames
            vrep = self.verifier.verify(
                out_mp4,
                timeline=timeline,
                render_meta=render_result if isinstance(render_result, dict) else None,
                work_dir=proj / "qc" / "frames",
            )
            rep["visual_verification"] = vrep
            rep["effect_matrix"] = vrep.get("matrix")
            if vrep.get("status") == "FAIL" and vrep.get("failures"):
                rep["status"] = "FAIL"
                rep.setdefault("errors", []).append({
                    "code": "QC_FAILED",
                    "message": f"Effect verification failed: {vrep.get('failures')}",
                })
            self.qc.write_reports(rep, proj / "qc")
            (proj / "qc" / "visual_verification.json").write_text(
                __import__("json").dumps(vrep, indent=2, ensure_ascii=False), encoding="utf-8"
            )
            return rep

        qc_result = self._stage("run_final_ai_qc", _qc).data

        # 22. Final report
        def _report():
            report = {
                "project_id": project_id,
                "topic": topic,
                "duration_target": duration_sec,
                "format": format_name,
                "decision_source": self.last_decision_source,
                "media_sources": self.media_sources_used,
                "pexels_available": self.pexels.available,
                "stages": [
                    {"stage": s.stage, "status": s.status, "elapsed": round(s.elapsed, 3), "error": s.error}
                    for s in self.stage_log
                ],
                "edl": str(edl_path),
                "timeline": str(tl_path),
                "render": render_result,
                "qc": qc_result,
                "final_mp4": str(out_mp4) if out_mp4.exists() else None,
            }
            (proj / "logs" / "pipeline_report.json").write_text(
                json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
            )
            return report

        final = self._stage("produce_final_report", _report).data
        return final
