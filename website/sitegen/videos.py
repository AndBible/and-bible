"""The curated video catalog (data/videos.yaml): validation, docs "Related videos" and /videos/."""

from __future__ import annotations

import re
from dataclasses import dataclass
from collections.abc import Sequence
from datetime import date
from pathlib import Path

import yaml
from jinja2 import Environment
from markupsafe import Markup, escape

from sitegen.paths import BASE_URL
from sitegen.tutorials import Series
from sitegen.youtube import embed_html

TOPICS = ("Getting started", "Navigation & windows", "Bookmarks & StudyPads", "Search & study tools",
          "Sync & backup", "AI assistant", "Customisation", "Developer diaries")
_ID = re.compile(r"[A-Za-z0-9_-]{11}")
_VERSION = re.compile(r"\d+\.\d+")
KINDS: tuple[str, ...] = ("video", "whats-new", "interview")
FEED_SINCE = date(2026, 11, 1)


@dataclass(frozen=True)
class Video:
    id: str
    title: str
    topic: str
    published: date
    docs: str | None = None
    short: bool = False
    version: str | None = None
    kind: str = "video"
    post: str | None = None


class _Loader(yaml.SafeLoader):
    """SafeLoader that leaves dates as text, so a bad one is reported by `_published`, not by YAML."""


_Loader.yaml_implicit_resolvers = {
    key: [(tag, rx) for tag, rx in resolvers if tag != "tag:yaml.org,2002:timestamp"]
    for key, resolvers in yaml.SafeLoader.yaml_implicit_resolvers.items()}


def _published(path: Path, vid: str, raw: object, today: date) -> date:
    """The `published` field: a YYYY-MM-DD date, not in the future."""
    text = str(raw)
    try:
        day = date.fromisoformat(text) if re.fullmatch(r"\d{4}-\d{2}-\d{2}", text) else None
    except ValueError:
        day = None
    if day is None:
        raise ValueError(f"{path}: video {vid} needs published: YYYY-MM-DD (got {raw!r})")
    if day > today:
        raise ValueError(f"{path}: video {vid} is published in the future ({day})")
    return day


def load(path: Path, docs_pages: set[str], today: date | None = None,
         posts: set[str] = frozenset()) -> list[Video]:
    """Read and validate the catalog; a missing file is an empty catalog. Raises ValueError."""
    if not path.is_file():
        return []
    entries = yaml.load(path.read_text(encoding="utf-8"), _Loader) or []
    if not isinstance(entries, list):
        raise ValueError(f"{path}: the catalog must be a list of videos")
    videos: list[Video] = []
    seen: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError(f"{path}: entry {entry!r} is not a mapping")
        vid, title = str(entry.get("id", "")), str(entry.get("title", "")).strip()
        if not _ID.fullmatch(vid):
            raise ValueError(f"{path}: video id {vid!r} must be 11 characters")
        if vid in seen:
            raise ValueError(f"{path}: duplicate video id {vid}")
        seen.add(vid)
        if not title:
            raise ValueError(f"{path}: video {vid} has no title")
        if entry.get("topic") not in TOPICS:
            raise ValueError(f"{path}: video {vid} has unknown topic {entry.get('topic')!r}")
        docs = entry.get("docs")
        if docs is not None and docs not in docs_pages:
            raise ValueError(f"{path}: video {vid} links to unpublished docs page {docs!r}")
        short = bool(entry.get("short", False))
        if docs and short:
            raise ValueError(f"{path}: video {vid} is a short, which cannot be linked to a docs page")
        published = _published(path, vid, entry.get("published"), today or date.today())
        version = entry.get("version")
        if version is not None and not (isinstance(version, str) and _VERSION.fullmatch(version)):
            raise ValueError(f"{path}: video {vid} needs version: \"major.minor\" such as \"5.1\" (got {version!r})")
        kind = entry.get("kind", "video")
        if kind not in KINDS:
            raise ValueError(f"{path}: video {vid} has unknown kind {kind!r}")
        post = entry.get("post")
        if post is not None and post not in posts:
            raise ValueError(f"{path}: video {vid} links to unknown blog post {post!r}")
        videos.append(Video(vid, title, entry["topic"], published, docs, short, version, kind, post))
    return videos


def anchor_id(title: str) -> str:
    """Turn a section title into a lowercase ASCII anchor."""
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")


def feed(videos: list[Video]) -> list[Video]:
    """Current entries newest first; catalog order breaks publication-date ties."""
    return sorted((v for v in videos if v.published >= FEED_SINCE),
                  key=lambda v: v.published, reverse=True)


def archive(videos: list[Video]) -> list[Video]:
    """Entries before the feed cutoff, preserving catalog order."""
    return [v for v in videos if v.published < FEED_SINCE]


def newest(videos: list[Video], n: int = 3) -> list[Video]:
    """The n most recently published videos; ties keep catalog order (sorted() is stable)."""
    return sorted(videos, key=lambda v: v.published, reverse=True)[:n]


ENGLISH_MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")


def format_date(day: date, months: Sequence[str] = ENGLISH_MONTHS, pattern: str = "{d} {month} {y}") -> str:
    """"5 Jul 2026": built from the month names and pattern of the UI strings, never from the machine locale."""
    return pattern.format(d=day.day, month=months[day.month - 1], y=day.year)


def card(video: Video, strings: dict | None = None, show_version: bool = False) -> Markup:
    """The click-to-load card shared by /videos/ and the landing page.

    Under the title sits a muted line with the publication date and, when `show_version` (the /videos/
    page only) and the catalog has one, the AndBible version first: `v5.1 · 5 Jul 2026`."""
    labels = (strings or {}).get("videos", {})
    when = format_date(video.published, labels.get("months", ENGLISH_MONTHS), labels.get("date_format", "{d} {month} {y}"))
    parts = []
    if show_version and video.version:
        parts.append(escape(labels.get("version_label", "v{version}").format(version=video.version)))
    parts.append(f'<time datetime="{video.published.isoformat()}">{escape(when)}</time>')
    return Markup(embed_html(video.id, "short" if video.short else "video", video.title, card=True,
                             meta=" · ".join(parts)))


def related(videos: list[Video]) -> dict[str, list[tuple[str, str]]]:
    """Docs page stem -> (id, title) pairs. Shorts are left out: docs embeds are 16:9."""
    result: dict[str, list[tuple[str, str]]] = {}
    for video in videos:
        if video.docs and not video.short:
            result.setdefault(video.docs, []).append((video.id, video.title))
    return result


def teaser(series: Series, strings: dict) -> dict | None:
    count = series.published_count()
    labels = strings["videos"]
    return {"title": labels["teaser_title"], "count_text": labels["teaser_count"].format(n=count),
            "url": "/tutorials/"} if count else None


def _time(video: Video, strings: dict) -> Markup:
    labels = strings["videos"]
    when = format_date(video.published, labels.get("months", ENGLISH_MONTHS), labels.get("date_format", "{d} {month} {y}"))
    return Markup('<time datetime="{}">{}</time>').format(video.published.isoformat(), when)


def _row(video: Video, meta: Markup, badge=None, links=()) -> dict:
    return {"title": video.title, "url": f"https://www.youtube.com/watch?v={video.id}",
            "thumb": video.id, "meta": meta, "badge": badge, "links": links}


def feed_row(video: Video, strings: dict, post_urls: dict[str, str]) -> dict:
    labels = strings["videos"]
    kind = labels["kinds"]["short" if video.short else video.kind]
    links = [{"url": post_urls[video.post], "label": labels["read_post"]}] if video.post else []
    return _row(video, Markup("{} · {}").format(_time(video, strings), kind), links=links)


def archive_row(video: Video, strings: dict) -> dict:
    labels = strings["videos"]
    meta = _time(video, strings)
    if video.version:
        meta = Markup("{} · {}").format(labels["version_label"].format(version=video.version), meta)
    return _row(video, meta, badge=labels["kinds"]["short"] if video.short else None)


def render_videos(env: Environment, strings: dict, videos: list[Video], out: Path,
                  tutorials: Series = Series(None, ()), post_urls: dict[str, str] | None = None) -> list[str]:
    page = strings["videos"]
    common = dict(lang="en", prefix="", strings=strings, og_type="website")
    target = out / "videos" / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(env.get_template("videos.html").render(
        **common, rows=[feed_row(v, strings, post_urls or {}) for v in feed(videos)],
        teaser=teaser(tutorials, strings), title=page["title"], description=page["description"],
        canonical=f"{BASE_URL}/videos/"), encoding="utf-8")
    old = archive(videos)
    groups = []
    for topic in TOPICS:
        selected = sorted((v for v in old if v.topic == topic), key=lambda v: v.published, reverse=True)
        if selected:
            groups.append({"title": topic, "id": anchor_id(topic), "count": len(selected),
                           "rows": [archive_row(v, strings) for v in selected]})
    target = out / "videos" / "archive" / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(env.get_template("videos_archive.html").render(
        **common, groups=groups, title=page["archive"]["title"], description=page["archive"]["description"],
        canonical=f"{BASE_URL}/videos/archive/"), encoding="utf-8")
    return ["/videos/", "/videos/archive/"]
