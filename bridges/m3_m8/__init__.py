"""
AVSP Phase 2C — M3 → M8 voice bridge (Windows/desktop).

Does not rewrite M3 or M8 engines. Thin adapter + orchestration only.
"""

from .adapter import M3VoicePackage, discover_m3_package
from .concat import concat_segment_wavs
from .pipeline import run_m3_to_m8, run_m3_to_m8_to_m9_mock

__all__ = [
    "M3VoicePackage",
    "discover_m3_package",
    "concat_segment_wavs",
    "run_m3_to_m8",
    "run_m3_to_m8_to_m9_mock",
]
