"""HISTORICAL one-shot tool: the legacy Read the Docs site is deprecated; its captured ids live in the fixtures. Do not re-run.

Capture the legacy Read the Docs site: build the Sphinx HTML and record every anchor id per page.

Task 15 converts the RST to Markdown and must keep every ``docs.andbible.org/en/latest/<page>.html#<id>``
deep link working, so the ids Sphinx emitted are the ground truth stored in the fixtures.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

from bs4 import BeautifulSoup

FIXTURES = Path(__file__).resolve().parents[2] / "tests" / "fixtures"


def extract_ids(html: str) -> list[str]:
    """Every `id` inside the main content area, in document order, deduplicated.

    All tags count (section, headings, explicit-target span/ul/figure/table, `.. contents::` nav, dt,
    toc back-reference anchors such as `id9`), because an old deep link can target any of them.
    Not collected: ids outside `[role="main"]` (sidebar, toctree, theme chrome). The search page's UI
    ids (`fallback`, `search-results`) are inside main and are kept; they are harmless.
    """
    soup = BeautifulSoup(html, "html.parser")
    root = soup.select_one('[role="main"]') or soup
    ids: list[str] = []
    for el in root.find_all(id=True):
        if el["id"] not in ids:
            ids.append(el["id"])
    return ids


def build_html(docs_repo: Path, out: Path) -> None:
    subprocess.run(
        [sys.executable, "-m", "sphinx", "-b", "html", "-q", str(docs_repo / "docs" / "source"), str(out)],
        check=True,
    )


def page_names(docs_repo: Path) -> list[str]:
    """Documents are the RST sources; Sphinx's own search.html/genindex.html are not pages."""
    src = docs_repo / "docs" / "source"
    return sorted(p.relative_to(src).with_suffix("").as_posix() for p in src.rglob("*.rst"))


def capture(docs_repo: Path, out: Path, fixtures: Path = FIXTURES) -> dict[str, list[str]]:
    build_html(docs_repo, out)
    pages = page_names(docs_repo)
    anchors = {p: extract_ids((out / f"{p}.html").read_text(encoding="utf-8")) for p in pages}
    urls = ["en/latest/", "en/latest/index.html"] + [f"en/latest/{p}.html" for p in pages if p != "index"]
    fixtures.mkdir(parents=True, exist_ok=True)
    (fixtures / "rtd_urls.txt").write_text("\n".join(urls) + "\n", encoding="utf-8")
    (fixtures / "rtd_anchors.json").write_text(
        json.dumps(anchors, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return anchors


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--docs-repo", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    anchors = capture(args.docs_repo, args.out)
    print(f"{len(anchors)} pages, {sum(map(len, anchors.values()))} ids")


if __name__ == "__main__":
    main()
