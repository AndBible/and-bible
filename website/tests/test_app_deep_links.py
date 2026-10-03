import re
from pathlib import Path

import pytest

from sitegen import paths

CALL = re.compile(r'DocsLinks\.page\(\s*"([a-z0-9_/.-]+)"\s*(?:,\s*"([a-z0-9_-]+)"\s*)?\)')
ROOTS = [paths.REPO / "app" / "src" / "main", paths.REPO / "sharedUi" / "src",
         paths.REPO / "sharedCore" / "src" / "commonMain"]


def scan(roots):
    found = []
    for root in roots:
        for kt in sorted(root.rglob("*.kt")):
            for number, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
                for match in CALL.finditer(line):
                    found.append((kt, number, match.group(1), match.group(2)))
    return found


def unscannable(roots):
    """Every `DocsLinks.page(` occurrence that `scan` cannot check (multi-line, named or computed args)."""
    problems = []
    for root in roots:
        for kt in sorted(root.rglob("*.kt")):
            if kt.name == "DocsLinks.kt":
                continue
            for number, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
                if line.count("DocsLinks.page(") != len(CALL.findall(line)):
                    problems.append(f"{kt}:{number}: DocsLinks.page call is not a literal one-line call; cannot be checked")
    return problems


def missing(site, links):
    problems = []
    for kt, number, page, anchor in links:
        html = site / "docs" / page / "index.html"
        if not html.is_file():
            problems.append(f"{kt}:{number}: no docs page {page!r}")
        elif anchor and f'id="{anchor}"' not in html.read_text(encoding="utf-8"):
            problems.append(f"{kt}:{number}: /docs/{page}/ has no #{anchor}")
    return problems


def test_scan_finds_the_app_links():
    pages = {page for _, _, page, _ in scan(ROOTS)}
    assert {"ai", "bookmarks", "custom_repositories", "navigation"} <= pages


def test_every_app_docs_link_exists():
    assert paths.SITE.is_dir(), "run `make site` first"
    assert missing(paths.SITE, scan(ROOTS)) == []


def test_no_hardcoded_docs_urls_left():
    stale = [f"{kt}:{n}" for root in ROOTS for kt in root.rglob("*.kt") if kt.name != "DocsLinks.kt"
             for n, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1)
             if "docs.andbible.org" in line or "andbible.org/docs/" in line]
    assert stale == []


def test_a_bad_anchor_is_reported(tmp_path):
    kt = tmp_path / "X.kt"
    kt.write_text('val u = DocsLinks.page("getting_started", "no-such-heading")\n')
    problems = missing(paths.SITE, scan([tmp_path]))
    assert problems == [f"{kt}:1: /docs/getting_started/ has no #no-such-heading"]


def test_every_docs_link_call_is_scannable():
    assert unscannable(ROOTS) == []


@pytest.mark.parametrize("source", [
    'val u = DocsLinks.page(\n    "ai", "no-such")\n',
    'val u = DocsLinks.page(page = "ai", anchor = "no-such")\n',
    'val u = DocsLinks.page(name, "no-such")\n',
    'val u = DocsLinks.page("ai", "x$y")\n',
])
def test_an_unscannable_call_is_reported(tmp_path, source):
    kt = tmp_path / "X.kt"
    kt.write_text(source)
    assert len(unscannable([tmp_path])) >= 1
    assert unscannable([tmp_path])[0].startswith(f"{kt}:1:")
