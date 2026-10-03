import pytest

from sitegen.youtube import embed_html, expand_lines, parse


@pytest.mark.parametrize(
    "url, expected",
    [
        ("https://www.youtube.com/watch?v=abcDEF12345", ("abcDEF12345", "video")),
        ("https://youtube.com/watch?v=abcDEF12345&t=10s", ("abcDEF12345", "video")),
        ("https://m.youtube.com/watch?v=abcDEF12345", ("abcDEF12345", "video")),
        ("https://youtu.be/abcDEF12345", ("abcDEF12345", "video")),
        ("https://www.youtube.com/shorts/abcDEF12345", ("abcDEF12345", "short")),
        ("https://www.youtube.com/embed/abcDEF12345?rel=0", ("abcDEF12345", "video")),
        ("https://www.youtube.com/@AndBible", None),
        ("https://example.com/watch?v=abcDEF12345", None),
    ],
)
def test_parse(url, expected):
    assert parse(url) == expected


def test_expand_replaces_only_whole_url_lines():
    md = (
        "Intro\n\n"
        "https://youtu.be/abcDEF12345\n\n"
        "See https://youtu.be/zzzzzzzzzzz inline.\n\n"
        "<https://www.youtube.com/shorts/shortID1234>\n\n"
        "```\nhttps://youtu.be/notinfence1\n```\n"
    )
    out, ids = expand_lines(md)
    assert ids == ["abcDEF12345", "shortID1234"]
    assert "See https://youtu.be/zzzzzzzzzzz inline." in out
    assert "https://youtu.be/notinfence1" in out
    assert out.count('class="yt ') == 2
    assert 'class="yt yt--short"' in out


def test_embed_without_js_has_link_and_no_iframe():
    html = embed_html("abcDEF12345", "video")
    assert "<iframe" not in html
    assert 'src="/media/videos/abcDEF12345.webp"' in html
    assert 'href="https://www.youtube.com/watch?v=abcDEF12345"' in html
    assert 'data-yt-id="abcDEF12345"' in html
    assert "youtube-nocookie" not in html
