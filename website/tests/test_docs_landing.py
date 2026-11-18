"""The docs landing page lists the nav sections (one row each); it must mirror the nav and nothing may rot."""
import re
from pathlib import Path

import pytest

from sitegen import home, paths
from sitegen.docs import build_docs, published_pages
from sitegen.i18n import strings
from sitegen.videos import load as load_videos, render_videos

INDEX = paths.CONTENT / "en" / "docs" / "index.md"


def md_links(text: str) -> list[str]:
    return re.findall(r"\]\(([^)\s]+)\)", text)


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
    # /videos/ belongs to the landing site, not the docs build: render the real catalog page beside the docs.
    pages = {p.removesuffix(".md") for p in published_pages(paths.WEBSITE / "zensical.toml")}
    render_videos(home.environment(), strings(paths.CONTENT, "en"), load_videos(paths.DATA / "videos.yaml", pages), out)
    return out, (out / "docs" / "index.html").read_text()


def test_built_landing_links_resolve(landing_html):
    out, html = landing_html
    body = html[html.index("<article"):html.index("</article>")]
    hrefs = re.findall(r'href="([^"#]+)(?:#[^"]*)?"', body)
    assert len(hrefs) > 12
    for href in hrefs:
        if href.startswith(("http://", "https://", "mailto:")):
            continue
        target = (out / "docs" / href).resolve() if not href.startswith("/") else out / href.lstrip("/")
        assert (target / "index.html").is_file() or target.is_file(), f"dead landing link {href}"


def nav_groups() -> dict[str, list]:
    import tomllib
    groups = {}
    for entry in tomllib.loads((paths.WEBSITE / "zensical.toml").read_text())["project"]["nav"]:
        (name, items), = entry.items()
        if isinstance(items, list):
            groups[name] = items
    return groups


def landing_rows() -> list[tuple[str, str, str]]:
    """(section name, link target, description) for each row of the single "Browse by topic" table."""
    rows = []
    for line in INDEX.read_text().splitlines():
        if line.startswith("| ["):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            m = re.match(r"\[([^\]]+)\]\(([^)]+)\)", cells[0])
            rows.append((m.group(1), m.group(2), cells[1]))
    return rows


def test_quick_start_comes_before_the_tables():
    text = INDEX.read_text()
    assert 0 < text.index("## Quick start") < text.index("| Section |")


def test_every_row_has_a_description():
    rows = landing_rows()
    assert rows and all(len(desc) > 10 for _, _, desc in rows)


def test_sections_mirror_nav_groups_in_order_and_link_their_first_page():
    """The landing page lists the nav sections only, so it cannot drift from the nav."""
    groups = nav_groups()
    assert [name for name, _, _ in landing_rows()] == list(groups)
    for name, target, _ in landing_rows():
        assert target == groups[name][0], name


def test_landing_tables_use_a_unique_wrapper_not_the_contents_id(landing_html, built_faq):
    assert 'class="ab-docs-landing"' in landing_html[1]
    assert "ab-docs-landing" not in built_faq


def test_css_never_keys_on_the_contents_id():
    css = (paths.WEBSITE / "theme" / "assets" / "andbible-docs.css").read_text()
    assert "#contents" not in css


@pytest.fixture(scope="module")
def built_faq(landing_html):
    return (landing_html[0] / "docs" / "faq" / "index.html").read_text()


def test_docs_header_and_nav_link_to_the_videos_page(landing_html, built_faq):
    html = landing_html[1]
    header = html[html.index("<header"):html.index("</header>")]
    assert '<a href="/videos/">' in header, "docs header lost its Videos link"
    assert re.search(r'<a href="/videos/" class="md-nav__link">\s*(?:<[^>]+>\s*)*All videos', built_faq)


def test_nav_url_entries_are_links_not_pages():
    pages = published_pages(paths.WEBSITE / "zensical.toml")
    assert "/videos/" not in pages and all(p.endswith(".md") for p in pages)
