import pytest

pytest.importorskip("bs4")
pytest.importorskip("markdownify")

from sitegen.migrate.wxr import dump_front_matter, prepare_html, slugify, summary_of  # noqa: E402
from pathlib import Path  # noqa: E402
from sitegen.frontmatter import split  # noqa: E402



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


def test_summary_keeps_punctuation_attached_to_links():
    text = '<p>Run by <a href="x">Consulting</a>, a company. See the <a href="y">webshop</a>.</p><p>Next</p>'
    assert summary_of(text, "") == "Run by Consulting, a company. See the webshop. Next"


def _synthetic(tmp_path, pages=""):
    import io
    import tarfile

    from PIL import Image

    tar = tmp_path / "up.tar"
    with tarfile.open(tar, "w") as tf:
        buf = io.BytesIO()
        Image.new("RGB", (40, 20), (1, 2, 3)).save(buf, "PNG")
        info = tarfile.TarInfo("2024/01/My_Shot.png")
        info.size = buf.tell()
        buf.seek(0)
        tf.addfile(info, buf)
    wxr = tmp_path / "e.xml"
    wxr.write_text(f"""<?xml version="1.0"?>
<rss xmlns:wp="http://wordpress.org/export/1.2/" xmlns:content="http://purl.org/rss/1.0/modules/content/"
 xmlns:excerpt="http://wordpress.org/export/1.2/excerpt/"><channel>
<item><title>Late night</title><link>https://andbible.org/2024/01/05/late-night/</link>
<content:encoded><![CDATA[<p>Hi <img src="https://andbible.org/wp-content/uploads/2024/01/My_Shot-1024x768.png?w=300"></p>]]></content:encoded>
<wp:post_name>late-night</wp:post_name><wp:post_date>2024-01-04 23:30:00</wp:post_date>
<wp:post_type>post</wp:post_type><wp:status>publish</wp:status>
<category domain="category" nicename="tips-tricks"><![CDATA[tips &amp; tricks]]></category></item>
<item><title>About</title><link>https://andbible.org/about/</link><content:encoded></content:encoded>
<wp:post_name>about</wp:post_name><wp:post_type>page</wp:post_type><wp:status>publish</wp:status></item>
{pages}</channel></rss>""", encoding="utf-8")
    return wxr, tar


def test_run_permalink_url_date_suffix_stripping_and_page_skip(tmp_path):
    from sitegen.migrate.wxr import run

    wxr, tar = _synthetic(tmp_path)
    data = tmp_path / "data"
    data.mkdir()
    (data / "redirects.yaml").write_text("/about/: /\n")
    content, media = tmp_path / "content", tmp_path / "media"
    code = run(wxr, tar, content, media, tmp_path / "urls.txt", tmp_path / "r.md", fetch_thumbnails=False, data=data)
    assert code == 0
    post, = (content / "en" / "blog").glob("*.md")
    assert post.name == "2024-01-04-late-night.md"  # date from post_date, URL date from <link>
    front, body = split(post.read_text(), post)
    assert front["url_date"] == "2024-01-05" and front["categories"] == ["tips & tricks"]
    assert "![](/media/blog/2024/01/my-shot.webp)" in body and (media / "blog/2024/01/my-shot.webp").is_file()
    urls = (tmp_path / "urls.txt").read_text().split()
    assert {"/2024/01/05/late-night/", "/2024/01/05/", "/2024/01/", "/2024/", "/about/", "/category/tips-tricks/"} <= set(urls)
    assert "/wp-content/uploads/2024/01/My_Shot-1024x768.png/" in (data / "wp-uploads-redirects.yaml").read_text()


def test_run_rejects_an_unknown_page(tmp_path):
    from sitegen.migrate.wxr import run

    page = ('<item><title>New</title><link>https://andbible.org/new/</link><content:encoded></content:encoded>'
            '<wp:post_name>new</wp:post_name><wp:post_type>page</wp:post_type><wp:status>publish</wp:status></item>')
    wxr, tar = _synthetic(tmp_path, page)
    (tmp_path / "data").mkdir()
    (tmp_path / "data" / "redirects.yaml").write_text("{}\n")
    with pytest.raises(ValueError, match="neither imported nor explicitly skipped"):
        run(wxr, tar, tmp_path / "c", tmp_path / "m", tmp_path / "u.txt", tmp_path / "r.md",
            fetch_thumbnails=False, data=tmp_path / "data")


def test_overlong_excerpt_is_cut_like_a_generated_summary():
    excerpt = "<p>" + " ".join(f"w{i}" for i in range(200)) + "</p>"
    got = summary_of("<p>x</p>", excerpt)
    assert got.endswith("w59…") and len(got.split()) == 60
