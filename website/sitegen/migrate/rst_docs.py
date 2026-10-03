"""HISTORICAL, ONE-SHOT: kept for reference only. Its output (content/en/docs/*.md) has since been edited by
hand and is now the source of truth; do not re-run it.

One-shot migration of the legacy Read the Docs site (Sphinx/RST) to Markdown under content/en/docs/.

The Sphinx HTML (built by `rtd_capture`) is converted, not the RST, so roles, substitutions and
directives are already resolved. The point of the migration is that every old
``docs.andbible.org/en/latest/<page>.html#<id>`` deep link still lands on the same section:

* a heading keeps the Sphinx section id, pinned as ``{#id}`` only when Python-Markdown's own slug
  of the heading text would differ;
* every other id Sphinx emitted (explicit labels, ``.. contents::`` entries and their ``idN``
  back-references, ids on lists and tables) becomes an empty ``<span id="...">`` at the same place.

Usage: ``python -m sitegen.migrate.rst_docs --html SPHINX_HTML --source RST_SOURCE``.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import sys
from collections.abc import Iterable
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit

from bs4 import BeautifulSoup, Tag
from markdown.extensions.toc import slugify

from sitegen import paths
from sitegen.migrate import html2md, rtd_capture

DOCS = paths.CONTENT / "en" / "docs"
_HEADINGS = ("h1", "h2", "h3", "h4", "h5", "h6")
_IMAGE_DIRS = ("images", "resources")
_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}  # .eps is dropped
_BLOCK = {"ul", "ol", "dl", "div", "table", "figure", "p", "blockquote", "nav", "pre"}
_CELL = {"li", "dt", "dd", "td", "th"}

# Cross references Sphinx could not resolve (it printed warnings and rendered plain text or a
# "problematic" span). The target is the section the author meant.
LINK_FIXES: dict[tuple[str, str], str] = {
    ("look_and_feel", "`Formatting`_"): "#formatting-options",
    ("look_and_feel", "`Text Appearance`_"): "#text-appearance-options",
    ("style_guide", "This jumps to Inline Formatting above"): "#inline-formatting",
    ("verse_action_dialog", "Creating a note"): "notes.md#creating-notes",
    ("workspaces", "Easily navigate between workspaces using gestures."):
        "navigation.md#moving-between-workspaces",
}

# look_and_feel.rst underlines "Formatting" and "Text Appearance" with a title style docutils rejects, so
# both headings vanished from the old page and only the labelled lists remained. The prose still points
# at them, so each list gets its label back as a bold line (the old `#...-options` ids stay on the lists).
LIST_LABELS: dict[tuple[str, str], str] = {
    ("look_and_feel", "formatting-options"): "Formatting",
    ("look_and_feel", "text-appearance-options"): "Text appearance",
}

# The screenshot substitutions in reading_plans.rst are named the wrong way round (|reading_plan_list|
# points at the daily-readings file and vice versa). The rendered order was right (list, daily
# readings, days), only the alt texts were swapped, so the alt text is written from the pictures.
ALT_TEXT: dict[str, str] = {
    "reading-plan-list.png": "Choose a reading plan",
    "reading-plan-daily-readings.png": "Daily readings for one day of a plan",
    "reading-plan-days.png": "List of the days in a plan",
}

# Pages Sphinx replaced with its own generated page (search.rst is shadowed by search.html);
# the real content comes from the RST source.
_RST_ONLY = {"search"}


def image_name(name: str) -> str:
    """Lower-kebab file name: spaces and underscores become hyphens."""
    stem, ext = os.path.splitext(name)
    return re.sub(r"[\s_]+", "-", stem).lower() + ext.lower()


def _flat(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _humanise(src: str) -> str:
    stem = re.sub(r"[-_\s]+", " ", os.path.splitext(os.path.basename(unquote(src)))[0]).strip()
    return stem[:1].upper() + stem[1:]


class _Converter(html2md._Converter):
    """The shared converter plus what Sphinx output needs: pinned heading ids, anchors, 4-space lists."""

    def convert_hN(self, n, el, text, parent_tags):  # noqa: N802 (markdownify's name)
        out = super().convert_hN(n, el, text, parent_tags)
        section = el.parent
        if "_inline" in parent_tags or section is None or section.name != "section":
            return out
        sphinx_id = section.get("id")
        if sphinx_id and slugify(_flat(el.get_text()), "-") != sphinx_id:
            return out.rstrip("\n") + f" {{#{sphinx_id}}}\n\n"
        return out

    def convert_span(self, el, text, parent_tags):
        anchor = el.get("data-anchor")
        return f'<span id="{anchor}"></span>' if anchor else text

    def convert_p(self, el, text, parent_tags):
        if "caption" in (el.get("class") or []) and "_inline" not in parent_tags:
            return f"\n\n**{_flat(text)}**\n\n"  # a toctree caption
        return super().convert_p(el, text, parent_tags)

    def convert_cite(self, el, text, parent_tags):
        return self.convert_em(el, text, parent_tags)

    def convert_li(self, el, text, parent_tags):
        # Python-Markdown nests a list only under four spaces, markdownify indents by the bullet width
        text = (text or "").strip()
        if not text:
            return "\n"
        parent = el.parent
        if parent is not None and parent.name == "ol":
            start = int(parent["start"]) if str(parent.get("start", "")).isnumeric() else 1
            bullet = f"{start + len(el.find_previous_siblings('li'))}. "
        else:
            bullet = "- "
        first, *rest = text.split("\n")
        return "\n".join([bullet + first, *(f"    {line}" if line else "" for line in rest)]) + "\n"

    def convert_img(self, el, text, parent_tags):
        if not el.get("data-mapped"):
            el["data-mapped"] = "1"
            name = os.path.basename(unquote(el.get("src") or ""))
            alt = el.get("alt") or ""
            if not alt or alt == el.get("src") or alt.startswith("_images/") or alt.lower().endswith(
                    tuple(_IMAGE_EXTENSIONS)):
                alt = _humanise(name)
            el["alt"] = ALT_TEXT.get(image_name(name), alt)
            el["src"] = self._image_map(el["src"])
        attrs: list[str] = []
        for cls in el.get("class") or []:
            if cls.startswith("align-"):
                attrs.append(f".{cls}")
        for key in ("width", "height"):
            if el.get(key):
                attrs.append(f'{key}="{el[key]}"')
        style = (el.get("style") or "").strip().rstrip(";")
        if style:
            attrs.append(f'style="{style}"')
        suffix = "{ " + " ".join(attrs) + " }" if attrs else ""
        return f"![{el['alt']}]({el['src']}){suffix}"


def _marker(soup: BeautifulSoup, anchor: str) -> Tag:
    return soup.new_tag("span", attrs={"data-anchor": anchor})


def _block_markers(soup: BeautifulSoup, anchors: Iterable[str]) -> Tag:
    paragraph = soup.new_tag("p")
    for anchor in anchors:
        paragraph.append(_marker(soup, anchor))
    return paragraph


def _move_ids_to_markers(soup: BeautifulSoup, root: Tag) -> list[str]:
    """Replace every id except a section's by an empty marker span; return the ids kept."""
    kept: list[str] = [s["id"] for s in root.find_all("section", id=True)]
    for el in list(root.find_all(id=True)):
        if el.name == "section" or el.parent is None:
            continue
        if el.name == "nav" and "contents" in (el.get("class") or []):
            # the in-page table of contents duplicates the theme's own: keep only its anchors
            anchors = [el["id"], *(a["id"] for a in el.find_all(id=True))]
            el.replace_with(_block_markers(soup, anchors))
            kept += anchors
            continue
        anchor = el["id"]
        del el["id"]
        kept.append(anchor)
        if el.name in _CELL:
            el.insert(0, _marker(soup, anchor))
        elif el.name in _BLOCK or (el.name == "span" and not el.get_text(strip=True)
                                   and el.parent.name in {"section", "div"}):
            wrapper = _block_markers(soup, [anchor])
            if el.name == "span" and not el.get_text(strip=True):
                el.replace_with(wrapper)
            else:
                el.insert_before(wrapper)
        elif el.name == "span" and not el.get_text(strip=True):
            el.replace_with(_marker(soup, anchor))
        else:
            el.insert_before(_marker(soup, anchor))
    return kept


def _apply_link_fixes(soup: BeautifulSoup, root: Tag, page: str) -> None:
    for span in root.select("span.xref, span.problematic"):
        if span.parent.name == "a" and "xref" in span.get("class", []):
            span.unwrap()  # a resolved toctree entry: Sphinx only wraps the label
            continue
        target = LINK_FIXES.get((page, _flat(span.get_text())))
        if target is None:
            print(f"warning: {page}: unresolved reference {_flat(span.get_text())!r}", file=sys.stderr)
            continue
        label = _flat(span.get_text()).strip("`_ ")
        parent = span.parent
        if parent.name == "a":  # keep the anchor markers that sit next to the span inside this link
            parent["href"] = target
            span.replace_with(label)
        else:
            link = soup.new_tag("a", href=target)
            link.string = label
            span.replace_with(link)


def _playlists_to_links(soup: BeautifulSoup, root: Tag) -> None:
    """The site only embeds single videos (a click-to-load player), so a playlist player becomes a link."""
    for frame in root.find_all("iframe"):
        parts = urlsplit(frame.get("src") or "")
        if not parts.path.endswith("/embed/videoseries"):
            continue
        playlist = parse_qs(parts.query).get("list", [""])[0]
        previous = frame.find_previous_sibling()
        if previous is not None and previous.name == "p" and "video below" in previous.get_text():
            previous.decompose()  # "click the menu at the top right of the video below" describes the player
        link = soup.new_tag("a", href=f"https://www.youtube.com/playlist?list={playlist}")
        link.string = "Watch the playlist on YouTube"
        wrapper = soup.new_tag("p")
        wrapper.append(link)
        frame.replace_with(wrapper)


def _docutils_body(rst: str) -> str:
    from docutils.core import publish_parts

    html = publish_parts(rst, writer_name="html5", settings_overrides={
        "doctitle_xform": False, "initial_header_level": 1, "report_level": 4, "syntax_highlight": "none"})["body"]
    return html.replace("<aside ", "<div ").replace("</aside>", "</div>")


def convert_page(page: str, html: str, want_ids: Iterable[str] | None = None) -> str:
    """Markdown for one Sphinx page (`page` is its path without suffix, e.g. ``releases/release_5_0``).

    `want_ids` are the ids the old page had; any the conversion did not carry over are added as empty
    anchors at the end, so a legacy deep link never goes dead silently.
    """
    soup = BeautifulSoup(html, "html.parser")
    root = soup.select_one('div[itemprop="articleBody"]') or soup.select_one("div.document") or soup
    for link in root.select("a.headerlink"):
        link.decompose()
    for link in root.select("a.toc-backref"):
        link.unwrap()
    kept = _move_ids_to_markers(soup, root)
    for (label_page, anchor), label in LIST_LABELS.items():
        marker = root.find("span", attrs={"data-anchor": anchor}) if label_page == page else None
        if marker is not None:
            line = soup.new_tag("p")
            line.append(soup.new_tag("strong"))
            line.strong.string = label
            marker.parent.insert_after(line)
    _apply_link_fixes(soup, root, page)
    _playlists_to_links(soup, root)
    missing = [i for i in dict.fromkeys(want_ids or []) if i not in kept]
    if missing:
        root.append(_block_markers(soup, missing))
    page_dir = os.path.dirname(page) or "."

    def link_map(href: str) -> str:
        parts = urlsplit(href)
        if not parts.scheme and "_images/" in parts.path:  # Sphinx links a resized image to its full size
            return image_map(parts.path)
        if parts.scheme or parts.netloc or not parts.path.endswith(".html"):
            return href
        return parts._replace(path=parts.path.removesuffix(".html") + ".md").geturl()

    def image_map(src: str) -> str:
        target = f"images/{image_name(os.path.basename(unquote(src)))}"
        return os.path.relpath(target, page_dir)

    return html2md.convert(str(root), link_map, image_map, converter=_Converter)


_TOCTREE = re.compile(r"^\.\. toctree::\n((?:[ \t]+.*\n|[ \t]*\n)*)", re.M)


def _toctrees(index_rst: str) -> list[tuple[str, list[str], bool]]:
    trees = []
    for block in _TOCTREE.findall(index_rst + "\n"):
        caption = re.search(r":caption:\s*(.+)", block)
        entries = [ln.strip() for ln in block.splitlines() if ln.strip() and not ln.strip().startswith(":")]
        trees.append((caption[1].strip() if caption else "", entries, ":hidden:" in block))
    return trees


def hidden_entries(index_rst: str) -> list[str]:
    """Documents only reachable through a captionless hidden toctree (still published, so their URLs live)."""
    return [e for caption, entries, hidden in _toctrees(index_rst) if hidden and not caption for e in entries]


def nav_from_index(index_rst: str) -> list:
    nav: list = [{"Home": "index.md"}]
    for caption, entries, _hidden in _toctrees(index_rst):
        if caption:
            nav.append({caption: [f"{e}.md" for e in entries]})
    return nav


def _toml(node) -> str:
    import json

    if isinstance(node, str):
        return json.dumps(node, ensure_ascii=False)
    if isinstance(node, list):
        return "[" + ", ".join(_toml(n) for n in node) + "]"
    ((key, value),) = node.items()
    return "{ " + f"{_toml(key)} = {_toml(value)}" + " }"


def write_nav(config: Path, nav: list) -> None:
    block = "nav = [\n" + "".join(f"  {_toml(item)},\n" for item in nav) + "]\n"
    text = config.read_text(encoding="utf-8")
    new, count = re.subn(r"^nav = \[\n.*?^\]\n", lambda _m: block, text, count=1, flags=re.M | re.S)
    if count != 1:
        raise ValueError(f"no `nav = [...]` block in {config}")
    config.write_text(new, encoding="utf-8")


def _section(entry: str, pages: list[str]) -> str | dict:
    """A directory's `index.md` becomes a nav section holding every page below it (newest release first)."""
    directory, _, name = entry.rpartition("/")
    if name != "index.md":
        return entry
    siblings = sorted((p for p in pages if p.startswith(f"{directory}/") and not p.endswith("/index")),
                      reverse=directory == "releases")
    return {directory.capitalize(): [entry, *(f"{p}.md" for p in siblings)]}


def copy_images(source: Path, dest: Path) -> list[str]:
    dest.mkdir(parents=True, exist_ok=True)
    copied: list[str] = []
    sources = [f for d in _IMAGE_DIRS for f in sorted((source / d).glob("*"))]
    sources += sorted((source / "videos").glob("*.gif"))
    for file in sources:
        if file.suffix.lower() in _IMAGE_EXTENSIONS:
            shutil.copy2(file, dest / image_name(file.name))
            copied.append(image_name(file.name))
    return copied


def migrate(html_dir: Path, source: Path, docs: Path = DOCS, config: Path | None = None) -> list[str]:
    pages = sorted(p.relative_to(source).with_suffix("").as_posix() for p in source.rglob("*.rst"))
    for page in pages:
        html = (html_dir / f"{page}.html").read_text(encoding="utf-8")
        want = rtd_capture.extract_ids(html)
        if page in _RST_ONLY:
            body = _docutils_body((source / f"{page}.rst").read_text(encoding="utf-8"))
            html = f'<div itemprop="articleBody">{body}</div>'
        target = docs / f"{page}.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(convert_page(page, html, want), encoding="utf-8")
    copy_images(source, docs / "images")
    index_rst = (source / "index.rst").read_text(encoding="utf-8")
    nav = nav_from_index(index_rst)
    for entry in nav[1:]:
        ((_caption, items),) = entry.items()
        items[:] = [_section(item, pages) for item in items]
    hidden = [f"{h}.md" for h in hidden_entries(index_rst)]
    nav.append({"More": ["style_guide.md", *hidden]})
    write_nav(config or paths.WEBSITE / "zensical.toml", nav)
    return pages


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--html", type=Path, required=True, help="Sphinx HTML output (rtd_capture)")
    ap.add_argument("--source", type=Path, required=True, help="docs/source of the legacy docs repo")
    args = ap.parse_args()
    print(f"{len(migrate(args.html, args.source))} pages written to {DOCS}")


if __name__ == "__main__":
    main()
