"""Shared jinja2 environment and the landing page."""

from __future__ import annotations

import hashlib
import re
from pathlib import Path

import markdown
from jinja2 import Environment, FileSystemLoader, select_autoescape
from markupsafe import Markup

from sitegen import paths
from sitegen.content import Post
from sitegen.i18n import prefix


def _md_inline(text: str) -> Markup:
    html = markdown.markdown(text)
    return Markup(re.sub(r"^<p>(.*)</p>$", r"\1", html, flags=re.S))


def asset_hashes() -> dict[str, str]:
    """8-char content hashes for cache-busting ?v= query strings."""
    return {
        p.relative_to(paths.ASSETS).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()[:8]
        for p in paths.ASSETS.rglob("*") if p.is_file()
    }


def asset_url(name: str) -> str:
    """Cache-busted URL of a file under assets/; an unknown name raises KeyError."""
    return f"/assets/{name}?v={asset_hashes()[name]}"


def environment() -> Environment:
    env = Environment(loader=FileSystemLoader(paths.TEMPLATES), autoescape=select_autoescape(["html", "xml"]))
    env.filters["md_inline"] = _md_inline
    env.globals["asset_hash"] = asset_hashes()
    env.globals["asset_url"] = asset_url
    env.globals["base_url"] = paths.BASE_URL
    return env


def render_home(env: Environment, strings: dict, lang: str, latest: list[Post], out: Path) -> None:
    target = out / prefix(lang).lstrip("/") / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(env.get_template("home.html").render(
        lang=lang, prefix=prefix(lang), strings=strings, latest=latest[:3],
        title=f"{strings['site_name']}: {strings['hero']['eyebrow']}",
        description=strings["hero"]["lead"],
        canonical=f"{paths.BASE_URL}{prefix(lang)}/",
        og_image=f"{paths.BASE_URL}/assets/img/og-default.png", og_type="website",
    ), encoding="utf-8")
