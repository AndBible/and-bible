"""Whole-site check: every local URL in the built HTML resolves to a file in _site."""

from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

import pytest

from sitegen import paths

pytestmark = pytest.mark.skipif(not paths.SITE.is_dir(), reason="run `make site` first")

# Documented exceptions: none at the moment. Add (url, reason) pairs only for a gap that cannot be fixed.
EXCEPTIONS: dict[str, str] = {}


class _Urls(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.urls: list[str] = []

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if not value:
                continue
            if name in ("src", "href"):
                self.urls.append(value)
            elif name == "srcset":
                self.urls += [part.strip().split()[0] for part in value.split(",") if part.strip()]


def _resolves(site: Path, url: str) -> bool:
    path = unquote(urlsplit(url).path)
    target = site / path.lstrip("/")
    return target.is_file() or (target.is_dir() and (target / "index.html").is_file())


def test_every_local_url_in_the_built_site_resolves():
    site = paths.SITE
    broken: list[str] = []
    checked = 0
    for page in sorted(site.rglob("*.html")):
        parser = _Urls()
        parser.feed(page.read_text(encoding="utf-8", errors="replace"))
        for url in parser.urls:
            if not url.startswith("/") or url.startswith("//") or url in EXCEPTIONS:
                continue
            checked += 1
            if not _resolves(site, url):
                broken.append(f"{page.relative_to(site)}: {url}")
    assert checked > 1000, f"only {checked} local URLs found; the scan is not looking at the site"
    assert not broken, f"{len(broken)} unresolved local URLs, e.g.\n" + "\n".join(broken[:20])


def test_the_resolver_flags_a_missing_file_and_accepts_directory_indexes(tmp_path):
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs" / "index.html").write_text("x")
    (tmp_path / "a b.png").write_text("x")
    assert _resolves(tmp_path, "/docs/#anchor") and _resolves(tmp_path, "/docs") and _resolves(tmp_path, "/a%20b.png?v=1")
    assert not _resolves(tmp_path, "/nope.png")
