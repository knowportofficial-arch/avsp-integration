from .base import Publisher, PublisherConfig

# Lazy exports to avoid circular imports with controller / mock
def __getattr__(name):
    if name in (
        "YouTubePublisher", "FacebookPublisher", "InstagramPublisher",
        "TelegramPublisher", "WebPublisher",
        "MockYouTubePublisher", "MockFacebookPublisher", "MockInstagramPublisher",
        "MockTelegramPublisher", "MockWebPublisher",
        "get_mock_publisher", "get_publisher",
    ):
        from . import mock as _mock
        from . import youtube as _yt
        from . import facebook as _fb
        from . import instagram as _ig
        from . import telegram as _tg
        from . import web as _web
        mapping = {
            "YouTubePublisher": _yt.YouTubePublisher,
            "FacebookPublisher": _fb.FacebookPublisher,
            "InstagramPublisher": _ig.InstagramPublisher,
            "TelegramPublisher": _tg.TelegramPublisher,
            "WebPublisher": _web.WebPublisher,
            "MockYouTubePublisher": _mock.MockYouTubePublisher,
            "MockFacebookPublisher": _mock.MockFacebookPublisher,
            "MockInstagramPublisher": _mock.MockInstagramPublisher,
            "MockTelegramPublisher": _mock.MockTelegramPublisher,
            "MockWebPublisher": _mock.MockWebPublisher,
            "get_mock_publisher": _mock.get_mock_publisher,
            "get_publisher": _mock.get_publisher,
        }
        return mapping[name]
    raise AttributeError(name)

__all__ = [
    "Publisher",
    "PublisherConfig",
    "YouTubePublisher",
    "FacebookPublisher",
    "InstagramPublisher",
    "TelegramPublisher",
    "WebPublisher",
    "MockYouTubePublisher",
    "MockFacebookPublisher",
    "MockInstagramPublisher",
    "MockTelegramPublisher",
    "MockWebPublisher",
    "get_mock_publisher",
    "get_publisher",
]
