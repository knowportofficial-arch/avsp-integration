"""
M3 voice package discovery + validation (read-only toward M3 artifacts).
"""

from __future__ import annotations

import json
import struct
import wave
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


class M3ContractError(ValueError):
    """Raised when an M3 package fails discovery or validation."""


@dataclass
class M3SegmentRef:
    segment_id: str
    scene_id: str
    order: int
    relative_audio_path: str
    absolute_path: Path
    start_ms: int
    end_ms: int
    duration_ms: int
    source_text: str = ""
    language: str = ""


@dataclass
class M3VoicePackage:
    root: Path
    voice_json_path: Path
    package: Dict[str, Any]
    project_id: str
    script_id: str
    audio_package_id: str
    language: str
    total_duration_ms: int
    segments: List[M3SegmentRef] = field(default_factory=list)

    @property
    def total_duration_sec(self) -> float:
        return self.total_duration_ms / 1000.0


def _read_json(path: Path) -> Dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise M3ContractError(f"expected object JSON: {path}")
    return data


def _resolve_voice_json(package_dir: Path) -> Path:
    """
    Accept:
      - directory containing voice.json
      - directory containing package.json (AudioPackage)
      - path to voice.json / package.json
      - generated/audio/ tree (prefer voice.json)
    """
    p = Path(package_dir)
    if p.is_file():
        if p.name in ("voice.json", "package.json"):
            return p
        raise M3ContractError(f"not an M3 voice JSON file: {p}")

    candidates = [
        p / "voice.json",
        p / "generated" / "audio" / "voice.json",
    ]
    # package.json under aud_* child
    for child in sorted(p.glob("aud_*/package.json")):
        candidates.append(child)
    if (p / "package.json").exists():
        candidates.append(p / "package.json")

    for c in candidates:
        if c.exists():
            return c
    raise M3ContractError(f"no voice.json/package.json under {p}")


def discover_m3_package(package_dir: Path) -> M3VoicePackage:
    voice_path = _resolve_voice_json(Path(package_dir))
    pkg = _read_json(voice_path)

    # Resolve root for relative segment paths.
    # voice.json lives at generated/audio/voice.json → project root is parents[2]
    # package.json lives at generated/audio/{aud_id}/package.json → project root parents[3]
    if voice_path.name == "voice.json" and voice_path.parent.name == "audio":
        project_root = voice_path.parents[2] if len(voice_path.parents) >= 3 else voice_path.parent
        search_roots = [project_root, voice_path.parent, voice_path.parent.parent]
    elif voice_path.name == "package.json":
        # .../generated/audio/{aud}/package.json
        project_root = voice_path.parents[3] if len(voice_path.parents) >= 4 else voice_path.parent
        search_roots = [project_root, voice_path.parent, voice_path.parents[1]]
    else:
        project_root = voice_path.parent
        search_roots = [project_root, voice_path.parent]

    segments_raw = pkg.get("segments") or []
    if not segments_raw:
        raise M3ContractError("AudioPackage has no segments")

    segments: List[M3SegmentRef] = []
    for raw in sorted(segments_raw, key=lambda s: int(s.get("order", 0))):
        rel = raw.get("relativeAudioPath") or raw.get("relative_audio_path") or ""
        if not rel:
            raise M3ContractError(f"segment missing relativeAudioPath: {raw.get('segmentId')}")
        abs_path = _resolve_segment_file(rel, search_roots, voice_path.parent)
        segments.append(
            M3SegmentRef(
                segment_id=str(raw.get("segmentId") or raw.get("segment_id") or ""),
                scene_id=str(raw.get("sceneId") or raw.get("scene_id") or ""),
                order=int(raw.get("order", 0)),
                relative_audio_path=rel,
                absolute_path=abs_path,
                start_ms=int(raw.get("startMs") or raw.get("start_ms") or 0),
                end_ms=int(raw.get("endMs") or raw.get("end_ms") or 0),
                duration_ms=int(raw.get("durationMs") or raw.get("duration_ms") or 0),
                source_text=str(raw.get("sourceText") or raw.get("source_text") or ""),
                language=str(raw.get("language") or pkg.get("language") or ""),
            )
        )

    return M3VoicePackage(
        root=project_root,
        voice_json_path=voice_path,
        package=pkg,
        project_id=str(pkg.get("projectId") or pkg.get("project_id") or ""),
        script_id=str(pkg.get("scriptId") or pkg.get("script_id") or ""),
        audio_package_id=str(pkg.get("audioPackageId") or pkg.get("audio_package_id") or ""),
        language=str(pkg.get("language") or "en"),
        total_duration_ms=int(pkg.get("totalDurationMs") or pkg.get("total_duration_ms") or 0),
        segments=segments,
    )


def _resolve_segment_file(rel: str, search_roots: List[Path], fallback: Path) -> Path:
    rel_p = Path(rel)
    if rel_p.is_absolute() and rel_p.exists():
        return rel_p
    for root in search_roots:
        cand = root / rel
        if cand.exists():
            return cand.resolve()
    # Bare filename next to package
    name = rel_p.name
    for root in list(search_roots) + [fallback]:
        cand = root / name
        if cand.exists():
            return cand.resolve()
        # aud_* folder sibling
        for child in root.glob(f"*/{name}"):
            return child.resolve()
    raise M3ContractError(f"segment WAV not found: {rel}")


def read_wav_format(path: Path) -> Dict[str, int]:
    try:
        with wave.open(str(path), "rb") as w:
            return {
                "channels": w.getnchannels(),
                "sampwidth": w.getsampwidth(),
                "framerate": w.getframerate(),
                "nframes": w.getnframes(),
            }
    except wave.Error as e:
        raise M3ContractError(f"invalid WAV: {path}: {e}") from e


def wav_duration_ms(path: Path) -> int:
    fmt = read_wav_format(path)
    if fmt["framerate"] <= 0:
        return 0
    return int(round(1000.0 * fmt["nframes"] / fmt["framerate"]))


def validate_m3_package(
    voice: M3VoicePackage,
    *,
    expect_sample_rate: int = 16000,
    expect_channels: int = 1,
    expect_sampwidth: int = 2,
) -> List[str]:
    """Return list of validation errors (empty = ok)."""
    errors: List[str] = []
    if not voice.segments:
        errors.append("no segments")
    if voice.total_duration_ms <= 0:
        errors.append("totalDurationMs must be > 0")

    sum_ms = sum(s.duration_ms for s in voice.segments)
    if voice.total_duration_ms and sum_ms and voice.total_duration_ms != sum_ms:
        errors.append(
            f"totalDurationMs={voice.total_duration_ms} != sum(segment.durationMs)={sum_ms}"
        )

    cursor = 0
    for s in voice.segments:
        if not s.absolute_path.exists():
            errors.append(f"missing file: {s.absolute_path}")
            continue
        if s.start_ms != cursor:
            errors.append(
                f"non-contiguous timeline at {s.segment_id}: startMs={s.start_ms} expected {cursor}"
            )
        if s.end_ms != s.start_ms + s.duration_ms:
            errors.append(f"endMs mismatch for {s.segment_id}")
        cursor = s.end_ms

        try:
            fmt = read_wav_format(s.absolute_path)
        except M3ContractError as e:
            errors.append(str(e))
            continue
        if fmt["framerate"] != expect_sample_rate:
            errors.append(
                f"{s.segment_id}: sample rate {fmt['framerate']} != {expect_sample_rate}"
            )
        if fmt["channels"] != expect_channels:
            errors.append(f"{s.segment_id}: channels {fmt['channels']} != {expect_channels}")
        if fmt["sampwidth"] != expect_sampwidth:
            errors.append(
                f"{s.segment_id}: sampwidth {fmt['sampwidth']} != {expect_sampwidth} (16-bit)"
            )
        # pcm_s16le: linear PCM — wave module implies PCM for standard WAV
        file_ms = wav_duration_ms(s.absolute_path)
        if abs(file_ms - s.duration_ms) > 50:
            errors.append(
                f"{s.segment_id}: file duration {file_ms}ms vs metadata {s.duration_ms}ms"
            )

    return errors


def assert_valid(voice: M3VoicePackage) -> None:
    errs = validate_m3_package(voice)
    if errs:
        raise M3ContractError("; ".join(errs))
