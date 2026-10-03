"""Static redirect stubs: GitHub Pages has no server-side redirects."""

from __future__ import annotations

from pathlib import Path

import yaml
from jinja2 import Environment


def load(path: Path) -> dict[str, str]:
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    data.pop("pending", None)
    for old, new in data.items():
        if not isinstance(old, str) or not old.startswith("/"):
            raise ValueError(f"{path}: redirect source {old!r} must start with /")
        if not isinstance(new, str) or not (new.startswith("/") or new.startswith("https://")):
            raise ValueError(f"{path}: redirect target {new!r} must be /path or https://")
    return data


def _file_for(out: Path, path: str) -> Path:
    rel = path.lstrip("/")
    return out / rel / "index.html" if path.endswith("/") else out / rel


def stub_html(env: Environment, target: str) -> str:
    return env.get_template("redirect.html").render(target=target)


def write_stubs(env: Environment, mapping: dict[str, str], out: Path) -> None:
    for old, new in sorted(mapping.items()):
        if new.startswith("/") and not _file_for(out, new.split("#")[0]).is_file():
            raise ValueError(f"redirect {old} -> {new}: target does not exist in the built site")
        target = _file_for(out, old)
        if target.exists():
            raise ValueError(f"redirect {old} would overwrite a generated page")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(stub_html(env, new), encoding="utf-8")
