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
