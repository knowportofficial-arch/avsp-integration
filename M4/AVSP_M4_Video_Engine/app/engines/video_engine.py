"""
AVSP M4 — Video Engine & Assembler

Reusable FFmpeg-based video generation engine.
Produces real final MP4 with H.264 + AAC.
Supports 9:16 Shorts and 16:9, images, video clips,
audio, subtitles, intro/outro, music, scaling/cropping,
concatenation, trimming, and structured render reporting.
"""

from __future__ import annotations

import json
import logging
import os
import re
import shutil
import subprocess
import tempfile
import time
import uuid
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

logger = logging.getLogger("avsp.video_engine")


# ---------------------------------------------------------------------------
# Structured result / error types
# ---------------------------------------------------------------------------

@dataclass
class RenderError:
    code: str
    message: str
    details: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        d = {"code": self.code, "message": self.message}
        if self.details:
            d["details"] = self.details
        return d


@dataclass
class RenderResult:
    project_id: str
    file: Optional[str]
    width: int
    height: int
    fps: float
    duration: float
    video_codec: str
    audio_codec: str
    status: str  # "success" | "error"
    error: Optional[Dict[str, Any]] = None
    progress: float = 0.0
    ffmpeg_cmd: Optional[str] = None
    render_time_sec: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "project_id": self.project_id,
            "file": self.file,
            "width": self.width,
            "height": self.height,
            "fps": self.fps,
            "duration": round(self.duration, 3),
            "video_codec": self.video_codec,
            "audio_codec": self.audio_codec,
            "status": self.status,
            "error": self.error,
            "progress": self.progress,
            "render_time_sec": round(self.render_time_sec, 3),
        }

    def save(self, path: Union[str, Path]) -> None:
        Path(path).write_text(
            json.dumps(self.to_dict(), indent=2, ensure_ascii=False),
            encoding="utf-8",
        )


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _run(cmd: List[str], timeout: Optional[int] = 600) -> Tuple[int, str, str]:
    """Run a command and return (returncode, stdout, stderr)."""
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        return proc.returncode, proc.stdout or "", proc.stderr or ""
    except subprocess.TimeoutExpired as e:
        return -1, "", f"Timeout after {timeout}s: {e}"
    except Exception as e:
        return -1, "", str(e)


def _ffprobe_json(path: Union[str, Path]) -> Optional[Dict[str, Any]]:
    cmd = [
        "ffprobe", "-v", "quiet",
        "-print_format", "json",
        "-show_format", "-show_streams",
        str(path),
    ]
    rc, out, err = _run(cmd, timeout=30)
    if rc != 0:
        logger.warning("ffprobe failed for %s: %s", path, err)
        return None
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return None


def _media_duration(path: Union[str, Path]) -> float:
    info = _ffprobe_json(path)
    if not info:
        return 0.0
    # Prefer format duration
    try:
        return float(info.get("format", {}).get("duration", 0) or 0)
    except (TypeError, ValueError):
        pass
    for s in info.get("streams", []):
        try:
            d = float(s.get("duration", 0) or 0)
            if d > 0:
                return d
        except (TypeError, ValueError):
            continue
    return 0.0


def _has_audio_stream(path: Union[str, Path]) -> bool:
    info = _ffprobe_json(path)
    if not info:
        return False
    return any(s.get("codec_type") == "audio" for s in info.get("streams", []))


def _has_video_stream(path: Union[str, Path]) -> bool:
    info = _ffprobe_json(path)
    if not info:
        return False
    return any(s.get("codec_type") == "video" for s in info.get("streams", []))


def _safe_path(p: Union[str, Path]) -> str:
    """Return absolute path string safe for FFmpeg (no shell injection)."""
    return str(Path(p).resolve())


def _escape_drawtext(text: str) -> str:
    """Escape text for FFmpeg drawtext filter."""
    text = text.replace("\\", "\\\\")
    text = text.replace(":", "\\:")
    text = text.replace("'", "\\'")
    text = text.replace("%", "%%")
    text = text.replace("\n", " ")
    return text


def _escape_ass(text: str) -> str:
    """Basic ASS dialogue text escaping."""
    text = text.replace("\\", "\\\\")
    text = text.replace("{", "\\{")
    text = text.replace("}", "\\}")
    text = text.replace("\n", "\\N")
    return text


def _seconds_to_ass_time(t: float) -> str:
    h = int(t // 3600)
    m = int((t % 3600) // 60)
    s = t % 60
    return f"{h}:{m:02d}:{s:05.2f}"


# ---------------------------------------------------------------------------
# VideoEngine
# ---------------------------------------------------------------------------

class VideoEngine:
    """
    AVSP M4 Video Engine & Assembler.

    Input contract:
      - script: dict (M2 script JSON) with optional scenes, narration path, subtitles
      - audio_path: narration / voice audio (M3)
      - media: list of media items (video/image paths with optional duration/start/end)
      - template: template name or path to template JSON
      - project_id: optional identifier

    Output:
      - final.mp4 (or configured output path)
      - render.json
    """

    SUPPORTED_IMAGE_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif"}
    SUPPORTED_VIDEO_EXT = {".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v"}
    SUPPORTED_AUDIO_EXT = {".mp3", ".wav", ".aac", ".m4a", ".ogg", ".flac"}

    def __init__(
        self,
        root: Optional[Union[str, Path]] = None,
        progress_callback: Optional[Callable[[float, str], None]] = None,
    ):
        self.root = Path(root or Path(__file__).resolve().parents[2])
        self.templates_dir = self.root / "templates"
        self.output_dir = self.root / "output"
        self.temp_dir = self.root / "temp" / "video_engine"
        self.assets_dir = self.root / "assets"
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.temp_dir.mkdir(parents=True, exist_ok=True)
        self.progress_callback = progress_callback
        self._work: Optional[Path] = None

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def render(
        self,
        script: Optional[Dict[str, Any]] = None,
        audio_path: Optional[Union[str, Path]] = None,
        media: Optional[List[Dict[str, Any]]] = None,
        template: Union[str, Path, Dict[str, Any]] = "default_shorts",
        project_id: Optional[str] = None,
        output_path: Optional[Union[str, Path]] = None,
        subtitles: Optional[List[Dict[str, Any]]] = None,
        music_path: Optional[Union[str, Path]] = None,
        intro: Optional[Dict[str, Any]] = None,
        outro: Optional[Dict[str, Any]] = None,
        force_duration: Optional[float] = None,
    ) -> RenderResult:
        """
        Main render entry point. Builds a timeline and produces final.mp4 + render.json.
        """
        t0 = time.time()
        project_id = project_id or str(uuid.uuid4())[:12]
        self._work = self.temp_dir / project_id
        self._work.mkdir(parents=True, exist_ok=True)

        self._report(0.0, "starting")

        try:
            tpl = self._load_template(template)
            width = int(tpl["width"])
            height = int(tpl["height"])
            fps = float(tpl.get("fps", 30))
            vcodec = tpl.get("video_codec", "libx264")
            acodec = tpl.get("audio_codec", "aac")

            # Normalize inputs from script if provided
            script = script or {}
            if not media and script.get("scenes"):
                media = self._scenes_to_media(script["scenes"])
            if audio_path is None and script.get("audio"):
                audio_path = script["audio"]
            if subtitles is None and script.get("subtitles"):
                subtitles = script["subtitles"]
            if music_path is None and script.get("music"):
                music_path = script["music"]
            if intro is None and script.get("intro"):
                intro = script["intro"]
            if outro is None and script.get("outro"):
                outro = script["outro"]

            # Apply template intro/outro defaults if not overridden
            if intro is None and tpl.get("intro", {}).get("enabled"):
                intro = tpl["intro"]
            if outro is None and tpl.get("outro", {}).get("enabled"):
                outro = tpl["outro"]

            # Validate core inputs
            err = self._validate_inputs(audio_path, media, music_path)
            if err:
                return self._fail(project_id, width, height, fps, vcodec, acodec, err, t0)

            media = media or []
            self._report(0.05, "inputs validated")

            # Resolve output path
            if output_path is None:
                output_path = self.output_dir / f"{project_id}_final.mp4"
            output_path = Path(output_path)
            output_path.parent.mkdir(parents=True, exist_ok=True)

            # Build visual timeline clips (normalized intermediate files)
            self._report(0.1, "preparing visual clips")
            visual_clips, visual_duration = self._prepare_visual_timeline(
                media, width, height, fps, tpl, force_duration, audio_path
            )
            if not visual_clips:
                # Pure color background for the duration of audio (or minimum)
                audio_dur = _media_duration(audio_path) if audio_path else 5.0
                dur = force_duration or max(audio_dur, 1.0)
                bg = self._make_color_clip("black", dur, width, height, fps)
                visual_clips = [bg]
                visual_duration = dur

            self._report(0.35, "visual timeline ready")

            # Concat visuals
            concat_video = self._work / "visual_concat.mp4"
            self._concat_videos(visual_clips, concat_video, fps)
            self._report(0.45, "visuals concatenated")

            # Optional intro / outro prepend/append (before audio so we know final duration)
            timeline_video = concat_video
            if intro or outro:
                timeline_video = self._apply_intro_outro(
                    concat_video, intro, outro, width, height, fps, tpl
                )
                visual_duration = _media_duration(timeline_video)
            self._report(0.55, "intro/outro applied")

            # Handle audio (narration + optional music) sized to final visual duration
            final_audio = self._work / "final_audio.aac"
            audio_ok = self._build_audio_track(
                audio_path, music_path, final_audio, visual_duration, tpl
            )
            self._report(0.65, "audio track ready")

            # Subtitles
            subtitled = timeline_video
            if subtitles and tpl.get("subtitle", {}).get("enabled", True):
                subtitled = self._burn_subtitles(
                    timeline_video, subtitles, tpl, width, height
                )
            self._report(0.75, "subtitles processed")

            # Mux video + audio → final
            self._report(0.8, "muxing final MP4")
            self._mux_final(
                subtitled,
                final_audio if audio_ok else None,
                output_path,
                width,
                height,
                fps,
                vcodec,
                acodec,
                visual_duration,
            )

            # Validate output
            self._report(0.95, "validating output")
            val_err = self._validate_output(output_path, width, height)
            if val_err:
                return self._fail(
                    project_id, width, height, fps, vcodec, acodec, val_err, t0,
                    file=str(output_path),
                )

            duration = _media_duration(output_path)
            result = RenderResult(
                project_id=project_id,
                file=str(output_path.resolve()),
                width=width,
                height=height,
                fps=fps,
                duration=duration,
                video_codec="h264",
                audio_codec="aac",
                status="success",
                error=None,
                progress=1.0,
                render_time_sec=time.time() - t0,
            )
            render_json = output_path.with_name(
                output_path.stem.replace("_final", "") + "_render.json"
            )
            if render_json == output_path:
                render_json = output_path.with_suffix(".render.json")
            # Prefer sibling render.json named after project
            render_json = self.output_dir / f"{project_id}_render.json"
            result.save(render_json)
            self._report(1.0, "done")
            logger.info("Render success: %s (%.2fs)", output_path, result.render_time_sec)
            return result

        except Exception as e:
            logger.exception("Render failed")
            return self._fail(
                project_id,
                1080, 1920, 30, "libx264", "aac",
                RenderError("INTERNAL", str(e)),
                t0,
            )

    # ------------------------------------------------------------------
    # Input helpers
    # ------------------------------------------------------------------

    def _load_template(self, template: Union[str, Path, Dict[str, Any]]) -> Dict[str, Any]:
        if isinstance(template, dict):
            return template
        p = Path(template)
        if p.is_file():
            return json.loads(p.read_text(encoding="utf-8"))
        # name lookup
        candidate = self.templates_dir / f"{template}.json"
        if candidate.is_file():
            return json.loads(candidate.read_text(encoding="utf-8"))
        # fallback
        fallback = self.templates_dir / "default_shorts.json"
        if fallback.is_file():
            return json.loads(fallback.read_text(encoding="utf-8"))
        # hard defaults
        return {
            "name": "fallback",
            "width": 1080,
            "height": 1920,
            "fps": 30,
            "video_codec": "libx264",
            "audio_codec": "aac",
            "subtitle": {"enabled": True, "font_size": 48, "margin_v": 120},
            "scene_defaults": {"fit_mode": "cover", "background": "black"},
        }

    def _scenes_to_media(self, scenes: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        media = []
        for sc in scenes:
            item: Dict[str, Any] = {
                "duration": float(sc.get("duration", 3.0)),
            }
            if sc.get("media"):
                item["path"] = sc["media"]
            elif sc.get("path"):
                item["path"] = sc["path"]
            elif sc.get("color"):
                item["color"] = sc["color"]
            else:
                item["color"] = "black"
            if sc.get("start") is not None:
                item["start"] = float(sc["start"])
            if sc.get("end") is not None:
                item["end"] = float(sc["end"])
            media.append(item)
        return media

    def _validate_inputs(
        self,
        audio_path: Optional[Union[str, Path]],
        media: Optional[List[Dict[str, Any]]],
        music_path: Optional[Union[str, Path]],
    ) -> Optional[RenderError]:
        if audio_path is not None:
            ap = Path(audio_path)
            if not ap.is_file():
                return RenderError(
                    "INPUT_NOT_FOUND",
                    f"Audio file not found: {audio_path}",
                    {"path": str(audio_path)},
                )
            if ap.suffix.lower() not in self.SUPPORTED_AUDIO_EXT | {".mp4", ".mov", ".mkv"}:
                # still allow; ffmpeg may handle it
                pass

        if music_path is not None:
            mp = Path(music_path)
            if not mp.is_file():
                return RenderError(
                    "INPUT_NOT_FOUND",
                    f"Music file not found: {music_path}",
                    {"path": str(music_path)},
                )

        if media:
            for i, m in enumerate(media):
                if "path" in m and m["path"]:
                    p = Path(m["path"])
                    if not p.is_file():
                        return RenderError(
                            "INPUT_NOT_FOUND",
                            f"Media file not found: {m['path']}",
                            {"index": i, "path": str(m["path"])},
                        )
        return None

    # ------------------------------------------------------------------
    # Visual timeline
    # ------------------------------------------------------------------

    def _prepare_visual_timeline(
        self,
        media: List[Dict[str, Any]],
        width: int,
        height: int,
        fps: float,
        tpl: Dict[str, Any],
        force_duration: Optional[float],
        audio_path: Optional[Union[str, Path]],
    ) -> Tuple[List[Path], float]:
        clips: List[Path] = []
        total = 0.0
        fit_mode = tpl.get("scene_defaults", {}).get("fit_mode", "cover")
        bg = tpl.get("scene_defaults", {}).get("background", "black")

        # If no media but we have audio, create full-duration background later
        if not media:
            return [], 0.0

        # If force_duration and single media, stretch/trim to it
        for i, m in enumerate(media):
            out = self._work / f"clip_{i:03d}.mp4"
            path = m.get("path")
            color = m.get("color", bg)
            start = m.get("start")
            end = m.get("end")
            dur = m.get("duration")

            if path:
                p = Path(path)
                ext = p.suffix.lower()
                if ext in self.SUPPORTED_IMAGE_EXT:
                    # Image → video of given duration
                    d = float(dur or 3.0)
                    self._image_to_video(p, out, d, width, height, fps, fit_mode, bg)
                    total += d
                    clips.append(out)
                elif ext in self.SUPPORTED_VIDEO_EXT or _has_video_stream(p):
                    # Video: trim / scale / pad
                    src_dur = _media_duration(p)
                    if start is not None or end is not None:
                        ss = float(start or 0)
                        ee = float(end) if end is not None else src_dur
                        d = max(0.1, ee - ss)
                    elif dur is not None:
                        d = float(dur)
                        ss = 0.0
                    else:
                        d = src_dur
                        ss = 0.0
                    self._video_to_normalized(p, out, ss, d, width, height, fps, fit_mode, bg)
                    total += d
                    clips.append(out)
                else:
                    # Treat as color fallback
                    d = float(dur or 3.0)
                    self._make_color_clip(color, d, width, height, fps, out)
                    total += d
                    clips.append(out)
            else:
                d = float(dur or 3.0)
                self._make_color_clip(color, d, width, height, fps, out)
                total += d
                clips.append(out)

        # If force_duration and we have audio longer/shorter, adjust last clip or pad
        if force_duration is not None and clips:
            target = float(force_duration)
            if total < target - 0.05:
                # pad with black
                pad = self._work / "pad_black.mp4"
                self._make_color_clip("black", target - total, width, height, fps, pad)
                clips.append(pad)
                total = target
            elif total > target + 0.05:
                # trim last clip roughly by re-encoding shorter (simple approach)
                # For precision we could rebuild; for now accept and let mux handle
                pass

        # If audio is longer than visuals, pad
        if audio_path and clips:
            ad = _media_duration(audio_path)
            if ad > total + 0.15:
                pad = self._work / "audio_pad.mp4"
                self._make_color_clip("black", ad - total, width, height, fps, pad)
                clips.append(pad)
                total = ad

        return clips, total

    def _make_color_clip(
        self,
        color: str,
        duration: float,
        width: int,
        height: int,
        fps: float,
        out: Optional[Path] = None,
    ) -> Path:
        if out is None:
            out = self._work / f"color_{uuid.uuid4().hex[:8]}.mp4"
        duration = max(0.1, float(duration))
        # color source + silent audio so concat stays consistent
        cmd = [
            "ffmpeg", "-y",
            "-f", "lavfi", "-i", f"color=c={color}:s={width}x{height}:d={duration:.4f}:r={fps}",
            "-f", "lavfi", "-i", f"anullsrc=channel_layout=stereo:sample_rate=44100:d={duration:.4f}",
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k",
            "-shortest",
            "-t", f"{duration:.4f}",
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0 or not out.is_file():
            raise RuntimeError(f"Failed to create color clip: {err[-500:]}")
        return out

    def _image_to_video(
        self,
        image: Path,
        out: Path,
        duration: float,
        width: int,
        height: int,
        fps: float,
        fit_mode: str,
        bg: str,
    ) -> None:
        duration = max(0.1, float(duration))
        # scale + pad or crop to target
        if fit_mode == "cover":
            vf = (
                f"scale={width}:{height}:force_original_aspect_ratio=increase,"
                f"crop={width}:{height},setsar=1,fps={fps},format=yuv420p"
            )
        else:  # contain
            vf = (
                f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
                f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color={bg},"
                f"setsar=1,fps={fps},format=yuv420p"
            )
        cmd = [
            "ffmpeg", "-y",
            "-loop", "1", "-i", str(image),
            "-f", "lavfi", "-i", f"anullsrc=channel_layout=stereo:sample_rate=44100",
            "-vf", vf,
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20",
            "-c:a", "aac", "-b:a", "128k",
            "-t", f"{duration:.4f}",
            "-shortest",
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0 or not out.is_file():
            raise RuntimeError(f"image_to_video failed for {image}: {err[-500:]}")

    def _video_to_normalized(
        self,
        video: Path,
        out: Path,
        start: float,
        duration: float,
        width: int,
        height: int,
        fps: float,
        fit_mode: str,
        bg: str,
    ) -> None:
        duration = max(0.1, float(duration))
        if fit_mode == "cover":
            vf = (
                f"scale={width}:{height}:force_original_aspect_ratio=increase,"
                f"crop={width}:{height},setsar=1,fps={fps},format=yuv420p"
            )
        else:
            vf = (
                f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
                f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:color={bg},"
                f"setsar=1,fps={fps},format=yuv420p"
            )
        cmd = [
            "ffmpeg", "-y",
            "-ss", f"{start:.4f}",
            "-i", str(video),
            "-t", f"{duration:.4f}",
            "-vf", vf,
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20",
            "-an",  # drop source audio; we mix narration later
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0 or not out.is_file():
            # Retry with explicit audio null if needed
            raise RuntimeError(f"video_to_normalized failed for {video}: {err[-500:]}")

        # Ensure the clip has a silent audio track for consistent concat
        # (re-mux with anullsrc if no audio)
        if not _has_audio_stream(out):
            tmp = out.with_suffix(".tmp.mp4")
            cmd2 = [
                "ffmpeg", "-y",
                "-i", str(out),
                "-f", "lavfi", "-i", f"anullsrc=channel_layout=stereo:sample_rate=44100:d={duration:.4f}",
                "-c:v", "copy",
                "-c:a", "aac", "-b:a", "128k",
                "-shortest",
                str(tmp),
            ]
            rc2, _, err2 = _run(cmd2)
            if rc2 == 0 and tmp.is_file():
                tmp.replace(out)

    def _concat_videos(self, clips: List[Path], out: Path, fps: float) -> None:
        if len(clips) == 1:
            shutil.copy2(clips[0], out)
            return
        list_file = self._work / "concat_list.txt"
        with list_file.open("w", encoding="utf-8") as f:
            for c in clips:
                # Safe path quoting for concat demuxer
                p = str(c.resolve()).replace("'", "'\\''")
                f.write(f"file '{p}'\n")
        cmd = [
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0",
            "-i", str(list_file),
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20",
            "-c:a", "aac", "-b:a", "128k",
            "-pix_fmt", "yuv420p",
            "-r", str(int(fps)),
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0 or not out.is_file():
            raise RuntimeError(f"concat failed: {err[-800:]}")

    # ------------------------------------------------------------------
    # Audio
    # ------------------------------------------------------------------

    def _build_audio_track(
        self,
        narration: Optional[Union[str, Path]],
        music: Optional[Union[str, Path]],
        out: Path,
        target_duration: float,
        tpl: Dict[str, Any],
    ) -> bool:
        """Build final audio (narration + optional ducked music). Returns True if audio produced."""
        if narration is None and music is None:
            # Generate silence matching target duration
            cmd = [
                "ffmpeg", "-y",
                "-f", "lavfi",
                "-i", f"anullsrc=channel_layout=stereo:sample_rate=44100:d={max(0.1, target_duration):.4f}",
                "-c:a", "aac", "-b:a", "128k",
                str(out),
            ]
            rc, _, err = _run(cmd)
            return rc == 0 and out.is_file()

        music_vol = float(tpl.get("music", {}).get("volume", 0.08))
        duck = bool(tpl.get("music", {}).get("duck_under_voice", True))

        if narration and not music:
            # Just normalize / re-encode narration to target length (pad or trim)
            nd = _media_duration(narration)
            cmd = [
                "ffmpeg", "-y",
                "-i", str(narration),
                "-af", f"apad=whole_dur={max(nd, target_duration):.4f}",
                "-t", f"{max(nd, target_duration):.4f}",
                "-c:a", "aac", "-b:a", "192k",
                "-ac", "2", "-ar", "44100",
                str(out),
            ]
            rc, _, err = _run(cmd)
            if rc != 0:
                logger.warning("Narration encode failed: %s", err[-300:])
                return False
            return out.is_file()

        if music and not narration:
            cmd = [
                "ffmpeg", "-y",
                "-stream_loop", "-1",
                "-i", str(music),
                "-t", f"{max(0.1, target_duration):.4f}",
                "-af", f"volume={music_vol}",
                "-c:a", "aac", "-b:a", "192k",
                "-ac", "2", "-ar", "44100",
                str(out),
            ]
            rc, _, err = _run(cmd)
            return rc == 0 and out.is_file()

        # Both narration + music → mix with optional ducking
        # Simple sidechain-ish: lower music when narration is present (approx constant duck)
        if duck:
            # Constant duck of music under voice
            filter_complex = (
                f"[1:a]volume={music_vol},volume=0.35[m];"
                f"[0:a][m]amix=inputs=2:duration=first:dropout_transition=0[a]"
            )
        else:
            filter_complex = (
                f"[1:a]volume={music_vol}[m];"
                f"[0:a][m]amix=inputs=2:duration=first:dropout_transition=0[a]"
            )
        cmd = [
            "ffmpeg", "-y",
            "-i", str(narration),
            "-stream_loop", "-1", "-i", str(music),
            "-filter_complex", filter_complex,
            "-map", "[a]",
            "-t", f"{max(0.1, target_duration):.4f}",
            "-c:a", "aac", "-b:a", "192k",
            "-ac", "2", "-ar", "44100",
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0:
            logger.warning("Audio mix failed, falling back to narration only: %s", err[-300:])
            return self._build_audio_track(narration, None, out, target_duration, tpl)
        return out.is_file()

    # ------------------------------------------------------------------
    # Intro / Outro
    # ------------------------------------------------------------------

    def _apply_intro_outro(
        self,
        main_video: Path,
        intro: Optional[Dict[str, Any]],
        outro: Optional[Dict[str, Any]],
        width: int,
        height: int,
        fps: float,
        tpl: Dict[str, Any],
    ) -> Path:
        parts: List[Path] = []
        if intro:
            idur = float(intro.get("duration", 2.0))
            itext = intro.get("text", "AVSP")
            ibg = intro.get("background", "black")
            icolor = intro.get("text_color", "white")
            ifsize = int(intro.get("font_size", 72))
            intro_clip = self._work / "intro.mp4"
            self._make_text_card(intro_clip, itext, idur, width, height, fps, ibg, icolor, ifsize)
            parts.append(intro_clip)
        parts.append(main_video)
        if outro:
            odur = float(outro.get("duration", 2.0))
            otext = outro.get("text", "Thanks for watching")
            obg = outro.get("background", "black")
            ocolor = outro.get("text_color", "white")
            ofsize = int(outro.get("font_size", 56))
            outro_clip = self._work / "outro.mp4"
            self._make_text_card(outro_clip, otext, odur, width, height, fps, obg, ocolor, ofsize)
            parts.append(outro_clip)

        out = self._work / "with_intro_outro.mp4"
        self._concat_videos(parts, out, fps)
        return out

    def _make_text_card(
        self,
        out: Path,
        text: str,
        duration: float,
        width: int,
        height: int,
        fps: float,
        bg: str,
        color: str,
        font_size: int,
    ) -> None:
        duration = max(0.1, float(duration))
        escaped = _escape_drawtext(text)
        # Prefer a system font if available
        fontfile = self._find_font()
        font_opt = f":fontfile={fontfile}" if fontfile else ""
        vf = (
            f"drawtext=text='{escaped}':fontsize={font_size}:fontcolor={color}"
            f"{font_opt}:x=(w-text_w)/2:y=(h-text_h)/2"
        )
        cmd = [
            "ffmpeg", "-y",
            "-f", "lavfi", "-i", f"color=c={bg}:s={width}x{height}:d={duration:.4f}:r={fps}",
            "-f", "lavfi", "-i", f"anullsrc=channel_layout=stereo:sample_rate=44100:d={duration:.4f}",
            "-vf", vf,
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k",
            "-shortest",
            "-t", f"{duration:.4f}",
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0 or not out.is_file():
            # Fallback without text
            logger.warning("drawtext failed, using plain color: %s", err[-300:])
            self._make_color_clip(bg, duration, width, height, fps, out)

    def _find_font(self) -> Optional[str]:
        candidates = [
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        ]
        for c in candidates:
            if Path(c).is_file():
                return c
        return None

    # ------------------------------------------------------------------
    # Subtitles
    # ------------------------------------------------------------------

    def _burn_subtitles(
        self,
        video: Path,
        subtitles: List[Dict[str, Any]],
        tpl: Dict[str, Any],
        width: int,
        height: int,
    ) -> Path:
        if not subtitles:
            return video
        ass_path = self._work / "subs.ass"
        sub_cfg = tpl.get("subtitle", {})
        self._write_ass(ass_path, subtitles, width, height, sub_cfg)
        out = self._work / "subtitled.mp4"
        # Use subtitles filter (libass)
        # Escape path for filter
        ass_esc = str(ass_path.resolve()).replace("\\", "/").replace(":", "\\:")
        cmd = [
            "ffmpeg", "-y",
            "-i", str(video),
            "-vf", f"ass={ass_esc}",
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20",
            "-c:a", "copy",
            "-pix_fmt", "yuv420p",
            str(out),
        ]
        rc, _, err = _run(cmd)
        if rc != 0 or not out.is_file():
            logger.warning("ASS burn failed, trying subtitles filter / skip: %s", err[-400:])
            # Fallback: try force_style with srt
            srt_path = self._work / "subs.srt"
            self._write_srt(srt_path, subtitles)
            srt_esc = str(srt_path.resolve()).replace("\\", "/").replace(":", "\\:")
            fs = int(sub_cfg.get("font_size", 48))
            mv = int(sub_cfg.get("margin_v", 120))
            cmd2 = [
                "ffmpeg", "-y",
                "-i", str(video),
                "-vf", f"subtitles={srt_esc}:force_style='FontSize={fs},MarginV={mv},Outline=3'",
                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20",
                "-c:a", "copy",
                "-pix_fmt", "yuv420p",
                str(out),
            ]
            rc2, _, err2 = _run(cmd2)
            if rc2 != 0 or not out.is_file():
                logger.warning("Subtitle burn failed entirely, continuing without: %s", err2[-300:])
                return video
        return out

    def _write_ass(
        self,
        path: Path,
        subs: List[Dict[str, Any]],
        width: int,
        height: int,
        cfg: Dict[str, Any],
    ) -> None:
        fs = int(cfg.get("font_size", 48))
        mv = int(cfg.get("margin_v", 120))
        primary = cfg.get("font_color", "white")
        # Convert simple color names to ASS &HBBGGRR&
        color_map = {
            "white": "&H00FFFFFF",
            "black": "&H00000000",
            "yellow": "&H0000FFFF",
            "red": "&H000000FF",
        }
        primary_ass = color_map.get(str(primary).lower(), "&H00FFFFFF")
        outline = color_map.get(str(cfg.get("outline_color", "black")).lower(), "&H00000000")
        align = int(cfg.get("alignment", 2))
        header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {width}
PlayResY: {height}
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,DejaVu Sans,{fs},{primary_ass},&H000000FF,{outline},&H80000000,-1,0,0,0,100,100,0,0,1,3,1,{align},40,40,{mv},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
        lines = [header]
        for s in subs:
            start = float(s.get("start", 0))
            end = float(s.get("end", start + 2))
            text = _escape_ass(str(s.get("text", "")))
            lines.append(
                f"Dialogue: 0,{_seconds_to_ass_time(start)},{_seconds_to_ass_time(end)},Default,,0,0,0,,{text}\n"
            )
        path.write_text("".join(lines), encoding="utf-8")

    def _write_srt(self, path: Path, subs: List[Dict[str, Any]]) -> None:
        def ts(t: float) -> str:
            h = int(t // 3600)
            m = int((t % 3600) // 60)
            s = int(t % 60)
            ms = int(round((t - int(t)) * 1000))
            return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

        parts = []
        for i, s in enumerate(subs, 1):
            start = float(s.get("start", 0))
            end = float(s.get("end", start + 2))
            text = str(s.get("text", "")).replace("\n", "\n")
            parts.append(f"{i}\n{ts(start)} --> {ts(end)}\n{text}\n")
        path.write_text("\n".join(parts), encoding="utf-8")

    # ------------------------------------------------------------------
    # Final mux
    # ------------------------------------------------------------------

    def _mux_final(
        self,
        video: Path,
        audio: Optional[Path],
        out: Path,
        width: int,
        height: int,
        fps: float,
        vcodec: str,
        acodec: str,
        target_duration: float,
    ) -> None:
        if audio and audio.is_file():
            cmd = [
                "ffmpeg", "-y",
                "-i", str(video),
                "-i", str(audio),
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c:v", vcodec, "-preset", "fast", "-crf", "20",
                "-pix_fmt", "yuv420p",
                "-c:a", acodec, "-b:a", "192k",
                "-shortest",
                "-movflags", "+faststart",
                "-r", str(int(fps)),
                str(out),
            ]
        else:
            cmd = [
                "ffmpeg", "-y",
                "-i", str(video),
                "-c:v", vcodec, "-preset", "fast", "-crf", "20",
                "-pix_fmt", "yuv420p",
                "-an",
                "-movflags", "+faststart",
                "-r", str(int(fps)),
                str(out),
            ]
        rc, _, err = _run(cmd, timeout=900)
        if rc != 0 or not out.is_file():
            raise RuntimeError(f"Final mux failed: {err[-800:]}")

    def _validate_output(
        self, path: Path, expected_w: int, expected_h: int
    ) -> Optional[RenderError]:
        if not path.is_file():
            return RenderError("OUTPUT_MISSING", f"Output file not created: {path}")
        if path.stat().st_size < 1000:
            return RenderError("OUTPUT_TOO_SMALL", f"Output suspiciously small: {path.stat().st_size} bytes")
        info = _ffprobe_json(path)
        if not info:
            return RenderError("OUTPUT_UNREADABLE", "ffprobe could not read output")
        streams = info.get("streams", [])
        vstreams = [s for s in streams if s.get("codec_type") == "video"]
        astreams = [s for s in streams if s.get("codec_type") == "audio"]
        if not vstreams:
            return RenderError("NO_VIDEO_STREAM", "Output has no video stream")
        vs = vstreams[0]
        w = int(vs.get("width", 0))
        h = int(vs.get("height", 0))
        if w != expected_w or h != expected_h:
            return RenderError(
                "DIMENSION_MISMATCH",
                f"Expected {expected_w}x{expected_h}, got {w}x{h}",
                {"width": w, "height": h},
            )
        duration = float(info.get("format", {}).get("duration", 0) or 0)
        if duration < 0.05:
            return RenderError("DURATION_TOO_SHORT", f"Duration {duration}s is too short")
        # Check for common corruption signals is limited; size + streams is good baseline
        return None

    # ------------------------------------------------------------------
    # Progress / failure helpers
    # ------------------------------------------------------------------

    def _report(self, progress: float, message: str) -> None:
        logger.info("[%.0f%%] %s", progress * 100, message)
        if self.progress_callback:
            try:
                self.progress_callback(progress, message)
            except Exception:
                pass

    def _fail(
        self,
        project_id: str,
        width: int,
        height: int,
        fps: float,
        vcodec: str,
        acodec: str,
        error: RenderError,
        t0: float,
        file: Optional[str] = None,
    ) -> RenderResult:
        result = RenderResult(
            project_id=project_id,
            file=file,
            width=width,
            height=height,
            fps=fps,
            duration=0.0,
            video_codec=vcodec.replace("lib", ""),
            audio_codec=acodec,
            status="error",
            error=error.to_dict(),
            progress=0.0,
            render_time_sec=time.time() - t0,
        )
        try:
            rj = self.output_dir / f"{project_id}_render.json"
            result.save(rj)
        except Exception:
            pass
        return result


# ---------------------------------------------------------------------------
# CLI for independent testing
# ---------------------------------------------------------------------------

def main():
    import argparse

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="AVSP M4 Video Engine")
    parser.add_argument("--script", help="Path to M2 script JSON")
    parser.add_argument("--audio", help="Narration audio path")
    parser.add_argument("--media", nargs="*", help="Media files (images/videos)")
    parser.add_argument("--template", default="default_shorts")
    parser.add_argument("--output", help="Output MP4 path")
    parser.add_argument("--project-id", default=None)
    parser.add_argument("--duration", type=float, default=None, help="Force total duration")
    parser.add_argument("--music", default=None)
    parser.add_argument("--intro-text", default=None)
    parser.add_argument("--outro-text", default=None)
    args = parser.parse_args()

    script = None
    if args.script:
        script = json.loads(Path(args.script).read_text(encoding="utf-8"))

    media = None
    if args.media:
        media = [{"path": m} for m in args.media]

    intro = {"enabled": True, "text": args.intro_text, "duration": 2.0} if args.intro_text else None
    outro = {"enabled": True, "text": args.outro_text, "duration": 2.0} if args.outro_text else None

    engine = VideoEngine()
    result = engine.render(
        script=script,
        audio_path=args.audio,
        media=media,
        template=args.template,
        project_id=args.project_id,
        output_path=args.output,
        music_path=args.music,
        intro=intro,
        outro=outro,
        force_duration=args.duration,
    )
    print(json.dumps(result.to_dict(), indent=2, ensure_ascii=False))
    if result.status != "success":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
