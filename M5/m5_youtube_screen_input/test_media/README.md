# Test Media - M5.1

## Real Test Assets (Deterministic Generation)

This folder contains real test assets for REAL OCR testing.

### Generation (Keeps ZIP small)

Instead of shipping large binaries, run:

```bash
python test_media/generate_test_media.py
```

This creates (deterministically):

- real_english.png - English text "ENGLISH TEST 123"
- real_numbers.png - Numbers "42 3.14 100 0.93"
- real_bengali.png - Bengali "বাংলা ভাষা পরীক্ষা"
- real_hindi.png - Hindi "हिंदी भाषा परीक्षण"
- real_multilingual.png - Mixed
- real_multilingual.mp4 - 4 sec video from above images

### Why generation?

- Keeps ZIP < 1MB
- Deterministic - same text every run
- Allows environment to check font support

### Requirements for REAL OCR tests

- tesseract-ocr
- tesseract-ocr-ben
- tesseract-ocr-hin
- ffmpeg
- Pillow, opencv-python (in requirements.txt)

If language data missing, REAL tests will be SKIPPED (not falsely PASSED).

### Mock tests

Mock tests do not need real media and use artificial "MOCK:" prefixed text.
