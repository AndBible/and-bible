"""The release channel switch (`release:` in site.yaml): beta or stable download links.

`release.channel` is `beta` while a major version is in public beta and `stable` otherwise.
It drives two things:

- the landing page's download entries (`getapp(site)`): stable uses `getapp.stores` and
  `getapp.others` as written; beta replaces them with `getapp.beta`;
- the docs, through conditional blocks and link placeholders resolved while staging
  (`apply(text, release, source)`):

      <!-- if beta -->
      ... shown only in the beta channel ...
      <!-- else -->
      ... shown otherwise ...
      <!-- end -->

  Conditions are `beta`, `stable`, `testflight` (beta with a TestFlight link set) and
  `not <condition>`. Blocks nest; each marker is a line of its own. Placeholders
  `{{ name }}` take a link from `links(release)`; an unknown name fails the build.

Unprocessed, the markers are HTML comments, so a page read on GitHub shows every branch.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

CHANNELS = ("beta", "stable")
RELEASES_PAGE = "https://github.com/AndBible/and-bible/releases"
RELEASES_LATEST = f"{RELEASES_PAGE}/latest"
PLAY_BETA = "https://play.google.com/apps/testing/net.bible.android.activity"

_MARKER = re.compile(r"^\s*<!--\s*(if\s+(not\s+)?([a-z]+)|else|end)\s*-->\s*$")
_PLACEHOLDER = re.compile(r"\{\{\s*([a-z_]+)\s*\}\}")
_CONDITIONS = ("beta", "stable", "testflight")


@dataclass(frozen=True)
class Release:
    channel: str = "stable"
    ios_testflight_link: str = ""
    beta_release_link: str = ""

    @property
    def beta(self) -> bool:
        return self.channel == "beta"

    @property
    def testflight(self) -> bool:
        return self.beta and bool(self.ios_testflight_link)


def settings(site: dict[str, Any], source: str = "site.yaml") -> Release:
    """The validated `release:` section; a missing section means stable."""
    data = site.get("release") or {}
    if not isinstance(data, dict):
        raise ValueError(f"{source}: release must be a mapping")
    channel = data.get("channel", "stable")
    if channel not in CHANNELS:
        raise ValueError(f"{source}: release.channel must be one of {', '.join(CHANNELS)}, not {channel!r}")
    links = {}
    for key in ("ios_testflight_link", "beta_release_link"):
        value = data.get(key) or ""
        if not isinstance(value, str) or (value and not value.startswith("https://")):
            raise ValueError(f"{source}: release.{key} must be empty or an https:// URL")
        links[key] = value.strip()
    return Release(channel=channel, **links)


def links(release: Release) -> dict[str, str]:
    """The `{{ name }}` placeholders of docs pages and of `getapp.beta` URLs."""
    return {
        "beta_release": release.beta_release_link or RELEASES_PAGE,
        "releases_page": RELEASES_PAGE,
        "releases_latest": RELEASES_LATEST,
        "play_beta": PLAY_BETA,
        "testflight": release.ios_testflight_link,
    }


def _fill(text: str, release: Release, source: str) -> str:
    table = links(release)

    def one(match: re.Match[str]) -> str:
        name = match.group(1)
        if name not in table:
            raise ValueError(f"{source}: unknown link placeholder {{{{ {name} }}}}")
        if not table[name]:
            raise ValueError(f"{source}: placeholder {{{{ {name} }}}} is empty in this channel; "
                             "guard it with <!-- if testflight -->")
        return table[name]

    return _PLACEHOLDER.sub(one, text)


def apply(text: str, release: Release, source: str = "page") -> str:
    """Keep the conditional blocks that hold for `release`, then fill the link placeholders."""
    state = {"beta": release.beta, "stable": not release.beta, "testflight": release.testflight}
    stack: list[tuple[bool, bool]] = []  # (this branch shown, parent shown)
    shown = True
    out: list[str] = []
    for number, line in enumerate(text.splitlines(keepends=True), start=1):
        marker = _MARKER.match(line)
        if marker is None:
            if "<!-- if" in line or "<!-- else" in line or "<!-- end" in line:
                raise ValueError(f"{source}:{number}: a channel marker must be a line of its own")
            if shown:
                out.append(line)
            continue
        word = marker.group(1).split()[0]
        if word == "if":
            name = marker.group(3)
            if name not in _CONDITIONS:
                raise ValueError(f"{source}:{number}: unknown condition {name!r}")
            holds = state[name] != bool(marker.group(2))
            stack.append((holds, shown))
            shown = shown and holds
        elif not stack:
            raise ValueError(f"{source}:{number}: <!-- {word} --> without <!-- if -->")
        elif word == "else":
            holds, parent = stack[-1]
            stack[-1] = (not holds, parent)
            shown = parent and not holds
        else:
            _, shown = stack.pop()
    if stack:
        raise ValueError(f"{source}: <!-- if --> without <!-- end -->")
    return _fill("".join(out), release, source)


def _entries(items: list[dict[str, Any]], release: Release, source: str) -> list[dict[str, Any]]:
    """`when: testflight` / `when: not testflight` entries kept or dropped, URLs filled."""
    kept = []
    for item in items:
        when = item.get("when")
        if when is not None:
            negate = when.startswith("not ")
            if when.removeprefix("not ") != "testflight":
                raise ValueError(f"{source}: getapp.beta entry {item.get('name')!r}: unknown when {when!r}")
            if release.testflight == negate:
                continue
        entry = {k: v for k, v in item.items() if k != "when"}
        if "url" in entry:
            entry["url"] = _fill(entry["url"], release, source)
        kept.append(entry)
    return kept


def getapp(site: dict[str, Any], source: str = "site.yaml") -> dict[str, Any]:
    """The landing page's `getapp` strings for the configured channel."""
    release = settings(site, source)
    base = dict(site["getapp"])
    beta = base.pop("beta", None)
    if not release.beta:
        return base
    if not isinstance(beta, dict):
        raise ValueError(f"{source}: release.channel is beta but getapp.beta is missing")
    return base | {key: value for key, value in beta.items() if key not in ("stores", "others")} | {
        "stores": _entries(beta["stores"], release, source),
        "others": _entries(beta["others"], release, source),
    }
