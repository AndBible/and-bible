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
