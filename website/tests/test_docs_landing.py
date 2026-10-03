"""The docs landing page is hand-curated: every nav page needs a row, and nothing may rot."""
import re
from pathlib import Path

import pytest

from sitegen import paths
from sitegen.docs import build_docs, published_pages

INDEX = paths.CONTENT / "en" / "docs" / "index.md"
# Nav pages deliberately absent from the landing page (the contributor style guide and the
# hidden video page stay reachable from the nav; releases/* are covered by the one Releases row).
EXCLUDED = {"index.md", "style_guide.md", "videos/note_add_to_existing_bookmark.md",
            "customisation/custom_css.md", "customisation/module_creation.md",
            "releases/release_5_0.md", "releases/release_4_1.md", "releases/release_4_0.md"}


def md_links(text: str) -> list[str]:
    return re.findall(r"\]\(([^)\s]+)\)", text)


def test_every_nav_page_is_linked_from_the_landing_page():
    linked = {link.split("#")[0] for link in md_links(INDEX.read_text()) if link.endswith((".md", ".md#"))
              or ".md#" in link}
    missing = set(published_pages(paths.WEBSITE / "zensical.toml")) - EXCLUDED - linked
    assert missing == set(), f"add a row for these pages to docs/index.md: {sorted(missing)}"


def test_exclusions_name_real_nav_pages():
    assert EXCLUDED <= set(published_pages(paths.WEBSITE / "zensical.toml"))


def test_landing_links_only_to_pages_not_to_sub_headings():
    rows = [line for line in INDEX.read_text().splitlines() if line.startswith("| [")]
    assert rows and all("#" not in line.split("|")[1] for line in rows)


def test_no_active_development_note_or_intro_link_list():
    text = INDEX.read_text().lower()
    assert "under active development" not in text
    assert "github repository" not in text and "- [webpage]" not in text


def test_legacy_anchors_survive():
    text = INDEX.read_text()
    assert "{#welcome-to-the-andbible-bible-study-s-documentation}" in text
    assert "{#contents}" in text


@pytest.fixture(scope="module")
def landing_html(tmp_path_factory):
    out = tmp_path_factory.mktemp("landing")
    build_docs(paths.CONTENT, out, {})
    return out, (out / "docs" / "index.html").read_text()


def test_built_landing_links_resolve(landing_html):
    out, html = landing_html
    body = html[html.index("<article"):html.index("</article>")]
    hrefs = re.findall(r'href="([^"#]+)(?:#[^"]*)?"', body)
    assert len(hrefs) > 30
    for href in hrefs:
        if href.startswith(("http://", "https://", "mailto:")) or href == "/videos/":
            continue
        target = (out / "docs" / href).resolve() if not href.startswith("/") else out / href.lstrip("/")
        assert (target / "index.html").is_file() or target.is_file(), f"dead landing link {href}"
