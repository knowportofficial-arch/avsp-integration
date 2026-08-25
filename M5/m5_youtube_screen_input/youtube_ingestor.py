"""
YouTube Ingestor - Part A - M5.1
Preserves M5 functionality
"""
import json, subprocess, tempfile, os
from typing import Optional, Dict, Any, List
from datetime import datetime
import logging
try:
    from .utils import validate_youtube_url, extract_video_id, YouTubeOutput, VideoMetadata, clean_transcript_text, segment_transcript, extract_topics, identify_hooks
except ImportError:
    from utils import validate_youtube_url, extract_video_id, YouTubeOutput, VideoMetadata, clean_transcript_text, segment_transcript, extract_topics, identify_hooks

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class YouTubeIngestor:
    def __init__(self):
        self._ytdlp_available = self._check_ytdlp()
    def _check_ytdlp(self):
        try:
            subprocess.run(['yt-dlp','--version'], capture_output=True, check=True)
            return True
        except:
            logger.warning("yt-dlp not found - mock mode")
            return False
    def ingest(self, url: str, output_file: Optional[str] = None) -> Dict[str, Any]:
        logger.info(f"Ingesting YouTube: {url}")
        if not validate_youtube_url(url):
            raise ValueError(f"Invalid YouTube URL: {url}")
        vid = extract_video_id(url)
        if not vid:
            raise ValueError(f"Could not extract ID: {url}")
        metadata = self._get_metadata(url)
        transcript = self._get_transcript(url)
        if transcript:
            transcript = clean_transcript_text(transcript)
        segments = segment_transcript(transcript) if transcript else []
        structure = self._analyze_structure(segments)
        topics = extract_topics(segments) if segments else []
        hooks = identify_hooks(segments) if segments else []
        out = YouTubeOutput(url=url, metadata=metadata.to_dict() if metadata else {}, transcript=transcript, segments=segments, topics=topics, hooks=hooks, structured_content=structure, confidence=self._calculate_confidence(transcript, metadata), processed_at=datetime.now().isoformat())
        result = out.to_dict()
        if output_file:
            self._save_output(result, output_file)
        return result
    def _get_metadata(self, url):
        try:
            if not self._ytdlp_available:
                return self._get_mock_metadata()
            cmd = ['yt-dlp','--skip-download','--no-playlist','--print-json', url]
            r = subprocess.run(cmd, capture_output=True, text=True, check=True)
            data = json.loads(r.stdout)
            return VideoMetadata(title=data.get('title',''), duration=data.get('duration',0), channel=data.get('uploader',''), views=data.get('view_count',0), likes=data.get('like_count'), description=data.get('description',''), upload_date=data.get('upload_date',''), tags=data.get('tags',[]))
        except Exception as e:
            logger.warning(f"Metadata error: {e}")
            return self._get_mock_metadata()
    def _get_transcript(self, url):
        try:
            if not self._ytdlp_available:
                return None
            with tempfile.TemporaryDirectory() as td:
                cmd = ['yt-dlp','--skip-download','--write-subs','--sub-langs','en','--convert-subs','txt','-o', os.path.join(td,'%(title)s.%(ext)s'), url]
                subprocess.run(cmd, capture_output=True, text=True, check=True)
                files = [f for f in os.listdir(td) if f.endswith('.txt')]
                if files:
                    with open(os.path.join(td, files[0]), 'r', encoding='utf-8') as f:
                        return f.read()
            return None
        except Exception as e:
            logger.warning(f"Transcript unavailable: {e}")
            return None
    def _analyze_structure(self, segs):
        if not segs:
            return {'segments_count':0,'average_length':0,'total_length':0,'structure':'empty'}
        total = sum(s['length'] for s in segs)
        return {'segments_count':len(segs),'average_length':total/len(segs),'total_length':total,'structure':'segmented'}
    def _calculate_confidence(self, transcript, metadata):
        c = 0.5 if transcript and len(transcript)>100 else 0.1
        c += 0.2 if metadata and metadata.title else 0
        c += 0.1 if metadata and metadata.duration>60 else 0
        return min(c,0.95)
    def _get_mock_metadata(self):
        return VideoMetadata(title="Sample YouTube Video - AI Studio Pro", duration=180, channel="AVSP Channel", views=1000, likes=50, description="Sample", upload_date="20240101", tags=["AI","Video","Studio"])
    def _save_output(self, data, fp):
        with open(fp,'w',encoding='utf-8') as f:
            json.dump(data,f,indent=2,ensure_ascii=False)
