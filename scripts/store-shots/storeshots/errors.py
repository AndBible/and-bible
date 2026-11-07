class ShotError(Exception):
    """A shot (or its caption) is unusable; the message names the shot."""

    def __init__(self, shot: str, message: str):
        self.shot = shot
        super().__init__(f"{shot}: {message}")
