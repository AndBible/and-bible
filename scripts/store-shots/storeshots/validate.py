from __future__ import annotations

from pathlib import Path

from PIL import Image

from .errors import ShotError
from .targets import Target

__all__ = ["ShotError", "validate_raw", "validate_final"]


def _fmt(size) -> str:
    return f"{size[0]}x{size[1]}"


def validate_raw(raw: Path, target: Target) -> None:
    """A raw capture must have exactly the target's raw size: never upscaled silently."""
    raw = Path(raw)
    with Image.open(raw) as img:
        if img.size != target.raw_size:
            raise ShotError(raw.stem, f"raw capture is {_fmt(img.size)}, expected "
                                      f"{_fmt(target.raw_size)} for {target.id}")


def validate_final(img: Path, target: Target) -> None:
    """A framed image has exactly the target size and plain RGB (no alpha, no palette)."""
    img = Path(img)
    with Image.open(img) as im:
        if im.size != target.size:
            raise ShotError(img.stem, f"image is {_fmt(im.size)}, expected {_fmt(target.size)} for {target.id}")
        if im.mode != "RGB":
            raise ShotError(img.stem, f"image mode is {im.mode}, expected RGB (no alpha) for {target.id}")
