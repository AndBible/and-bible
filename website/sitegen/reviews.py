"""The curated Google Play reviews (data/reviews.yaml) shown on the landing page."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml


@dataclass(frozen=True)
class Review:
    year: int
    text: str


def load(path: Path) -> list[Review]:
    """Read and validate the list; a missing file is no reviews (the section is omitted). Raises ValueError."""
    if not path.is_file():
        return []
    entries = yaml.safe_load(path.read_text(encoding="utf-8")) or []
    if not isinstance(entries, list) or not entries:
        raise ValueError(f"{path}: must be a non-empty list of reviews")
    reviews: list[Review] = []
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError(f"{path}: each review must be a mapping, got {entry!r}")
        if set(entry) - {"year", "text"}:
            raise ValueError(f"{path}: unexpected keys {sorted(set(entry) - {'year', 'text'})} (reviews carry no names)")
        text, year = entry.get("text"), entry.get("year")
        if not isinstance(text, str) or not text.strip():
            raise ValueError(f"{path}: empty review text: {entry!r}")
        if not isinstance(year, int) or isinstance(year, bool):
            raise ValueError(f"{path}: year must be an integer: {entry!r}")
        reviews.append(Review(year, text.strip()))
    return reviews
