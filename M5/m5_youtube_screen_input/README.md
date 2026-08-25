# M5.1 — YouTube / Screen Recording Input & Content Analysis
AVSP — AI Video Studio Pro

## Correction of M5

M5 reported 13/13 tests passing but had limitations:
1. MLKitAdapter was only a mock/interface, not real ML Kit
2. Screen/OCR tests could pass using artificial mock fallback text
3. No real test media included
4. Test invocation was non-deterministic

M5.1 fixes these while preserving all working functionality.

## What is preserved from M5

- YouTube URL validation, video ID extraction, metadata, transcript handling, segmentation, structure analysis, topic extraction, hook extraction, M2 handoff
- Screen-video validation, frame extraction, OCR adapter architecture, Tesseract adapter, ML Kit adapter/interface, OCR cleaning, number detection, heading detection, combined OCR, ScreenOutput, JSON outputs

## M5.1 Changes

- **Real OCR testing**: Separate REAL vs MOCK vs SKIPPED. Real tests invoke actual TesseractAdapter on real images, must FAIL if mocked, SKIPPED if language data missing (no fake PASS)
- **MLKitAdapter documented**: Clearly marked as Android stub/interface, not real Python OCR. TesseractAdapter is the real desktop OCR.
- **Deterministic test media**: test_media/generate_test_media.py generates real_*.png/mp4 with English, Bengali, Hindi, numbers
- **Deterministic runner**: python test_runner.py works from project root, also python -m unittest discover -s tests -v
- **Error handling**: Added controlled handling for 10 error cases (see M2_HANDOFF.md)

## Installation

```bash
# System deps (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install tesseract-ocr tesseract-ocr-ben tesseract-ocr-hin ffmpeg

# Python deps
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Verify
tesseract --version
tesseract --list-langs
yt-dlp --version
```

## Generate Real Test Media

```bash
python test_media/generate_test_media.py
# Creates:
# test_media/real_english.png
# test_media/real_numbers.png
# test_media/real_bengali.png
# test_media/real_hindi.png
# test_media/real_multilingual.png
# test_media/real_multilingual.mp4
```

## Run Tests (Canonical)

```bash
python test_runner.py
# Also:
python -m unittest discover -s tests -v
# Or specific:
python -m unittest tests.test_youtube -v
python -m unittest tests.test_screen -v
```

## Expected Output Categories

- REAL TESTS: Invoke actual Tesseract, fail if mock used
- MOCK TESTS: Deterministic unit tests with allow_mock_fallback=True, labeled MOCK
- SKIPPED: When tesseract binary or ben/hin language data missing - clear reason
- FAILURES: Should be 0 for acceptance

Example:
```
Real OCR tests: 2 passed, 3 skipped (missing ben/hin)
Mock/unit tests: 11 passed
Failures: 0
```

## M2 Handoff

See sample_outputs/ and M2_HANDOFF.md for schema.

## Dependencies

Minimal: yt-dlp, pytesseract, Pillow, opencv-python, numpy, pydantic, requests, python-dotenv
No AI/cloud APIs.

## Frozen

M5.1 frozen after acceptance.
