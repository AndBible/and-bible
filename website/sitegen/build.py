"""Build andbible.org into website/_site/.

    uv run python -m sitegen.build [--content DIR] [--out DIR] [--data DIR] [--no-docs]

Each stage is one call in `build()`, in order. The output directory is rebuilt
from scratch so that removed content disappears from the next deploy.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from sitegen import home, paths, redirects
from sitegen.blog import render_blog, render_pages, write_sitemap
from sitegen.content import load_pages, load_posts
from sitegen.docs import build_docs, published_pages
from sitegen.home import render_home
from sitegen.i18n import languages, strings
from sitegen.paths import DEFAULT_LANG
from sitegen.redirects import write_stubs
from sitegen.reviews import load as load_reviews
from sitegen.videos import load as load_videos, related, render_videos


def build(content: Path, out: Path, data: Path = paths.DATA, docs: bool = True) -> None:
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    (out / "CNAME").write_text("andbible.org\n")
    shutil.copytree(paths.ASSETS, out / "assets")
    env = home.environment()
    posts = load_posts(content / DEFAULT_LANG / "blog", paths.MEDIA)
    reviews = load_reviews(data / "reviews.yaml")
    for lang in languages(content):
        render_home(env, strings(content, lang), lang, posts, out, reviews)
    docs_pages = {p.removesuffix(".md") for p in published_pages(paths.WEBSITE / "zensical.toml")}
    videos = load_videos(data / "videos.yaml", docs_pages)
    sitemap = ["/"]
    sitemap += render_blog(env, strings(content, DEFAULT_LANG), posts, out, paths.MEDIA)
    sitemap += render_pages(env, strings(content, DEFAULT_LANG),
                            load_pages(content / DEFAULT_LANG / "pages"), out, paths.MEDIA)
    if docs:
        sitemap += build_docs(content, out, related=related(videos))
    sitemap += render_videos(env, strings(content, DEFAULT_LANG), videos, out)
    if paths.MEDIA.is_dir():
        shutil.copytree(paths.MEDIA, out / "media", ignore=shutil.ignore_patterns(".git"))
    stubs: dict[str, str] = {}
    for name in ("redirects.yaml", "wp-uploads-redirects.yaml"):  # the second is generated
        if (data / name).is_file():
            stubs |= redirects.load(data / name)
    write_stubs(env, stubs, out)
    write_sitemap(sitemap, out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, default=paths.CONTENT)
    parser.add_argument("--out", type=Path, default=paths.SITE)
    parser.add_argument("--data", type=Path, default=paths.DATA)
    parser.add_argument("--no-docs", action="store_true", help="skip the Zensical docs build")
    args = parser.parse_args(argv)
    try:
        build(args.content, args.out, args.data, docs=not args.no_docs)
    except ValueError as exc:
        print(f"site build failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
