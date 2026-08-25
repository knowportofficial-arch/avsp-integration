#!/usr/bin/env python3
"""
AVSP M8 — One-command autonomous video production pipeline.

Usage:
  python main.py --topic "Belda Railway Station" --duration 5m --format 9:16
  python main.py --topic "Test" --duration 15s --format 9:16 --skip-render
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

# Ensure project root on path
ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
# Do NOT put M4 on sys.path first — it shadows app.*
# M4Adapter loads M4 via importlib under m4_frozen namespace.

from app.m8.controller import AutonomousProductionController
from app.m8.errors import M8Error


def main():
    parser = argparse.ArgumentParser(description="AVSP M8 Autonomous Production Controller")
    parser.add_argument("--topic", required=True, help="Video topic / subject")
    parser.add_argument("--duration", default="5m", help="Target duration (e.g. 5m, 30s)")
    parser.add_argument("--format", dest="format_name", default="9:16", choices=["9:16", "16:9", "shorts", "landscape"])
    parser.add_argument("--project-id", default=None)
    parser.add_argument("--skip-render", action="store_true", help="Skip M4 render (planning only)")
    parser.add_argument("--root", default=None, help="Project root override")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    ctrl = AutonomousProductionController(root=Path(args.root) if args.root else ROOT)
    try:
        result = ctrl.run(
            topic=args.topic,
            duration=args.duration,
            format_name=args.format_name,
            project_id=args.project_id,
            skip_render=args.skip_render,
        )
        print("\n=== M8 PIPELINE COMPLETE ===")
        print(f"Project: {result.get('project_id')}")
        print(f"Topic:   {result.get('topic')}")
        print(f"MP4:     {result.get('final_mp4')}")
        qc = result.get("qc") or {}
        print(f"QC:      {qc.get('status')}")
        failed = [s for s in result.get("stages", []) if s.get("status") == "error"]
        if failed:
            print("Failed stages:", failed)
            sys.exit(1)
        sys.exit(0)
    except M8Error as e:
        print(f"\nM8 ERROR: {e}", file=sys.stderr)
        print(json.dumps(e.to_dict(), indent=2), file=sys.stderr)
        sys.exit(2)
    except Exception as e:
        print(f"\nUNEXPECTED: {e}", file=sys.stderr)
        sys.exit(3)


if __name__ == "__main__":
    main()
