"""Build andbible.org into website/_site/.

    uv run python -m sitegen.build [--content DIR] [--out DIR]

Each stage is one call in `build()`, in order. The output directory is rebuilt
from scratch so that removed content disappears from the next deploy.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from sitegen import home, paths
from sitegen.blog import render_blog, write_sitemap
from sitegen.content import load_posts
from sitegen.home import render_home
from sitegen.i18n import languages, strings
from sitegen.paths import DEFAULT_LANG


def build(content: Path, out: Path) -> None:
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    (out / "CNAME").write_text("andbible.org\n")
    shutil.copytree(paths.ASSETS, out / "assets")
    env = home.environment()
    posts = load_posts(content / DEFAULT_LANG / "blog", paths.MEDIA)
    for lang in languages(content):
        render_home(env, strings(content, lang), lang, posts, out)
    sitemap = ["/"]
    sitemap += render_blog(env, strings(content, DEFAULT_LANG), posts, out, paths.MEDIA)
    write_sitemap(sitemap, out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, default=paths.CONTENT)
    parser.add_argument("--out", type=Path, default=paths.SITE)
    args = parser.parse_args(argv)
    try:
        build(args.content, args.out)
    except ValueError as exc:
        print(f"site build failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
