"""
Screen Ingestor - Part B - M5.1 Correction
Preserves working functionality, adds strict real vs mock separation
"""
import cv2
import numpy as np
import os
import json
import logging
from typing import Optional, Dict, Any, List
from datetime import datetime
import re
try:
    from .utils import ScreenOutput, check_tesseract_available, check_tesseract_langs
except ImportError:
    from utils import ScreenOutput, check_tesseract_available, check_tesseract_langs

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class BaseOCRAdapter:
    def extract(self, frame: np.ndarray, lang: str = 'eng') -> str:
        raise NotImplementedError
    def is_available(self) -> bool:
        return False

class TesseractAdapter(BaseOCRAdapter):
    """
    Real Python OCR implementation using pytesseract.
    This is the actual desktop OCR for M5.1.
    """
    def __init__(self):
        self._available = False
        self._version = ""
        self._langs = {}
        self._init_check()
    def _init_check(self):
        avail, ver = check_tesseract_available()
        self._available = avail
        self._version = ver
        if avail:
            self._langs = check_tesseract_langs()
    def is_available(self) -> bool:
        return self._available
    def get_missing_langs(self, required: List[str]) -> List[str]:
        return [l for l in required if not self._langs.get(l, False)]
    def extract(self, frame: np.ndarray, lang: str = 'eng+ben+hin') -> str:
        if not self._available:
            raise RuntimeError(f"Tesseract not available: {self._version}")
        missing = self.get_missing_langs(lang.split('+'))
        if missing:
            raise RuntimeError(f"Missing Tesseract language data: {missing}. Install tesseract-ocr-{' tesseract-ocr-'.join(missing)}")
        try:
            import pytesseract
            # Preprocess
            if len(frame.shape) == 3:
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            else:
                gray = frame
            # Simple threshold for better OCR
            _, thresh = cv2.threshold(gray, 150, 255, cv2.THRESH_BINARY)
            text = pytesseract.image_to_string(thresh, lang=lang, config='--psm 6 --oem 3')
            return text.strip()
        except Exception as e:
            logger.error(f"Tesseract extract failed: {e}")
            raise

class MLKitAdapter(BaseOCRAdapter):
    """
    M5.1 Documentation:
    MLKitAdapter = Android integration interface/stub for future AVSP Android app.
    - On Android: this would wrap com.google.mlkit:vision-text
    - On Python desktop: this is intentionally a stub that does NOT perform real OCR.
    - It exists to preserve the adapter architecture required by M5 spec.
    - Real desktop OCR is TesseractAdapter.
    """
    def __init__(self):
        self._available = True  # Interface is available, but not real OCR in Python
        logger.info("MLKitAdapter: Android stub - not real OCR in Python. Use TesseractAdapter for real OCR.")
    def is_available(self) -> bool:
        # Interface available, but not as real OCR
        return True
    def is_real_ocr_in_python(self) -> bool:
        return False
    def extract(self, frame: np.ndarray, lang: str = 'eng') -> str:
        # Intentionally does not perform real OCR in Python environment
        # In Android build, this would call ML Kit
        raise NotImplementedError("MLKitAdapter real OCR is only available on Android. Use TesseractAdapter for desktop Python M5.1 real OCR testing.")

def get_ocr_adapter(name: str = 'tesseract') -> BaseOCRAdapter:
    name = name.lower().strip()
    if name in ('mlkit', 'ml_kit', 'ml-kit'):
        return MLKitAdapter()
    if name in ('tesseract', 'pytesseract'):
        return TesseractAdapter()
    raise ValueError(f"Unsupported OCR adapter: {name}. Supported: 'tesseract', 'mlkit'")

class ScreenIngestor:
    def __init__(self, ocr_adapter: str = 'tesseract'):
        self.ocr_adapter_name = ocr_adapter
        self.ocr = get_ocr_adapter(ocr_adapter)

    def ingest(self, video_path: str, output_file: Optional[str] = None, frame_interval: int = 30, allow_mock_fallback: bool = True) -> Dict[str, Any]:
        """
        allow_mock_fallback: If True (default for M5 compatibility), uses mock when Tesseract unavailable.
        If False (for REAL OCR tests), raises error instead of faking.
        """
        logger.info(f"Ingesting screen: {video_path} with {self.ocr_adapter_name}, allow_mock={allow_mock_fallback}")
        self._validate_video(video_path)
        frames = self._extract_frames(video_path, frame_interval)
        if not frames:
            raise ValueError(f"No frames extracted from: {video_path}")
        ocr_results = self._perform_ocr(frames, allow_mock_fallback=allow_mock_fallback)
        cleaned = self._clean_ocr_text(ocr_results)
        numbers = self._detect_numbers(cleaned)
        headings = self._detect_headings(cleaned)
        combined = self._combine_repeated_text(cleaned)
        output = ScreenOutput(
            text=combined,
            numbers=numbers,
            headings=headings,
            repeated_text=self._find_repeated_text(ocr_results),
            frames_processed=len(frames),
            confidence=self._calculate_confidence(cleaned, numbers),
            processed_at=datetime.now().isoformat()
        )
        result = output.to_dict()
        if output_file:
            self._save_output(result, output_file)
        return result

    def ingest_image(self, image_path: str, lang: str = 'eng', allow_mock_fallback: bool = False) -> Dict[str, Any]:
        """Direct image OCR for real tests - no mock allowed when allow_mock_fallback=False"""
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found: {image_path}")
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"Cannot read image: {image_path}")
        # For real OCR tests, must use TesseractAdapter and must not fallback
        if isinstance(self.ocr, MLKitAdapter):
            raise RuntimeError("MLKitAdapter does not support real OCR in Python - use TesseractAdapter for REAL OCR tests")
        if not allow_mock_fallback:
            # Strict real OCR path
            text = self.ocr.extract(img, lang=lang)
            if not text:
                raise ValueError("Empty OCR result from real Tesseract")
        else:
            try:
                text = self.ocr.extract(img, lang=lang)
            except Exception as e:
                logger.warning(f"Real OCR failed, using mock fallback (allowed): {e}")
                text = self._get_mock_ocr_results(1)[0]
        cleaned = self._clean_ocr_text([text])
        numbers = self._detect_numbers(cleaned)
        return {
            "source": "screen",
            "text": cleaned,
            "numbers": numbers,
            "confidence": self._calculate_confidence(cleaned, numbers),
            "frames_processed": 1,
            "lang": lang,
            "adapter": self.ocr_adapter_name,
            "is_mock": allow_mock_fallback and "AI Video Studio" in text  # heuristic
        }

    def _validate_video(self, path: str):
        if not os.path.exists(path):
            raise FileNotFoundError(f"Video not found: {path}")
        if not path.lower().endswith(('.mp4','.mov','.mkv','.avi','.png','.jpg','.jpeg')):
            # Allow images too for flexibility, but primary is video
            if not os.path.isfile(path):
                raise ValueError(f"Unsupported format: {path}")
        cap = cv2.VideoCapture(path)
        if not cap.isOpened():
            # If it's an image, VideoCapture may fail but imread may succeed - check
            if cv2.imread(path) is None:
                raise ValueError(f"Cannot open video/image: {path}")
        cap.release()

    def _extract_frames(self, video_path: str, interval: int) -> List[np.ndarray]:
        # If it's an image, return as single frame
        img = cv2.imread(video_path)
        cap_test = cv2.VideoCapture(video_path)
        ret, _ = cap_test.read()
        cap_test.release()
        if img is not None and not ret:
            # It's likely a static image, not video
            # But for test purposes, treat image as frame if it's not a video file extension that should be video
            if video_path.lower().endswith(('.png','.jpg','.jpeg')):
                return [img]
        frames=[]
        cap=cv2.VideoCapture(video_path)
        count=0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            if count % interval == 0:
                frames.append(frame)
            count+=1
        cap.release()
        if not frames:
            cap=cv2.VideoCapture(video_path)
            ret, frame = cap.read()
            if ret:
                frames=[frame]
            cap.release()
        return frames

    def _perform_ocr(self, frames: List[np.ndarray], allow_mock_fallback: bool = True) -> List[str]:
        results=[]
        if isinstance(self.ocr, MLKitAdapter):
            if allow_mock_fallback:
                logger.warning("MLKitAdapter in Python - using mock fallback for compatibility (not real OCR)")
                return self._get_mock_ocr_results(len(frames))
            else:
                raise NotImplementedError("MLKitAdapter real OCR not available in Python")
        # Tesseract path
        if not self.ocr.is_available():
            if allow_mock_fallback:
                logger.warning("Tesseract not available - using mock fallback (allow_mock=True)")
                return self._get_mock_ocr_results(len(frames))
            else:
                raise RuntimeError(f"Tesseract not available: {self.ocr._version}")
        for i, fr in enumerate(frames):
            try:
                text = self.ocr.extract(fr, lang='eng+ben+hin')
                results.append(text)
            except Exception as e:
                if allow_mock_fallback:
                    logger.warning(f"Real OCR failed for frame {i}: {e} - using mock")
                    results.append(self._get_mock_ocr_results(1)[0])
                else:
                    raise
        return results

    def _clean_ocr_text(self, ocr_results: List[str]) -> str:
        combined=' '.join(ocr_results)
        cleaned=re.sub(r'[^\w\s.,!?;:()\-\u0980-\u09FF\u0900-\u097F]', ' ', combined)
        cleaned=re.sub(r'\s+',' ', cleaned)
        return cleaned.strip()

    def _detect_numbers(self, text: str) -> List[float]:
        nums=[]
        for m in re.findall(r'-?\d*\.?\d+', text):
            try:
                nums.append(float(m))
            except:
                pass
        seen=set()
        uniq=[]
        for n in nums:
            if n not in seen:
                seen.add(n)
                uniq.append(n)
        return uniq

    def _detect_headings(self, text: str) -> List[str]:
        headings=[]
        for line in text.split('\n'):
            line=line.strip()
            if not line: continue
            if line.isupper() and len(line)>5:
                headings.append(line)
            elif line.endswith(':') and len(line)<100:
                headings.append(line)
            elif len(line)<50 and line[0].isupper():
                headings.append(line)
        return headings[:10]

    def _combine_repeated_text(self, text: str) -> str:
        sentences=re.split(r'(?<=[.!?])\s+', text)
        seen=set()
        combined=[]
        for s in sentences:
            norm=s.lower().strip()
            if len(norm)<10:
                combined.append(s)
                continue
            if norm not in seen:
                seen.add(norm)
                combined.append(s)
        return ' '.join(combined)

    def _find_repeated_text(self, ocr_results: List[str]) -> List[str]:
        from collections import Counter
        words=' '.join(ocr_results).split()
        cnt=Counter(words)
        rep=[w for w,c in cnt.items() if c>2 and len(w)>3]
        return list(set(rep))[:10]

    def _calculate_confidence(self, text: str, numbers: List[float]) -> float:
        c=0.0
        if text and len(text)>50: c+=0.4
        elif text and len(text)>10: c+=0.2
        if numbers: c+=0.2
        if len(text)>100: c+=0.2
        if self._detect_headings(text): c+=0.1
        return min(c,0.95)

    def _get_mock_ocr_results(self, num_frames: int) -> List[str]:
        mock_texts=[
            "MOCK: AI Video Studio Pro - Screen Recording",
            "MOCK: Processing video data with AI",
            "MOCK: Detected numbers: 42, 3.14, 100",
            "MOCK: Heading: Video Analysis Results",
            "MOCK: Confidence score: 0.93",
            "MOCK: Bengali text: বাংলা ভাষা পরীক্ষা",
            "MOCK: Hindi text: हिंदी भाषा परीक्षण",
            "MOCK: English text: This is clear English text for OCR",
        ]
        return [mock_texts[i % len(mock_texts)] for i in range(num_frames)]

    def _save_output(self, data: Dict[str, Any], filepath: str) -> None:
        with open(filepath,'w',encoding='utf-8') as f:
            json.dump(data,f,indent=2,ensure_ascii=False)
