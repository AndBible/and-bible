"""YAML front matter between `---` lines at the top of a Markdown file."""

from __future__ import annotations

from pathlib import Path

import yaml


def split(text: str, source: Path) -> tuple[dict[str, object], str]:
    if not text.startswith("---\n") or "\n---\n" not in text[4:]:
        raise ValueError(f"{source}: missing YAML front matter (--- delimiters)")
    header, body = text[4:].split("\n---\n", 1)
    try:
        # BaseLoader: every scalar stays a string, so `date: 2025-08-19` is validated
        # by us rather than silently becoming a datetime.date.
        data = yaml.load(header, Loader=yaml.BaseLoader)
    except yaml.YAMLError as exc:
        raise ValueError(f"{source}: invalid YAML front matter: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError(f"{source}: front matter must be a mapping")
    return data, body
