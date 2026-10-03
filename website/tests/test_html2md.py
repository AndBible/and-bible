from pathlib import Path

import pytest

pytest.importorskip("bs4")
pytest.importorskip("markdownify")

from sitegen.migrate.html2md import convert, heading_ids  # noqa: E402
from sitegen.render import markdown_to_html  # noqa: E402

ident = lambda x: x  # noqa: E731


def test_wp_youtube_embed_block_becomes_url_line():
    html = ('<figure class="wp-block-embed is-type-video is-provider-youtube wp-block-embed-youtube">'
            '<div class="wp-block-embed__wrapper">\nhttps://www.youtube.com/watch?v=abcDEF12345\n</div></figure>')
    assert convert(html, ident, ident).strip() == "https://www.youtube.com/watch?v=abcDEF12345"


def test_vertical_embed_becomes_shorts_url():
    html = ('<figure class="wp-block-embed is-provider-youtube wp-embed-aspect-9-16">'
            '<div class="wp-block-embed__wrapper">https://youtube.com/shorts/abcDEF12345?si=x</div></figure>')
    assert convert(html, ident, ident).strip() == "https://www.youtube.com/shorts/abcDEF12345"


def test_sphinx_iframe_becomes_url_line():
    html = '<div class="video"><iframe src="https://www.youtube.com/embed/abcDEF12345" width="560"></iframe></div>'
    assert "https://www.youtube.com/watch?v=abcDEF12345" in convert(html, ident, ident)


def test_admonition():
    html = ('<div class="admonition note"><p class="admonition-title">Note</p>'
            '<p>Back up first.</p></div>')
    assert convert(html, ident, ident).strip() == "!!! note\n\n    Back up first."


def test_custom_title_admonition():
    html = ('<div class="admonition tip"><p class="admonition-title">Pro tip</p><p>Long-press.</p></div>')
    assert convert(html, ident, ident).strip().startswith('!!! tip "Pro tip"')


def test_table_with_pipe_in_cell():
    html = "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>x|y</td><td>2</td></tr></tbody></table>"
    md = convert(html, ident, ident)
    assert "| A | B |" in md and "x\\|y" in md


def test_gallery_and_caption():
    html = ('<figure class="wp-block-gallery"><figure class="wp-block-image"><img src="a.png" alt="A"></figure>'
            '<figure class="wp-block-image"><img src="b.png" alt="B"></figure></figure>'
            '<figure class="wp-block-image"><img src="c.png" alt="C"><figcaption>Cap</figcaption></figure>')
    md = convert(html, ident, lambda s: "/media/" + s)
    assert '<div class="gallery" markdown>' in md
    assert "![A](/media/a.png)" in md and "![B](/media/b.png)" in md
    assert "![C](/media/c.png)\n*Cap*" in md


def test_links_and_images_are_mapped():
    html = '<p><a href="https://andbible.org/2024/01/01/x/">x</a> <img src="https://andbible.org/wp-content/uploads/2024/01/a.png" alt=""></p>'
    md = convert(html, lambda h: h.replace("https://andbible.org", ""), lambda s: "/media/blog/2024/01/a.webp")
    assert "[x](/2024/01/01/x/)" in md and "](/media/blog/2024/01/a.webp)" in md


def test_sphinx_heading_ids_come_from_sections():
    html = ('<section id="setting-permissions"><h2>Setting permissions'
            '<a class="headerlink" href="#setting-permissions">¶</a></h2></section>')
    assert heading_ids(html) == [("Setting permissions", "setting-permissions", 2)]
    assert convert(html, ident, ident).strip() == "## Setting permissions"


def test_footnotes_and_trailer():
    html = ('<p>Hello<sup class="fn"><a href="#fn-1" id="fnref-1">1</a></sup> world.</p>'
            '<ol class="wp-block-footnotes"><li id="fn-1">The note. <a href="#fnref-1">↩︎</a></li></ol>')
    md = convert(html, ident, ident)
    assert "Hello[^1] world." in md
    assert md.strip().endswith("[^1]: The note.")


def test_round_trip_through_site_renderer(tmp_path: Path):
    media = tmp_path / "media"
    media.mkdir()
    for name in ("a.png", "b.png"):
        (media / name).write_bytes(b"x")
    html = (
        '<figure class="wp-block-gallery"><figure class="wp-block-image"><img src="a.png" alt="A"></figure>'
        '<figure class="wp-block-image"><img src="b.png" alt="B"></figure></figure>'
        "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>"
        '<p>Text<sup class="fn"><a href="#fn-1">1</a></sup></p>'
        '<ol class="wp-block-footnotes"><li id="fn-1">Source.</li></ol>'
        '<div class="admonition note"><p class="admonition-title">Note</p><p>Back up first.</p></div>'
    )
    md = convert(html, ident, lambda s: "/media/" + s)
    out = markdown_to_html(md, tmp_path / "post.md", media)
    assert '<div class="gallery"' in out and out.count("<img") == 2
    assert '<div class="table-wrap"><table>' in out and "<th>A</th>" in out
    assert 'class="footnote-ref"' in out and "Source." in out
    assert '<div class="admonition note">' in out and "Back up first." in out


def test_headerless_table_promotes_first_row_to_header(tmp_path: Path):
    html = ("<table><tbody><tr><td><strong>Date</strong></td><td><strong>Event</strong></td></tr>"
            "<tr><td>1</td><td>2</td></tr></tbody></table>")
    md = convert(html, ident, ident)
    assert md.strip().splitlines()[0] == "| Date | Event |"
    out = markdown_to_html(md, tmp_path / "p.md", tmp_path)
    assert "<thead>" in out and "<th>Date</th>" in out and "<th></th>" not in out
    assert "<td>1</td>" in out


def test_code_with_blank_line_inside_admonition_round_trips(tmp_path: Path):
    html = ('<div class="admonition note"><p class="admonition-title">Note</p><p>Run:</p>'
            "<pre>line1\n\nline3</pre><p>After.</p></div>")
    out = markdown_to_html(convert(html, ident, ident), tmp_path / "p.md", tmp_path)
    assert "<pre><code>line1\n\nline3\n</code></pre>" in out
    assert "```" not in out and "<p>After.</p>" in out


def test_literal_backticks_in_text_are_escaped_but_code_is_untouched():
    md = convert("<p>Type `show w' and `show c' now, or <code>ls -a</code>.</p>", ident, ident)
    assert md.strip() == "Type \\`show w' and \\`show c' now, or `ls -a`."
    html = markdown_to_html(md, Path("x.md"), Path("."))
    assert "<code>show" not in html and "<code>ls -a</code>" in html
