from sitegen.check import check


def page(tmp_path, html, name="index.html"):
    (tmp_path / name).write_text(html)
    return tmp_path


def test_clean_page_passes(tmp_path):
    assert check(page(tmp_path, '<a href="https://github.com/">x</a><img src="/media/a.webp">')) == []


def test_external_script_font_and_image_fail(tmp_path):
    html = ('<script src="https://cdn.example.com/x.js"></script>'
            '<link rel="stylesheet" href="https://fonts.googleapis.com/css">'
            '<img src="//i.ytimg.com/vi/x/hq.jpg">')
    assert len(check(page(tmp_path, html))) == 3


def test_css_url_fails(tmp_path):
    (tmp_path / "a.css").write_text('@import url("https://fonts.example.com/x.css");')
    page(tmp_path, "<p>ok</p>")
    assert any("a.css" in p for p in check(tmp_path))


def test_canonical_and_rss_alternate_allowed(tmp_path):
    html = ('<link rel="canonical" href="https://andbible.org/">'
            '<link rel="alternate" type="application/rss+xml" href="https://andbible.org/feed/">')
    assert check(page(tmp_path, html)) == []


def test_empty_site_is_a_problem(tmp_path):
    assert check(tmp_path) != []


import pytest

from sitegen.docs import theme_language


@pytest.mark.parametrize("html,needle", [
    ('<a data-md-component="source" href="x">r</a>', "data-md-component"),
    ('<div class="a glightbox"></div>', "glightbox"),
    ('<div class="pyodide"></div>', "pyodide"),
    ('<p style="background:url(//x/y.png)">x</p>', "stylesheet fetches"),
    ('<style>@import url(//x/y.css)</style>', "stylesheet fetches"),
    ('<img srcset="a.png 1x, https://x/b.png 2x">', "srcset"),
    ('<video poster="https://x/p.jpg"></video>', "poster"),
    ('<object data="//x/o.swf"></object>', "data="),
    ('<link rel="alternate stylesheet" href="https://x/s.css">', "href="),
    ('<svg><use xlink:href="https://x/s.svg#i"/></svg>', "xlink:href"),
])
def test_detection_paths(tmp_path, html, needle):
    problems = check(page(tmp_path, html))
    assert problems and any(needle in p for p in problems)


def test_external_url_in_standalone_css_fails(tmp_path):
    (tmp_path / "s.css").write_text("a{background:url(https://x/y.png)}")
    page(tmp_path, "<p>ok</p>")
    assert any("s.css" in p and "stylesheet fetches" in p for p in check(tmp_path))


def test_theme_language_falls_back():
    assert theme_language("fi") == "fi"
    assert theme_language("xx") == "en"


def test_feed_self_link_allowed_but_other_atom_links_are_not(tmp_path):
    ok = '<channel><atom:link href="https://andbible.org/feed/" rel="self" type="application/rss+xml"/></channel>'
    assert check(page(tmp_path, ok)) == []
    bad = '<channel><atom:link href="https://evil.example/x" rel="hub"/></channel>'
    assert len(check(page(tmp_path, bad, "feed.html"))) == 1
