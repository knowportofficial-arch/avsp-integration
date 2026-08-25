# Complete Code Patch — Caption Burn Hard-Fail (Windows-compatible)

## Files changed
1. `app/engines/effects_composer.py` — primary fix
2. `app/engines/visual_verifier.py` — VERIFIED requires burn success
3. `app/engines/final_qc.py` — QC FAIL when burn fails
4. `tests/test_caption_burn_hardfail.py` — new tests

---

## 1. `app/engines/effects_composer.py`

### A. In `compose()`, initialize flags (near top of method):

```python
        self.work_dir.mkdir(parents=True, exist_ok=True)
        self._rendered_transitions = []
        self._caption_burn_ok = False
        self._caption_burn_method = "pending"
        output_path = Path(output_path)
```

### B. In the success return `effects` dict, add:

```python
                "caption_count": len(timeline.captions),
                "caption_burn_ok": getattr(self, "_caption_burn_ok", False),
                "caption_burn_method": getattr(self, "_caption_burn_method", "unknown"),
                "bgm": bool(timeline.music and timeline.music.path),
```

### C. Replace entire `_burn_captions` method with:

```python
    def _burn_captions(self, visual: Path, timeline: Timeline, out: Path, w: int, h: int) -> None:
        """
        Burn captions into video. Cross-platform:
        1) Prefer drawtext (no libass / original_size issues on Windows)
        2) Optional ASS attempt only as secondary
        HARD FAIL if captions were planned and burn fails — never silently copy.
        """
        import shutil
        if not timeline.captions:
            shutil.copy(visual, out)
            self._caption_burn_ok = True
            self._caption_burn_method = "none"
            return

        # Safe bottom margin for 9:16
        margin_v = 140 if h > w else 80
        fontsize = 48 if h > w else 36
        y_expr = f"h-{margin_v}-{fontsize}"

        filter_parts = []
        current = "[0:v]"
        for i, c in enumerate(timeline.captions):
            raw = (c.text or "").replace("\\", "\\\\").replace(":", "\\:").replace("'", "").replace("%", "")
            raw = raw.replace(",", " ").replace("[", "(").replace("]", ")")
            text = raw[:120]
            if not text.strip():
                continue
            start_t = float(c.start)
            end_t = float(c.end)
            enable = f"between(t\\,{start_t:.3f}\\,{end_t:.3f})"
            label = f"[c{i}]"
            fs = fontsize + (8 if c.emphasis else 0)
            color = "yellow" if c.emphasis else "white"
            filter_parts.append(
                f"{current}drawtext=text='{text}':fontsize={fs}:fontcolor={color}:"
                f"borderw=3:bordercolor=black:x=(w-text_w)/2:y={y_expr}:"
                f"enable='{enable}'{label}"
            )
            current = label

        if not filter_parts:
            shutil.copy(visual, out)
            self._caption_burn_ok = True
            self._caption_burn_method = "empty_text"
            return

        filt = ";".join(filter_parts)
        cmd = [
            "ffmpeg", "-y", "-i", str(visual),
            "-filter_complex", filt,
            "-map", current,
            "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
            str(out),
        ]
        rc, _, err = _run(cmd, timeout=900)
        if rc == 0 and out.exists() and out.stat().st_size > 1000:
            self._caption_burn_ok = True
            self._caption_burn_method = "drawtext"
            logger.info("Captions burned via drawtext (%d events)", len(timeline.captions))
            return

        # Secondary: ASS with Windows-safe relative path (cwd=work_dir)
        drawtext_err = err[-400:] if err else "unknown"
        ass = self.work_dir / "captions.ass"
        margin = margin_v
        lines = [
            "[Script Info]",
            "ScriptType: v4.00+",
            f"PlayResX: {w}",
            f"PlayResY: {h}",
            "WrapStyle: 0",
            "",
            "[V4+ Styles]",
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, "
            "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
            "Alignment, MarginL, MarginR, MarginV, Encoding",
            f"Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,3,0,2,40,40,{margin},1",
            "",
            "[Events]",
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        ]

        def ts(sec: float) -> str:
            h_ = int(sec // 3600)
            m = int((sec % 3600) // 60)
            s = sec % 60
            return f"{h_}:{m:02d}:{s:05.2f}"

        for c in timeline.captions:
            text = (c.text or "").replace("\n", "\\N").replace("{", "(").replace("}", ")")[:200]
            lines.append(f"Dialogue: 0,{ts(c.start)},{ts(c.end)},Default,,0,0,0,,{text}")
        ass.write_text("\n".join(lines), encoding="utf-8")

        ass_name = "captions.ass"
        cmd2 = [
            "ffmpeg", "-y", "-i", str(visual.resolve()),
            "-vf", f"subtitles={ass_name}",
            "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
            str(out.resolve()),
        ]
        try:
            import subprocess
            proc = subprocess.run(
                cmd2, capture_output=True, text=True, timeout=900,
                cwd=str(self.work_dir),
            )
            rc2, err2 = proc.returncode, (proc.stderr or "")
        except Exception as e:
            rc2, err2 = -1, str(e)

        if rc2 == 0 and out.exists() and out.stat().st_size > 1000:
            self._caption_burn_ok = True
            self._caption_burn_method = "ass_subtitles"
            logger.info("Captions burned via ASS subtitles filter")
            return

        # HARD FAIL — do not copy uncaptioned video and claim success
        self._caption_burn_ok = False
        self._caption_burn_method = "failed"
        raise M8Error(
            ErrorCode.CAPTION_ERROR,
            "Caption burn failed on all methods (drawtext + ASS). "
            f"drawtext_err={drawtext_err[:200]} ass_err={err2[-200:]}",
            stage="caption_burn",
            details={
                "drawtext_error": drawtext_err[:500],
                "ass_error": err2[-500:],
                "caption_count": len(timeline.captions),
            },
        )
```

**DELETE** the old logic that did:
```python
# OLD (removed) — caused false QC PASS on Windows
if rc != 0:
    logger.warning("subtitle burn failed: ...")
    shutil.copy(visual, out)   # ← forbidden
```

---

## 2. `app/engines/visual_verifier.py`

Replace caption verification block with:

```python
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
```

---

## 3. `app/engines/final_qc.py`

Add after effects/meta loading (before critical_fail):

```python
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
```

Include `"captions"` in critical failure keys:

```python
        critical_fail = any(
            checks.get(k) == "FAIL" for k in ("resolution", "video_codec", "cta", "missing_assets", "captions")
        )
```

---

## 4. New test: `tests/test_caption_burn_hardfail.py`

```python
"""Caption burn must succeed or hard-fail — never silent PASS."""
from __future__ import annotations
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path = [x for x in sys.path if "vendor/m4" not in str(x)]

from app.engines.effects_composer import EffectsComposer
from app.schemas.timeline import Timeline, VisualEvent, CaptionEvent


class TestCaptionBurn(unittest.TestCase):
    def test_drawtext_caption_burn_succeeds(self):
        out_dir = ROOT / "projects" / "_caption_qa"
        out_dir.mkdir(parents=True, exist_ok=True)
        tl = Timeline(
            project_id="cap_ok",
            format="9:16", width=1080, height=1920, fps=30, duration=3.0,
            visual=[VisualEvent(start=0, end=3, path="", media_type="color")],
            captions=[CaptionEvent(start=0.2, end=2.8, text="BELDA RAILWAY STATION")],
        )
        out = out_dir / "captions_ok.mp4"
        result = EffectsComposer(work_dir=out_dir / "work").compose(tl, out)
        self.assertEqual(result["status"], "success")
        self.assertTrue(result["effects"]["caption_burn_ok"])
        self.assertEqual(result["effects"]["caption_burn_method"], "drawtext")
        self.assertGreater(result["effects"]["caption_count"], 0)

    def test_no_silent_skip_when_captions_planned(self):
        out_dir = ROOT / "projects" / "_caption_qa"
        tl = Timeline(
            project_id="cap_flag",
            format="9:16", width=640, height=360, fps=24, duration=2.0,
            visual=[VisualEvent(start=0, end=2, path="", media_type="color")],
            captions=[CaptionEvent(start=0.1, end=1.9, text="QA CAPTION")],
        )
        out = out_dir / "captions_flag.mp4"
        result = EffectsComposer(work_dir=out_dir / "work2").compose(tl, out)
        self.assertIn("caption_burn_ok", result["effects"])
        self.assertTrue(result["effects"]["caption_burn_ok"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
```

---

## Behavior after patch

| Situation | Result |
|-----------|--------|
| Captions planned + drawtext OK | `caption_burn_ok=True`, method=`drawtext`, QC can PASS |
| drawtext fails, ASS OK | `caption_burn_ok=True`, method=`ass_subtitles` |
| Both fail | **Raises `CAPTION_ERROR`**, pipeline stops, **no QC PASS** |
| No captions planned | method=`none`, burn skipped OK |

## Apply on Windows
Replace the three engine files from the ZIP, or paste the methods above into the existing sources, then:

```bat
set PYTHONPATH=.
python tests\test_caption_burn_hardfail.py
python main.py --topic "Belda Railway Station" --duration 5m --format 9:16
```

Confirm log line: `Captions burned via drawtext (N events)`  
and `render.json` → `"caption_burn_ok": true`.
