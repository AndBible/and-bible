#!/usr/bin/env python3
"""Generate AndBible's multi-resolution Windows icon from the Linux app artwork.

Requires Pillow: python3 -m pip install Pillow
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
source = root / "packaging/linux/andbible.png"
target = root / "packaging/windows/andbible.ico"
with Image.open(source) as artwork:
    artwork.convert("RGBA").save(target, format="ICO", sizes=[(size, size) for size in (16, 32, 48, 64, 128, 256)])
