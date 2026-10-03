"""Every deep link into the legacy Read the Docs site must still land on the same section.

`fixtures/rtd_anchors.json` (Task 14) holds every id Sphinx emitted per page, so a legacy
`docs.andbible.org/en/latest/<page>.html#<id>` has to find that id on `/docs/<page>/` of the built site.
Needs `make site` first, like the other tests that read `_site/`.
"""

import json
import re
from pathlib import Path

import pytest

from sitegen import paths

ANCHORS = json.loads((Path(__file__).parent / "fixtures" / "rtd_anchors.json").read_text())


def built_page(page: str) -> Path:
    """`index` is the docs root and `<dir>/index` is `<dir>/`; every other page is `<page>/`."""
    url = page.removesuffix("index").rstrip("/")
    return paths.SITE / "docs" / url / "index.html"


@pytest.mark.parametrize("page", sorted(ANCHORS))
def test_every_legacy_anchor_exists(page):
    target = built_page(page)
    assert target.is_file(), f"/docs/{page}/ missing"
    ids = set(re.findall(r'\sid="([^"]+)"', target.read_text(encoding="utf-8")))
    missing = [a for a in ANCHORS[page] if a not in ids]
    assert not missing, f"{page}: anchors lost: {missing}"


def test_fixture_has_every_page_and_id():
    assert len(ANCHORS) == 38
    assert sum(map(len, ANCHORS.values())) == 310
