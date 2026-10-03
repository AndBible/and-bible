"""Local YouTube thumbnails: media/videos/<id>.webp, fetched when a video is added or refreshed on demand."""

from __future__ import annotations

import argparse
import io
import os
import sys
import urllib.error
import urllib.request
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field
from pathlib import Path

import yaml
from PIL import Image

from sitegen import paths
from sitegen.youtube import expand_lines

WIDTH = 480
QUALITY = 80
TIMEOUT = 30
BAR_MAX_MEAN = 12  # a letterbox row is nearly flat black: low mean and low peak luminance
BAR_MAX_PEAK = 40
BAR_MIN_FRACTION = 0.08  # each bar must cover at least this share of the height
_VARIANTS = ("maxresdefault", "hqdefault", "mqdefault")
_URL = "https://i.ytimg.com/vi/{id}/{name}.jpg"


def _download(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "andbible-website-build"})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.read()


def _get(video_id: str, opener: Callable[[str], bytes]) -> tuple[bytes, str]:
    """The best available thumbnail JPEG and its variant name (404 falls through to the next)."""
    for name in _VARIANTS[:-1]:
        try:
            return opener(_URL.format(id=video_id, name=name)), name
        except urllib.error.HTTPError as exc:
            if exc.code != 404:
                raise
    name = _VARIANTS[-1]
    return opener(_URL.format(id=video_id, name=name)), name


def crop_letterbox(img: Image.Image) -> Image.Image:
    """Remove symmetric near-black bars at the top and bottom (baked into hqdefault for 16:9 video).

    Both sides must have a bar of at least BAR_MIN_FRACTION of the height; the smaller of the two is
    cropped from each side. A dark scene is not a bar: a row counts only if it is nearly flat black.
    """
    gray = img.convert("L")
    width, height = gray.size

    def is_bar(y: int) -> bool:
        low, high = gray.crop((0, y, width, y + 1)).getextrema()
        mean = gray.crop((0, y, width, y + 1)).resize((1, 1), Image.Resampling.BOX).getpixel((0, 0))
        return high <= BAR_MAX_PEAK and mean < BAR_MAX_MEAN

    top = 0
    while top < height // 2 and is_bar(top):
        top += 1
    bottom = 0
    while bottom < height // 2 and is_bar(height - 1 - bottom):
        bottom += 1
    least = round(height * BAR_MIN_FRACTION)
    if top < least or bottom < least:
        return img
    cut = min(top, bottom)
    return img.crop((0, cut, width, height - cut))


def _render(video_id: str, opener: Callable[[str], bytes]) -> bytes:
    """The finished WEBP bytes for a video."""
    data, variant = _get(video_id, opener)
    with Image.open(io.BytesIO(data)) as raw:
        img = raw.convert("RGB")
    if variant == "hqdefault":
        img = crop_letterbox(img)
    if img.width > WIDTH:
        img = img.resize((WIDTH, round(img.height * WIDTH / img.width)), Image.Resampling.LANCZOS)
    out = io.BytesIO()
    img.save(out, "WEBP", quality=QUALITY, method=6)
    return out.getvalue()


def _store(target: Path, payload: bytes) -> bool:
    """Write atomically (temp file, then replace); return True if the content changed."""
    if target.is_file() and target.read_bytes() == payload:
        return False
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_name(f".{target.name}.tmp")
    try:
        tmp.write_bytes(payload)
        os.replace(tmp, target)
    finally:
        tmp.unlink(missing_ok=True)
    return True


def _target(media_dir: Path, video_id: str) -> Path:
    return media_dir / "videos" / f"{video_id}.webp"


def fetch(
    video_ids: Iterable[str],
    media_dir: Path,
    opener: Callable[[str], bytes] = _download,
) -> list[str]:
    """Download the missing thumbnails; return the ids fetched. Errors name the video id."""
    fetched: list[str] = []
    for video_id in video_ids:
        target = _target(media_dir, video_id)
        if target.exists():
            continue
        try:
            _store(target, _render(video_id, opener))
        except (OSError, ValueError) as exc:  # URLError/HTTPError/timeouts are OSErrors
            raise RuntimeError(f"thumbnail for YouTube video {video_id} failed: {exc}") from exc
        fetched.append(video_id)
    return fetched


@dataclass
class RefreshReport:
    changed: list[str] = field(default_factory=list)
    unchanged: list[str] = field(default_factory=list)
    failed: dict[str, str] = field(default_factory=dict)


def refresh(
    video_ids: Iterable[str],
    media_dir: Path,
    opener: Callable[[str], bytes] = _download,
) -> RefreshReport:
    """Re-download and overwrite; a failure leaves that video's existing file untouched and goes on."""
    report = RefreshReport()
    for video_id in video_ids:
        try:
            changed = _store(_target(media_dir, video_id), _render(video_id, opener))
        except (OSError, ValueError) as exc:
            report.failed[video_id] = str(exc)
            continue
        (report.changed if changed else report.unchanged).append(video_id)
    return report


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


def resolve_ids(values: Iterable[str], known: set[str]) -> list[str]:
    """Bare ids or YouTube URLs -> ids; raises ValueError for one that is not in the catalog/content."""
    from sitegen.newvideo import NewVideoError, parse_video  # newvideo imports this module

    ids: list[str] = []
    for value in values:
        try:
            video_id, _ = parse_video(value)
        except NewVideoError as exc:
            raise ValueError(str(exc)) from exc
        if video_id not in known:
            raise ValueError(f"YouTube video {video_id} is not in videos.yaml or any content page")
        ids.append(video_id)
    return ids


def main(argv: list[str] | None = None, opener: Callable[[str], bytes] = _download) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m sitegen.thumbnails",
        description="Fetch local YouTube thumbnails into media/videos/ (missing ones only by default).",
    )
    parser.add_argument("--refresh", nargs="+", metavar="ID_OR_URL", default=[],
                        help="re-download and overwrite these videos (ids or YouTube URLs)")
    parser.add_argument("--all", action="store_true", help="re-download every referenced video")
    parser.add_argument("--dry-run", action="store_true", help="list what would be fetched, write nothing")
    parser.add_argument("--media-dir", type=Path, default=paths.MEDIA, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    known = referenced_ids(paths.CONTENT, paths.DATA / "videos.yaml")
    try:
        wanted = resolve_ids(args.refresh, known)
    except ValueError as exc:
        print(f"thumbnails: {exc}", file=sys.stderr)
        return 2
    overwrite = args.all or bool(wanted)
    if args.all:
        wanted = sorted(known)
    if not overwrite:
        wanted = [v for v in sorted(known) if not _target(args.media_dir, v).exists()]
    wanted = list(dict.fromkeys(wanted))

    if args.dry_run:
        verb = "refresh" if overwrite else "fetch"
        for video_id in wanted:
            print(f"would {verb} {video_id}")
        print(f"thumbnails: dry run, {len(wanted)} to {verb}")
        return 0
    if not overwrite:
        try:
            got = fetch(wanted, args.media_dir, opener)
        except RuntimeError as exc:
            print(f"thumbnails: {exc}", file=sys.stderr)
            return 1
        print(f"thumbnails: {len(got)} fetched, {len(known) - len(got)} already present")
        return 0

    report = refresh(wanted, args.media_dir, opener)
    for video_id in report.changed:
        print(f"  {video_id}: changed")
    for video_id in report.unchanged:
        print(f"  {video_id}: unchanged")
    for video_id, why in report.failed.items():
        print(f"  {video_id}: FAILED ({why})", file=sys.stderr)
    done = len(report.changed) + len(report.unchanged)
    print(f"thumbnails: refreshed {done} (changed {len(report.changed)}), failed {len(report.failed)}")
    return 1 if report.failed else 0


if __name__ == "__main__":
    sys.exit(main())
