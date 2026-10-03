"""HTML -> Markdown for the one-shot WordPress and Sphinx migrations.

The output targets the site renderer (`sitegen.render`: tables, fenced_code,
footnotes, attr_list, md_in_html, toc, admonition). YouTube embeds become URL-only
lines (which `sitegen.youtube` expands), galleries become `<div class="gallery"
markdown>` blocks and footnotes become `[^n]` references plus a trailing list.
"""

from __future__ import annotations

import re
from collections.abc import Callable

from bs4 import BeautifulSoup, NavigableString, Tag
from markdownify import MarkdownConverter

from sitegen import youtube

_FENCE = re.compile(r"^```[^\n]*\n(.*?)\n```[ \t]*$", re.S | re.M)
_HEADINGS = ("h1", "h2", "h3", "h4", "h5", "h6")
_BACKLINK_TEXT = {"↩", "↩︎", "↑", "↵"}


def _flat(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _classes(el: Tag) -> list[str]:
    return el.get("class") or []


def _unbold(cell: str) -> str:
    match = re.fullmatch(r"\*\*([^*]+)\*\*", cell)
    return match[1] if match else cell


def _youtube_line(url: str, vertical: bool) -> str | None:
    found = youtube.parse(url)
    if found is None:
        return None
    video_id, kind = found
    if vertical or kind == "short":
        return f"https://www.youtube.com/shorts/{video_id}"
    return f"https://www.youtube.com/watch?v={video_id}"


class _Converter(MarkdownConverter):
    def __init__(self, link_map: Callable[[str], str], image_map: Callable[[str], str]):
        super().__init__(heading_style="ATX", bullets="-", strong_em_symbol="*")
        self._link_map = link_map
        self._image_map = image_map

    def escape(self, text, parent_tags):
        text = super().escape(text, parent_tags)
        # markdownify leaves backticks alone, so a stray pair in prose would open a code span
        return text if parent_tags & {"code", "pre"} else text.replace("`", "\\`")

    def convert_iframe(self, el, text, parent_tags):
        line = _youtube_line(el.get("src") or "", "wp-embed-aspect-9-16" in _classes(el))
        return f"\n\n{line}\n\n" if line else ""

    def convert_figure(self, el, text, parent_tags):
        classes = _classes(el)
        if "wp-block-embed" in classes and "is-provider-youtube" in classes:
            iframe = el.find("iframe")
            url = (iframe.get("src") if iframe else None) or _flat(el.get_text())
            line = _youtube_line(url, "wp-embed-aspect-9-16" in classes)
            return f"\n\n{line}\n\n" if line else ""
        if "wp-block-gallery" in classes:
            lines = [self._image_line(img) for img in el.find_all("img")]
            return '\n\n<div class="gallery" markdown>\n' + "\n".join(lines) + "\n</div>\n\n"
        img, caption = el.find("img"), el.find("figcaption")
        if img is not None and caption is not None:
            return f"\n\n{self._image_line(img)}\n*{_flat(caption.get_text())}*\n\n"
        return f"\n\n{text.strip()}\n\n"

    def _image_line(self, img: Tag) -> str:
        return self.convert_img(img, "", set())

    def convert_div(self, el, text, parent_tags):
        classes = _classes(el)
        if "admonition" in classes:
            kind = next((c for c in classes if c != "admonition"), "note")
            title = el.get("data-title", "")
            head = f"!!! {kind}"
            if title and title.lower() != kind:
                head += f' "{title.replace(chr(34), chr(39))}"'
            # fenced_code only matches at column 0, so code inside the box becomes an indented block
            text = _FENCE.sub(lambda m: "\n".join(f"    {l}" if l else "" for l in m[1].split("\n")), text)
            body = "\n".join(f"    {line}" if line else "" for line in text.strip("\n").splitlines())
            return f"\n\n{head}\n\n{body}\n\n"
        return super().convert_div(el, text, parent_tags)

    def convert_table(self, el, text, parent_tags):
        rows = [[self._cell(c) for c in tr.find_all(["th", "td"])] for tr in el.find_all("tr")]
        rows = [r for r in rows if r]
        if not rows:
            return ""
        width = max(len(r) for r in rows)
        rows = [r + [""] * (width - len(r)) for r in rows]
        header, body = rows[0], rows[1:]  # a headerless table promotes its first row
        if el.find("tr").find("th") is None:
            header = [_unbold(c) for c in header]
        fmt = lambda r: "| " + " | ".join(r) + " |"  # noqa: E731
        lines = [fmt(header), fmt(["---"] * width), *map(fmt, body)]
        return "\n\n" + "\n".join(lines) + "\n\n"

    def _cell(self, cell: Tag) -> str:
        inner = "".join(self.process_element(c, parent_tags={"td", "_inline"}) for c in cell.children)
        return _flat(inner).replace("|", "\\|")

    def convert_a(self, el, text, parent_tags):
        if el.get("href"):
            el["href"] = self._link_map(el["href"])
        return super().convert_a(el, text, parent_tags)

    def convert_img(self, el, text, parent_tags):
        if el.get("src") and not el.get("data-mapped"):
            el["src"] = self._image_map(el["src"])
            el["data-mapped"] = "1"  # figures re-render their images after the children ran
        return super().convert_img(el, text, parent_tags)

    def convert_sup(self, el, text, parent_tags):
        if "fn" in _classes(el):
            return f"[^{_flat(el.get_text())}]"
        return f"<sup>{text}</sup>" if text.strip() else ""


def _prepare(soup: BeautifulSoup) -> None:
    for a in soup.select("a.headerlink"):
        a.decompose()
    for span in soup.select("span[id]"):
        if span.get_text(strip=True):
            span.unwrap()
        else:
            span.decompose()
    for box in soup.select("div.admonition"):
        title = box.select_one("p.admonition-title")
        if title is not None:
            box["data-title"] = _flat(title.get_text())
            title.decompose()


def _footnote_trailer(soup: BeautifulSoup, conv: _Converter) -> list[str]:
    labels = {
        a["href"].lstrip("#"): _flat(a.get_text())
        for a in soup.select("sup.fn a[href^='#']")
    }
    lines: list[str] = []
    for lst in soup.select("ol.wp-block-footnotes, div.footnote ol"):
        for index, li in enumerate(lst.find_all("li", recursive=False), start=1):
            for back in li.find_all("a"):
                if _flat(back.get_text()) in _BACKLINK_TEXT or (back.get("href") or "").startswith("#fnref"):
                    back.decompose()
            label = labels.get(li.get("id", ""), str(index))
            body = _flat("".join(conv.process_element(c, parent_tags=set()) for c in li.children))
            lines.append(f"[^{label}]: {body}")
        lst.decompose()
    return lines


def convert(html: str, link_map: Callable[[str], str], image_map: Callable[[str], str]) -> str:
    soup = BeautifulSoup(html, "html.parser")
    _prepare(soup)
    conv = _Converter(link_map, image_map)
    trailer = _footnote_trailer(soup, conv)
    md = re.sub(r"\n{3,}", "\n\n", conv.convert_soup(soup)).strip()
    if trailer:
        md += "\n\n" + "\n".join(trailer)
    return md + "\n"


def _heading_text(heading: Tag) -> str:
    parts = [
        s for s in heading.find_all(string=True)
        if s.find_parent("a", class_="headerlink") is None
    ]
    return _flat("".join(parts))


def _heading_id(heading: Tag) -> str:
    if heading.get("id"):
        return heading["id"]
    parent = heading.find_parent(["section", "div"])
    if parent is not None and parent.get("id"):
        first = parent.find(_HEADINGS)
        if first is heading:
            return parent["id"]
    return ""


def heading_ids(html: str) -> list[tuple[str, str, int]]:
    """`(text, id, level)` for every h1-h6; Sphinx keeps the id on the parent section."""
    soup = BeautifulSoup(html, "html.parser")
    return [(_heading_text(h), _heading_id(h), int(h.name[1])) for h in soup.find_all(_HEADINGS)]
