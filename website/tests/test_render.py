from pathlib import Path

import pytest

from sitegen.render import markdown_to_html


@pytest.fixture
def media(tmp_path):
    for rel in ["blog/2024/01/a.webp", "videos/abcDEF12345.webp"]:
        (tmp_path / rel).parent.mkdir(parents=True, exist_ok=True)
        (tmp_path / rel).write_text("x")
    return tmp_path


SRC = Path("post.md")


def test_tables_are_wrapped_for_horizontal_scroll(media):
    html = markdown_to_html("| a | b |\n|---|---|\n| 1 | 2 |\n", SRC, media)
    assert '<div class="table-wrap"><table>' in html


def test_footnotes_and_gallery_render(media):
    md = (
        "Text[^1].\n\n[^1]: Note.\n\n"
        '<div class="gallery" markdown>\n![one](/media/blog/2024/01/a.webp)\n</div>\n'
    )
    html = markdown_to_html(md, SRC, media)
    assert 'class="footnote' in html
    assert '<div class="gallery">' in html and 'src="/media/blog/2024/01/a.webp"' in html


def test_missing_image_fails(media):
    with pytest.raises(ValueError, match="missing media.*nope.webp"):
        markdown_to_html("![x](/media/blog/nope.webp)", SRC, media)


def test_missing_thumbnail_fails(media):
    with pytest.raises(ValueError, match="thumbnail.*zzzzzzzzzzz"):
        markdown_to_html("https://youtu.be/zzzzzzzzzzz\n", SRC, media)


def test_youtube_line_renders_embed(media):
    html = markdown_to_html("https://youtu.be/abcDEF12345\n", SRC, media)
    assert 'data-yt-id="abcDEF12345"' in html


@pytest.mark.parametrize("bad", ["<script>alert(1)</script>", '<iframe src="x"></iframe>',
                                 '<img src="/media/blog/2024/01/a.webp" onerror="x">'])
def test_unsafe_html_is_rejected(media, bad):
    with pytest.raises(ValueError, match="not allowed"):
        markdown_to_html(bad, SRC, media)


def test_external_images_are_rejected(media):
    with pytest.raises(ValueError, match="external image"):
        markdown_to_html("![x](https://andbible.org/wp-content/uploads/a.png)", SRC, media)
