"""One-shot: seed data/videos.yaml from the AndBible YouTube channel and the app's help playlists.

    uv run python -m sitegen.migrate.yt_seed > data/videos.yaml

Scrapes `ytInitialData` from the channel's /videos and /shorts tabs and from each playlist, and follows
continuations through youtubei/v1/browse. Every video gets the placeholder topic "Getting started"
and a `# playlist: ...` hint comment; the catalog is then curated by hand. Counts go to stderr.
"""

from __future__ import annotations

import json
import re
import sys
import time
import urllib.request
from collections.abc import Iterator
from typing import Any

CHANNEL = "https://www.youtube.com/@AndBible"
PLAYLISTS = {  # CommonUtils.kt
    "PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ": "windows & workspaces",
    "PLD-W_Iw-N2Mnv8aYRK3QbZBjE3ZMmrJZ7": "labels & bookmarks",
    "PLD-W_Iw-N2MlzNt0Zpna-QoTBpEpWSden": "bookmarks & my notes",
    "PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m": "notes & study pads",
    "PLD-W_Iw-N2Ml4arSb_fDBYqgiYtVPmjFo": "speak",
    "PLD-W_Iw-N2MlOXgRTLQqoXZpQxkqf119a": "whats new 4.0",
}
_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
_ID = re.compile(r"[A-Za-z0-9_-]{11}")


def _request(url: str, body: bytes | None = None) -> str:
    request = urllib.request.Request(url, data=body, headers={
        "User-Agent": _UA, "Accept-Language": "en-US,en;q=0.9", "Cookie": "CONSENT=YES+1; SOCS=CAI",
        **({"Content-Type": "application/json"} if body else {})})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8")
        except OSError:
            if attempt == 3:
                raise
            time.sleep(3 * (attempt + 1))
    raise AssertionError("unreachable")


def initial_data(html: str) -> tuple[dict, str, str]:
    """(ytInitialData, INNERTUBE_API_KEY, client version) from a YouTube page."""
    start = html.index("ytInitialData") + len("ytInitialData")
    start = html.index("{", start)
    data, _ = json.JSONDecoder().raw_decode(html[start:])
    key = re.search(r'"INNERTUBE_API_KEY":"([^"]+)"', html)
    version = re.search(r'"INNERTUBE_CONTEXT_CLIENT_VERSION":"([^"]+)"', html)
    return data, key.group(1) if key else "", version.group(1) if version else "2.20240101.00.00"


def _walk(node: Any) -> Iterator[dict]:
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from _walk(value)
    elif isinstance(node, list):
        for value in node:
            yield from _walk(value)


def _text(node: Any) -> str:
    if isinstance(node, dict):
        if "simpleText" in node:
            return node["simpleText"]
        if "content" in node:
            return node["content"]
        return "".join(run.get("text", "") for run in node.get("runs", []))
    return ""


def videos_in(data: dict) -> list[tuple[str, str]]:
    """(id, title) pairs, in page order, from videoRenderer, playlistVideoRenderer and the shorts view models."""
    found: list[tuple[str, str]] = []
    for node in _walk(data):
        video_id = title = None
        if "videoId" in node and isinstance(node["videoId"], str) and "title" in node:
            video_id, title = node["videoId"], _text(node["title"])
        elif "onTap" in node and "overlayMetadata" in node:  # shortsLockupViewModel
            video_id = node["onTap"].get("innertubeCommand", {}).get("reelWatchEndpoint", {}).get("videoId")
            title = _text(node["overlayMetadata"].get("primaryText", {}))
        elif node.get("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" and "contentId" in node:  # lockupViewModel
            video_id = node["contentId"]
            title = _text(node.get("metadata", {}).get("lockupMetadataViewModel", {}).get("title", {}))
        if video_id and title and _ID.fullmatch(video_id):
            found.append((video_id, title))
    return found


def continuations(data: dict) -> list[str]:
    return [n["continuationCommand"]["token"] for n in _walk(data)
            if isinstance(n.get("continuationCommand"), dict) and "token" in n["continuationCommand"]]


def scrape(url: str) -> list[tuple[str, str]]:
    data, key, version = initial_data(_request(url))
    result = videos_in(data)
    pending = continuations(data)
    seen_tokens: set[str] = set()
    try:
        while pending:
            token = pending.pop(0)
            if token in seen_tokens:
                continue
            seen_tokens.add(token)
            body = json.dumps({"context": {"client": {"clientName": "WEB", "clientVersion": version, "hl": "en"}},
                               "continuation": token}).encode()
            page = json.loads(_request(f"https://www.youtube.com/youtubei/v1/browse?key={key}", body))
            result += videos_in(page)
            pending += continuations(page)
    except (OSError, ValueError) as exc:
        print(f"{url}: continuation failed ({exc}); kept {len(result)} videos", file=sys.stderr)
    unique = list(dict.fromkeys(result))
    print(f"{url}: {len(unique)} videos", file=sys.stderr)
    return unique


def main() -> int:
    hints: dict[str, list[str]] = {}
    titles: dict[str, str] = {}
    short_titles = dict(scrape(f"{CHANNEL}/shorts"))
    titles.update(dict(scrape(f"{CHANNEL}/videos")))
    titles.update(short_titles)
    for playlist, name in PLAYLISTS.items():
        for vid, title in scrape(f"https://www.youtube.com/playlist?list={playlist}"):
            titles.setdefault(vid, title)
            hints.setdefault(vid, []).append(name)
    for vid, title in titles.items():
        entry = {"id": vid, "title": title, "topic": "Getting started"}
        notes = [f"playlist: {', '.join(hints[vid])}"] if vid in hints else []
        print("- " + json.dumps(entry, ensure_ascii=False) + (f"  # {'; '.join(notes)}" if notes else "")
              + ("  # short" if vid in short_titles else ""))
    print(f"seeded {len(titles)} videos ({len(short_titles)} shorts)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
