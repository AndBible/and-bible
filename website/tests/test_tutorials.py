"""The 6.0 tutorial series data (data/tutorials.yaml): validation, rendering, and sync with the app's registry."""

from __future__ import annotations

import re
from pathlib import Path

import pytest
import yaml

from sitegen import paths
from sitegen.docs import stage
from sitegen.tutorials import Episode, Series, docs_block, insert_after_title, load, videos_section

REPO = paths.WEBSITE.parent
KOTLIN = REPO / "sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/TutorialVideos.kt"
DATA = paths.DATA / "tutorials.yaml"
EPISODES = paths.WEBSITE / "video" / "episodes"
PLACEHOLDER = "RlLkpKZZwTg"

GOOD = """\
placeholder: RlLkpKZZwTg
episodes:
  - {slug: whats-new-6-0, number: null, title: "What's new", docs: search, anchor: null, youtube: null}
  - {slug: tutorial-01-a, number: 1, title: One, docs: search, anchor: null, youtube: null}
  - {slug: tutorial-02-b, number: 2, title: "Two: more", docs: search, anchor: advanced-search, youtube: abcDEF12345}
"""


def write(tmp_path, text):
    path = tmp_path / "tutorials.yaml"
    path.write_text(text)
    return path


def test_load(tmp_path):
    series = load(write(tmp_path, GOOD), {"search"})
    assert series.placeholder == PLACEHOLDER
    assert [e.number for e in series.episodes] == [None, 1, 2]
    assert series.episodes[2].docs_path == "/docs/search/#advanced-search"
    assert series.ids() == {PLACEHOLDER, "abcDEF12345"}
    assert [e.slug for e in series.by_docs()["search"]] == ["whats-new-6-0", "tutorial-01-a", "tutorial-02-b"]


def test_missing_file_is_an_empty_series(tmp_path):
    assert load(tmp_path / "none.yaml", set()) == Series(None, ())


@pytest.mark.parametrize("bad, message", [
    (GOOD.replace("tutorial-02-b", "tutorial-01-a"), "duplicate"),
    (GOOD.replace("number: 2", "number: 1"), "number"),
    (GOOD.replace("docs: search, anchor: null, youtube: null}\n  - {slug: tutorial-02", "docs: nosuch, anchor: null, youtube: null}\n  - {slug: tutorial-02", 1), "nosuch"),
    (GOOD.replace("abcDEF12345", "short"), "11-character"),
    (GOOD.replace("abcDEF12345", PLACEHOLDER), "reuses"),
    (GOOD.replace("advanced-search", "bad anchor"), "anchor"),
    (GOOD.replace("title: One", "title: ''"), "no title"),
])
def test_invalid_series_rejected(tmp_path, bad, message):
    with pytest.raises(ValueError, match=message):
        load(write(tmp_path, bad), {"search"})


def test_docs_block_mixes_embeds_and_coming_soon():
    series = Series(PLACEHOLDER, (Episode("a", 1, "One", "p"), Episode("b", 2, "Two", "p", None, "abcDEF12345")))
    html = docs_block(list(series.episodes), series)
    assert "\n\n" not in html  # one raw HTML block for Markdown
    assert html.index("Video tutorial: One") < html.index("Video tutorial: Two")
    assert "Coming soon" in html and f"watch?v={PLACEHOLDER}" in html
    assert html.count("data-yt-id") == 1  # the placeholder is a link, never a repeated embed
    assert "<Video" not in html


def test_titles_are_escaped():
    series = Series(None, (Episode("a", 1, "A <b> & c", "p"),))
    assert "&lt;b&gt;" in docs_block(list(series.episodes), series)


def test_insert_after_title():
    assert insert_after_title("# T {#x}\n\nBody\n", "BLOCK") == "# T {#x}\n\nBLOCK\n\nBody\n"
    assert insert_after_title("Body\n", "BLOCK") == "BLOCK\n\nBody\n"
    assert insert_after_title("```\n# not a title\n```\n# T\nx", "B") == "```\n# not a title\n```\n# T\n\nB\n\nx"


def test_videos_section_shows_one_preview_only_while_something_is_unpublished():
    series = Series(PLACEHOLDER, (Episode("a", 1, "One", "p", None, "abcDEF12345"),))
    assert videos_section(series)["preview"] is None
    series = Series(PLACEHOLDER, (Episode("a", 1, "One", "p"), Episode("b", 2, "Two", "p")))
    section = videos_section(series)
    assert section["preview"].count("data-yt-id") == 1 and [r["embed"] for r in section["rows"]] == [None, None]


def test_stage_embeds_after_the_title_in_episode_order_and_skips_a_catalog_duplicate(tmp_path):
    content = tmp_path / "content"
    (content / "en" / "docs").mkdir(parents=True)
    (content / "en" / "docs" / "search.md").write_text("# Search\n\nText\n")
    media = tmp_path / "media"
    (media / "videos").mkdir(parents=True)
    (media / "videos" / "abcDEF12345.webp").write_bytes(b"x")
    series = Series(PLACEHOLDER, (Episode("a", 1, "One", "search"), Episode("b", 2, "Two", "search", None, "abcDEF12345")))
    out = tmp_path / "out"
    stage(content, "en", out, ["search.md"], {"search": [("abcDEF12345", "Catalog title")]}, media_dir=media,
          tutorials=series, site={})
    text = (out / "search.md").read_text()
    assert text.startswith("# Search\n\n<div class=\"tutorial-videos\">")
    assert text.count('data-yt-id="abcDEF12345"') == 1  # no second "Related videos" copy
    assert "Related videos" not in text


def test_stage_requires_a_thumbnail_for_a_published_episode(tmp_path):
    content = tmp_path / "content"
    (content / "en" / "docs").mkdir(parents=True)
    (content / "en" / "docs" / "search.md").write_text("# Search\n")
    series = Series(PLACEHOLDER, (Episode("b", 2, "Two", "search", None, "abcDEF12345"),))
    with pytest.raises(ValueError, match="thumbnail"):
        stage(content, "en", tmp_path / "o", ["search.md"], {}, media_dir=tmp_path / "m", tutorials=series)


# --- The real file ---------------------------------------------------------------------------------

def _kotlin_episodes() -> dict[str, str | None]:
    text = KOTLIN.read_text(encoding="utf-8")
    block = text.split("enum class Episode", 1)[1].split(";", 1)[0]
    found = {}
    for name, ident in re.findall(r"^\s*([A-Z0-9_]+)\((null|\"[A-Za-z0-9_-]{11}\")\)", block, re.M):
        found[name.lower().replace("_", "-")] = None if ident == "null" else ident.strip('"')
    return found


def _published_pages() -> set[str]:
    from sitegen.docs import published_pages
    return {p.removesuffix(".md") for p in published_pages(paths.WEBSITE / "zensical.toml")}


def test_real_file_is_valid():
    assert load(DATA, _published_pages()).episodes


def test_tutorials_yaml_and_the_app_registry_agree():
    data = yaml.safe_load(DATA.read_text(encoding="utf-8"))
    assert {e["slug"]: e["youtube"] for e in data["episodes"]} == _kotlin_episodes()
    placeholder = re.search(r'PLACEHOLDER_VIDEO_ID: String\? = (null|"([^"]+)")', KOTLIN.read_text()).group(2)
    assert data["placeholder"] == placeholder


@pytest.mark.skipif(not EPISODES.is_dir(), reason="website/video is not checked out (private submodule)")
def test_episode_folders_and_tutorials_yaml_agree():
    folders = {d.name for d in EPISODES.iterdir() if d.name.startswith("tutorial-") or d.name == "whats-new-6-0"}
    assert folders == {e["slug"] for e in yaml.safe_load(DATA.read_text())["episodes"]}


def test_the_site_build_never_reads_website_video():
    """website/video moves to a private submodule the public build does not check out."""
    offenders = []
    for source in sorted((paths.WEBSITE / "sitegen").rglob("*.py")):
        if "migrate" in source.parts:
            continue
        text = source.read_text(encoding="utf-8")
        if re.search(r"website/video|WEBSITE\s*/\s*[\"']video[\"']|[\"']video[\"']\s*/\s*[\"']episodes|\.\./video", text):
            offenders.append(source.name)
    assert offenders == []


def test_videos_page_renders_the_preview_as_html_not_text(tmp_path):
    from sitegen import home
    from sitegen.i18n import strings
    from sitegen.videos import render_videos
    series = Series(PLACEHOLDER, (Episode("a", 1, "One", "search"), Episode("b", 2, "Two", "search", None, "abcDEF12345")))
    render_videos(home.environment(), strings(paths.CONTENT, "en"), [], tmp_path, series)
    page = (tmp_path / "videos" / "index.html").read_text()
    assert "&lt;div" not in page and page.count('class="yt yt--video yt--card"') == 2
    assert page.index("Video tutorials (AndBible 6.0)") < page.index("One") < page.index("Two")
