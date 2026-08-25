"""M8 core package."""
__all__ = ["AutonomousProductionController", "M8Error", "ErrorCode"]

def __getattr__(name):
    if name == "AutonomousProductionController":
        from .controller import AutonomousProductionController
        return AutonomousProductionController
    if name == "M8Error":
        from .errors import M8Error
        return M8Error
    if name == "ErrorCode":
        from .errors import ErrorCode
        return ErrorCode
    raise AttributeError(name)
