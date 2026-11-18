"""The AndBible 6.0 tutorial series (data/tutorials.yaml): validation, the /videos/ section and docs embeds.

The file is public and hand-maintainable; the site build reads nothing from the (private) video pipeline. An episode's
`youtube` id is null until it is published. While it is null the series shows a "Coming soon" state that
links the one placeholder video (`placeholder`, an unlisted "videos coming soon" video).
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from html import escape
from pathlib import Path

import yaml
from markupsafe import Markup

from sitegen.youtube import embed_html

_ID = re.compile(r"[A-Za-z0-9_-]{11}")
_SLUG = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")
_ANCHOR = re.compile(r"[A-Za-z0-9_-]+")
_LANG = re.compile(r"[a-z]{2,3}(-[A-Z][a-z]{3})?(-[A-Z]{2}|-[0-9]{3})?")
DEFAULT_LABELS = {
    "video_tutorial": "Video tutorial: {title}",
    "coming_soon": "Coming soon",
    "watch_preview": "Watch the preview video",
    "episode": "Episode {n}",
}


@dataclass(frozen=True)
class Episode:
    slug: str
    number: int | None  # null for "What's new"
    title: str
    docs: str  # docs page stem, e.g. "search" or "releases/release_6_0"
    anchor: str | None = None
    youtube: str | None = None
    subtitles: tuple[str, ...] = ()  # YouTube caption languages besides English, sorted BCP-47 codes

    @property
    def docs_path(self) -> str:
        return f"/docs/{self.docs}/" + (f"#{self.anchor}" if self.anchor else "")


@dataclass(frozen=True)
class Series:
    placeholder: str | None
    episodes: tuple[Episode, ...]

    def ids(self) -> set[str]:
        """Every YouTube id the series can show (for thumbnails)."""
        found = {e.youtube for e in self.episodes if e.youtube}
        if self.placeholder:
            found.add(self.placeholder)
        return found

    def by_docs(self) -> dict[str, list[Episode]]:
        result: dict[str, list[Episode]] = {}
        for episode in self.episodes:
            result.setdefault(episode.docs, []).append(episode)
        return result


def _video_id(path: Path, what: str, raw: object) -> str | None:
    if raw is None:
        return None
    if not isinstance(raw, str) or not _ID.fullmatch(raw):
        raise ValueError(f"{path}: {what} must be an 11-character YouTube id or null (got {raw!r})")
    return raw


def youtube_language(code: str) -> str:
    """A media-folder language (Android `pt-rBR`, `b+sr+Latn`) as a YouTube BCP-47 code (`pt-BR`, `sr-Latn`)."""
    if code.startswith("b+"):
        return code[2:].replace("+", "-")
    return re.sub(r"-r(?=[A-Z]{2}$)", "-", code)


def _subtitles(path: Path, slug: str, raw: object) -> tuple[str, ...]:
    if raw is None:
        return ()
    if not isinstance(raw, list):
        raise ValueError(f"{path}: episode {slug} subtitles must be a list of language codes")
    raw = [youtube_language(c) if isinstance(c, str) else c for c in raw]
    for code in raw:
        if not isinstance(code, str) or not _LANG.fullmatch(code):
            raise ValueError(f"{path}: episode {slug} has a bad subtitle language {code!r}")
        if code == "en" or code.startswith("en-"):
            raise ValueError(f"{path}: episode {slug} lists English; subtitles are the languages besides English")
    if raw != sorted(set(raw)):
        raise ValueError(f"{path}: episode {slug} subtitles must be sorted and unique (got {raw!r})")
    return tuple(raw)


def load(path: Path, docs_pages: set[str]) -> Series:
    """Read and validate the series; a missing file is an empty series. Raises ValueError."""
    if not path.is_file():
        return Series(None, ())
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(data, dict) or not isinstance(data.get("episodes"), list):
        raise ValueError(f"{path}: expected a mapping with an `episodes` list")
    placeholder = _video_id(path, "placeholder", data.get("placeholder"))
    episodes: list[Episode] = []
    slugs: set[str] = set()
    ids: set[str] = set()
    last = 0
    for entry in data["episodes"]:
        if not isinstance(entry, dict):
            raise ValueError(f"{path}: episode {entry!r} is not a mapping")
        slug = str(entry.get("slug", ""))
        if not _SLUG.fullmatch(slug):
            raise ValueError(f"{path}: bad episode slug {slug!r}")
        if slug in slugs:
            raise ValueError(f"{path}: duplicate episode slug {slug}")
        slugs.add(slug)
        title = str(entry.get("title", "")).strip()
        if not title:
            raise ValueError(f"{path}: episode {slug} has no title")
        number = entry.get("number")
        if number is not None:
            if not isinstance(number, int) or isinstance(number, bool) or number <= last:
                raise ValueError(f"{path}: episode {slug} needs a number above {last} (got {number!r})")
            last = number
        docs = entry.get("docs")
        if docs not in docs_pages:
            raise ValueError(f"{path}: episode {slug} links to unpublished docs page {docs!r}")
        anchor = entry.get("anchor")
        if anchor is not None and not (isinstance(anchor, str) and _ANCHOR.fullmatch(anchor)):
            raise ValueError(f"{path}: episode {slug} has a bad anchor {anchor!r}")
        youtube = _video_id(path, f"episode {slug} youtube", entry.get("youtube"))
        if youtube:
            if youtube in ids or youtube == placeholder:
                raise ValueError(f"{path}: episode {slug} reuses YouTube id {youtube}")
            ids.add(youtube)
        episodes.append(Episode(slug, number, title, docs, anchor, youtube, _subtitles(path, slug, entry.get("subtitles"))))
    return Series(placeholder, tuple(episodes))


def _labels(strings: dict | None) -> dict[str, str]:
    return DEFAULT_LABELS | ((strings or {}).get("videos", {}) or {}).get("tutorials", {})


def _placeholder_link(series: Series, labels: dict[str, str]) -> str:
    if not series.placeholder:
        return ""
    return (f' <a class="tutorial-note__link" href="https://www.youtube.com/watch?v={series.placeholder}" '
            f'rel="noopener">{escape(labels["watch_preview"])}</a>')


def docs_block(episodes: list[Episode], series: Series, strings: dict | None = None) -> str:
    """The embeds for one docs page, in episode order (one raw-HTML block, no blank lines).

    A published episode is a click-to-load embed. An unpublished one is a compact "Coming soon" note
    linking the placeholder video: several identical placeholder thumbnails (the AI page has four episodes)
    would look broken, a note does not."""
    labels = _labels(strings)
    parts = []
    for episode in episodes:
        heading = labels["video_tutorial"].format(title=episode.title)
        if episode.youtube:
            parts.append(embed_html(episode.youtube, "video", heading))
        else:
            parts.append(
                f'<p class="tutorial-note"><span class="tutorial-note__icon" aria-hidden="true"></span>'
                f'<span class="tutorial-note__title">{escape(heading)}</span>'
                f'<span class="tutorial-note__badge">{escape(labels["coming_soon"])}</span>'
                f'{_placeholder_link(series, labels)}</p>')
    return '<div class="tutorial-videos">' + "".join(parts) + "</div>"


def insert_after_title(markdown: str, block: str) -> str:
    """Put `block` after the page's first `# ` heading (at the top when there is none)."""
    lines = markdown.splitlines()
    fence = False
    for index, line in enumerate(lines):
        if line.lstrip().startswith(("```", "~~~")):
            fence = not fence
        if not fence and line.startswith("# "):
            after = [] if index + 1 < len(lines) and not lines[index + 1].strip() else [""]
            lines[index + 1:index + 1] = ["", block, *after]
            return "\n".join(lines) + ("\n" if markdown.endswith("\n") else "")
    return block + "\n\n" + markdown


def videos_section(series: Series, strings: dict | None = None) -> dict:
    """Template data for the /videos/ series section."""
    labels = _labels(strings)
    rows = []
    for e in series.episodes:
        heading = labels["episode"].format(n=e.number) if e.number is not None else None
        rows.append({
            "number": e.number, "label": heading, "title": e.title, "docs_url": e.docs_path,
            "embed": Markup(embed_html(e.youtube, "video", e.title, card=True)) if e.youtube else None,
        })
    preview = Markup(embed_html(series.placeholder, "video", None, card=True)) if series.placeholder else None
    return {"rows": rows, "preview": preview if any(r["embed"] is None for r in rows) else None,
            "labels": labels}
