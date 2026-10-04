"""Languages, English fallback and UI strings.

English lives at the site root; another language `xx` (any content/xx/ with a
site.yaml) is built under /xx/. A file the language lacks falls back to the
English file, so a partial translation is always a complete site.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from sitegen.paths import DEFAULT_LANG


def languages(content: Path) -> list[str]:
    others = sorted(
        d.name for d in content.iterdir()
        if d.is_dir() and d.name != DEFAULT_LANG and (d / "site.yaml").is_file()
    )
    return [DEFAULT_LANG, *others]


def prefix(lang: str) -> str:
    return "" if lang == DEFAULT_LANG else f"/{lang}"


def resolve(content: Path, lang: str, rel: str) -> Path:
    for candidate in (content / lang / rel, content / DEFAULT_LANG / rel):
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f"{rel}: not in content/{lang}/ nor content/{DEFAULT_LANG}/")


def _merge(base: dict[str, Any], over: dict[str, Any]) -> dict[str, Any]:
    merged = dict(base)
    for key, value in over.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            merged[key] = _merge(base[key], value)
        else:
            merged[key] = value
    return merged


def strings(content: Path, lang: str) -> dict[str, Any]:
    base = yaml.safe_load((content / DEFAULT_LANG / "site.yaml").read_text(encoding="utf-8"))
    if lang == DEFAULT_LANG:
        return base
    own = content / lang / "site.yaml"
    return _merge(base, yaml.safe_load(own.read_text(encoding="utf-8")) or {}) if own.is_file() else base
