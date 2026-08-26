"""
Phase 2C orchestration: M3 voice package → narration.wav → M8 render (real VO).

Does not modify M3/M8 engine source. Injects narration_audio via a temporary
EffectsComposer.compose patch while AutonomousProductionController.run executes.
"""

from __future__ import annotations

import json
import sys
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

from .adapter import (
    M3ContractError,
    M3VoicePackage,
    assert_valid,
    discover_m3_package,
    wav_duration_ms,
)
from .concat import concat_voice_package


def _repo_root() -> Path:
    here = Path(__file__).resolve().parent
    for cand in (here.parents[1], here.parents[2], Path.cwd()):
        if (cand / "M8" / "m8").is_dir():
            return cand
    return here.parents[1]


REPO_ROOT = _repo_root()
M8_ROOT = REPO_ROOT / "M8" / "m8"
M9_ROOT = REPO_ROOT / "M9" / "m9"


def _purge_app_modules() -> None:
    for k in list(sys.modules):
        if k == "app" or k.startswith("app."):
            del sys.modules[k]


def _prefer_path(root: Path) -> None:
    root_s = str(root.resolve())
    drop = {str(M8_ROOT.resolve()), str(M9_ROOT.resolve())}
    cleaned = [p for p in sys.path if p not in drop]
    sys.path[:] = [root_s] + cleaned


def _ensure_repo_on_path() -> None:
    root_s = str(REPO_ROOT)
    if root_s not in sys.path:
        sys.path.insert(0, root_s)


def _duration_arg_from_ms(total_ms: int) -> str:
    """Format M3 totalDurationMs as M8 duration string (seconds)."""
    sec = max(1.0, total_ms / 1000.0)
    # Prefer integer seconds when close; else one decimal.
    if abs(sec - round(sec)) < 0.05:
        return f"{int(round(sec))}s"
    return f"{sec:.1f}s"


@dataclass
class Phase2CResult:
    ok: bool
    m3_project_id: str
    m3_audio_package_id: str
    m8_project_id: str
    narration_wav: Optional[str]
    narration_duration_ms: int
    m8_duration_arg: str
    final_mp4: Optional[str]
    qc_status: Optional[str]
    audio_meta: Dict[str, Any] = field(default_factory=dict)
    mp4_duration_sec: Optional[float] = None
    timing: Dict[str, float] = field(default_factory=dict)
    m8_report: Dict[str, Any] = field(default_factory=dict)
    errors: List[str] = field(default_factory=list)
    m9: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


def prepare_narration(
    m3_package_dir: Path,
    work_dir: Path,
) -> tuple[M3VoicePackage, Path]:
    """Discover, validate, concat → narration.wav under work_dir."""
    voice = discover_m3_package(Path(m3_package_dir))
    assert_valid(voice)
    out = Path(work_dir) / "narration.wav"
    concat_voice_package(voice, out)
    file_ms = wav_duration_ms(out)
    if abs(file_ms - voice.total_duration_ms) > 80:
        raise M3ContractError(
            f"concat duration {file_ms}ms != totalDurationMs {voice.total_duration_ms}"
        )
    return voice, out.resolve()


def _run_m8_with_narration(
    *,
    topic: str,
    duration: str,
    format_name: str,
    project_id: Optional[str],
    narration_wav: Path,
    m8_root: Path,
) -> Dict[str, Any]:
    """
    Call AutonomousProductionController.run while forcing EffectsComposer to use
    the concatenated M3 narration file (no M8 source edits).
    """
    _purge_app_modules()
    _prefer_path(m8_root)
    from app.engines import effects_composer as ec_mod  # type: ignore
    from app.m8.controller import AutonomousProductionController  # type: ignore

    orig_compose = ec_mod.EffectsComposer.compose
    narr = Path(narration_wav).resolve()

    def compose_with_vo(self, timeline, output_path, narration_audio=None):
        # Prefer injected M3 narration when controller passes None.
        use = Path(narration_audio) if narration_audio else narr
        return orig_compose(self, timeline, output_path, narration_audio=use)

    ec_mod.EffectsComposer.compose = compose_with_vo  # type: ignore
    try:
        ctrl = AutonomousProductionController(root=m8_root)
        return ctrl.run(
            topic=topic,
            duration=duration,
            format_name=format_name,
            project_id=project_id,
            skip_render=False,
        )
    finally:
        ec_mod.EffectsComposer.compose = orig_compose  # type: ignore


def _probe_mp4(path: Path) -> Dict[str, Any]:
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
            str(path),
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        return {}
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError:
        return {}


def run_m3_to_m8(
    m3_package_dir: Path,
    topic: str,
    *,
    format_name: str = "9:16",
    project_id: Optional[str] = None,
    m8_root: Optional[Path] = None,
    work_dir: Optional[Path] = None,
    duration_override: Optional[str] = None,
) -> Phase2CResult:
    """
    M3 package → concat → M8 render with real narration.

    Duration defaults to M3 totalDurationMs so timeline ≈ voice length
    (M8 still enforces ~5s CTA and min body — use ≥~15s VO for best match).
    """
    t0 = time.time()
    errors: List[str] = []
    _ensure_repo_on_path()
    m8_root = Path(m8_root or M8_ROOT)
    project_id = project_id or f"phase2c_m3_m8_{int(t0)}"
    work = Path(work_dir or (m8_root / "projects" / project_id / "input" / "m3_voice"))
    work.mkdir(parents=True, exist_ok=True)

    try:
        voice, narr = prepare_narration(Path(m3_package_dir), work)
    except (M3ContractError, OSError, ValueError) as e:
        return Phase2CResult(
            ok=False,
            m3_project_id="",
            m3_audio_package_id="",
            m8_project_id=project_id,
            narration_wav=None,
            narration_duration_ms=0,
            m8_duration_arg="",
            final_mp4=None,
            qc_status=None,
            errors=[str(e)],
            timing={"t0_start": t0, "t1_done": time.time()},
        )

    duration = duration_override or _duration_arg_from_ms(voice.total_duration_ms)
    t1 = time.time()

    # Persist bridge handoff metadata for traceability
    handoff = {
        "m3_project_id": voice.project_id,
        "m3_audio_package_id": voice.audio_package_id,
        "m3_script_id": voice.script_id,
        "language": voice.language,
        "total_duration_ms": voice.total_duration_ms,
        "narration_wav": str(narr),
        "m8_duration_arg": duration,
        "m8_project_id": project_id,
        "segment_count": len(voice.segments),
    }
    (work / "m3_m8_handoff.json").write_text(
        json.dumps(handoff, indent=2), encoding="utf-8"
    )

    try:
        report = _run_m8_with_narration(
            topic=topic,
            duration=duration,
            format_name=format_name,
            project_id=project_id,
            narration_wav=narr,
            m8_root=m8_root,
        )
    except Exception as e:
        return Phase2CResult(
            ok=False,
            m3_project_id=voice.project_id,
            m3_audio_package_id=voice.audio_package_id,
            m8_project_id=project_id,
            narration_wav=str(narr),
            narration_duration_ms=voice.total_duration_ms,
            m8_duration_arg=duration,
            final_mp4=None,
            qc_status=None,
            errors=[f"M8 run failed: {e}"],
            timing={"t0_start": t0, "t1_prepare": t1, "t2_fail": time.time()},
        )

    t2 = time.time()
    final_mp4 = report.get("final_mp4")
    mp4_path = Path(final_mp4) if final_mp4 else m8_root / "projects" / project_id / "render" / "final.mp4"
    if not mp4_path.exists():
        errors.append(f"final MP4 missing: {mp4_path}")

    render = report.get("render") or {}
    audio_meta = {}
    if isinstance(render, dict):
        audio_meta = render.get("audio_meta") or {}
    if not audio_meta.get("narration"):
        errors.append("EffectsComposer audio_meta.narration is not True (VO not accepted)")

    probe = _probe_mp4(mp4_path) if mp4_path.exists() else {}
    streams = probe.get("streams") or []
    has_audio = any(s.get("codec_type") == "audio" for s in streams)
    has_video = any(s.get("codec_type") == "video" for s in streams)
    if mp4_path.exists() and not has_audio:
        errors.append("final MP4 has no audio stream")
    if mp4_path.exists() and not has_video:
        errors.append("final MP4 has no video stream")

    mp4_dur = None
    try:
        mp4_dur = float((probe.get("format") or {}).get("duration") or 0) or None
    except (TypeError, ValueError):
        mp4_dur = None

    narr_sec = voice.total_duration_ms / 1000.0
    if mp4_dur is not None and narr_sec > 0:
        # M8 may be slightly longer due to CTA/scene rounding; allow 20% or 2s
        tol = max(2.0, narr_sec * 0.2)
        if abs(mp4_dur - narr_sec) > tol:
            errors.append(
                f"MP4 duration {mp4_dur:.2f}s vs narration {narr_sec:.2f}s (tol {tol:.2f}s)"
            )

    qc = report.get("qc") or {}
    qc_status = qc.get("status") if isinstance(qc, dict) else None
    if qc_status == "FAIL":
        errors.append("M8 QC status FAIL")

    ok = mp4_path.exists() and has_audio and has_video and not errors
    return Phase2CResult(
        ok=ok,
        m3_project_id=voice.project_id,
        m3_audio_package_id=voice.audio_package_id,
        m8_project_id=report.get("project_id") or project_id,
        narration_wav=str(narr),
        narration_duration_ms=voice.total_duration_ms,
        m8_duration_arg=duration,
        final_mp4=str(mp4_path) if mp4_path.exists() else None,
        qc_status=qc_status,
        audio_meta=audio_meta if isinstance(audio_meta, dict) else {},
        mp4_duration_sec=mp4_dur,
        timing={
            "t0_start": t0,
            "t1_prepare_done": t1,
            "t2_m8_done": t2,
            "prepare_sec": round(t1 - t0, 3),
            "m8_render_sec": round(t2 - t1, 3),
            "total_sec": round(t2 - t0, 3),
        },
        m8_report={
            "project_id": report.get("project_id"),
            "topic": report.get("topic"),
            "duration_target": report.get("duration_target"),
            "format": report.get("format"),
            "final_mp4": report.get("final_mp4"),
            "decision_source": report.get("decision_source"),
        },
        errors=errors,
    )


def run_m3_to_m8_to_m9_mock(
    m3_package_dir: Path,
    topic: str,
    **kwargs: Any,
) -> Phase2CResult:
    """M3 → M8 (real VO) → Phase 2A M9 mock publish."""
    result = run_m3_to_m8(m3_package_dir, topic, **kwargs)
    if not result.ok or not result.final_mp4:
        return result

    _ensure_repo_on_path()
    from bridges.m8_m9.pipeline import run_m8_to_m9_mock

    m8_root = Path(kwargs.get("m8_root") or M8_ROOT)
    project_dir = m8_root / "projects" / result.m8_project_id
    m9 = run_m8_to_m9_mock(
        topic=topic,
        force_mock=True,
        existing_project_dir=project_dir,
        platforms=kwargs.get("platforms") or ["youtube", "telegram"],
        queue_db=kwargs.get("queue_db"),
        analytics_path=kwargs.get("analytics_path"),
    )
    result.m9 = m9.to_dict() if hasattr(m9, "to_dict") else {}
    if not m9.ok:
        result.ok = False
        result.errors.append("Phase 2A M9 mock publish failed")
        result.errors.extend(m9.errors or [])
    return result


def main(argv: Optional[List[str]] = None) -> int:
    import argparse

    p = argparse.ArgumentParser(description="AVSP Phase 2C M3→M8 voice bridge")
    p.add_argument("--m3-package", required=True, help="Path to M3 voice package / voice.json parent")
    p.add_argument("--topic", required=True)
    p.add_argument("--format", default="9:16", dest="format_name")
    p.add_argument("--project-id", default=None)
    p.add_argument("--with-m9", action="store_true", help="Also run Phase 2A mock publish")
    args = p.parse_args(argv)

    if args.with_m9:
        result = run_m3_to_m8_to_m9_mock(
            Path(args.m3_package),
            args.topic,
            format_name=args.format_name,
            project_id=args.project_id,
        )
    else:
        result = run_m3_to_m8(
            Path(args.m3_package),
            args.topic,
            format_name=args.format_name,
            project_id=args.project_id,
        )
    print(json.dumps(result.to_dict(), indent=2, default=str))
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
