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
from sitegen.youtube import embed_html

TOPICS = ("Getting started", "Navigation & windows", "Bookmarks & StudyPads", "Search & study tools",
          "Sync & backup", "Customisation", "Developer diaries")
_ID = re.compile(r"[A-Za-z0-9_-]{11}")
_VERSION = re.compile(r"\d+\.\d+")


@dataclass(frozen=True)
class Video:
    id: str
    title: str
    topic: str
    published: date
    docs: str | None = None
    short: bool = False
    version: str | None = None


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


def load(path: Path, docs_pages: set[str], today: date | None = None) -> list[Video]:
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
        videos.append(Video(vid, title, entry["topic"], published, docs, short, version))
    return videos


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


def render_videos(env: Environment, strings: dict, videos: list[Video], out: Path) -> list[str]:
    def embeds(topic: str) -> list[Markup]:
        """One ordered list per topic: clips and shorts together, in catalog order."""
        return [card(v, strings, show_version=True) for v in videos if v.topic == topic]

    sections = [(topic, embeds(topic)) for topic in TOPICS]
    page = strings["videos"]
    target = out / "videos" / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(env.get_template("videos.html").render(
        lang="en", prefix="", strings=strings, sections=[s for s in sections if s[1]],
        title=page["title"], description=page["description"], canonical=f"{BASE_URL}/videos/",
        og_type="website"), encoding="utf-8")
    return ["/videos/"]
