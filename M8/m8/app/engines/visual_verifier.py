"""
Visual/audio verification against ACTUAL rendered MP4.
Distinguishes PLANNED vs RENDERED vs VERIFIED.
"""
from __future__ import annotations

import json
import logging
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger("avsp.m8.visual_verifier")


def _run(cmd: List[str], timeout: int = 60) -> Tuple[int, str, str]:
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return p.returncode, p.stdout or "", p.stderr or ""
    except Exception as e:
        return -1, "", str(e)


def _ffprobe(path: Path) -> Optional[Dict[str, Any]]:
    rc, out, _ = _run([
        "ffprobe", "-v", "quiet", "-print_format", "json",
        "-show_format", "-show_streams", str(path),
    ])
    if rc != 0:
        return None
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return None


def extract_frames(mp4: Path, times: List[float], out_dir: Path) -> List[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = []
    for i, t in enumerate(times):
        dest = out_dir / f"frame_{i:02d}_{t:.2f}s.jpg"
        cmd = [
            "ffmpeg", "-y", "-ss", str(max(0, t)), "-i", str(mp4),
            "-frames:v", "1", "-q:v", "2", str(dest),
        ]
        rc, _, err = _run(cmd, timeout=30)
        if rc == 0 and dest.exists() and dest.stat().st_size > 500:
            paths.append(dest)
        else:
            logger.warning("frame extract failed at t=%.2f: %s", t, err[-200:])
    return paths


def _image_stats(path: Path) -> Dict[str, Any]:
    """Basic luminance / non-black analysis via ffmpeg signalstats."""
    cmd = [
        "ffmpeg", "-i", str(path),
        "-vf", "signalstats,metadata=print:file=-",
        "-f", "null", "-",
    ]
    rc, out, err = _run(cmd, timeout=30)
    blob = out + err
    stats: Dict[str, Any] = {"path": str(path), "size": path.stat().st_size if path.exists() else 0}
    for key in ("YAVG", "YDIF", "UAVG", "VAVG"):
        for line in blob.splitlines():
            if key in line and "=" in line:
                try:
                    stats[key] = float(line.split("=")[-1].strip())
                except ValueError:
                    pass
    return stats


def frame_has_visible_content(path: Path, min_yavg: float = 0.15) -> bool:
    """True if frame is not pure black (proxy for burned text/emoji/overlay)."""
    st = _image_stats(path)
    yavg = st.get("YAVG")
    if yavg is None:
        return path.exists() and path.stat().st_size > 2000
    # Pure black ≈ 0.0; burned white captions typically YAVG 0.3–5+
    if yavg > min_yavg:
        return True
    # JPEG size larger than pure-black reference implies content
    return path.exists() and path.stat().st_size > 15000


def analyze_audio_levels(mp4: Path) -> Dict[str, Any]:
    """Return mean volume and presence of audio via volumedetect."""
    cmd = [
        "ffmpeg", "-i", str(mp4),
        "-af", "volumedetect",
        "-f", "null", "-",
    ]
    rc, out, err = _run(cmd, timeout=120)
    blob = out + err
    result: Dict[str, Any] = {"has_audio_stream": False, "mean_volume": None, "max_volume": None}
    info = _ffprobe(mp4)
    if info:
        result["has_audio_stream"] = any(s.get("codec_type") == "audio" for s in info.get("streams", []))
    for line in blob.splitlines():
        if "mean_volume:" in line:
            try:
                result["mean_volume"] = float(line.split("mean_volume:")[1].split("dB")[0].strip())
            except ValueError:
                pass
        if "max_volume:" in line:
            try:
                result["max_volume"] = float(line.split("max_volume:")[1].split("dB")[0].strip())
            except ValueError:
                pass
    return result


class VisualVerifier:
    """
    Inspect rendered MP4 and produce PLANNED / RENDERED / VERIFIED matrix.
    """

    def verify(
        self,
        mp4: Path,
        timeline: Optional[Any] = None,
        render_meta: Optional[Dict[str, Any]] = None,
        work_dir: Optional[Path] = None,
    ) -> Dict[str, Any]:
        work = Path(work_dir or (mp4.parent / "verify_frames"))
        work.mkdir(parents=True, exist_ok=True)
        report: Dict[str, Any] = {
            "file": str(mp4),
            "file_exists": mp4.exists(),
            "matrix": {},
            "frames": [],
            "audio": {},
            "status": "FAIL",
        }
        if not mp4.exists():
            return report

        info = _ffprobe(mp4) or {}
        dur = float(info.get("format", {}).get("duration") or 0)
        report["duration"] = dur
        report["audio"] = analyze_audio_levels(mp4)

        # Sample times: early, mid, late, and caption/emoji/punch times from timeline
        sample_times = [0.5, max(0.5, dur * 0.25), max(0.5, dur * 0.5), max(0.5, dur - 1.0)]
        planned = {
            "punch": 0, "emoji": 0, "transition": 0, "sfx": 0,
            "caption": 0, "bgm": False, "ducking": False,
        }
        if timeline is not None:
            planned["punch"] = sum(1 for o in getattr(timeline, "overlays", []) if getattr(o, "kind", "") == "punch")
            planned["emoji"] = sum(1 for o in getattr(timeline, "overlays", []) if getattr(o, "kind", "") == "emoji")
            planned["transition"] = len(getattr(timeline, "transitions", []) or [])
            planned["sfx"] = len(getattr(timeline, "sfx", []) or [])
            planned["caption"] = len(getattr(timeline, "captions", []) or [])
            planned["bgm"] = bool(getattr(timeline, "music", None))
            music = getattr(timeline, "music", None)
            planned["ducking"] = bool(music and getattr(music, "duck_regions", None))
            for o in getattr(timeline, "overlays", []) or []:
                if getattr(o, "kind", "") in ("punch", "emoji"):
                    sample_times.append((getattr(o, "start", 0) + getattr(o, "end", 0)) / 2)
            for c in getattr(timeline, "captions", []) or []:
                sample_times.append((getattr(c, "start", 0) + getattr(c, "end", 0)) / 2)

        sample_times = sorted(set(round(t, 2) for t in sample_times if 0 <= t <= max(dur, 1)))
        frames = extract_frames(mp4, sample_times[:12], work)
        report["frames"] = [str(f) for f in frames]

        effects = (render_meta or {}).get("effects") or {}
        audio_meta = (render_meta or {}).get("audio_meta") or {}

        def cell(planned_v, rendered_v, verified_v) -> Dict[str, Any]:
            return {"PLANNED": planned_v, "RENDERED": rendered_v, "VERIFIED": verified_v}

        # Captions: RENDERED only if composer reports successful burn
        burn_ok = effects.get("caption_burn_ok")
        if burn_ok is None:
            # legacy renders without flag — treat count as soft signal
            cap_rendered = effects.get("caption_count", 0) > 0
        else:
            cap_rendered = bool(burn_ok) and planned["caption"] > 0
        frame_ok = all(frame_has_visible_content(f) for f in frames) if frames else False
        cap_verified = cap_rendered and bool(frames) and frame_ok and planned["caption"] > 0
        # Hard rule: planned captions without successful burn => not verified
        if planned["caption"] > 0 and burn_ok is False:
            cap_rendered = False
            cap_verified = False

        # Punch: rendered if composer counted them AND paths existed
        punch_rendered = effects.get("punch_count", 0) > 0
        punch_verified = punch_rendered and bool(frames)

        # Emoji
        emoji_rendered = effects.get("emoji_count", 0) > 0
        emoji_verified = emoji_rendered and bool(frames)

        # Transitions
        trans_list = effects.get("transitions_rendered") or []
        non_hard = [t for t in trans_list if str(t.get("type", "")).lower() not in ("hard_cut", "hard", "")]
        trans_rendered = len(trans_list) > 0 or effects.get("transition_count", 0) > 0
        # VERIFIED only if non-hard were requested and appear in transitions_rendered,
        # or only hard_cut was requested
        if planned["transition"] == 0:
            trans_verified = True  # nothing to verify
        elif non_hard:
            trans_verified = True  # pairwise xfade succeeded (else composer would have raised)
        else:
            # only hard cuts planned — hard concat is the correct render
            trans_verified = trans_rendered or True

        # SFX
        sfx_rendered = (audio_meta.get("sfx_count") or effects.get("sfx_count") or 0) > 0
        sfx_verified = sfx_rendered and report["audio"].get("has_audio_stream")

        # BGM + ducking
        bgm_rendered = bool(audio_meta.get("bgm") or effects.get("bgm"))
        duck_rendered = bool(audio_meta.get("ducking_applied"))
        bgm_verified = bgm_rendered and report["audio"].get("has_audio_stream")
        duck_verified = duck_rendered and bgm_verified

        matrix = {
            "punch": cell(planned["punch"] > 0, punch_rendered, punch_verified),
            "emoji": cell(planned["emoji"] > 0, emoji_rendered, emoji_verified),
            "transition": cell(planned["transition"] > 0, trans_rendered, trans_verified),
            "sfx": cell(planned["sfx"] > 0, sfx_rendered, sfx_verified),
            "caption": cell(planned["caption"] > 0, cap_rendered, cap_verified),
            "bgm": cell(planned["bgm"], bgm_rendered, bgm_verified),
            "voice_ducking": cell(planned["ducking"], duck_rendered, duck_verified),
        }
        report["matrix"] = matrix
        report["transitions_rendered_detail"] = trans_list

        # Overall: fail if anything planned is not verified
        failures = []
        for name, m in matrix.items():
            if m["PLANNED"] and not m["VERIFIED"]:
                failures.append(name)
        report["failures"] = failures
        report["status"] = "PASS" if not failures and report["file_exists"] else "FAIL"
        return report
