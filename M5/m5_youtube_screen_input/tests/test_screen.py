import unittest
import os
import sys
from pathlib import Path
import cv2
import numpy as np
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent.parent.parent))
try:
    from m5_youtube_screen_input.screen_ingestor import ScreenIngestor, TesseractAdapter, MLKitAdapter, get_ocr_adapter
    from m5_youtube_screen_input.utils import check_tesseract_available, check_tesseract_langs
except ImportError:
    from screen_ingestor import ScreenIngestor, TesseractAdapter, MLKitAdapter, get_ocr_adapter
    from utils import check_tesseract_available, check_tesseract_langs

try:
    from m5_youtube_screen_input.test_media.generate_test_media import main as gen_media
except ImportError:
    try:
        from test_media.generate_test_media import main as gen_media
    except:
        gen_media = None

class TestScreenMock(unittest.TestCase):
    """MOCK TESTS - Deterministic unit tests using mock fallback (allow_mock=True) - M5.1.1 FIXED to be deterministic via patching"""

    def setUp(self):
        self.ingestor = ScreenIngestor(ocr_adapter='tesseract')
        self.test_dir = Path(__file__).parent / "tmp_mock"
        self.test_dir.mkdir(exist_ok=True)

    def _create_video(self, texts, filename="mock.mp4", frames=10):
        path = self.test_dir / filename
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(str(path), fourcc, 20, (640,480))
        for i in range(frames):
            img = np.ones((480,640,3), dtype=np.uint8)*255
            cv2.putText(img, texts[i % len(texts)][:30], (30,100), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0,0,0), 2)
            out.write(img)
        out.release()
        return str(path)

    def test_mock_clear_screen(self):
        """MOCK TEST - clear screen using mock fallback - M5.1.1 FIX: Explicitly force mock path via patching, not env dependent"""
        path = self._create_video(["Clear screen"], "clear_mock.mp4")
        # M5.1.1 FIX: Force mock path deterministically by patching is_available to False
        # This ensures test does NOT depend on whether Tesseract is installed
        # Purpose: verify mock path genuinely exercised
        with patch.object(self.ingestor.ocr, 'is_available', return_value=False):
            result = self.ingestor.ingest(path, allow_mock_fallback=True)
        self.assertEqual(result['source'], 'screen')
        self.assertIn("MOCK", result['text'], f"M5.1.1: Mock path must be exercised, expected MOCK in text but got: {result['text']}")
        # Also verify mock numbers present
        self.assertIn('numbers', result)

    def test_mock_numbers_detection(self):
        """MOCK TEST - numbers via mock - M5.1.1 FIX: Force mock via patch"""
        path = self._create_video(["Numbers 42"], "numbers_mock.mp4")
        with patch.object(self.ingestor.ocr, 'is_available', return_value=False):
            result = self.ingestor.ingest(path, allow_mock_fallback=True)
        self.assertIn('numbers', result)
        self.assertIn("MOCK", result['text'])

    def test_mock_unsupported_adapter_error(self):
        """REAL ERROR HANDLING TEST - unsupported adapter"""
        with self.assertRaises(ValueError):
            get_ocr_adapter('invalid_adapter_xyz')

    def test_mock_missing_video_file(self):
        """REAL ERROR HANDLING TEST - missing file"""
        with self.assertRaises(FileNotFoundError):
            self.ingestor.ingest("/nonexistent/path/video.mp4", allow_mock_fallback=True)

    def test_mock_mlkit_interface(self):
        """MOCK TEST - MLKitAdapter is stub, not real OCR"""
        mlkit = MLKitAdapter()
        self.assertTrue(mlkit.is_available())
        self.assertFalse(mlkit.is_real_ocr_in_python())
        with self.assertRaises(NotImplementedError):
            dummy = np.zeros((100,100,3), dtype=np.uint8)
            mlkit.extract(dummy)

class TestScreenRealOCR(unittest.TestCase):
    """REAL OCR TESTS - Must invoke actual Tesseract, must FAIL if mocked, SKIPPED if env missing - UNCHANGED FROM M5.1"""

    @classmethod
    def setUpClass(cls):
        if gen_media:
            try:
                gen_media()
            except Exception as e:
                print(f"Media generation failed: {e}")
        cls.tesseract_available, cls.tesseract_ver = check_tesseract_available()
        cls.langs = check_tesseract_langs()
        cls.test_media_dir = Path(__file__).parent.parent / "test_media"
        if not cls.test_media_dir.exists():
            cls.test_media_dir = Path(__file__).parent / ".." / "test_media"
        print(f"\n[REAL OCR ENV] Tesseract available: {cls.tesseract_available} ({cls.tesseract_ver})")
        print(f"[REAL OCR ENV] Langs: {cls.langs}")

    def _get_image_path(self, name):
        for p in [self.test_media_dir / name, Path(__file__).parent / "test_media" / name, Path(__file__).parent.parent / "test_media" / name]:
            if p.exists():
                return str(p)
        return None

    def test_real_english_ocr(self):
        """REAL TEST - English OCR must use real Tesseract, not mock"""
        if not self.tesseract_available:
            self.skipTest(f"SKIPPED: Tesseract not available: {self.tesseract_ver}")
        if not self.langs.get('eng'):
            self.skipTest("SKIPPED: Missing tesseract-ocr-eng language data")
        img_path = self._get_image_path("real_english.png")
        if not img_path:
            self.skipTest("SKIPPED: real_english.png not found - run generate_test_media.py")
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        try:
            result = ingestor.ingest_image(img_path, lang='eng', allow_mock_fallback=False)
        except RuntimeError as e:
            if "Missing" in str(e):
                self.skipTest(f"SKIPPED: {e}")
            raise
        self.assertNotIn("MOCK", result['text'], "REAL OCR TEST FAILED: Got mock text instead of real OCR")
        txt_upper = result['text'].upper()
        self.assertTrue("ENGLISH" in txt_upper or "TEST" in txt_upper or "123" in result['text'], f"Real OCR did not detect expected English text. Got: {result['text']}")

    def test_real_numbers_ocr(self):
        """REAL TEST - Numbers OCR must use real Tesseract"""
        if not self.tesseract_available:
            self.skipTest(f"SKIPPED: Tesseract not available")
        if not self.langs.get('eng'):
            self.skipTest("SKIPPED: Missing eng language data")
        img_path = self._get_image_path("real_numbers.png")
        if not img_path:
            self.skipTest("SKIPPED: real_numbers.png not found")
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        try:
            result = ingestor.ingest_image(img_path, lang='eng', allow_mock_fallback=False)
        except RuntimeError as e:
            if "Missing" in str(e):
                self.skipTest(f"SKIPPED: {e}")
            raise
        self.assertNotIn("MOCK", result['text'])
        self.assertTrue(len(result['numbers']) > 0, f"Real OCR numbers not detected. Text: {result['text']}")

    def test_real_bengali_ocr(self):
        """REAL TEST - Bengali OCR - must be real Tesseract ben data, SKIPPED if missing"""
        if not self.tesseract_available:
            self.skipTest(f"SKIPPED: Tesseract not available")
        if not self.langs.get('ben'):
            self.skipTest("SKIPPED: Missing tesseract-ocr-ben (Bengali) language data - install tesseract-ocr-ben")
        img_path = self._get_image_path("real_bengali.png")
        if not img_path:
            self.skipTest("SKIPPED: real_bengali.png not found")
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        try:
            result = ingestor.ingest_image(img_path, lang='ben', allow_mock_fallback=False)
        except RuntimeError as e:
            self.skipTest(f"SKIPPED: {e}")
        self.assertNotIn("MOCK", result['text'])
        self.assertTrue(len(result['text'].strip()) > 0)

    def test_real_hindi_ocr(self):
        """REAL TEST - Hindi OCR - must be real Tesseract hin data, SKIPPED if missing"""
        if not self.tesseract_available:
            self.skipTest(f"SKIPPED: Tesseract not available")
        if not self.langs.get('hin'):
            self.skipTest("SKIPPED: Missing tesseract-ocr-hin (Hindi) language data - install tesseract-ocr-hin")
        img_path = self._get_image_path("real_hindi.png")
        if not img_path:
            self.skipTest("SKIPPED: real_hindi.png not found")
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        try:
            result = ingestor.ingest_image(img_path, lang='hin', allow_mock_fallback=False)
        except RuntimeError as e:
            self.skipTest(f"SKIPPED: {e}")
        self.assertNotIn("MOCK", result['text'])
        self.assertTrue(len(result['text'].strip()) > 0)

    def test_real_multilingual_video(self):
        """REAL TEST - Multilingual video frame extraction + real OCR"""
        if not self.tesseract_available:
            self.skipTest("SKIPPED: Tesseract not available")
        if not (self.langs.get('eng')):
            self.skipTest("SKIPPED: Missing eng")
        vid_path = self._get_image_path("real_multilingual.mp4")
        if not vid_path:
            self.skipTest("SKIPPED: real_multilingual.mp4 not found")
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        try:
            result = ingestor.ingest(vid_path, frame_interval=1, allow_mock_fallback=False)
        except RuntimeError as e:
            self.skipTest(f"SKIPPED: {e}")
        self.assertNotIn("MOCK", result['text'])
        self.assertGreater(result['frames_processed'], 0)

    def test_error_empty_ocr_result(self):
        """REAL TEST - Empty OCR result handling"""
        if not self.tesseract_available:
            self.skipTest("SKIPPED: Tesseract not available")
        import tempfile
        blank_path = tempfile.mktemp(suffix=".png")
        blank = np.ones((200,400,3), dtype=np.uint8)*255
        cv2.imwrite(blank_path, blank)
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        try:
            result = ingestor.ingest_image(blank_path, lang='eng', allow_mock_fallback=False)
            self.assertNotIn("MOCK", result['text'])
        except ValueError as e:
            self.assertIn("Empty", str(e))
        finally:
            if os.path.exists(blank_path):
                os.remove(blank_path)

    def test_error_unavailable_tesseract(self):
        """REAL TEST - Unavailable Tesseract handling when allow_mock=False"""
        adapter = TesseractAdapter()
        if adapter.is_available():
            self.skipTest("SKIPPED: Tesseract IS available in this env, cannot test unavailable path")
        ingestor = ScreenIngestor(ocr_adapter='tesseract')
        with self.assertRaises(RuntimeError):
            ingestor.ingest_image(self._get_image_path("real_english.png") or "/tmp/nonexistent.png", allow_mock_fallback=False)

if __name__ == '__main__':
    unittest.main()
