import pytest

pytest.importorskip("bs4")
pytest.importorskip("markdownify")

from sitegen.migrate.wxr import dump_front_matter, prepare_html, slugify, summary_of  # noqa: E402
from sitegen.frontmatter import split  # noqa: E402
from pathlib import Path  # noqa: E402


def test_embed_block_becomes_youtube_figure_and_comments_go():
    raw = ('<!-- wp:paragraph --><p>Hi</p><!-- /wp:paragraph -->\n'
           '<!-- wp:embed {"url":"https://www.youtube.com/watch?v=abc\\u002dDEF1234\\u0026amp;list=X","type":"rich"} -->\n'
           '<figure class="wp-block-embed"><div>junk</div></figure>\n<!-- /wp:embed -->')
    html, embeds, dropped = prepare_html(raw)
    assert embeds == 1 and not dropped and "<!--" not in html
    assert 'is-provider-youtube' in html and "v=abc-DEF1234&list=X" in html and "junk" not in html


def test_non_youtube_embed_is_not_counted():
    _, embeds, _ = prepare_html('<!-- wp:embed {"url":"https://example.com/x"} --><figure></figure><!-- /wp:embed -->')
    assert embeds == 0


def test_dynamic_block_and_its_heading_are_dropped_and_reported():
    raw = ('<!-- wp:heading {"level":4} --><h4 class="x">More</h4><!-- /wp:heading -->\n'
           '<!-- wp:a8c/blog-posts {"categories":["1"]} /-->')
    html, _, dropped = prepare_html(raw)
    assert html.strip() == "" and dropped == ["wp:a8c/blog-posts"]


def test_media_slug_and_summary():
    assert slugify("2025-q2_hours_comparison-3") == "2025-q2-hours-comparison-3"
    text = "<p>" + " ".join(f"w{i}" for i in range(40)) + "</p>"
    short = summary_of(text, "")
    assert short.endswith("w29…") and len(short.split()) == 30
    assert summary_of(text, "<p>Given &amp; taken</p>") == "Given & taken"


def test_front_matter_quotes_and_round_trips():
    text = dump_front_matter({"title": "A: b # c", "date": "2024-01-04", "tags": ["x & y"]}) + "\nbody"
    data, body = split(text, Path("x.md"))
    assert data == {"title": "A: b # c", "date": "2024-01-04", "tags": ["x & y"]} and body.strip() == "body"
