import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent.parent.parent))
try:
    from m5_youtube_screen_input.utils import validate_youtube_url, extract_video_id
    from m5_youtube_screen_input.youtube_ingestor import YouTubeIngestor
except ImportError:
    from utils import validate_youtube_url, extract_video_id
    from youtube_ingestor import YouTubeIngestor

class TestYouTube(unittest.TestCase):
    """REAL UNIT TESTS (not mock OCR dependent) - YouTube module"""
    def setUp(self):
        self.ingestor = YouTubeIngestor()

    def test_valid_url_real(self):
        """REAL TEST - URL validation logic"""
        urls = ["https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ"]
        for url in urls:
            self.assertTrue(validate_youtube_url(url))

    def test_invalid_url_real(self):
        """REAL TEST - invalid URL handling"""
        invalid = ["https://www.youtube.com/", "https://invalid-url.com", "not a url", ""]
        for url in invalid:
            self.assertFalse(validate_youtube_url(url))
            with self.assertRaises(ValueError):
                self.ingestor.ingest(url)

    def test_unavailable_transcript_real(self):
        """REAL TEST - transcript unavailable handling (no fake PASS)"""
        orig = self.ingestor._get_transcript
        self.ingestor._get_transcript = lambda url: None
        try:
            result = self.ingestor.ingest("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            self.assertIsNone(result['transcript'])
            self.assertEqual(result['segments'], [])
            self.assertIsInstance(result['confidence'], float)
        finally:
            self.ingestor._get_transcript = orig

    def test_missing_metadata_real(self):
        """REAL TEST - missing metadata handling"""
        from utils import VideoMetadata
        orig = self.ingestor._get_metadata
        self.ingestor._get_metadata = lambda url: VideoMetadata(title="", duration=0, channel="", views=0)
        try:
            result = self.ingestor.ingest("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            self.assertEqual(result['metadata']['title'], "")
            self.assertLess(result['confidence'], 0.5)
        finally:
            self.ingestor._get_metadata = orig

    def test_video_id_extraction_real(self):
        """REAL TEST - ID extraction"""
        self.assertEqual(extract_video_id("https://www.youtube.com/watch?v=abc123defgh"), "abc123defgh")
        self.assertEqual(extract_video_id("https://youtu.be/abc123defgh"), "abc123defgh")

    def test_ingest_produces_valid_m2_handoff(self):
        """REAL TEST - M2 handoff schema"""
        result = self.ingestor.ingest("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        self.assertIn('source', result)
        self.assertEqual(result['source'], 'youtube')
        self.assertIn('metadata', result)
        self.assertIn('confidence', result)
        self.assertIn('structured_content', result)

if __name__ == '__main__':
    unittest.main()
