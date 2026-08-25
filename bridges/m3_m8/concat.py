"""
Lossless PCM WAV concatenation for M3 segment files.
"""

from __future__ import annotations

import wave
from pathlib import Path
from typing import Iterable, List, Sequence

from .adapter import M3ContractError, M3SegmentRef, M3VoicePackage, read_wav_format


def concat_segment_wavs(
    segments: Sequence[M3SegmentRef],
    output_path: Path,
) -> Path:
    """
    Concatenate ordered segment WAVs into one PCM WAV.

    Requires identical channels / sampwidth / framerate (validated).
    No MP3 conversion — M8 EffectsComposer accepts WAV.
    """
    if not segments:
        raise M3ContractError("no segments to concatenate")

    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    first = read_wav_format(segments[0].absolute_path)
    frames: List[bytes] = []
    for seg in segments:
        fmt = read_wav_format(seg.absolute_path)
        if (
            fmt["channels"] != first["channels"]
            or fmt["sampwidth"] != first["sampwidth"]
            or fmt["framerate"] != first["framerate"]
        ):
            raise M3ContractError(
                f"format mismatch concatenating {seg.segment_id}: {fmt} vs {first}"
            )
        with wave.open(str(seg.absolute_path), "rb") as w:
            frames.append(w.readframes(w.getnframes()))

    with wave.open(str(output_path), "wb") as out:
        out.setnchannels(first["channels"])
        out.setsampwidth(first["sampwidth"])
        out.setframerate(first["framerate"])
        out.writeframes(b"".join(frames))

    return output_path.resolve()


def concat_voice_package(voice: M3VoicePackage, output_path: Path) -> Path:
    return concat_segment_wavs(voice.segments, output_path)
