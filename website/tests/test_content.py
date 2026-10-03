from datetime import date
from pathlib import Path

import pytest

from sitegen.content import load_pages, load_posts, taxonomy_slug

POST = """---
title: "New feature: Memorize"
date: 2025-08-19
slug: new-feature-memorize
categories: [New features]
tags: [memorize, Tips & tricks]
image: blog/2025/08/m.webp
image_alt: Memorize screen
summary: Learn verses.
---
Body text.
"""


def write(path: Path, text: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    return path


@pytest.fixture
def media(tmp_path):
    write(tmp_path / "media/blog/2025/08/m.webp", "x")
    return tmp_path / "media"


def test_post_parses_and_builds_wordpress_path(tmp_path, media):
    write(tmp_path / "blog/2025-08-19-new-feature-memorize.md", POST)
    [post] = load_posts(tmp_path / "blog", media)
    assert post.title == "New feature: Memorize"
    assert post.date == date(2025, 8, 19)
    assert post.path == "/2025/08/19/new-feature-memorize/"
    assert post.categories == ("New features",)
    assert post.tags == ("memorize", "Tips & tricks")
    assert post.image_url == "/media/blog/2025/08/m.webp"
    assert post.body_md.strip() == "Body text."


def test_url_date_overrides_the_permalink_date(tmp_path, media):
    text = POST.replace("slug: new-feature-memorize", "slug: new-feature-memorize\nurl_date: 2025-08-20")
    write(tmp_path / "blog/2025-08-19-new-feature-memorize.md", text)
    [post] = load_posts(tmp_path / "blog", media)
    assert post.date == date(2025, 8, 19)
    assert post.path == "/2025/08/20/new-feature-memorize/"


@pytest.mark.parametrize(
    "change, message",
    [
        (("title: \"New feature: Memorize\"\n", ""), "title"),
        (("date: 2025-08-19", "date: 2025-8-19"), "date must be YYYY-MM-DD"),
        (("slug: new-feature-memorize", "slug: Bad Slug"), "slug"),
        (("image: blog/2025/08/m.webp", "image: blog/missing.webp"), "image does not exist"),
        (("image: blog/2025/08/m.webp", "image: ../../etc/passwd"), "invalid image path"),
        (("image_alt: Memorize screen\n", ""), "image_alt"),
    ],
)
def test_invalid_front_matter_is_rejected(tmp_path, media, change, message):
    write(tmp_path / "blog/2025-08-19-new-feature-memorize.md", POST.replace(*change))
    with pytest.raises(ValueError, match=message):
        load_posts(tmp_path / "blog", media)


def test_filename_date_must_match_front_matter(tmp_path, media):
    write(tmp_path / "blog/2025-08-18-new-feature-memorize.md", POST)
    with pytest.raises(ValueError, match="filename"):
        load_posts(tmp_path / "blog", media)


def test_duplicate_permalinks_are_rejected(tmp_path, media):
    write(tmp_path / "blog/2025-08-19-new-feature-memorize.md", POST)
    write(tmp_path / "blog/2025-08-19-new-feature-memorize-2.md", POST)
    with pytest.raises(ValueError, match="duplicate permalink"):
        load_posts(tmp_path / "blog", media)


def test_pages_load_with_slug_from_filename(tmp_path):
    write(tmp_path / "pages/privacy.md", "---\ntitle: Privacy Policy\n---\nText\n")
    [page] = load_pages(tmp_path / "pages")
    assert (page.slug, page.title, page.path) == ("privacy", "Privacy Policy", "/privacy/")


@pytest.mark.parametrize(
    "name, slug",
    [("New features", "new-features"), ("Tips & tricks", "tips-tricks"),
     ("tips & tricks", "tips-tricks"), ("Developer diaries", "developer-diaries")],
)
def test_taxonomy_slug_matches_wordpress_nicename(name, slug):
    assert taxonomy_slug(name) == slug


def _level_one_headings(markdown_text: str) -> list[str]:
    """ATX `# ` headings outside fenced code."""
    found, fence = [], None
    for line in markdown_text.splitlines():
        stripped = line.strip()
        if stripped.startswith(("```", "~~~")):
            fence = None if fence == stripped[:3] else (fence or stripped[:3])
        elif not fence and line.startswith("# "):
            found.append(line)
    return found


def test_level_one_heading_detector_ignores_fences_and_deeper_levels():
    text = "# Top\n\n## Two\n\n```\n# comment\n```\n\n~~~\n# also code\n~~~\n#hashtag\n"
    assert _level_one_headings(text) == ["# Top"]


def test_blog_posts_have_no_level_one_heading_in_the_body():
    """The post title is the page's only <h1> (a body `# Heading` would make a second one)."""
    from sitegen import paths
    offenders = {}
    for post in sorted((paths.CONTENT / "en" / "blog").glob("*.md")):
        headings = _level_one_headings(post.read_text(encoding="utf-8").split("\n---\n", 1)[1])
        if headings:
            offenders[post.name] = headings
    assert not offenders
