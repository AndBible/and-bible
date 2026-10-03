"""The curated video catalog (data/videos.yaml): validation, docs "Related videos" and /videos/."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import yaml
from jinja2 import Environment
from markupsafe import Markup

from sitegen.paths import BASE_URL
from sitegen.youtube import embed_html

TOPICS = ("Getting started", "Navigation & windows", "Bookmarks & StudyPads", "Search & study tools",
          "Sync & backup", "Customisation", "Developer diaries")
_ID = re.compile(r"[A-Za-z0-9_-]{11}")


@dataclass(frozen=True)
class Video:
    id: str
    title: str
    topic: str
    docs: str | None = None
    short: bool = False


def load(path: Path, docs_pages: set[str]) -> list[Video]:
    """Read and validate the catalog; a missing file is an empty catalog. Raises ValueError."""
    if not path.is_file():
        return []
    entries = yaml.safe_load(path.read_text(encoding="utf-8")) or []
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
        videos.append(Video(vid, title, entry["topic"], docs, short))
    return videos


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
        return [Markup(embed_html(v.id, "short" if v.short else "video", v.title, card=True))
                for v in videos if v.topic == topic]

    sections = [(topic, embeds(topic)) for topic in TOPICS]
    page = strings["videos"]
    target = out / "videos" / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(env.get_template("videos.html").render(
        lang="en", prefix="", strings=strings, sections=[s for s in sections if s[1]],
        title=page["title"], description=page["description"], canonical=f"{BASE_URL}/videos/",
        og_image=f"{BASE_URL}/assets/img/og-default.png", og_type="website"), encoding="utf-8")
    return ["/videos/"]
