"""Local YouTube thumbnails: media/videos/<id>.webp, fetched once when a video is added."""

from __future__ import annotations

import io
import urllib.error
import urllib.request
from collections.abc import Callable, Iterable
from pathlib import Path

import yaml
from PIL import Image

from sitegen import paths
from sitegen.youtube import expand_lines

WIDTH = 480
QUALITY = 80
TIMEOUT = 30
_URL = "https://i.ytimg.com/vi/{id}/{name}.jpg"


def _download(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "andbible-website-build"})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.read()


def _get(video_id: str, opener: Callable[[str], bytes]) -> bytes:
    try:
        return opener(_URL.format(id=video_id, name="hqdefault"))
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise
    return opener(_URL.format(id=video_id, name="mqdefault"))


def fetch(
    video_ids: Iterable[str],
    media_dir: Path,
    opener: Callable[[str], bytes] = _download,
) -> list[str]:
    """Download the missing thumbnails; return the ids fetched. Errors name the video id."""
    fetched: list[str] = []
    for video_id in video_ids:
        target = media_dir / "videos" / f"{video_id}.webp"
        if target.exists():
            continue
        try:
            data = _get(video_id, opener)
            with Image.open(io.BytesIO(data)) as img:
                img = img.convert("RGB")
                if img.width > WIDTH:
                    img = img.resize((WIDTH, round(img.height * WIDTH / img.width)), Image.Resampling.LANCZOS)
                target.parent.mkdir(parents=True, exist_ok=True)
                img.save(target, "WEBP", quality=QUALITY, method=6)
        except (OSError, ValueError) as exc:  # URLError/HTTPError/timeouts are OSErrors
            target.unlink(missing_ok=True)
            raise RuntimeError(f"thumbnail for YouTube video {video_id} failed: {exc}") from exc
        fetched.append(video_id)
    return fetched


def referenced_ids(content: Path, videos_yaml: Path) -> set[str]:
    """Every YouTube id embedded in content/**/*.md plus the ids listed in videos.yaml."""
    ids: set[str] = set()
    for page in sorted(content.rglob("*.md")):
        ids.update(expand_lines(page.read_text(encoding="utf-8"))[1])
    if videos_yaml.is_file():
        data = yaml.safe_load(videos_yaml.read_text(encoding="utf-8")) or []
        entries = data.get("videos", []) if isinstance(data, dict) else data
        ids.update(str(e["id"]) for e in entries if isinstance(e, dict) and "id" in e)
    return ids


def main() -> None:
    ids = referenced_ids(paths.CONTENT, paths.DATA / "videos.yaml")
    got = fetch(sorted(ids), paths.MEDIA)
    print(f"thumbnails: {len(got)} fetched, {len(ids) - len(got)} already present")


if __name__ == "__main__":
    main()
