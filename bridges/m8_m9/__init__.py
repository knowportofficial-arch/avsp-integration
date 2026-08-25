"""
AVSP Phase 2A — M8 → M9 production bridge (Windows/desktop).

Does not rewrite M8 or M9 engines. Thin adapter + orchestration only.
"""

from .adapter import M8ToM9Adapter, M8ProjectBundle
from .seo import SEOPackage, build_seo_package
from .pipeline import run_m8_to_m9_mock

__all__ = [
    "M8ToM9Adapter",
    "M8ProjectBundle",
    "SEOPackage",
    "build_seo_package",
    "run_m8_to_m9_mock",
]
