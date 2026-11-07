"""Downloads the Sword modules a language's shots need (demo-content/modules.yaml) from the app's own repositories
(app/src/main/res/raw/repositories.txt), into a cache folder of `<initials>.zip` files the drivers install."""
from __future__ import annotations

import urllib.request
from pathlib import Path

import yaml

from .errors import ShotError


def parse_repositories(text: str) -> dict[str, tuple[str, str]]:
    """repository name -> (host, zip directory), from the app's `Installer.N=proto,name,host,zipdir,rawdir,...` lines."""
    out = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        fields = line.split("=", 1)[1].split(",")
        if len(fields) >= 4:
            out[fields[1]] = (fields[2], fields[3])
    return out


def module_url(repos: dict[str, tuple[str, str]], repo: str, initials: str) -> str:
    if repo not in repos:
        raise KeyError(f"repository {repo!r} is not in the app's repository list")
    host, zipdir = repos[repo]
    return f"https://{host}{zipdir}/{initials}.zip"


def modules_for(path: Path, lang: str) -> list[tuple[str, str]]:
    table = yaml.safe_load(Path(path).read_text()) or {}
    if lang not in table:
        raise KeyError(f"no module list for language {lang!r} in {path}")
    return [(m["initials"], m["repo"]) for m in table[lang]]


def fetch(lang: str, out: Path, root: Path) -> list[Path]:
    repos = parse_repositories((root / "app/src/main/res/raw/repositories.txt").read_text())
    out.mkdir(parents=True, exist_ok=True)
    got = []
    for initials, repo in modules_for(root / "store-screenshots/demo-content/modules.yaml", lang):
        dest = out / f"{initials}.zip"
        if not dest.exists():
            url = module_url(repos, repo, initials)
            tmp = dest.with_suffix(".part")
            try:
                with urllib.request.urlopen(url, timeout=300) as r, open(tmp, "wb") as f:
                    f.write(r.read())
            except OSError as e:
                raise ShotError(initials, f"cannot download {url}: {e}") from e
            tmp.replace(dest)
        got.append(dest)
    return got


def cmd_modules(args) -> int:
    from .state import repo_root, work_root
    out = Path(args.out) if args.out else work_root() / "modules"
    for p in fetch(args.lang, out, repo_root()):
        print(p)
    return 0


def register(sub) -> None:
    p = sub.add_parser("modules", help="download the language's Sword modules from the app's repositories")
    p.add_argument("--lang", required=True)
    p.add_argument("--out", help="cache folder (default .local/store-shots/modules)")
    p.set_defaults(handler=cmd_modules)
