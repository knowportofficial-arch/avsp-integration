"""
P13 — Final AI QC.
Inspects the ACTUAL rendered MP4 (not just planning logs).
"""
from __future__ import annotations

import json
import logging
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional

from app.schemas.timeline import Timeline
from app.m8.errors import ErrorCode

logger = logging.getLogger("avsp.m8.qc")


def _ffprobe(path: Path) -> Optional[Dict[str, Any]]:
    cmd = [
        "ffprobe", "-v", "quiet",
        "-print_format", "json",
        "-show_format", "-show_streams",
        str(path),
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if proc.returncode != 0:
            return None
        return json.loads(proc.stdout)
    except Exception as e:
        logger.warning("ffprobe failed: %s", e)
        return None


class FinalQC:
    def __init__(self, tolerance_duration: float = 8.0):
        self.tolerance_duration = tolerance_duration

    def inspect(
        self,
        mp4_path: Path,
        timeline: Optional[Timeline] = None,
        expected_duration: Optional[float] = None,
        expected_format: str = "9:16",
        render_meta: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        report: Dict[str, Any] = {
            "status": "FAIL",
            "file_exists": False,
            "playable": False,
            "duration": 0.0,
            "resolution": "",
            "fps": 0.0,
            "video_codec": "",
            "audio_codec": "",
            "audio_present": False,
            "caption_presence": "unknown",
            "cta": "SKIP",
            "black_frames": "unknown",
            "missing_assets": 0,
            "checks": {},
            "errors": [],
            "warnings": [],
        }

        path = Path(mp4_path)
        if not path.exists():
            report["errors"].append({"code": "MEDIA_NOT_FOUND", "message": f"MP4 not found: {path}"})
            return report

        report["file_exists"] = True
        info = _ffprobe(path)
        if not info:
            report["errors"].append({"code": "MEDIA_DECODE_ERROR", "message": "ffprobe failed"})
            return report

        report["playable"] = True
        fmt = info.get("format", {})
        streams = info.get("streams", [])
        vstreams = [s for s in streams if s.get("codec_type") == "video"]
        astreams = [s for s in streams if s.get("codec_type") == "audio"]

        try:
            report["duration"] = float(fmt.get("duration") or 0)
        except (TypeError, ValueError):
            report["duration"] = 0.0

        if vstreams:
            vs = vstreams[0]
            w = int(vs.get("width") or 0)
            h = int(vs.get("height") or 0)
            report["resolution"] = f"{w}x{h}"
            report["video_codec"] = (vs.get("codec_name") or "").lower()
            try:
                # avg_frame_rate like "30/1"
                fr = vs.get("avg_frame_rate") or vs.get("r_frame_rate") or "0/1"
                num, den = fr.split("/")
                report["fps"] = float(num) / float(den) if float(den) else 0.0
            except Exception:
                report["fps"] = 0.0

        if astreams:
            report["audio_present"] = True
            report["audio_codec"] = (astreams[0].get("codec_name") or "").lower()

        checks = {}
        # Duration
        if expected_duration:
            ok = abs(report["duration"] - expected_duration) <= self.tolerance_duration
            checks["duration"] = "PASS" if ok else "FAIL"
            if not ok:
                report["warnings"].append(
                    f"Duration {report['duration']:.1f}s vs target {expected_duration:.1f}s"
                )
        else:
            checks["duration"] = "PASS" if report["duration"] > 0.5 else "FAIL"

        # Resolution / format
        if expected_format in ("9:16", "shorts"):
            ok = report["resolution"] == "1080x1920"
        else:
            ok = report["resolution"] == "1920x1080"
        checks["resolution"] = "PASS" if ok else "FAIL"
        if not ok:
            report["warnings"].append(f"Resolution {report['resolution']} unexpected for {expected_format}")

        checks["video_codec"] = "PASS" if "264" in report["video_codec"] or report["video_codec"] == "h264" else "FAIL"
        checks["audio"] = "PASS" if report["audio_present"] else "WARN"
        checks["fps"] = "PASS" if 24 <= report["fps"] <= 60 else "WARN"

        # CTA from timeline
        if timeline and timeline.cta:
            ok_cta = timeline.validate_cta(tolerance=0.2)
            report["cta"] = "PASS" if ok_cta else "FAIL"
            checks["cta"] = report["cta"]
            if not ok_cta:
                report["errors"].append({
                    "code": "QC_FAILED",
                    "message": f"CTA duration {timeline.cta.end - timeline.cta.start:.2f} != 5.0",
                })
        else:
            report["cta"] = "SKIP"
            checks["cta"] = "SKIP"

        # Captions: if timeline has captions we expect them burned or present in plan
        if timeline and timeline.captions:
            report["caption_presence"] = "planned"
            checks["captions"] = "PASS"
        else:
            report["caption_presence"] = "none"
            checks["captions"] = "WARN"

        # Missing assets from timeline
        missing = 0
        if timeline:
            for v in timeline.visual:
                if v.path and not Path(v.path).exists() and v.media_type != "color":
                    missing += 1
        report["missing_assets"] = missing
        checks["missing_assets"] = "PASS" if missing == 0 else "FAIL"


        # Effects from composer meta / timeline
        effects = (render_meta or {}).get("effects") or {}
        audio_meta = (render_meta or {}).get("audio_meta") or {}
        report["effects"] = effects
        report["audio_meta"] = audio_meta
        if timeline:
            report["planned_punch"] = sum(1 for o in timeline.overlays if o.kind == "punch")
            report["planned_emoji"] = sum(1 for o in timeline.overlays if o.kind == "emoji")
            report["planned_sfx"] = len(timeline.sfx)
            report["planned_transitions"] = len(timeline.transitions)
            report["planned_captions"] = len(timeline.captions)
        
        # Caption burn hard gate
        fx = (render_meta or {}).get("effects") or {}
        if fx.get("caption_count", 0) > 0 and fx.get("caption_burn_ok") is False:
            checks["captions"] = "FAIL"
            report["errors"].append({
                "code": "CAPTION_ERROR",
                "message": f"Caption burn failed (method={fx.get('caption_burn_method')})",
            })
        elif fx.get("caption_burn_ok") is True and fx.get("caption_count", 0) > 0:
            checks["captions"] = "PASS"
            report["caption_presence"] = f"burned:{fx.get('caption_burn_method')}"

        checks["effects_pipeline"] = "PASS" if (render_meta or {}).get("status") == "success" else "WARN"
        if audio_meta.get("ducking_applied"):
            checks["bgm_ducking"] = "PASS"
        elif audio_meta.get("bgm"):
            checks["bgm_ducking"] = "WARN"
        else:
            checks["bgm_ducking"] = "SKIP"

        report["checks"] = checks
        critical_fail = any(
            checks.get(k) == "FAIL" for k in ("resolution", "video_codec", "cta", "missing_assets", "captions")
        )
        # duration FAIL is warning-level unless severely off
        if checks.get("duration") == "FAIL" and report["duration"] < 1.0:
            critical_fail = True

        if not report["file_exists"] or not report["playable"]:
            critical_fail = True
        report["status"] = "FAIL" if critical_fail else "PASS"
        return report

    def write_reports(self, report: Dict[str, Any], out_dir: Path) -> None:
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "final_qc.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        lines = [
            "# FINAL QC REPORT",
            "",
            f"**Status:** {report['status']}",
            "",
            f"- File exists: {report['file_exists']}",
            f"- Playable: {report['playable']}",
            f"- Duration: {report['duration']:.2f}s",
            f"- Resolution: {report['resolution']}",
            f"- FPS: {report['fps']}",
            f"- Video codec: {report['video_codec']}",
            f"- Audio: {report['audio_codec']} (present={report['audio_present']})",
            f"- Captions: {report['caption_presence']}",
            f"- CTA: {report['cta']}",
            f"- Missing assets: {report['missing_assets']}",
            "",
            "## Checks",
        ]
        for k, v in report.get("checks", {}).items():
            lines.append(f"- {k}: {v}")
        if report.get("errors"):
            lines.append("")
            lines.append("## Errors")
            for e in report["errors"]:
                lines.append(f"- {e}")
        if report.get("warnings"):
            lines.append("")
            lines.append("## Warnings")
            for w in report["warnings"]:
                lines.append(f"- {w}")
        (out_dir / "FINAL_QC_REPORT.md").write_text("\n".join(lines), encoding="utf-8")
