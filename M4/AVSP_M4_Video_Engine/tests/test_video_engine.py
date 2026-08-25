#!/usr/bin/env python3
"""
AVSP M4 Video Engine — Acceptance Tests A–H

Generates synthetic assets and verifies real MP4 output.
"""

from __future__ import annotations

import json
import logging
import subprocess
import sys
import wave
from pathlib import Path

# Ensure project root on path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.engines.video_engine import VideoEngine, RenderResult, _ffprobe_json, _media_duration

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("tests")

TEST_DIR = ROOT / "temp" / "m4_tests"
ASSETS = TEST_DIR / "assets"
OUT = TEST_DIR / "output"
ASSETS.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)


def run_cmd(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode, r.stdout, r.stderr


def make_silent_wav(path: Path, duration: float = 4.0, sr: int = 44100):
    """Create a short silent WAV (or low tone) for narration tests."""
    import struct
    import math
    nframes = int(sr * duration)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        # Soft 440 Hz tone so we can detect audio presence
        frames = []
        for i in range(nframes):
            val = int(8000 * math.sin(2 * math.pi * 440 * i / sr))
            frames.append(struct.pack("<h", val))
        w.writeframes(b"".join(frames))
    return path


def make_test_image(path: Path, color=(30, 120, 200), size=(640, 480), label="IMG"):
    from PIL import Image, ImageDraw, ImageFont
    img = Image.new("RGB", size, color)
    draw = ImageDraw.Draw(img)
    draw.rectangle([20, 20, size[0] - 20, size[1] - 20], outline=(255, 255, 255), width=4)
    draw.text((40, size[1] // 2 - 10), label, fill=(255, 255, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return path


def make_test_video(path: Path, duration: float = 2.0, w: int = 640, h: int = 360, color="blue"):
    path.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c={color}:s={w}x{h}:d={duration}:r=30",
        "-f", "lavfi", "-i", f"sine=frequency=220:duration={duration}",
        "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-shortest",
        str(path),
    ]
    rc, _, err = run_cmd(cmd)
    if rc != 0:
        raise RuntimeError(f"make_test_video failed: {err[-300:]}")
    return path


def probe_ok(path: Path) -> dict:
    info = _ffprobe_json(path)
    assert info is not None, f"ffprobe failed on {path}"
    return info


def assert_mp4_valid(path: Path, expect_w: int, expect_h: int, require_audio: bool = True):
    assert path.is_file(), f"Missing output: {path}"
    assert path.stat().st_size > 2000, f"Output too small: {path.stat().st_size}"
    info = probe_ok(path)
    streams = info.get("streams", [])
    v = [s for s in streams if s.get("codec_type") == "video"]
    a = [s for s in streams if s.get("codec_type") == "audio"]
    assert v, "No video stream"
    assert int(v[0]["width"]) == expect_w
    assert int(v[0]["height"]) == expect_h
    codec = v[0].get("codec_name", "")
    assert codec in ("h264", "avc1"), f"Unexpected video codec: {codec}"
    if require_audio:
        assert a, "No audio stream"
        ac = a[0].get("codec_name", "")
        assert ac in ("aac", "mp3"), f"Unexpected audio codec: {ac}"
    dur = float(info.get("format", {}).get("duration", 0) or 0)
    assert dur > 0.2, f"Duration too short: {dur}"
    return info


def test_a_black_plus_narration():
    """TEST A: black/background + narration → MP4"""
    print("\n=== TEST A: black + narration ===")
    audio = make_silent_wav(ASSETS / "narr_a.wav", 3.5)
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[{"color": "black", "duration": 3.5}],
        template="default_shorts",
        project_id="test_a",
        output_path=OUT / "test_a.mp4",
    )
    assert result.status == "success", result.error
    assert_mp4_valid(Path(result.file), 1080, 1920, require_audio=True)
    print("PASS A", result.file, f"dur={result.duration:.2f}s")


def test_b_images_plus_narration():
    """TEST B: images + narration → MP4"""
    print("\n=== TEST B: images + narration ===")
    audio = make_silent_wav(ASSETS / "narr_b.wav", 6.0)
    img1 = make_test_image(ASSETS / "img1.png", (200, 50, 50), label="Scene1")
    img2 = make_test_image(ASSETS / "img2.png", (50, 150, 50), label="Scene2")
    img3 = make_test_image(ASSETS / "img3.png", (50, 50, 180), label="Scene3")
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[
            {"path": str(img1), "duration": 2.0},
            {"path": str(img2), "duration": 2.0},
            {"path": str(img3), "duration": 2.0},
        ],
        template="default_shorts",
        project_id="test_b",
        output_path=OUT / "test_b.mp4",
    )
    assert result.status == "success", result.error
    assert_mp4_valid(Path(result.file), 1080, 1920)
    print("PASS B", result.file, f"dur={result.duration:.2f}s")


def test_c_video_clips_plus_narration():
    """TEST C: video clips + narration → MP4"""
    print("\n=== TEST C: video clips + narration ===")
    audio = make_silent_wav(ASSETS / "narr_c.wav", 5.0)
    v1 = make_test_video(ASSETS / "clip1.mp4", 2.0, color="red")
    v2 = make_test_video(ASSETS / "clip2.mp4", 2.5, color="green")
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[
            {"path": str(v1), "duration": 2.0},
            {"path": str(v2), "duration": 2.5},
        ],
        template="default_shorts",
        project_id="test_c",
        output_path=OUT / "test_c.mp4",
    )
    assert result.status == "success", result.error
    assert_mp4_valid(Path(result.file), 1080, 1920)
    print("PASS C", result.file, f"dur={result.duration:.2f}s")


def test_d_intro_narration_outro():
    """TEST D: intro + narration + outro → MP4"""
    print("\n=== TEST D: intro + narration + outro ===")
    audio = make_silent_wav(ASSETS / "narr_d.wav", 3.0)
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[{"color": "#1a1a2e", "duration": 3.0}],
        template="default_shorts",
        project_id="test_d",
        output_path=OUT / "test_d.mp4",
        intro={"enabled": True, "text": "AVSP Intro", "duration": 1.5, "background": "black"},
        outro={"enabled": True, "text": "Thanks for watching", "duration": 1.5, "background": "black"},
    )
    assert result.status == "success", result.error
    info = assert_mp4_valid(Path(result.file), 1080, 1920)
    dur = float(info["format"]["duration"])
    # intro 1.5 + 3.0 + outro 1.5 ≈ 6.0
    assert dur >= 5.5, f"Expected ~6s with intro/outro, got {dur}"
    print("PASS D", result.file, f"dur={result.duration:.2f}s")


def test_e_subtitles():
    """TEST E: subtitles → MP4"""
    print("\n=== TEST E: subtitles ===")
    audio = make_silent_wav(ASSETS / "narr_e.wav", 5.0)
    subs = [
        {"start": 0.0, "end": 2.0, "text": "Welcome to Belda Railway Station"},
        {"start": 2.0, "end": 4.5, "text": "A historic stop in West Bengal"},
    ]
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[{"color": "navy", "duration": 5.0}],
        template="default_shorts",
        project_id="test_e",
        output_path=OUT / "test_e.mp4",
        subtitles=subs,
    )
    assert result.status == "success", result.error
    assert_mp4_valid(Path(result.file), 1080, 1920)
    print("PASS E", result.file, f"dur={result.duration:.2f}s")


def test_f_shorts_1080x1920():
    """TEST F: 1080x1920 Shorts → MP4"""
    print("\n=== TEST F: 1080x1920 Shorts ===")
    audio = make_silent_wav(ASSETS / "narr_f.wav", 4.0)
    img = make_test_image(ASSETS / "wide.png", (80, 80, 80), size=(1280, 720), label="WIDE")
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[{"path": str(img), "duration": 4.0}],
        template="default_shorts",
        project_id="test_f",
        output_path=OUT / "test_f.mp4",
    )
    assert result.status == "success", result.error
    assert_mp4_valid(Path(result.file), 1080, 1920)
    assert result.width == 1080 and result.height == 1920
    print("PASS F", result.file, f"{result.width}x{result.height}")


def test_g_invalid_input():
    """TEST G: invalid input → controlled failure"""
    print("\n=== TEST G: invalid input ===")
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path="/nonexistent/path/narration.wav",
        media=[{"color": "black", "duration": 2.0}],
        template="default_shorts",
        project_id="test_g",
        output_path=OUT / "test_g.mp4",
    )
    assert result.status == "error", "Expected controlled failure"
    assert result.error is not None
    assert result.error.get("code") == "INPUT_NOT_FOUND"
    print("PASS G controlled error:", result.error.get("code"), result.error.get("message"))


def test_h_av_duration_mismatch():
    """TEST H: audio/video duration mismatch → handled"""
    print("\n=== TEST H: A/V duration mismatch ===")
    # Short video, longer audio → should pad video
    audio = make_silent_wav(ASSETS / "narr_h.wav", 6.0)
    v1 = make_test_video(ASSETS / "short_clip.mp4", 1.5, color="purple")
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[{"path": str(v1), "duration": 1.5}],
        template="default_shorts",
        project_id="test_h",
        output_path=OUT / "test_h.mp4",
    )
    assert result.status == "success", result.error
    info = assert_mp4_valid(Path(result.file), 1080, 1920)
    dur = float(info["format"]["duration"])
    # Should be close to audio length (padded)
    assert dur >= 5.5, f"Expected padding to ~6s, got {dur}"
    print("PASS H", result.file, f"dur={result.duration:.2f}s (padded)")


def test_landscape_template():
    """Bonus: 16:9 landscape template"""
    print("\n=== BONUS: 16:9 landscape ===")
    audio = make_silent_wav(ASSETS / "narr_l.wav", 3.0)
    engine = VideoEngine(root=ROOT)
    result = engine.render(
        audio_path=audio,
        media=[{"color": "teal", "duration": 3.0}],
        template="default_landscape",
        project_id="test_l",
        output_path=OUT / "test_landscape.mp4",
    )
    assert result.status == "success", result.error
    assert_mp4_valid(Path(result.file), 1920, 1080)
    print("PASS landscape", result.file)


def main():
    tests = [
        test_a_black_plus_narration,
        test_b_images_plus_narration,
        test_c_video_clips_plus_narration,
        test_d_intro_narration_outro,
        test_e_subtitles,
        test_f_shorts_1080x1920,
        test_g_invalid_input,
        test_h_av_duration_mismatch,
        test_landscape_template,
    ]
    failed = []
    for t in tests:
        try:
            t()
        except Exception as e:
            logger.exception("FAILED %s", t.__name__)
            failed.append((t.__name__, str(e)))

    print("\n" + "=" * 50)
    if failed:
        print(f"FAILED {len(failed)}/{len(tests)}")
        for name, err in failed:
            print(f"  - {name}: {err}")
        sys.exit(1)
    else:
        print(f"ALL {len(tests)} TESTS PASSED")
        print(f"Outputs in: {OUT}")
        sys.exit(0)


if __name__ == "__main__":
    main()
