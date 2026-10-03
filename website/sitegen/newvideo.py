"""Add a new YouTube video to the site: catalog line, thumbnail and a starter blog post.

    uv run python -m sitegen.newvideo <url-or-id> --topic <TOPIC> --summary "<text>" [options]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
import unicodedata
import urllib.request
from collections.abc import Callable, Sequence
from datetime import date
from pathlib import Path

import yaml

from sitegen import paths, thumbnails, videos
from sitegen.docs import published_pages
from sitegen.youtube import parse as parse_url

_BARE_ID = re.compile(r"[A-Za-z0-9_-]{11}")
_OEMBED = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={id}&format=json"


class NewVideoError(Exception):
    """A problem the user can fix; reported without a traceback."""


def parse_video(value: str) -> tuple[str, bool]:
    """Return (video id, is_short) from a YouTube URL or a bare 11-character id."""
    value = value.strip()
    if _BARE_ID.fullmatch(value):
        return value, False
    parsed = parse_url(value)
    if parsed is None:
        raise NewVideoError(f"cannot find a YouTube video id in {value!r}")
    return parsed[0], parsed[1] == "short"


def _http_get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "andbible-website-build"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def fetch_title(video_id: str, fetcher: Callable[[str], bytes] = _http_get) -> str:
    """The video's title from YouTube oEmbed."""
    try:
        title = str(json.loads(fetcher(_OEMBED.format(id=video_id)))["title"]).strip()
    except (OSError, ValueError, KeyError) as exc:
        raise NewVideoError(f"could not read the title of YouTube video {video_id} ({exc}); pass --title") from exc
    if not title:
        raise NewVideoError(f"YouTube video {video_id} has an empty title; pass --title")
    return title


def slugify(title: str) -> str:
    ascii_title = unicodedata.normalize("NFKD", title).encode("ascii", "ignore").decode()
    slug = re.sub(r"[^a-z0-9]+", "-", ascii_title.lower()).strip("-")
    if not slug or slug == "page":
        raise NewVideoError(f"cannot derive a slug from {title!r}; pass --slug")
    return slug


def catalog_line(video_id: str, title: str, topic: str, docs: str | None, short: bool) -> str:
    """One flow-style line in the layout of data/videos.yaml."""
    parts = [f"id: {json.dumps(video_id)}", f"title: {json.dumps(title, ensure_ascii=False)}",
             f"topic: {json.dumps(topic)}"]
    if docs:
        parts.append(f"docs: {json.dumps(docs)}")
    if short:
        parts.append("short: true")
    return "- {" + ", ".join(parts) + "}\n"


def post_text(title: str, day: date, slug: str, summary: str, video_id: str, short: bool,
              categories: Sequence[str], tags: Sequence[str]) -> str:
    """Front matter (no image: the video thumbnail would duplicate it) plus summary and video."""
    front: dict[str, object] = {"title": title, "date": day.isoformat(), "slug": slug, "summary": summary}
    if categories:
        front["categories"] = list(categories)
    if tags:
        front["tags"] = list(tags)
    head = yaml.safe_dump(front, sort_keys=False, allow_unicode=True, width=10**6)
    url = f"https://www.youtube.com/{'shorts/' if short else 'watch?v='}{video_id}"
    return f"---\n{head}---\n\n{summary}\n\n{url}\n"


def run(args: argparse.Namespace, *, fetcher: Callable[[str], bytes] = _http_get,
        opener: Callable[[str], bytes] = thumbnails._download, data: Path = paths.DATA,
        content: Path = paths.CONTENT, media: Path = paths.MEDIA,
        docs_pages: set[str] | None = None, today: date | None = None) -> list[str]:
    """Do the work; return the lines to print. Raises NewVideoError before changing anything."""
    video_id, is_short = parse_video(args.video)
    short = is_short or args.short
    catalog = data / "videos.yaml"
    if docs_pages is None:
        docs_pages = {p.removesuffix(".md") for p in published_pages(paths.WEBSITE / "zensical.toml")}
    if args.topic not in videos.TOPICS:
        raise NewVideoError(f"unknown topic {args.topic!r}; choose one of: {', '.join(videos.TOPICS)}")
    existing = catalog.read_text(encoding="utf-8") if catalog.is_file() else ""
    if video_id in {v.id for v in videos.load(catalog, docs_pages)}:
        raise NewVideoError(f"video {video_id} is already in {catalog.name}")
    title = args.title or fetch_title(video_id, fetcher)
    line = catalog_line(video_id, title, args.topic, args.docs, short)
    with tempfile.TemporaryDirectory() as tmp:  # validate with the build's own rules
        probe = Path(tmp) / "videos.yaml"
        probe.write_text(probe_text(existing) + line, encoding="utf-8")
        try:
            videos.load(probe, docs_pages)
        except ValueError as exc:
            raise NewVideoError(str(exc).replace(str(probe), catalog.name)) from exc
    post: Path | None = None
    if not args.no_post:
        day = date.fromisoformat(args.date) if args.date else (today or date.today())
        slug = args.slug or slugify(title)
        post = content / "en" / "blog" / f"{day.isoformat()}-{slug}.md"
        if post.exists():
            raise NewVideoError(f"{post} already exists")
    thumbnails.fetch([video_id], media, opener)  # first: a failure must not leave a catalog line behind
    catalog.parent.mkdir(parents=True, exist_ok=True)
    catalog.write_text(probe_text(existing) + line, encoding="utf-8")
    out = [f"added {video_id} ({title}) to {catalog.name}", f"thumbnail: {media / 'videos' / (video_id + '.webp')}"]
    if post is not None:
        post.parent.mkdir(parents=True, exist_ok=True)
        post.write_text(post_text(title, day, slug, args.summary, video_id, short,
                                  args.category, args.tag), encoding="utf-8")
        out.append(f"post: {post}")
    out += ["", "Next:",
            "  git -C website/media add videos && git -C website/media commit -m 'videos: thumbnail'",
            "  git add website/media website/data/videos.yaml website/content   # gitlink bump + catalog + post",
            "  make site site-check"]
    return out


def probe_text(existing: str) -> str:
    return existing + ("\n" if existing and not existing.endswith("\n") else "")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="sitegen.newvideo", description=__doc__.split("\n")[0])
    p.add_argument("video", help="YouTube URL (watch, youtu.be, shorts, embed) or 11-character id")
    p.add_argument("--topic", required=True, help="one of: " + "; ".join(videos.TOPICS))
    p.add_argument("--summary", required=True, help="one or two sentences for lists and the post")
    p.add_argument("--title", help="default: the YouTube title")
    p.add_argument("--slug", help="default: kebab-case of the title")
    p.add_argument("--date", help="YYYY-MM-DD, default today")
    p.add_argument("--docs", help="published docs page stem the video belongs to")
    p.add_argument("--short", action="store_true", help="a YouTube Short (implied by a /shorts/ URL)")
    p.add_argument("--category", action="append", default=[])
    p.add_argument("--tag", action="append", default=[])
    p.add_argument("--no-post", action="store_true", help="catalog and thumbnail only")
    return p


def main(argv: Sequence[str] | None = None) -> int:
    try:
        lines = run(build_parser().parse_args(argv))
    except (NewVideoError, RuntimeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
