import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

from sitegen.blog import PAGE_SIZE, page_window
from sitegen.build import build
from sitegen.render import thumbnail_path

SITE_YAML = Path(__file__).resolve().parents[1] / "content" / "en" / "site.yaml"


def post(n: int, day: str, cats="[New features]", tags="[tips]", extra="") -> str:
    return (f"---\ntitle: Post {n}\ndate: {day}\nslug: post-{n}\ncategories: {cats}\n"
            f"tags: {tags}\nsummary: Summary {n}.\n{extra}---\nBody **{n}**.\n")


@pytest.fixture
def site(tmp_path, monkeypatch):
    content = tmp_path / "content"
    blog = content / "en" / "blog"
    blog.mkdir(parents=True)
    (content / "en" / "site.yaml").write_text(SITE_YAML.read_text())
    for n in range(1, 19):  # 18 posts + 1 moved = 19 -> /blog/ has 3 pages (9, 9, 1)
        day = f"2025-01-{n:02d}"
        (blog / f"{day}-post-{n}.md").write_text(post(n, day))
    (blog / "2023-12-22-moved.md").write_text(
        post(99, "2023-12-22", cats="[Tips & tricks]").replace("slug: post-99", "slug: moved\nurl_date: 2023-12-23"))
    media = tmp_path / "media"
    media.mkdir()
    monkeypatch.setattr("sitegen.paths.MEDIA", media)
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    return out


def test_article_and_legacy_url_date(site):
    assert "Body <strong>1</strong>" in (site / "2025/01/01/post-1/index.html").read_text()
    assert (site / "2023/12/23/moved/index.html").is_file()
    assert not (site / "2023/12/22/moved").exists()


def test_page_size_is_nine():
    assert PAGE_SIZE == 9


@pytest.mark.parametrize("current,total,expected", [
    (1, 1, [1]),
    (1, 2, [1, 2]),
    (2, 2, [1, 2]),
    (4, 7, [1, 2, 3, 4, 5, 6, 7]),
    (1, 8, [1, 2, None, 8]),
    (2, 8, [1, 2, 3, None, 8]),
    (4, 8, [1, 2, 3, 4, 5, None, 8]),
    (5, 8, [1, None, 4, 5, 6, 7, 8]),
    (8, 8, [1, None, 7, 8]),
    (10, 20, [1, None, 9, 10, 11, None, 20]),
    (3, 20, [1, 2, 3, 4, None, 20]),
    (19, 20, [1, None, 18, 19, 20]),
])
def test_page_window(current, total, expected):
    assert page_window(current, total) == expected


def test_blog_pagination(site):
    first = (site / "blog/index.html").read_text()
    assert "Post 18" in first and "Post 10" in first and "Post 9" not in first
    assert 'href="/blog/page/2/"' in first
    assert 'aria-current="page">1<' in first
    assert 'href="/blog/"' not in first.split('aria-label="Pagination"')[1]  # page 1 is not a link
    assert 'rel="prev"' not in first and 'rel="next" href="/blog/page/2/" aria-label="Next page"' in first
    assert 'pager__step--off" aria-hidden="true">&lsaquo;<' in first
    assert 'aria-label="Previous page"' not in first
    assert "Older posts" not in first and "Newer posts" not in first
    second = (site / "blog/page/2/index.html").read_text()
    assert "Post 9" in second and 'rel="prev" href="/blog/" aria-label="Previous page"' in second
    assert 'aria-current="page">2<' in second
    assert 'href="/blog/page/3/"' in second
    last = (site / "blog/page/3/index.html").read_text()
    assert 'aria-current="page">3<' in last and 'rel="next"' not in last
    assert 'pager__step--off" aria-hidden="true">&rsaquo;<' in last
    assert 'aria-label="Next page"' not in last


def test_pagination_in_archives(site):
    html = (site / "category/new-features/page/2/index.html").read_text()
    assert 'href="/category/new-features/"' in html
    assert 'aria-current="page">2<' in html
    assert '<nav class="pager" aria-label="Pagination">' in html


def test_single_page_archive_has_no_pager(site):
    assert 'aria-label="Pagination"' not in (site / "category/tips-tricks/index.html").read_text()


def test_taxonomy_and_date_archives(site):
    assert "Post 15" in (site / "category/new-features/index.html").read_text()
    assert "Post 99" in (site / "category/tips-tricks/index.html").read_text()
    assert "Post 15" in (site / "tag/tips/index.html").read_text()
    assert "Post 5" in (site / "2025/01/05/index.html").read_text()
    assert (site / "2025/01/index.html").is_file() and (site / "2025/index.html").is_file()
    assert "Post 99" in (site / "2023/12/23/index.html").read_text()


def test_feed_is_rss_with_full_content(site):
    root = ET.parse(site / "feed/index.xml").getroot()
    items = root.findall("./channel/item")
    assert len(items) == 19  # fewer than FEED_SIZE
    assert items[0].findtext("link") == "https://andbible.org/2025/01/18/post-18/"
    ns = {"content": "http://purl.org/rss/1.0/modules/content/"}
    assert "<strong>18</strong>" in items[0].find("content:encoded", ns).text
    assert (site / "feed/index.html").read_bytes() == (site / "feed/index.xml").read_bytes()


def test_feed_has_atom_self_link(site):
    root = ET.parse(site / "feed/index.xml").getroot()
    link = root.find("./channel/{http://www.w3.org/2005/Atom}link")
    assert link is not None and link.attrib == {
        "href": "https://andbible.org/feed/", "rel": "self", "type": "application/rss+xml"}


def test_absolute_urls_only_rewrites_site_relative_href_and_src():
    from sitegen.blog import absolute_urls
    html = ('<a href="/blog/">x</a><img src="/media/a.webp"> <a href="//cdn.x/y">c</a> '
            '<a href="https://z/">z</a> <a href="#n">n</a> <p>src="/not-an-attr"</p>')
    out = absolute_urls(html)
    assert 'href="https://andbible.org/blog/"' in out and 'src="https://andbible.org/media/a.webp"' in out
    assert 'href="//cdn.x/y"' in out and 'href="https://z/"' in out and 'href="#n"' in out


def test_sitemap_lists_posts(site):
    text = (site / "sitemap.xml").read_text()
    assert "https://andbible.org/2025/01/01/post-1/" in text
    assert "https://andbible.org/blog/" in text


def test_article_has_social_metadata(site):
    html = (site / "2025/01/01/post-1/index.html").read_text()
    assert 'property="og:type" content="article"' in html
    assert 'og:image" content="https://andbible.org/assets/img/og-default.png"' in html
    assert 'rel="canonical" href="https://andbible.org/2025/01/01/post-1/"' in html


VIDEO = "https://www.youtube.com/watch?v=abcDEF12345"


@pytest.mark.parametrize(
    "body, hero",
    [
        (f"{VIDEO}\n\nText.\n", False),
        (f"\n\n<{VIDEO}>\n\nText.\n", False),
        ("Intro text.\n\n" + VIDEO + "\n", True),
        (f"```\n{VIDEO}\n```\n\nText.\n", True),
        ("Plain text.\n", True),
    ],
)
def test_feature_image_hidden_only_above_a_leading_embed(tmp_path, monkeypatch, body, hero):
    content = tmp_path / "content"
    (content / "en" / "blog").mkdir(parents=True)
    (content / "en" / "site.yaml").write_text(SITE_YAML.read_text())
    media = tmp_path / "media"
    (media / "blog").mkdir(parents=True)
    (media / "blog" / "feature.png").write_bytes(b"png")
    (media / thumbnail_path("abcDEF12345")).parent.mkdir(parents=True, exist_ok=True)
    (media / thumbnail_path("abcDEF12345")).write_bytes(b"webp")
    monkeypatch.setattr("sitegen.paths.MEDIA", media)
    extra = "image: blog/feature.png\nimage_alt: Feature\n"
    text = post(1, "2025-01-01", extra=extra).split("---\n")
    (content / "en" / "blog" / "2025-01-01-post-1.md").write_text(f"---\n{text[1]}---\n{body}")
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)

    article = (out / "2025/01/01/post-1/index.html").read_text()
    assert ('class="post__image"' in article) is hero
    assert 'og:image" content="https://andbible.org/media/blog/feature.png"' in article
    assert 'src="/media/blog/feature.png"' in (out / "blog/index.html").read_text()
    assert ('data-yt-id="abcDEF12345"' in article) is (VIDEO in body and "```" not in body)



def test_post_without_categories_or_tags_has_no_empty_posted_in_footer(tmp_path, monkeypatch):
    content = tmp_path / "content"
    blog = content / "en" / "blog"
    blog.mkdir(parents=True)
    (content / "en" / "site.yaml").write_text(SITE_YAML.read_text())
    (blog / "2025-03-01-bare.md").write_text(
        "---\ntitle: Bare\ndate: '2025-03-01'\nslug: bare\nsummary: No taxonomy.\n---\nBody.\n")
    (blog / "2025-03-02-tagged.md").write_text(post(2, "2025-03-02").replace("slug: post-2", "slug: tagged"))
    media = tmp_path / "media"
    media.mkdir()
    monkeypatch.setattr("sitegen.paths.MEDIA", media)
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    assert "post__foot" not in (out / "2025/03/01/bare/index.html").read_text()
    tagged = (out / "2025/03/02/tagged/index.html").read_text()
    assert "post__foot" in tagged and 'rel="category"' in tagged
