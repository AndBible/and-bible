import re
from pathlib import Path

from markupsafe import Markup

from sitegen import home, videos


def render(template, **context):
    return home.environment().from_string(
        '{% from "_macros.html" import video_row, toc %}' + template
    ).render(**context)


def test_row_with_video_links_title_and_thumbnail():
    url = "https://www.youtube.com/watch?v=abcDEF12345"
    html = render("{{ video_row(row) }}", row={"thumb": "abcDEF12345", "title": "A & B",
                  "url": url, "meta": Markup("<time>Today</time>"), "badge": None,
                  "links": [{"label": "Docs", "url": "/docs/"}]})
    assert 'src="/media/videos/abcDEF12345.webp"' in html
    assert html.count(f'href="{url}"') == 2
    assert "A &amp; B" in html and "<time>Today</time>" in html
    assert 'href="/docs/"' in html and "Docs" in html
    assert "data-yt-id" not in html


def test_row_without_video_has_blank_thumb_badge_and_no_link():
    html = render("{{ video_row(row) }}", row={"thumb": None, "title": "Later", "url": None,
                  "meta": Markup(""), "badge": "Coming soon", "links": []})
    assert 'class="vrow__thumb vrow__thumb--blank"' in html
    assert "Coming soon" in html and "<img" not in html and "youtube.com" not in html
    assert "<a " not in html


def test_toc_links_each_item():
    html = render("{{ toc(items) }}", items=[{"id": "search", "title": "Search", "count": 2},
                                                {"id": "sync", "title": "Sync", "count": 1}])
    assert 'href="#search"' in html and "Search (2)" in html
    assert 'href="#sync"' in html and "Sync (1)" in html


def test_anchor_id():
    assert videos.anchor_id("Bookmarks, notes & study pads") == "bookmarks-notes-study-pads"
    assert videos.anchor_id("Sync & backup") == "sync-backup"


def test_row_css_keeps_text_shrinkable():
    css = (Path(__file__).resolve().parents[1] / "assets/css/site.css").read_text()
    assert re.search(r"\.vrow__text\s*\{[^}]*min-width:\s*0\s*;", css)
