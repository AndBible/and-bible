"""Repository facts (stars, licence) for the topbar GitHub link.

The site makes no third-party requests at runtime, so the numbers are committed in
`data/github.yaml` and refreshed on demand:

    uv run python -m sitegen.github
"""

from __future__ import annotations

import json
import sys
from collections.abc import Callable
from pathlib import Path

import yaml

from sitegen import paths

_API = "https://api.github.com/repos/AndBible/and-bible"
FILE = paths.DATA / "github.yaml"


def _http_get(url: str) -> bytes:
    import urllib.request
    request = urllib.request.Request(url, headers={"User-Agent": "andbible-website-build"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def format_stars(count: int) -> str:
    """816 -> "816", 1234 -> "1.2k", 12000 -> "12k"."""
    if count < 1000:
        return str(count)
    thousands = f"{count / 1000:.1f}".removesuffix(".0")
    return f"{thousands}k"


def load(path: Path = FILE) -> dict[str, str]:
    """`{"stars": "816", "license": "GPL-3.0"}`, or `{}` when the file is missing (link shows no badges)."""
    if not path.is_file():
        return {}
    raw = yaml.safe_load(path.read_text()) or {}
    if not isinstance(raw.get("stars"), int) or not raw.get("license"):
        raise ValueError(f"{path}: needs an integer `stars` and a `license`")
    return {"stars": format_stars(raw["stars"]), "license": str(raw["license"])}


def refresh(path: Path = FILE, fetcher: Callable[[str], bytes] = _http_get) -> dict:
    """Read the live repository and rewrite `path`. Raises OSError or ValueError when GitHub cannot be read."""
    raw = json.loads(fetcher(_API))
    license_id = (raw.get("license") or {}).get("spdx_id")
    if not isinstance(raw.get("stargazers_count"), int) or not license_id or license_id == "NOASSERTION":
        raise ValueError(f"unexpected GitHub answer: {str(raw)[:80]}")
    facts = {"stars": raw["stargazers_count"], "license": license_id}
    path.write_text("# Refreshed with: uv run python -m sitegen.github\n" + yaml.safe_dump(facts))
    return facts


if __name__ == "__main__":
    try:
        print(refresh())
    except (OSError, ValueError) as exc:
        print(f"github refresh failed: {exc}", file=sys.stderr)
        sys.exit(1)
