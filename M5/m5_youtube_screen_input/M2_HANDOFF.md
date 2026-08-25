# M2 Handoff Schema - M5.1

## YouTube Input (youtube_input.json)

```json
{
  "source": "youtube",
  "url": "https://www.youtube.com/watch?v=...",
  "metadata": {
    "title": "string",
    "duration": "int (seconds)",
    "channel": "string",
    "views": "int",
    "likes": "int | null",
    "description": "string",
    "upload_date": "string YYYYMMDD",
    "tags": ["string"]
  },
  "transcript": "string | null (null when unavailable - controlled handling)",
  "segments": [
    {"text": "string", "length": "int", "sentences": "int"}
  ],
  "topics": ["string (max 5)"],
  "hooks": ["string (max 3, first sentences with ?/!)"],
  "structured_content": {
    "segments_count": "int",
    "average_length": "float",
    "total_length": "int",
    "structure": "segmented|empty"
  },
  "confidence": "float 0.0-0.95",
  "processed_at": "ISO8601"
}
```

Originality: Content understanding for generation of original script, not verbatim copy.

## Screen Input (screen_input.json)

```json
{
  "source": "screen",
  "text": "string (cleaned combined OCR)",
  "numbers": ["float (unique)"],
  "confidence": "float 0.0-0.95",
  "frames_processed": "int",
  "headings": ["string (max 10)"],
  "repeated_text": ["string (words repeated >2 times)"],
  "processed_at": "ISO8601"
}
```

Real OCR variant (ingest_image) also includes:
- lang, adapter, is_mock (bool)

## M2 Consumption

M2 expects:
- source to route (youtube|screen)
- transcript/text for script generation understanding
- numbers/headings/topics/hooks for structure
- confidence for filtering low-quality inputs
- structured_content for segmentation

## Error Handling Contract

- invalid YouTube URL -> ValueError
- unavailable transcript -> transcript=None, segments=[], confidence low but not crash
- missing metadata -> metadata with empty title, confidence <0.5
- missing video file -> FileNotFoundError
- corrupt video -> ValueError "Cannot open"
- unavailable Tesseract -> RuntimeError when allow_mock_fallback=False, mock fallback when True (labeled MOCK)
- missing ben/hin language data -> RuntimeError "Missing Tesseract language data" -> test SKIPPED with reason
- unsupported OCR adapter -> ValueError
- empty OCR result -> ValueError "Empty OCR result" or low confidence result (controlled)

No silent fake success.

## Sample Outputs

See sample_outputs/ - valid, stable, machine-readable.
