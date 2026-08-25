# Lazy to avoid circular imports during package init
def __getattr__(name):
    if name == "PublishingController":
        from .controller import PublishingController
        return PublishingController
    if name == "PublishingQueue":
        from .queue import PublishingQueue
        return PublishingQueue
    if name == "PublishingAnalytics":
        from .analytics import PublishingAnalytics
        return PublishingAnalytics
    if name == "M9Error":
        from .errors import M9Error
        return M9Error
    raise AttributeError(name)

__all__ = [
    "PublishingController",
    "PublishingQueue",
    "PublishingAnalytics",
    "M9Error",
]
