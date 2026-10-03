"""Candidate lister for the landing page reviews (data/reviews.yaml). Never used by the build.

    uv run python -m sitegen.play_reviews [--min-len 60 --max-len 420 --limit 30]

Prints the most helpful 5-star Google Play reviews to the console for a human to curate. It uses
Google Play's undocumented batchexecute endpoint, so it may break without notice; a changed
response shape fails with a clear message. It never writes the catalog, and the reviewer names it
prints are for the curator's reference only: they must not be copied into data/ (see README).
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass

APP = "net.bible.android.activity"
URL = ("https://play.google.com/_/PlayStoreUi/data/batchexecute?rpcids=UsvDTd&f.sid=-697906427155521722"
       "&bl=boq_playuiserver_20190903.08_p0&hl=en&gl=US&authuser&soc-app=121&soc-platform=1&soc-device=1&_reqid=1065213")
SORT_HELPFUL = 1

Fetcher = Callable[[str, bytes], str]


@dataclass(frozen=True)
class PlayReview:
    id: str
    name: str
    score: int
    text: str
    year: int
    thumbs: int


class ResponseShapeError(RuntimeError):
    """Google Play answered, but not in the shape this tool knows."""


def http_fetch(url: str, body: bytes) -> str:
    req = urllib.request.Request(url, data=body, headers={
        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", "User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=30).read().decode()


def request_body(count: int, sort: int = SORT_HELPFUL) -> bytes:
    inner = json.dumps([None, None, [2, sort, [count, None, None], None, []], [APP, 7]], separators=(",", ":"))
    freq = json.dumps([[["UsvDTd", inner, None, "generic"]]], separators=(",", ":"))
    return urllib.parse.urlencode({"f.req": freq}).encode()


def parse(raw: str) -> list[PlayReview]:
    """Parse a batchexecute response. Raises ResponseShapeError when the shape is not as expected."""
    try:
        body = raw.split("\n", 2)[2] if raw.startswith(")]}'") else raw
        items = json.loads(json.loads(body)[0][2])[0]
        return [PlayReview(id=r[0], name=r[1][0], score=r[2], text=r[4], year=_year(r[5][0]), thumbs=r[6])
                for r in items]
    except (ValueError, IndexError, KeyError, TypeError) as e:
        raise ResponseShapeError(
            f"Google Play response shape changed ({type(e).__name__}: {e}); "
            "update sitegen/play_reviews.py or curate reviews by hand") from e


def _year(epoch_seconds: int) -> int:
    from datetime import datetime, timezone
    return datetime.fromtimestamp(epoch_seconds, timezone.utc).year


def candidates(raw: str, min_len: int, max_len: int, limit: int) -> list[PlayReview]:
    good = [r for r in parse(raw) if r.score == 5 and min_len <= len(r.text) <= max_len]
    return sorted(good, key=lambda r: -r.thumbs)[:limit]


def main(argv: list[str] | None = None, fetch: Fetcher = http_fetch) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--min-len", type=int, default=60)
    ap.add_argument("--max-len", type=int, default=420)
    ap.add_argument("--limit", type=int, default=30)
    ap.add_argument("--count", type=int, default=200, help="how many reviews to request")
    args = ap.parse_args(argv)
    try:
        found = candidates(fetch(URL, request_body(args.count)), args.min_len, args.max_len, args.limit)
    except ResponseShapeError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    for r in found:
        print(f"[{r.year}] helpful={r.thumbs} ({r.name}, console only)\n  {r.text}\n")
    print(f"{len(found)} candidates; copy year and text only (fix obvious typos, never reword) into data/reviews.yaml", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
