"""Which AndBible version was current on a given day, from the GitHub releases of AndBible/and-bible.

Only public releases count: `production-N`, `vX.Y.Z` and the old `build-NN.NN.NN` tags named "Release X.Y.Z".
alpha/beta/test builds are skipped. The answer is the highest major.minor released on or before the day,
which also ignores late backports (3.2.343 was published in 2022, after 4.0).
"""

from __future__ import annotations

import json
import re
from collections.abc import Callable
from datetime import date

_API = "https://api.github.com/repos/AndBible/and-bible/releases?per_page=100&page={page}"
_NAME_VERSION = re.compile(r"(\d+)\.(\d+)")
_PUBLIC_NAME = re.compile(r"(?i)release [\d.]+.*")
MAX_PAGES = 10


def _http_get(url: str) -> bytes:
    import urllib.request
    request = urllib.request.Request(url, headers={"User-Agent": "andbible-website-build"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def public_releases(raw: list[dict]) -> list[tuple[date, tuple[int, int]]]:
    """(published day, (major, minor)) of every public, non-draft release in a GitHub releases listing."""
    found: list[tuple[date, tuple[int, int]]] = []
    for release in raw:
        tag, name = str(release.get("tag_name", "")), str(release.get("name") or "")
        public = tag.startswith("production-") or re.fullmatch(r"v[\d.]+", tag) or (
            tag.startswith("build-") and _PUBLIC_NAME.fullmatch(name))
        version = _NAME_VERSION.search(name)
        if release.get("draft") or not public or "beta" in name.lower() or not version:
            continue
        found.append((date.fromisoformat(str(release["published_at"])[:10]),
                      (int(version.group(1)), int(version.group(2)))))
    return found


def version_on(day: date, releases: list[tuple[date, tuple[int, int]]]) -> str | None:
    """The highest "major.minor" released on or before `day`, or None when nothing was out yet."""
    known = [version for published, version in releases if published <= day]
    return "{}.{}".format(*max(known)) if known else None


def fetch_version(day: date, fetcher: Callable[[str], bytes] = _http_get) -> str | None:
    """`version_on` over the live releases; pages are read (newest first) until `day` is covered.

    Raises OSError or ValueError when GitHub cannot be read."""
    releases: list[tuple[date, tuple[int, int]]] = []
    for page in range(1, MAX_PAGES + 1):
        raw = json.loads(fetcher(_API.format(page=page)))
        if not isinstance(raw, list):
            raise ValueError(f"unexpected GitHub answer: {str(raw)[:80]}")
        releases += public_releases(raw)
        covered = any(published <= day for published, _ in releases)
        oldest = min((date.fromisoformat(str(r["published_at"])[:10]) for r in raw), default=None)
        if len(raw) < 100 or (covered and oldest is not None and oldest <= day):
            break
    return version_on(day, releases)
