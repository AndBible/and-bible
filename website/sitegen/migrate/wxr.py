"""One-shot WordPress (WXR) import: posts, pages, media, legacy-URL fixture and a report.

    uv run --group migrate python -m sitegen.migrate.wxr --wxr EXPORT.xml --media-tar UPLOADS.tar

Writes content/en/blog/*.md, content/en/pages/*.md, the optimised media under media/,
tests/fixtures/wp_urls.txt and migration-report.md. Exits 1 when a post's image or embed count
differs between the source and the converted Markdown (fix the converter, never the output).
"""

from __future__ import annotations

import argparse
import html
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

import yaml
from bs4 import BeautifulSoup

from sitegen import paths, thumbnails, youtube
from sitegen.blog import PAGE_SIZE
from sitegen.content import taxonomy_slug
from sitegen.migrate import html2md, media

NS = {
    "wp": "http://wordpress.org/export/1.2/",
    "content": "http://purl.org/rss/1.0/modules/content/",
    "excerpt": "http://wordpress.org/export/1.2/excerpt/",
}
SITE_HOSTS = {"andbible.org", "www.andbible.org", "andbibleorg.wordpress.com"}
# Page slug -> output page name. Pages not listed are covered by redirects or generated pages.
PAGES = {"privacy": "privacy", "terms": "terms", "gpl": "gpl", "tracking": "tracking",
         "printable-promotional-material": "flyers"}
UPLOAD_REDIRECTS = "wp-uploads-redirects.yaml"
SKIPPED_PAGES = {"about", "blog", "tutorial-videos", "sponsor-andbible-financially", "failed-orders"}
SUMMARY_WORDS = 30
EXCERPT_WORDS = 60  # some WordPress excerpts are the whole opening section
_UPLOAD = re.compile(r"/wp-content/uploads/(\d{4}/\d{2}/[^/?#]+)")
_SIZE_SUFFIX = re.compile(r"-\d+x\d+(?=\.[A-Za-z0-9]+$)")
_EMBED_BLOCK = re.compile(r"<!-- wp:embed (\{.*?\}) -->.*?<!-- /wp:embed -->", re.S)
_BLOCK_COMMENT = re.compile(r"<!--\s*/?wp:.*?-->\s*?\n?", re.S)
_DYNAMIC_BLOCK = re.compile(
    r"(?:<!-- wp:heading[^>]*-->\s*<h\d[^>]*>[^<]*</h\d>\s*<!-- /wp:heading -->\s*)?"
    r"<!-- wp:a8c/[a-z-]+ \{.*?\} /-->", re.S)


def slugify(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def prepare_html(raw: str) -> tuple[str, int, list[str]]:
    """Strip WP block comments; turn YouTube `wp:embed` blocks into canonical figures.

    Returns `(html, youtube_embed_count, dropped_dynamic_blocks)`.
    """
    dropped = [re.search(r"wp:a8c/[a-z-]+", m[0])[0] for m in _DYNAMIC_BLOCK.finditer(raw)]
    raw = _DYNAMIC_BLOCK.sub("", raw)
    count = 0

    def embed(match: re.Match[str]) -> str:
        nonlocal count
        url = html.unescape(json.loads(match[1]).get("url", ""))
        found = youtube.parse(url)
        if found is None:
            return match[0]
        count += 1
        vertical = "wp-embed-aspect-9-16" in match[1] or found[1] == "short"
        cls = "wp-block-embed is-provider-youtube" + (" wp-embed-aspect-9-16" if vertical else "")
        return (f'<figure class="{cls}"><div class="wp-block-embed__wrapper">\n'
                f"{url}\n</div></figure>")

    raw = _EMBED_BLOCK.sub(embed, raw)
    raw = _BLOCK_COMMENT.sub("", raw)
    iframes = len(BeautifulSoup(raw, "html.parser").find_all("iframe"))
    return raw, count + iframes, dropped


def summary_of(html_text: str, excerpt: str) -> str:
    """The excerpt (cut at 60 words), else the first 30 words of the paragraph text, cut at a word boundary."""
    if excerpt.strip():
        words = BeautifulSoup(html.unescape(excerpt), "html.parser").get_text().split()
        return " ".join(words[:EXCERPT_WORDS]) + ("…" if len(words) > EXCERPT_WORDS else "")
    soup = BeautifulSoup(html_text, "html.parser")
    for junk in soup.find_all(["table", "figure", "iframe", "ol", "sup"]):
        junk.decompose()
    for nested in soup.select("li ul"):  # their text would otherwise repeat inside the parent li
        nested.decompose()
    # No separator inside a block (`<a>x</a>,` must stay "x,"); a space between blocks.
    words = " ".join(b.get_text() for b in soup.find_all(["p", "li"])).split()
    words = [w for w in words if not youtube.parse(w)]
    text = " ".join(words[:SUMMARY_WORDS])
    return text + ("…" if len(words) > SUMMARY_WORDS else "")


def dump_front_matter(fields: dict[str, object]) -> str:
    """One `yaml.safe_dump` per key so the order is fixed and every string is safely quoted."""
    parts = [yaml.safe_dump({k: v}, allow_unicode=True, width=10_000, default_flow_style=False)
             for k, v in fields.items()]
    return "---\n" + "".join(parts) + "---\n"


@dataclass
class MediaResolver:
    """Maps WordPress upload URLs to optimised files in the media repo."""

    tar_files: dict[str, Path]
    media_dir: Path
    used: dict[str, Path] = field(default_factory=dict)  # tar key -> written file
    missing: list[str] = field(default_factory=list)
    redirects: dict[str, str] = field(default_factory=dict)  # old /wp-content/... path/ -> new URL

    def key_for(self, url: str) -> str | None:
        match = _UPLOAD.search(urlparse(url).path)
        if match is None:
            return None
        key = match[1]
        if key in self.tar_files:
            return key
        stripped = _SIZE_SUFFIX.sub("", key)
        return stripped if stripped in self.tar_files else None

    def resolve(self, url: str, subdir: str | None = None) -> str | None:
        """Optimise the referenced upload (once) and return its `/media/...` URL, or None."""
        key = self.key_for(url)
        if key is None:
            return None
        if key not in self.used:
            year_month, name = key.rsplit("/", 1)
            target = subdir or f"blog/{year_month}"
            out = media.optimise(self.tar_files[key], self.media_dir / target, slugify(Path(name).stem))
            clash = [k for k, v in self.used.items() if v == out]
            if clash:
                raise ValueError(f"media name clash: {key} and {clash[0]} both become {out}")
            self.used[key] = out
        new = "/media/" + self.used[key].relative_to(self.media_dir).as_posix()
        # Directory-style stubs: GitHub Pages serves a file by extension, so an HTML stub named
        # image.png would be sent as image/png. `image.png/index.html` is a page.
        self.redirects[f"/wp-content/uploads/{key}/"] = new
        self.redirects[f"/wp-content/uploads/{_UPLOAD.search(urlparse(url).path)[1]}/"] = new
        return new

    def image_map(self, src: str) -> str:
        found = self.resolve(src)
        if found is None:
            self.missing.append(src)
            return src
        return found


@dataclass
class Report:
    rows: list[dict] = field(default_factory=list)
    unmapped_links: list[tuple[str, str]] = field(default_factory=list)
    dropped: list[tuple[str, str]] = field(default_factory=list)

    @property
    def mismatches(self) -> int:
        return sum(1 for r in self.rows if r["images"][0] != r["images"][1] or r["embeds"][0] != r["embeds"][1])


def _text(item: ET.Element, tag: str) -> str:
    return item.findtext(tag, default="", namespaces=NS) or ""


def _meta(item: ET.Element, key: str) -> str | None:
    for meta in item.findall("wp:postmeta", NS):
        if _text(meta, "wp:meta_key") == key:
            return _text(meta, "wp:meta_value")
    return None


def _internal_path(url: str) -> str | None:
    """The site-relative path of an andbible.org / wordpress.com URL, or None for other hosts."""
    parsed = urlparse(url)
    if parsed.scheme in {"http", "https"} and parsed.netloc.lower() in SITE_HOSTS:
        return (parsed.path or "/") + (f"?{parsed.query}" if parsed.query else "") + (
            f"#{parsed.fragment}" if parsed.fragment else "")
    return None


def _convert(item: ET.Element, resolver: MediaResolver, label: str, report: Report,
             internal: list[tuple[str, str]]) -> tuple[str, str, dict]:
    """`(markdown, prepared_html, counts)` for one WordPress item."""
    cleaned, embeds_src, dropped = prepare_html(_text(item, "content:encoded"))
    report.dropped += [(label, name) for name in dropped]
    images_src = len(BeautifulSoup(cleaned, "html.parser").find_all("img"))
    missing_before = len(resolver.missing)

    def link_map(url: str) -> str:
        path = _internal_path(url)
        if path is None:
            return url
        if _UPLOAD.search(path):
            return resolver.resolve(url, "flyers" if path.lower().endswith(".pdf") else None) or url
        internal.append((label, path))
        return path

    md = html2md.convert(cleaned, link_map, resolver.image_map)
    counts = {
        "images": (images_src, md.count("![")),
        "embeds": (embeds_src, len(youtube.expand_lines(md)[1])),
        "unmapped_images": resolver.missing[missing_before:],
    }
    return md, cleaned, counts


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _url_path(link: str) -> str:
    return urlparse(link).path


def run(wxr: Path, media_tar: Path, content: Path, media_dir: Path, fixture: Path, report_path: Path,
        fetch_thumbnails: bool = True, data: Path = paths.DATA) -> int:
    root = ET.parse(wxr).getroot()
    items = root.findall("channel/item")
    attachments = {_text(i, "wp:post_id"): i for i in items if _text(i, "wp:post_type") == "attachment"}
    resolver = MediaResolver(media.from_tar(media_tar), media_dir)
    report = Report()
    internal: list[tuple[str, str]] = []
    known: set[str] = {"/", "/blog/"}
    fixture_paths: set[str] = {"/blog/", "/feed/", "/about/", "/privacy.html", "/terms.html"}

    posts = [i for i in items if _text(i, "wp:post_type") == "post" and _text(i, "wp:status") == "publish"]
    blog_dir = content / "en" / "blog"
    for stale in blog_dir.glob("*.md"):
        stale.unlink()
    taxonomy: dict[str, int] = {}  # archive base URL -> post count
    for item in posts:
        slug = _text(item, "wp:post_name")
        date = _text(item, "wp:post_date")[:10]
        link_path = _url_path(_text(item, "link"))
        match = re.fullmatch(rf"/(\d{{4}})/(\d{{2}})/(\d{{2}})/{re.escape(slug)}/", link_path)
        if match is None:
            raise ValueError(f"{slug}: unexpected permalink {link_path}")
        url_date = "-".join(match.groups())
        title = html.unescape(_text(item, "title")).strip()
        md, cleaned, counts = _convert(item, resolver, f"post:{slug}", report, internal)
        terms = [(c.get("domain"), html.unescape(c.text or ""), c.get("nicename") or taxonomy_slug(c.text or ""))
                 for c in item.findall("category")]
        terms = [t for t in terms if t[1] != "Uncategorized"]
        cats = [n for d, n, _ in terms if d == "category"]
        tagl = [n for d, n, _ in terms if d == "post_tag"]
        front: dict[str, object] = {"title": title, "date": date, "slug": slug}
        if url_date != date:
            front["url_date"] = url_date
        front["summary"] = summary_of(cleaned, _text(item, "excerpt:encoded")) or title
        if cats:
            front["categories"] = cats
        if tagl:
            front["tags"] = tagl
        thumb = attachments.get(_meta(item, "_thumbnail_id") or "")
        if thumb is not None:
            image = resolver.resolve(_text(thumb, "wp:attachment_url"))
            if image is None:
                counts["unmapped_images"].append(_text(thumb, "wp:attachment_url"))
            else:
                front["image"] = image.removeprefix("/media/")
                front["image_alt"] = (_meta(thumb, "_wp_attachment_image_alt") or "").strip() or title
        _write(blog_dir / f"{date}-{slug}.md", dump_front_matter(front) + "\n" + md)
        report.rows.append({"name": f"{date}-{slug}", **counts})
        known.add(link_path)
        fixture_paths.add(link_path)
        for d, _, nicename in terms:
            base = f"/{'tag' if d == 'post_tag' else 'category'}/{nicename}/"
            taxonomy[base] = taxonomy.get(base, 0) + 1
        year, month, day = match.groups()
        fixture_paths.update({f"/{year}/", f"/{year}/{month}/", f"/{year}/{month}/{day}/"})

    n_pages = 0
    pages_dir = content / "en" / "pages"
    for item in items:
        if _text(item, "wp:post_type") != "page" or _text(item, "wp:status") != "publish":
            continue
        slug = _text(item, "wp:post_name")
        if slug != "failed-orders":
            fixture_paths.add(_url_path(_text(item, "link")))
        if slug in SKIPPED_PAGES:
            continue
        if slug not in PAGES:
            raise ValueError(f"page {slug!r} is neither imported nor explicitly skipped")
        md, _, counts = _convert(item, resolver, f"page:{slug}", report, internal)
        title = html.unescape(_text(item, "title")).strip()
        _write(pages_dir / f"{PAGES[slug]}.md", dump_front_matter({"title": title}) + "\n" + md)
        report.rows.append({"name": f"page {PAGES[slug]}", **counts})
        known.add(f"/{PAGES[slug]}/")
        n_pages += 1

    for base, count in taxonomy.items():
        fixture_paths.add(base)
        fixture_paths.update(f"{base}page/{n_}/" for n_ in range(2, math.ceil(count / PAGE_SIZE) + 1))
    fixture_paths.update(f"/blog/page/{n}/" for n in range(2, math.ceil(len(posts) / PAGE_SIZE) + 1))
    known |= fixture_paths
    redirects = yaml.safe_load((data / "redirects.yaml").read_text(encoding="utf-8")) or {}
    known |= {k for k in redirects if k != "pending"}

    def is_known(path: str) -> bool:
        bare = path.split("#")[0].split("?")[0]
        return bare in known or bare.rstrip("/") + "/" in known  # GitHub Pages adds the slash

    report.unmapped_links = sorted({(who, p) for who, p in internal if not is_known(p)})

    _write(fixture, "\n".join(sorted(fixture_paths)) + "\n")
    _write(data / UPLOAD_REDIRECTS, "# Generated by sitegen.migrate.wxr: old WordPress upload URLs -> media repo files.\n"
           + yaml.safe_dump(dict(sorted(resolver.redirects.items())), width=10_000))
    thumbs = (0, 0)
    if fetch_thumbnails:
        ids = thumbnails.referenced_ids(content, data / "videos.yaml")
        thumbnails.fetch(sorted(ids), media_dir)
        thumbs = (len(ids), sum((media_dir / "videos" / f"{i}.webp").is_file() for i in ids))
    _write(report_path, render_report(report, len(posts), n_pages, len(resolver.used), thumbs))
    print(f"{len(posts)} posts, {n_pages} pages, {len(resolver.used)} media files, "
          f"{report.mismatches} count mismatches, {len(report.unmapped_links)} unmapped links")
    return 1 if report.mismatches else 0


def render_report(report: Report, posts: int, pages: int, media_files: int, thumbs: tuple[int, int]) -> str:
    out = ["# WordPress migration report", "",
           f"{posts} posts, {pages} pages, {media_files} media files written. "
           f"Thumbnails: referenced {thumbs[0]}, present {thumbs[1]}. {report.mismatches} count mismatches.", "",
           "| Item | Images (source/converted) | Embeds (source/converted) | Unmapped images |",
           "|---|---|---|---|"]
    for r in report.rows:
        flag = "" if r["images"][0] == r["images"][1] and r["embeds"][0] == r["embeds"][1] else " **MISMATCH**"
        unmapped = ", ".join(r["unmapped_images"]) or "-"
        out.append(f"| {r['name']}{flag} | {r['images'][0]}/{r['images'][1]} | "
                   f"{r['embeds'][0]}/{r['embeds'][1]} | {unmapped} |")
    out += ["", "## Unmapped internal links", ""]
    out += [f"- {who}: `{path}`" for who, path in report.unmapped_links] or ["None."]
    out += ["", "## Dropped dynamic blocks", ""]
    out += [f"- {who}: `{name}`" for who, name in report.dropped] or ["None."]
    return "\n".join(out) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wxr", type=Path, required=True)
    parser.add_argument("--media-tar", type=Path, required=True)
    parser.add_argument("--content", type=Path, default=paths.CONTENT)
    parser.add_argument("--media", type=Path, default=paths.MEDIA)
    parser.add_argument("--fixture", type=Path, default=paths.WEBSITE / "tests" / "fixtures" / "wp_urls.txt")
    parser.add_argument("--report", type=Path, default=paths.WEBSITE / "migration-report.md")
    parser.add_argument("--no-thumbnails", action="store_true")
    args = parser.parse_args(argv)
    return run(args.wxr, args.media_tar, args.content, args.media, args.fixture, args.report,
               fetch_thumbnails=not args.no_thumbnails)


if __name__ == "__main__":
    sys.exit(main())
