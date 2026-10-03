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


def test_embed_html_default_output_is_unchanged():
    from sitegen.youtube import embed_html
    out = embed_html("abcDEF12345", "short", "T")
    assert "yt__backdrop" not in out and "yt--card" not in out
    assert out.startswith('<div class="yt yt--short" data-yt-id="abcDEF12345">')


def test_embed_html_card_gives_uniform_shape_and_short_backdrop():
    from sitegen.youtube import embed_html
    short = embed_html("abcDEF12345", "short", "T", card=True)
    video = embed_html("abcDEF12345", "video", "T", card=True)
    assert short.startswith('<div class="yt yt--short yt--card"')
    assert video.startswith('<div class="yt yt--video yt--card"')
    backdrop = '<img class="yt__backdrop" src="/media/videos/abcDEF12345.webp" alt="" aria-hidden="true" loading="lazy" decoding="async">'
    assert backdrop in short and "yt__backdrop" not in video
    assert short.index("yt__backdrop") < short.index('class="yt__fg"')  # backdrop sits behind the foreground
    assert "Watch on YouTube" in short and "youtube-nocookie" not in short  # no third-party request before click


def test_card_css_is_16_9_for_every_listing_card():
    from sitegen import paths
    css = (paths.ASSETS / "css" / "embeds.css").read_text()
    assert ".yt--card .yt__play" in css and ".yt--card .yt__frame" in css
    for rule in (".yt--card .yt__play", ".yt--card .yt__frame"):
        block = css.split(rule + " {", 1)[1].split("}", 1)[0]
        assert "aspect-ratio: 16 / 9" in block and "max-width: none" in block


def test_card_keeps_fallback_link_and_css_hides_it_only_with_js():
    from sitegen import paths
    card = embed_html("abcDEF12345", "video", "T", card=True, meta="2026")
    assert 'class="yt__link"' in card and "Watch on YouTube" in card  # no-JS fallback and crawlers
    css = (paths.ASSETS / "css" / "embeds.css").read_text()
    assert ".js-yt .yt--card .yt__link { display: none; }" in css
    assert "\n.yt--card .yt__link" not in css  # never hidden without the JS hook
    js = (paths.ASSETS / "js" / "lite-yt.js").read_text()
    assert 'classList.add("js-yt")' in js
