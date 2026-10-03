"""Fail when the built site would make a reader's browser talk to a third party.

Adapted from jailbee's scripts/docs_site.py. Run as `python -m sitegen.check [site_dir]`.
"""

from __future__ import annotations

import argparse
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

from sitegen import paths

# Any absolute or protocol-relative URL in a fetched attribute. `<a href>` is
# the one place a reader's click is what leaves the site; everything else here
# is a request the browser makes on its own.
_ABSOLUTE = re.compile(r"^(?:https?:)?//", re.IGNORECASE)
_FETCHED_ATTRS = ("src", "href", "xlink:href", "poster", "srcset", "data")
# url(...) in a stylesheet or an inline <style>, and @import of one.
_CSS_URL = re.compile(r"""(?:url\(|@import\s+)\s*['"]?((?:https?:)?//[^)'"\s]+)""", re.IGNORECASE)
# <link rel=...> values that merely describe the page and are never fetched.
_DESCRIPTIVE_RELS = {"canonical", "alternate"}


def _srcset_urls(value: str) -> list[str]:
    """The URL token of each comma-separated `srcset` candidate."""
    urls = []
    for candidate in value.split(","):
        candidate = candidate.strip()
        if candidate:
            urls.append(candidate.split()[0])
    return urls


class _ReferenceCollector(HTMLParser):
    """Every fetched reference, inline CSS text, and component ids.

    Inline CSS is collected only from `<style>` bodies and `style=""` values, so
    a `url(...)`-looking string in a `<pre><code>` sample is never flagged.
    """

    def __init__(self) -> None:
        super().__init__()
        self.references: list[tuple[str, str, str, str | None]] = []
        self.components: list[str] = []
        self.style_texts: list[str] = []
        self.classes: list[str] = []
        self._in_style = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        component = attributes.get("data-md-component")
        if component:
            self.components.append(component)
        class_ = attributes.get("class")
        if class_:
            self.classes.append(class_)
        style = attributes.get("style")
        if style:
            self.style_texts.append(style)
        if tag == "style":
            self._in_style = True
        for name in _FETCHED_ATTRS:
            value = attributes.get(name)
            if not value:
                continue
            if name == "srcset":
                for candidate in _srcset_urls(value):
                    self.references.append((tag, name, candidate, attributes.get("rel")))
            else:
                self.references.append((tag, name, value, attributes.get("rel")))

    def handle_endtag(self, tag: str) -> None:
        if tag == "style":
            self._in_style = False

    def handle_data(self, data: str) -> None:
        if self._in_style:
            self.style_texts.append(data)


def check(site: Path = paths.SITE) -> list[str]:
    """Problems that would make a reader's browser talk to a third party.

    JavaScript bundles are deliberately not scanned: they carry URL strings
    that are never fetched, and a text match there says nothing about what the
    page does. The `data-md-component="source"` repo widget (GitHub API calls)
    and the `glightbox` / `pyodide` classes (CDN fetches) are flagged outright.
    """
    pages = sorted(site.rglob("*.html"))
    if not pages:
        return [f"no pages under {site} - was the site built?"]

    problems: list[str] = []
    for page in pages:
        where = page.relative_to(site)
        collector = _ReferenceCollector()
        collector.feed(page.read_text(encoding="utf-8"))
        for tag, attribute, value, rel in collector.references:
            if not _ABSOLUTE.match(value):
                continue
            if tag == "a" and attribute == "href":
                continue
            rel_tokens = set((rel or "").lower().split())
            if tag == "link" and rel_tokens and rel_tokens <= _DESCRIPTIVE_RELS:
                continue
            if tag == "atom:link" and rel_tokens == {"self"}:  # the feed's own address, never fetched
                continue
            problems.append(f"{where}: <{tag} {attribute}={value!r}> leaves the site")
        if "source" in collector.components:
            problems.append(f'{where}: data-md-component="source" makes the page call api.github.com')
        for class_ in collector.classes:
            names = class_.split()
            if "glightbox" in names:
                problems.append(f'{where}: class="{class_}" makes the bundle fetch glightbox')
            if "pyodide" in names:
                problems.append(f'{where}: class="{class_}" makes the bundle fetch ace + pyodide')
        problems += [
            f"{where}: stylesheet fetches {url}"
            for text in collector.style_texts
            for url in _CSS_URL.findall(text)
        ]

    for stylesheet in sorted(site.rglob("*.css")):
        where = stylesheet.relative_to(site)
        problems += [
            f"{where}: stylesheet fetches {url}"
            for url in _CSS_URL.findall(stylesheet.read_text(encoding="utf-8"))
        ]
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("site", nargs="?", type=Path, default=paths.SITE)
    problems = check(parser.parse_args(argv).site)
    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        print(f"{len(problems)} off-site reference(s) in the built site", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
