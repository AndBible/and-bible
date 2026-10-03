import re
import json
from pathlib import Path

import pytest

from sitegen import paths
from sitegen.docs import UNPUBLISHED, build_docs, published_pages, stage


def test_nav_whitelist_covers_every_docs_page():
    pages = set(published_pages(paths.WEBSITE / "zensical.toml"))
    on_disk = {p.name for p in (paths.CONTENT / "en" / "docs").glob("*.md")}
    on_disk |= {p.relative_to(paths.CONTENT / "en" / "docs").as_posix()
                for p in (paths.CONTENT / "en" / "docs").glob("*/*.md")}
    assert on_disk - pages - UNPUBLISHED == set(), "add new docs pages to zensical.toml nav"
    assert pages - on_disk == set(), "nav names a page that does not exist"


def test_stage_falls_back_to_english_and_adds_related_videos(tmp_path):
    content = tmp_path / "content"
    (content / "en" / "docs").mkdir(parents=True)
    (content / "en" / "docs" / "a.md").write_text("# A\n\nEnglish\n")
    (content / "en" / "docs" / "b.md").write_text("# B\n\nhttps://youtu.be/abcDEF12345\n")
    (content / "fi" / "docs").mkdir(parents=True)
    (content / "fi" / "docs" / "a.md").write_text("# A\n\nSuomi\n")
    stage(content, "fi", tmp_path / "stage", ["a.md", "b.md"],
          {"a": [("zzzzzzzzzzz", "Intro video")]})
    a = (tmp_path / "stage" / "a.md").read_text()
    assert "Suomi" in a and "## Related videos" in a and 'data-yt-id="zzzzzzzzzzz"' in a
    b = (tmp_path / "stage" / "b.md").read_text()
    assert 'data-yt-id="abcDEF12345"' in b


def test_build_docs_produces_directory_urls(tmp_path):
    out = tmp_path / "out"
    build_docs(paths.CONTENT, out, {})
    assert (out / "docs" / "index.html").is_file()
    assert (out / "docs" / "getting_started" / "index.html").is_file()
    html = (out / "docs" / "getting_started" / "index.html").read_text()
    anchor = json.loads((Path(__file__).parent / "fixtures" / "rtd_anchors.json").read_text())["getting_started"][1]
    assert f'id="{anchor}"' in html


@pytest.fixture(scope="module")
def built_docs(tmp_path_factory):
    out = tmp_path_factory.mktemp("docs-out")
    build_docs(paths.CONTENT, out, {})
    return out


def test_docs_header_matches_the_landing_topbar(built_docs):
    import re

    from sitegen.i18n import strings
    site = strings(paths.CONTENT, "en")
    html = (built_docs / "docs" / "ai" / "index.html").read_text()
    header = html[html.index('<header'):html.index('</header>')]
    assert re.search(r'class="ab-brand" href="/"[^>]*>.*?<span>AndBible</span>', header, re.S)
    nav = header[header.index('class="ab-nav"'):]
    links = re.findall(r'<a href="([^"]+)">([^<]+)</a>', nav)
    n = site["nav"]
    assert links[:5] == [("/blog/", n["blog"]), ("/docs/", n["docs"]), ("/videos/", n["videos"]),
                         (site["sections"]["support_url"], n["support"]), (site["footer"]["source_url"], n["github"])]
    assert "data-theme-toggle" in header and "data-md-component=\"search\"" in header
    assert 'data-md-component="palette"' not in html and 'data-md-component="source"' not in html


def test_docs_pages_apply_the_site_theme_before_first_paint(built_docs):
    html = (built_docs / "docs" / "ai" / "index.html").read_text()
    head = html[:html.index("</head>")]
    assert "andbible-docs-theme.js" in head
    assert (built_docs / "docs" / "assets" / "andbible-docs-theme.js").is_file()
    assert "andbible-theme" in (built_docs / "docs" / "assets" / "andbible-docs-theme.js").read_text()


BADGES = ("google-play", "f-droid", "amazon", "obtainium")


def test_install_badges_share_one_row_wrapper(built_docs):
    import re

    html = (built_docs / "docs" / "getting_started" / "index.html").read_text()
    wrapper = re.search(r'<div class="ab-badges">(.*?)</div>', html, re.S)
    assert wrapper, "install badges must sit in <div class=\"ab-badges\">"
    for name in BADGES:
        assert f"images/{name}-badge.png" in wrapper.group(1)
    assert "height=" not in wrapper.group(1), "height comes from the stylesheet, not per-image attributes"


def test_badge_images_have_one_aspect_ratio():
    from PIL import Image

    images = paths.CONTENT / "en" / "docs" / "images"
    ratios = [Image.open(images / f"{n}-badge.png").size for n in BADGES]
    assert max(w / h for w, h in ratios) - min(w / h for w, h in ratios) < 0.25, ratios
    assert len({h for _, h in ratios}) == 1, ratios


def test_badge_css_keeps_one_row_with_equal_heights():
    css = (paths.WEBSITE / "theme" / "assets" / "andbible-docs.css").read_text()
    assert re.search(r"\.ab-badges\s*\{[^}]*display:\s*flex[^}]*flex-wrap:\s*nowrap", css, re.S)
    assert re.search(r"\.ab-badges\s+img\s*\{[^}]*height:\s*auto[^}]*\}", css, re.S) or "ab-badges img" in css
