"""YouTube URLs -> click-to-load embeds.

A line holding only a YouTube URL becomes a thumbnail button plus a plain
"Watch on YouTube" link. No request reaches YouTube until the reader clicks:
assets/js/lite-yt.js swaps the button for a youtube-nocookie iframe. The
thumbnail is a local file (media/videos/<id>.webp), fetched by
`sitegen.thumbnails` when the video is added.
"""

from __future__ import annotations

import re
from html import escape
from urllib.parse import parse_qs, urlparse

_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
_HOSTS = {"youtube.com", "www.youtube.com", "m.youtube.com"}


def parse(url: str) -> tuple[str, str] | None:
    parsed = urlparse(url.strip())
    if parsed.scheme not in {"http", "https"}:
        return None
    host = parsed.netloc.lower()
    candidate, kind = None, "video"
    if host == "youtu.be":
        candidate = parsed.path.lstrip("/")
    elif host in _HOSTS:
        if parsed.path == "/watch":
            candidate = parse_qs(parsed.query).get("v", [None])[0]
        elif parsed.path.startswith("/shorts/"):
            candidate, kind = parsed.path.removeprefix("/shorts/"), "short"
        elif parsed.path.startswith("/embed/"):
            candidate = parsed.path.removeprefix("/embed/")
    if candidate and _ID.fullmatch(candidate.strip("/")):
        return candidate.strip("/"), kind
    return None


def embed_html(video_id: str, kind: str, title: str | None = None) -> str:
    label = escape(f"Play video: {title}" if title else "Play video")
    caption = f'<span class="yt__title">{escape(title)}</span>' if title else ""
    return (
        f'<div class="yt yt--{kind}" data-yt-id="{video_id}">'
        f'<button type="button" class="yt__play" aria-label="{label}">'
        f'<img src="/media/videos/{video_id}.webp" alt="" loading="lazy" decoding="async">'
        f'<span class="yt__icon" aria-hidden="true"></span></button>'
        f'{caption}'
        f'<a class="yt__link" href="https://www.youtube.com/watch?v={video_id}" rel="noopener">'
        f"Watch on YouTube</a></div>"
    )


def expand_lines(md: str) -> tuple[str, list[str]]:
    out: list[str] = []
    ids: list[str] = []
    fence: str | None = None
    for line in md.splitlines():
        stripped = line.strip()
        if stripped.startswith(("```", "~~~")):
            marker = stripped[:3]
            fence = None if fence == marker else (fence or marker)
            out.append(line)
            continue
        found = None if fence else parse(stripped.removeprefix("<").removesuffix(">"))
        if found and " " not in stripped:
            ids.append(found[0])
            out.append("")
            out.append(embed_html(*found))
            out.append("")
        else:
            out.append(line)
    return "\n".join(out) + ("\n" if md.endswith("\n") else ""), ids


def starts_with_embed(md: str) -> bool:
    """True when the first non-blank line is a video embed (as `expand_lines` sees it)."""
    for line in md.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith(("```", "~~~")):
            return False
        return " " not in stripped and parse(stripped.removeprefix("<").removesuffix(">")) is not None
    return False
