import json

import pytest

pytest.importorskip("bs4")
pytest.importorskip("markdownify")

from sitegen.migrate import rst_docs  # noqa: E402


def page(body: str) -> str:
    return f'<html><body><div itemprop="articleBody">{body}</div></body></html>'


def md(body: str, name: str = "x") -> str:
    return rst_docs.convert_page(name, page(body))


def test_kebab_image_names():
    assert rst_docs.image_name("Drop Shadow Settings.png") == "drop-shadow-settings.png"
    assert rst_docs.image_name("google_play_badge.PNG") == "google-play-badge.png"
    assert rst_docs.image_name("f-droid_badge.png") == "f-droid-badge.png"


def test_internal_links_point_at_markdown_sources():
    out = md('<section id="x"><h1>X</h1><p><a class="reference internal" href="look_and_feel.html#a-b">t</a> '
             '<a href="https://example.org/a.html">e</a> <a href="index.html">i</a></p></section>')
    assert "[t](look_and_feel.md#a-b)" in out
    assert "(https://example.org/a.html)" in out
    assert "[i](index.md)" in out


def test_subdirectory_page_links_and_images_stay_relative():
    out = md('<section id="x"><h1>X</h1><p><a href="../faq.html#q">f</a></p>'
             '<img alt="Pic" src="../_images/My Pic.png"/></section>', name="releases/x")
    assert "(../faq.md#q)" in out
    assert "(../images/my-pic.png)" in out


def test_heading_pins_only_the_ids_python_markdown_would_not_produce():
    out = md('<section id="windows"><h1>Windows</h1>'
             '<section id="changing-a-bookmark-s-icon"><h2>Changing a bookmark’s icon</h2></section>'
             '<section id="example-1"><h2>Example</h2></section></section>')
    assert "# Windows\n" in out
    assert "## Changing a bookmark’s icon {#changing-a-bookmark-s-icon}" in out
    assert "## Example {#example-1}" in out


def test_extra_ids_become_empty_spans_and_toc_nav_is_dropped():
    out = md('<section id="x"><h1><a class="toc-backref" href="#id1">X</a></h1>'
             '<nav class="contents" id="table-of-contents"><p class="topic-title">Table of Contents</p>'
             '<ul><li><a class="reference internal" href="#x" id="id1">X</a></li></ul></nav>'
             '<span id="lbl"></span><p>text</p>'
             '<ul id="opts"><li>a</li></ul><p id="para">q</p></section>')
    for anchor in ("table-of-contents", "id1", "lbl", "opts", "para"):
        assert f'<span id="{anchor}"></span>' in out
    assert "Table of Contents" not in out
    assert out.index('<span id="opts"></span>') < out.index("- a")


def test_image_size_and_alignment_survive_as_attr_list():
    out = md('<section id="x"><h1>X</h1><img alt="_images/a_b.png" class="align-center" '
             'src="_images/a_b.png" style="width: 200px;"/><img alt="B" height="50" src="_images/b.png"/></section>')
    assert '![A b](images/a-b.png){ .align-center style="width: 200px" }' in out
    assert '![B](images/b.png){ height="50" }' in out


def test_cite_is_emphasis_and_definition_lists_are_kept():
    out = md('<section id="x"><h1>X</h1><p><cite>Done</cite></p>'
             '<dl><dt>Term</dt><dd><p>Meaning</p></dd></dl></section>')
    assert "*Done*" in out
    assert "Term\n:   Meaning" in out


def test_page_with_missing_ids_gets_them_back():
    html = page('<section id="x"><h1>X</h1><div id="gone"></div></section>')
    out = rst_docs.convert_page("x", html.replace('<div id="gone"></div>', ''), want_ids=["x", "gone"])
    assert '<span id="gone"></span>' in out


def test_nav_from_index_toctrees():
    rst = ("Title\n=====\n\n.. toctree::\n    :maxdepth: 5\n    :caption: Getting Started\n\n"
           "    getting_started\n    usage\n\n.. toctree::\n    :caption: Help & Reference\n\n"
           "    faq\n    releases/index\n\n.. toctree::\n    :hidden:\n\n    videos/v\n")
    nav = rst_docs.nav_from_index(rst)
    assert nav[0] == {"Home": "index.md"}
    assert nav[1] == {"Getting Started": ["getting_started.md", "usage.md"]}
    assert nav[2] == {"Help & Reference": ["faq.md", "releases/index.md"]}
    assert rst_docs.hidden_entries(rst) == ["videos/v"]


def test_nav_block_replaces_the_seed(tmp_path):
    config = tmp_path / "zensical.toml"
    config.write_text('a = 1\nnav = [\n  { "Home" = "index.md" },\n]\n\n[project.theme]\nb = 2\n')
    rst_docs.write_nav(config, [{"Home": "index.md"}, {"G & H": ["a.md", {"R": ["r/i.md"]}]}])
    import tomllib
    text = config.read_text()
    assert tomllib.loads(text)["nav"] == [{"Home": "index.md"}, {"G & H": ["a.md", {"R": ["r/i.md"]}]}]
    assert "[project.theme]\nb = 2" in text


def test_fixture_is_json():  # guards the ground-truth fixture against accidental edits
    from pathlib import Path
    anchors = json.loads((Path(__file__).parent / "fixtures" / "rtd_anchors.json").read_text())
    assert sum(map(len, anchors.values())) == 310


def test_directory_index_becomes_a_section_with_its_pages():
    pages = ["releases/index", "releases/release_4_0", "releases/release_5_0", "faq"]
    assert rst_docs._section("faq.md", pages) == "faq.md"
    assert rst_docs._section("releases/index.md", pages) == {
        "Releases": ["releases/index.md", "releases/release_5_0.md", "releases/release_4_0.md"]}


def test_playlist_embed_becomes_a_link_and_drops_the_player_instructions():
    out = md('<section id="x"><h1>X</h1><p>Intro.</p>'
             '<p>To view all the videos in the playlist, click the menu at the top right of the video below.</p>'
             '<iframe src="https://www.youtube.com/embed/videoseries?si=Q&amp;list=PLabc_1-2"></iframe></section>')
    assert "[Watch the playlist on YouTube](https://www.youtube.com/playlist?list=PLabc_1-2)" in out
    assert "video below" not in out and "Intro." in out


def test_toctree_captions_are_bold_labels():
    out = md('<section id="x"><h1>X</h1><div class="toctree-wrapper compound">'
             '<p class="caption" role="heading"><span class="caption-text">Study Tools</span></p>'
             '<ul><li><a href="bookmarks.html">Bookmarks</a></li></ul></div></section>')
    assert "**Study Tools**" in out


def test_lists_that_lost_their_heading_get_a_label_after_the_anchor():
    out = md('<section id="x"><h1>X</h1><ul id="formatting-options"><li>a</li></ul></section>',
             name="look_and_feel")
    assert out.index('<span id="formatting-options"></span>') < out.index("**Formatting**") < out.index("- a")


def test_problematic_reference_keeps_its_own_anchor_and_gets_a_target():
    out = md('<section id="x"><h1>X</h1><p>see <a href="#id4"><span class="problematic" id="id5">`Formatting`_</span></a>.</p></section>',
             name="look_and_feel")
    assert '[<span id="id5"></span>Formatting](#formatting-options)' in out


def test_resized_image_links_to_the_copied_full_size_file():
    out = md('<section id="x"><h1>X</h1><a class="image-reference" href="_images/a_b.png">'
             '<img alt="A" src="_images/a_b.png" style="width: 32%;"/></a></section>')
    assert '[![A](images/a-b.png){ style="width: 32%" }](images/a-b.png)' in out
