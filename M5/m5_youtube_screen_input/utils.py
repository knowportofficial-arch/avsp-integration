"""
Utility functions for M5 module - M5.1
AVSP - AI Video Studio Pro
Preserves working functionality from M5
"""
import re
from typing import Optional, Dict, Any, List, Tuple
from dataclasses import dataclass, asdict
from datetime import datetime
import shutil
import subprocess

@dataclass
class VideoMetadata:
    title: str
    duration: int
    channel: str
    views: int
    likes: Optional[int] = None
    description: Optional[str] = None
    upload_date: Optional[str] = None
    tags: List[str] = None
    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

@dataclass
class YouTubeOutput:
    source: str = "youtube"
    url: str = ""
    metadata: Dict[str, Any] = None
    transcript: Optional[str] = None
    segments: List[Dict[str, Any]] = None
    topics: List[str] = None
    hooks: List[str] = None
    structured_content: Dict[str, Any] = None
    confidence: float = 0.0
    processed_at: str = ""
    def to_dict(self) -> Dict[str, Any]:
        return {
            "source": self.source,
            "url": self.url,
            "metadata": self.metadata or {},
            "transcript": self.transcript,
            "segments": self.segments or [],
            "topics": self.topics or [],
            "hooks": self.hooks or [],
            "structured_content": self.structured_content or {},
            "confidence": self.confidence,
            "processed_at": self.processed_at or datetime.now().isoformat()
        }

@dataclass
class ScreenOutput:
    source: str = "screen"
    text: str = ""
    numbers: List[float] = None
    confidence: float = 0.0
    frames_processed: int = 0
    headings: List[str] = None
    repeated_text: List[str] = None
    processed_at: str = ""
    def to_dict(self) -> Dict[str, Any]:
        return {
            "source": self.source,
            "text": self.text,
            "numbers": self.numbers or [],
            "confidence": self.confidence,
            "frames_processed": self.frames_processed,
            "headings": self.headings or [],
            "repeated_text": self.repeated_text or [],
            "processed_at": self.processed_at or datetime.now().isoformat()
        }

def validate_youtube_url(url: str) -> bool:
    youtube_regex = (
        r'(https?://)?(www\.)?'
        '(youtube|youtu|youtube-nocookie)\.(com|be)/'
        '(watch\?v=|embed/|v/|.+\?v=)?([^&=%\?]{11})'
    )
    return bool(re.match(youtube_regex, url))

def extract_video_id(url: str) -> Optional[str]:
    patterns = [
        r'(?:v=|\/)([0-9A-Za-z_-]{11})(?:[?&]|$)',
        r'(?:embed\/)([0-9A-Za-z_-]{11})',
        r'(?:youtu.be\/)([0-9A-Za-z_-]{11})'
    ]
    for pattern in patterns:
        m = re.search(pattern, url)
        if m:
            return m.group(1)
    return None

def clean_transcript_text(text: str) -> str:
    cleaned = re.sub(r'\[\d{2}:\d{2}:\d{2}\]', '', text)
    cleaned = re.sub(r'\s+', ' ', cleaned)
    return cleaned.strip()

def segment_transcript(transcript: str, max_length: int = 500) -> List[Dict[str, Any]]:
    if not transcript:
        return []
    sentences = re.split(r'(?<=[.!?])\s+', transcript)
    segments, cur, cur_len = [], [], 0
    for s in sentences:
        if cur_len + len(s) > max_length and cur:
            txt = ' '.join(cur)
            segments.append({'text': txt, 'length': len(txt), 'sentences': len(cur)})
            cur, cur_len = [], 0
        cur.append(s)
        cur_len += len(s)
    if cur:
        txt = ' '.join(cur)
        segments.append({'text': txt, 'length': len(txt), 'sentences': len(cur)})
    return segments

def extract_topics(segments: List[Dict[str, Any]]) -> List[str]:
    all_text = ' '.join([s['text'] for s in segments])
    keywords = ['AI', 'video', 'technology', 'software', 'development', 'python', 'machine learning', 'data', 'analysis']
    return [k for k in keywords if k.lower() in all_text.lower()][:5]

def identify_hooks(segments: List[Dict[str, Any]]) -> List[str]:
    hooks = []
    for i, seg in enumerate(segments[:3]):
        t = seg['text']
        if '?' in t or '!' in t:
            hooks.append(t[:100])
        elif len(t) < 150 and i == 0:
            hooks.append(t)
    return hooks[:3]

# --- M5.1: Environment checks for real OCR ---
def check_tesseract_available() -> Tuple[bool, str]:
    """Returns (available, version_or_error)"""
    if not shutil.which("tesseract"):
        return False, "tesseract binary not found in PATH"
    try:
        import pytesseract
        ver = str(pytesseract.get_tesseract_version())
        return True, ver
    except Exception as e:
        return False, f"pytesseract import/version failed: {e}"

def check_tesseract_langs() -> Dict[str, bool]:
    """Check for eng, ben, hin language data"""
    langs = {"eng": False, "ben": False, "hin": False}
    try:
        import pytesseract
        available = pytesseract.get_languages()
        for l in langs:
            langs[l] = l in available
    except Exception:
        # fallback via tesseract --list-langs
        try:
            out = subprocess.run(["tesseract", "--list-langs"], capture_output=True, text=True, timeout=5)
            txt = out.stdout + out.stderr
            for l in langs:
                langs[l] = l in txt
        except Exception:
            pass
    return langs

def clean_text(text: str) -> str:
    return clean_transcript_text(text) if text else ""
def timestamp_to_seconds(ts: str) -> int:
    if not ts: return 0
    try:
        parts=[int(p) for p in ts.split(":")]
        if len(parts)==3: return parts[0]*3600+parts[1]*60+parts[2]
        if len(parts)==2: return parts[0]*60+parts[1]
        if len(parts)==1: return parts[0]
    except: pass
    return 0
def validate_url(url: str) -> bool:
    try:
        from urllib.parse import urlparse
        return bool(urlparse(url).scheme and urlparse(url).netloc)
    except: return False
def format_for_m5(payload: dict) -> dict:
    assert "source" in payload and "data" in payload
    return payload
