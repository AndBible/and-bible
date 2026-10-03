"""User documentation: stage the nav pages per language, then run Zensical.

Zensical cannot exclude files, so `nav` in zensical.toml is the publication
whitelist: only the pages it names are staged. Each language gets its own
staged tree (English fallback per file) and its own generated config with
absolute paths, built into <out>/<prefix>/docs/.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any

from sitegen import paths
from sitegen.i18n import languages, prefix, resolve
from sitegen.youtube import embed_html, expand_lines

UNPUBLISHED: set[str] = {"style_guide.md"}


def published_pages(config: Path) -> list[str]:
    pages: list[str] = []

    def walk(node: Any) -> None:
        if isinstance(node, str):
            pages.append(node)
        elif isinstance(node, list):
            for item in node:
                walk(item)
        elif isinstance(node, dict):
            for value in node.values():
                walk(value)

    walk(tomllib.loads(config.read_text())["project"]["nav"])
    return pages


def stage(content: Path, lang: str, stage_dir: Path, pages: list[str],
          related: dict[str, list[tuple[str, str]]]) -> None:
    if stage_dir.exists():
        shutil.rmtree(stage_dir)
    stage_dir.mkdir(parents=True)
    for page in pages:
        text, _ = expand_lines(resolve(content, lang, f"docs/{page}").read_text(encoding="utf-8"))
        videos = related.get(Path(page).with_suffix("").as_posix(), [])
        if videos:
            text = text.rstrip() + "\n\n## Related videos\n\n" + "\n\n".join(
                embed_html(vid, "video", title) for vid, title in videos) + "\n"
        target = stage_dir / page
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
    for source_lang in dict.fromkeys([paths.DEFAULT_LANG, lang]):
        images = content / source_lang / "docs" / "images"
        if images.is_dir():
            shutil.copytree(images, stage_dir / "images", dirs_exist_ok=True)


def _language_config(base: str, lang: str, stage_dir: Path, site_dir: Path) -> str:
    # Zensical 0.0.67 panics on absolute docs_dir/site_dir and rejects a site_dir outside
    # the config's directory, so both are written relative to <config dir>.
    config_dir = paths.BUILD / lang
    overrides = {
        "docs_dir": stage_dir.relative_to(config_dir).as_posix(),
        "site_dir": site_dir.relative_to(config_dir).as_posix(),
        "site_url": f"{paths.BASE_URL}{prefix(lang)}/docs/",
    }
    lines = []
    for line in base.splitlines():
        key = line.split("=", 1)[0].strip()
        if key in overrides:
            line = f"{key} = {json.dumps(overrides.pop(key))}"
        elif key == "custom_dir":
            line = f"custom_dir = {json.dumps(str(paths.THEME))}"
        lines.append(line)
        if line.strip() == "[project.theme]":
            lines.append(f"language = {json.dumps(lang)}")
    return "\n".join(lines) + "\n"


def build_docs(content: Path, out: Path, related: dict[str, list[tuple[str, str]]]) -> list[str]:
    base = paths.WEBSITE / "zensical.toml"
    pages = published_pages(base)
    sitemap: list[str] = []
    for lang in languages(content):
        stage_dir = paths.BUILD / lang / "docs"
        stage(content, lang, stage_dir, pages, related)
        # Build inside the config's directory, then copy to the real destination.
        built = paths.BUILD / lang / "site"
        config = paths.BUILD / lang / "zensical.toml"
        config.write_text(_language_config(base.read_text(), lang, stage_dir, built))
        # Run through this interpreter so the venv's zensical is used, not PATH's.
        subprocess.run([sys.executable, "-m", "zensical", "build", "--clean", "--strict",
                        "-f", str(config)], check=True, cwd=paths.WEBSITE)
        target = out / prefix(lang).lstrip("/") / "docs"
        shutil.copytree(built, target, dirs_exist_ok=True)
        sitemap += [f"{prefix(lang)}/docs/" + ("" if p == "index.md" else p.removesuffix(".md") + "/")
                    for p in pages]
    return sitemap
