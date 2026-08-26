"""
Helpers to build synthetic M3-like voice packages for Phase 2C tests.
Deterministic PCM tone WAVs — same contract as MockTtsEngine / WavEncoder.
"""

from __future__ import annotations

import json
import math
import struct
import wave
from pathlib import Path
from typing import List, Sequence, Tuple


SAMPLE_RATE = 16000
CHANNELS = 1
SAMPWIDTH = 2


def write_tone_wav(path: Path, duration_ms: int, freq_hz: float = 440.0) -> int:
    """Write mono pcm_s16le WAV; returns actual duration_ms from frame count."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    n = max(1, int(SAMPLE_RATE * duration_ms / 1000))
    frames = bytearray()
    for i in range(n):
        env = 1.0
        if i < SAMPLE_RATE // 50:
            env = i / (SAMPLE_RATE / 50)
        elif i > n - SAMPLE_RATE // 50:
            env = (n - i) / (SAMPLE_RATE / 50)
        sample = int(0.25 * env * 32767 * math.sin(2 * math.pi * freq_hz * i / SAMPLE_RATE))
        frames += struct.pack("<h", max(-32767, min(32767, sample)))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(CHANNELS)
        w.setsampwidth(SAMPWIDTH)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(bytes(frames))
    return int(round(1000.0 * n / SAMPLE_RATE))


def build_m3_fixture(
    root: Path,
    *,
    project_id: str = "prj_phase2c",
    audio_package_id: str = "aud_phase2cfixture01",
    segment_ms: Sequence[int] = (8000, 7000, 10000),
    topic_hint: str = "Airplane Mode",
) -> Path:
    """
    Create:
      root/generated/audio/{aud}/aseg_NN.wav
      root/generated/audio/{aud}/package.json
      root/generated/audio/voice.json
    Returns path to generated/audio (discoverable).
    """
    audio_root = Path(root) / "generated" / "audio"
    pkg_dir = audio_root / audio_package_id
    pkg_dir.mkdir(parents=True, exist_ok=True)

    segments = []
    cursor = 0
    freqs = (330.0, 440.0, 550.0, 660.0)
    for i, ms in enumerate(segment_ms):
        seg_id = f"aseg_{i:02d}"
        rel = f"generated/audio/{audio_package_id}/{seg_id}.wav"
        abs_path = Path(root) / rel
        actual = write_tone_wav(abs_path, ms, freqs[i % len(freqs)])
        start = cursor
        end = start + actual
        segments.append(
            {
                "segmentId": seg_id,
                "sceneId": f"scene_{i+1}",
                "order": i,
                "sourceText": f"{topic_hint} segment {i+1}",
                "relativeAudioPath": rel,
                "durationMs": actual,
                "startMs": start,
                "endMs": end,
                "provider": "mock",
                "language": "en",
                "status": "GENERATED",
                "plannedDurationMs": ms,
                "durationDeltaMs": actual - ms,
            }
        )
        cursor = end

    total = sum(s["durationMs"] for s in segments)
    package = {
        "version": "1.0",
        "projectId": project_id,
        "scriptId": "scr_phase2c",
        "audioPackageId": audio_package_id,
        "language": "en",
        "provider": "mock",
        "voice": {
            "language": "en",
            "voiceId": "default",
            "speechRate": 1.0,
            "pitch": 1.0,
            "volume": 1.0,
            "providerId": "mock",
        },
        "segments": segments,
        "totalDurationMs": total,
        "validation": {"isValid": True, "status": "VALID", "warnings": [], "errors": []},
        "metadata": {
            "createdAt": 0,
            "updatedAt": 0,
            "scriptVersion": "1.0",
            "format": {
                "format": "wav",
                "sampleRateHz": SAMPLE_RATE,
                "channels": CHANNELS,
                "encoding": "pcm_s16le",
                "bitsPerSample": 16,
            },
            "voice": {
                "language": "en",
                "voiceId": "default",
                "speechRate": 1.0,
                "pitch": 1.0,
                "volume": 1.0,
                "providerId": "mock",
            },
            "timingDriftWarnings": [],
        },
    }
    (pkg_dir / "package.json").write_text(json.dumps(package, indent=2), encoding="utf-8")
    (audio_root / "voice.json").write_text(json.dumps(package, indent=2), encoding="utf-8")
    return audio_root
