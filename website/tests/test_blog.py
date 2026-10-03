import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

from sitegen.build import build

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
    for n in range(1, 13):  # 12 posts -> /blog/ has 2 pages
        day = f"2025-01-{n:02d}"
        (blog / f"{day}-post-{n}.md").write_text(post(n, day))
    (blog / "2023-12-22-moved.md").write_text(
        post(99, "2023-12-22", cats="[Tips & tricks]").replace("slug: post-99", "slug: moved\nurl_date: 2023-12-23"))
    media = tmp_path / "media"
    media.mkdir()
    monkeypatch.setattr("sitegen.paths.MEDIA", media)
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data")
    return out


def test_article_and_legacy_url_date(site):
    assert "Body <strong>1</strong>" in (site / "2025/01/01/post-1/index.html").read_text()
    assert (site / "2023/12/23/moved/index.html").is_file()
    assert not (site / "2023/12/22/moved").exists()


def test_blog_pagination(site):
    first = (site / "blog/index.html").read_text()
    assert "Post 12" in first and "Post 3" in first and "Post 2" not in first
    assert 'href="/blog/page/2/"' in first
    second = (site / "blog/page/2/index.html").read_text()
    assert "Post 2" in second and 'href="/blog/"' in second


def test_taxonomy_and_date_archives(site):
    assert "Post 5" in (site / "category/new-features/index.html").read_text()
    assert "Post 99" in (site / "category/tips-tricks/index.html").read_text()
    assert "Post 5" in (site / "tag/tips/index.html").read_text()
    assert "Post 5" in (site / "2025/01/05/index.html").read_text()
    assert (site / "2025/01/index.html").is_file() and (site / "2025/index.html").is_file()
    assert "Post 99" in (site / "2023/12/23/index.html").read_text()


def test_feed_is_rss_with_full_content(site):
    root = ET.parse(site / "feed/index.xml").getroot()
    items = root.findall("./channel/item")
    assert len(items) == 13  # fewer than FEED_SIZE
    assert items[0].findtext("link") == "https://andbible.org/2025/01/12/post-12/"
    ns = {"content": "http://purl.org/rss/1.0/modules/content/"}
    assert "<strong>12</strong>" in items[0].find("content:encoded", ns).text
    assert (site / "feed/index.html").read_bytes() == (site / "feed/index.xml").read_bytes()


def test_sitemap_lists_posts(site):
    text = (site / "sitemap.xml").read_text()
    assert "https://andbible.org/2025/01/01/post-1/" in text
    assert "https://andbible.org/blog/" in text


def test_article_has_social_metadata(site):
    html = (site / "2025/01/01/post-1/index.html").read_text()
    assert 'property="og:type" content="article"' in html
    assert 'og:image" content="https://andbible.org/assets/img/og-default.png"' in html
    assert 'rel="canonical" href="https://andbible.org/2025/01/01/post-1/"' in html
