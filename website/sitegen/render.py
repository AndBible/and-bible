"""Markdown -> HTML for blog posts and pages, with the site's safety and media checks."""

from __future__ import annotations

from html.parser import HTMLParser
from pathlib import Path

import markdown

from sitegen import youtube
from sitegen.content import media_file

_EXTENSIONS = ["tables", "fenced_code", "footnotes", "attr_list", "md_in_html", "toc", "admonition"]
_FORBIDDEN = {"script", "iframe", "style", "object", "embed", "form"}


def thumbnail_path(video_id: str) -> str:
    return f"videos/{video_id}.webp"


class _Audit(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.problems: list[str] = []
        self.images: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in _FORBIDDEN:
            self.problems.append(f"<{tag}> is not allowed")
        for name, _ in attrs:
            if name.startswith("on") or name == "style":
                self.problems.append(f"attribute {name} on <{tag}> is not allowed")
        if tag == "img":
            self.images.append(dict(attrs).get("src") or "")


def markdown_to_html(md: str, source: Path, media_dir: Path) -> str:
    expanded, video_ids = youtube.expand_lines(md)
    html = markdown.markdown(expanded, extensions=_EXTENSIONS, output_format="html")
    audit = _Audit()
    audit.feed(html)
    if audit.problems:
        raise ValueError(f"{source}: {audit.problems[0]}")
    for src in audit.images:
        if src.startswith(("http://", "https://", "//")):
            raise ValueError(f"{source}: external image {src}; copy it into media/")
        if src.startswith("/media/videos/"):
            # Embed thumbnails; one that belongs to an embed is also checked below with a clearer message.
            if not (media_dir / src.removeprefix("/media/")).is_file():
                raise ValueError(f"{source}: missing thumbnail {src}; run `make site-thumbs`")
            continue
        if not src.startswith("/media/"):
            raise ValueError(f"{source}: image {src} must be a /media/... path")
        try:
            media_file(src.removeprefix("/media/"), media_dir, source)
        except ValueError as exc:
            raise ValueError(f"{source}: missing media {src}") from exc
    for video_id in video_ids:
        if not (media_dir / thumbnail_path(video_id)).is_file():
            raise ValueError(
                f"{source}: missing thumbnail for {video_id}; run `make site-thumbs`"
            )
    return html.replace("<table>", '<div class="table-wrap"><table>').replace(
        "</table>", "</table></div>"
    )
