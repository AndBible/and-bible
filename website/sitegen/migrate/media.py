"""Optimise WordPress media into the media repo: WebP for images, PDFs and animated GIFs kept."""

from __future__ import annotations

import re
import shutil
import tarfile
import tempfile
from pathlib import Path

from PIL import Image, ImageOps

MAX_WIDTH = 1600
LOSSY_QUALITY = 85
PNG_FALLBACK_QUALITY = 90
PNG_LOSSLESS_LIMIT = 400_000

_TAR_KEY = re.compile(r"(?:^|/)(\d{4}/\d{2}/[^/]+)$")


def _scaled(img: Image.Image) -> Image.Image:
    if img.width <= MAX_WIDTH:
        return img
    height = max(1, round(img.height * MAX_WIDTH / img.width))
    return img.resize((MAX_WIDTH, height), Image.Resampling.LANCZOS)


def optimise(src: Path, dest_dir: Path, stem: str) -> Path:
    """Write the optimised copy of `src` to `dest_dir/<stem>.<ext>` and return its path.

    JPEGs become lossy WebP; PNGs (and still GIFs) become lossless WebP unless that is over
    400 KB, then lossy at quality 90. PDFs and animated GIFs are copied unchanged. EXIF
    orientation is applied and all metadata dropped.
    """
    dest_dir.mkdir(parents=True, exist_ok=True)
    suffix = src.suffix.lower()
    if suffix == ".pdf":
        out = dest_dir / f"{stem}.pdf"
        shutil.copyfile(src, out)
        return out
    out = dest_dir / f"{stem}.webp"
    with Image.open(src) as opened:
        if suffix == ".gif" and getattr(opened, "is_animated", False):
            out = dest_dir / f"{stem}.gif"
            shutil.copyfile(src, out)
            return out
        is_jpeg = opened.format == "JPEG"
        img = _scaled(ImageOps.exif_transpose(opened))
        has_alpha = img.mode in {"RGBA", "LA", "PA"} or "transparency" in img.info
        img = img.convert("RGBA" if has_alpha else "RGB")  # also drops the EXIF/ICC info
        if is_jpeg:
            img.save(out, "WEBP", quality=LOSSY_QUALITY, method=6)
            return out
        img.save(out, "WEBP", lossless=True, method=6)
        if out.stat().st_size > PNG_LOSSLESS_LIMIT:
            img.save(out, "WEBP", quality=PNG_FALLBACK_QUALITY, method=6)
    return out


def from_tar(tar: Path) -> dict[str, Path]:
    """Extract a WordPress uploads tarball to a scratch dir; map "YYYY/MM/name.ext" to the file.

    The caller owns the scratch directory (it lives under the system temp dir).
    """
    scratch = Path(tempfile.mkdtemp(prefix="andbible-media-"))
    with tarfile.open(tar) as tf:
        tf.extractall(scratch, filter="data")
    found: dict[str, Path] = {}
    for path in sorted(scratch.rglob("*")):
        if path.is_file():
            match = _TAR_KEY.search(path.relative_to(scratch).as_posix())
            if match:
                found[match.group(1)] = path
    return found
