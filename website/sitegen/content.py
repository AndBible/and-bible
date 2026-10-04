"""Blog posts and standalone pages, parsed and validated from content/<lang>/."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from sitegen.frontmatter import split

_FILENAME = re.compile(r"^(\d{4}-\d{2}-\d{2})-[a-z0-9-]+\.md$")
_SLUG = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def taxonomy_slug(name: str) -> str:
    """WordPress nicename for a category/tag: lowercase, non-alphanumerics collapse to '-'."""
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def media_file(value: str, media_dir: Path, source: Path) -> Path:
    """Resolve a media-relative path, refusing traversal and missing files."""
    if value.startswith("/") or any(part in {"", ".", ".."} for part in value.split("/")):
        raise ValueError(f"{source}: invalid image path: {value}")
    target = (media_dir / value).resolve()
    if not target.is_relative_to(media_dir.resolve()) or not target.is_file():
        raise ValueError(f"{source}: image does not exist in media/: {value}")
    return target


@dataclass(frozen=True)
class Post:
    slug: str
    title: str
    date: date
    url_date: date
    summary: str
    categories: tuple[str, ...]
    tags: tuple[str, ...]
    image: str | None
    image_alt: str | None
    body_md: str
    source: Path

    @property
    def path(self) -> str:
        return f"/{self.url_date:%Y/%m/%d}/{self.slug}/"

    @property
    def image_url(self) -> str | None:
        return f"/media/{self.image}" if self.image else None


@dataclass(frozen=True)
class Page:
    slug: str
    title: str
    body_md: str
    source: Path

    @property
    def path(self) -> str:
        return f"/{self.slug}/"


def _required(data: dict[str, object], key: str, source: Path) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{source}: {key} must be a nonempty string")
    return value.strip()


def _date(data: dict[str, object], key: str, source: Path) -> date:
    value = _required(data, key, source)
    if not _DATE.fullmatch(value):
        raise ValueError(f"{source}: {key} date must be YYYY-MM-DD")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(f"{source}: invalid {key}: {value}") from exc


def _names(data: dict[str, object], key: str, source: Path) -> tuple[str, ...]:
    value = data.get(key, [])
    if not isinstance(value, list) or not all(isinstance(v, str) and v.strip() for v in value):
        raise ValueError(f"{source}: {key} must be a list of names")
    return tuple(v.strip() for v in value)


def parse_post(source: Path, media_dir: Path) -> Post:
    match = _FILENAME.fullmatch(source.name)
    if match is None:
        raise ValueError(f"{source}: filename must be YYYY-MM-DD-slug.md")
    data, body = split(source.read_text(encoding="utf-8"), source)
    title = _required(data, "title", source)
    published = _date(data, "date", source)
    if published.isoformat() != match.group(1):
        raise ValueError(f"{source}: date must match the filename prefix")
    slug = _required(data, "slug", source)
    if not _SLUG.fullmatch(slug) or slug == "page":
        raise ValueError(f"{source}: slug must be lowercase-kebab-case and not 'page'")
    url_date = _date(data, "url_date", source) if "url_date" in data else published
    image = _required(data, "image", source) if "image" in data else None
    image_alt = None
    if image is not None:
        media_file(image, media_dir, source)
        image_alt = _required(data, "image_alt", source)
    elif "image_alt" in data:
        raise ValueError(f"{source}: image_alt needs an image")
    return Post(
        slug=slug,
        title=title,
        date=published,
        url_date=url_date,
        summary=_required(data, "summary", source),
        categories=_names(data, "categories", source),
        tags=_names(data, "tags", source),
        image=image,
        image_alt=image_alt,
        body_md=body,
        source=source,
    )


def load_posts(blog_dir: Path, media_dir: Path) -> list[Post]:
    if not blog_dir.is_dir():
        return []
    posts = [parse_post(p, media_dir) for p in sorted(blog_dir.glob("*.md"))]
    seen: dict[str, Path] = {}
    for post in posts:
        if post.path in seen:
            raise ValueError(f"{post.source}: duplicate permalink {post.path} (also {seen[post.path]})")
        seen[post.path] = post.source
    return sorted(posts, key=lambda p: (-p.date.toordinal(), p.slug))


def load_pages(pages_dir: Path) -> list[Page]:
    if not pages_dir.is_dir():
        return []
    pages = []
    for source in sorted(pages_dir.glob("*.md")):
        data, body = split(source.read_text(encoding="utf-8"), source)
        if not _SLUG.fullmatch(source.stem):
            raise ValueError(f"{source}: page filename must be lowercase-kebab-case")
        pages.append(Page(source.stem, _required(data, "title", source), body, source))
    return pages
