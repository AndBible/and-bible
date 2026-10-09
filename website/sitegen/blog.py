"""Blog articles, WordPress-compatible archives, RSS and the sitemap."""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from datetime import UTC, datetime
from email.utils import format_datetime
from pathlib import Path

from jinja2 import Environment

from sitegen.content import Page, Post, taxonomy_slug
from sitegen.paths import BASE_URL
from sitegen.render import markdown_to_html
from sitegen.youtube import starts_with_embed

PAGE_SIZE = 9  # a 3x3 card grid
FEED_SIZE = 20
_CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"
_ATOM_NS = "http://www.w3.org/2005/Atom"
_LOCAL_URL = re.compile(r'\b(href|src)="/(?!/)')


def absolute_urls(html: str) -> str:
    """Make site-relative href/src URLs absolute: feed readers have no base URL for `/media/...`."""
    return _LOCAL_URL.sub(lambda m: f'{m.group(1)}="{BASE_URL}/', html)


def archives(posts: list[Post], strings: dict) -> dict[str, tuple[str, list[Post]]]:
    result: dict[str, tuple[str, list[Post]]] = {"/blog/": (strings["blog"]["title"], list(posts))}
    for post in posts:
        d = post.url_date
        keys = [(f"/{d:%Y}/", f"{d:%Y}"), (f"/{d:%Y/%m}/", f"{d:%B %Y}"),
                (f"/{d:%Y/%m/%d}/", f"{d.day} {d:%B %Y}")]
        keys += [(f"/category/{taxonomy_slug(c)}/", strings["blog"]["category"].format(name=c))
                 for c in post.categories]
        keys += [(f"/tag/{taxonomy_slug(t)}/", strings["blog"]["tag"].format(name=t)) for t in post.tags]
        for base, heading in keys:
            result.setdefault(base, (heading, []))[1].append(post)
    return result


def page_window(current: int, total: int) -> list[int | None]:
    """Page numbers to show in the pager; None marks an ellipsis gap.

    Up to 7 pages are all shown. Otherwise: first, last, the current page and its neighbours,
    where a gap of a single page is filled in rather than replaced by an ellipsis.
    """
    if total <= 7:
        return list(range(1, total + 1))
    shown = sorted({1, total} | {n for n in (current - 1, current, current + 1) if 1 <= n <= total})
    result: list[int | None] = []
    for n in shown:
        if result:
            last = result[-1]
            if n - last == 2:
                result.append(last + 1)
            elif n - last > 2:
                result.append(None)
        result.append(n)
    return result


def _page_url(base: str, number: int) -> str:
    return base if number == 1 else f"{base}page/{number}/"


def _write(out: Path, url: str, html: str) -> None:
    target = out / url.lstrip("/") / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(html, encoding="utf-8")


def _feed(posts: list[Post], bodies: dict[str, str], strings: dict, out: Path) -> None:
    ET.register_namespace("content", _CONTENT_NS)
    ET.register_namespace("atom", _ATOM_NS)
    rss = ET.Element("rss", version="2.0")
    channel = ET.SubElement(rss, "channel")
    for tag, value in (("title", f"{strings['site_name']} – {strings['blog']['title']}"),
                       ("link", f"{BASE_URL}/blog/"), ("description", strings["meta"]["description"]),
                       ("language", "en")):
        ET.SubElement(channel, tag).text = value
    ET.SubElement(channel, f"{{{_ATOM_NS}}}link", href=f"{BASE_URL}/feed/", rel="self", type="application/rss+xml")
    for post in posts[:FEED_SIZE]:
        item = ET.SubElement(channel, "item")
        link = f"{BASE_URL}{post.path}"
        for tag, value in (("title", post.title), ("link", link), ("guid", link),
                           ("description", post.summary),
                           ("pubDate", format_datetime(datetime.combine(post.date, post.time, UTC), usegmt=True))):
            ET.SubElement(item, tag).text = value
        for category in post.categories:
            ET.SubElement(item, "category").text = category
        ET.SubElement(item, f"{{{_CONTENT_NS}}}encoded").text = absolute_urls(bodies[post.path])
    (out / "feed").mkdir(parents=True, exist_ok=True)
    ET.ElementTree(rss).write(out / "feed" / "index.xml", encoding="utf-8", xml_declaration=True)
    (out / "feed" / "index.html").write_bytes((out / "feed" / "index.xml").read_bytes())


def _tags_index(env: Environment, strings: dict, posts: list[Post], out: Path, common: dict) -> str:
    """`/tags/`: every tag with its post count, alphabetical. Linked from tag archives only."""
    names = {taxonomy_slug(t): t for post in posts for t in post.tags}  # first spelling wins, like the archives
    counts = {slug: sum(taxonomy_slug(t) == slug for post in posts for t in post.tags) for slug in names}
    tags = [(names[slug], f"/tag/{slug}/", counts[slug]) for slug in sorted(names, key=str.casefold)]
    title = strings["blog"]["tags_title"]
    _write(out, "/tags/", env.get_template("tags.html").render(
        heading=title, tags=tags, title=f"{title} – AndBible", description=strings["meta"]["description"],
        canonical=f"{BASE_URL}/tags/", og_type="website", **common))
    return "/tags/"


def render_blog(env: Environment, strings: dict, posts: list[Post], out: Path, media_dir: Path) -> list[str]:
    written: list[str] = []
    bodies = {p.path: markdown_to_html(p.body_md, p.source, media_dir) for p in posts}
    common = {"lang": "en", "prefix": "", "strings": strings}
    for post in posts:
        _write(out, post.path, env.get_template("article.html").render(
            post=post, body_html=bodies[post.path], show_hero=not starts_with_embed(post.body_md), title=f"{post.title} – AndBible",
            description=post.summary, canonical=f"{BASE_URL}{post.path}", og_type="article",
            og_image=f"{BASE_URL}{post.image_url}" if post.image_url else None,
            category_links=[(c, f"/category/{taxonomy_slug(c)}/") for c in post.categories],
            tag_links=[(t, f"/tag/{taxonomy_slug(t)}/") for t in post.tags], **common))
        written.append(post.path)
    for base, (heading, entries) in archives(posts, strings).items():
        chunks = [entries[i:i + PAGE_SIZE] for i in range(0, len(entries), PAGE_SIZE)] or [[]]
        for number, chunk in enumerate(chunks, 1):
            url = _page_url(base, number)
            _write(out, url, env.get_template("archive.html").render(
                heading=heading, posts=chunk,
                newer=_page_url(base, number - 1) if number > 1 else None,
                older=_page_url(base, number + 1) if number < len(chunks) else None,
                page_links=[(n, _page_url(base, n) if n else None) for n in page_window(number, len(chunks))],
                current_page=number, page_count=len(chunks), tags_index="/tags/" if base.startswith("/tag/") else None,
                title=f"{heading} – AndBible" + (f" (page {number})" if number > 1 else ""),
                description=strings["meta"]["description"], canonical=f"{BASE_URL}{url}",
                og_type="website", **common))
            if base == "/blog/":
                written.append(url)
    written.append(_tags_index(env, strings, posts, out, common))
    _feed(posts, bodies, strings, out)
    return written


def render_pages(env: Environment, strings: dict, pages: list[Page], out: Path, media_dir: Path) -> list[str]:
    written: list[str] = []
    for page in pages:
        _write(out, page.path, env.get_template("page.html").render(
            page=page, body_html=markdown_to_html(page.body_md, page.source, media_dir),
            title=f"{page.title} – AndBible", description=strings["meta"]["description"],
            canonical=f"{BASE_URL}{page.path}", og_type="website",
            lang="en", prefix="", strings=strings))
        written.append(page.path)
    return written


def write_sitemap(paths_: list[str], out: Path) -> None:
    ns = "http://www.sitemaps.org/schemas/sitemap/0.9"
    ET.register_namespace("", ns)
    root = ET.Element(f"{{{ns}}}urlset")
    for path in sorted(set(paths_)):
        ET.SubElement(ET.SubElement(root, f"{{{ns}}}url"), f"{{{ns}}}loc").text = f"{BASE_URL}{path}"
    ET.ElementTree(root).write(out / "sitemap.xml", encoding="utf-8", xml_declaration=True)
