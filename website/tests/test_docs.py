import re
import tomllib
import json
from pathlib import Path

import pytest

from sitegen import paths
from sitegen.docs import UNPUBLISHED, build_docs, published_pages, stage


def test_nav_whitelist_covers_every_docs_page():
    pages = set(published_pages(paths.WEBSITE / "zensical.toml"))
    on_disk = {p.name for p in (paths.CONTENT / "en" / "docs").glob("*.md")}
    on_disk |= {p.relative_to(paths.CONTENT / "en" / "docs").as_posix()
                for p in (paths.CONTENT / "en" / "docs").glob("*/*.md")}
    assert on_disk - pages - UNPUBLISHED == set(), "add new docs pages to zensical.toml nav"
    assert pages - on_disk == set(), "nav names a page that does not exist"


def _media(tmp_path, *ids):
    """A media dir holding a thumbnail for each video id."""
    media = tmp_path / "media"
    (media / "videos").mkdir(parents=True, exist_ok=True)
    for vid in ids:
        (media / "videos" / f"{vid}.webp").write_bytes(b"x")
    return media


def _one_page(tmp_path, text):
    content = tmp_path / "content"
    (content / "en" / "docs").mkdir(parents=True, exist_ok=True)
    (content / "en" / "docs" / "a.md").write_text(text)
    return content


def test_stage_fails_on_a_missing_embed_thumbnail(tmp_path):
    content = _one_page(tmp_path, "# A\n\nhttps://youtu.be/missingVID1\n")
    with pytest.raises(ValueError, match=r"a\.md.*missingVID1"):
        stage(content, "en", tmp_path / "stage", ["a.md"], {}, media_dir=_media(tmp_path))


def test_stage_fails_on_a_missing_related_video_thumbnail(tmp_path):
    content = _one_page(tmp_path, "# A\n")
    with pytest.raises(ValueError, match=r"a\.md.*relatedVID12"):
        stage(content, "en", tmp_path / "stage", ["a.md"], {"a": [("relatedVID12", "T")]},
              media_dir=_media(tmp_path))


def test_stage_falls_back_to_english_and_adds_related_videos(tmp_path):
    content = tmp_path / "content"
    (content / "en" / "docs").mkdir(parents=True)
    (content / "en" / "docs" / "a.md").write_text("# A\n\nEnglish\n")
    (content / "en" / "docs" / "b.md").write_text("# B\n\nhttps://youtu.be/abcDEF12345\n")
    (content / "fi" / "docs").mkdir(parents=True)
    (content / "fi" / "docs" / "a.md").write_text("# A\n\nSuomi\n")
    stage(content, "fi", tmp_path / "stage", ["a.md", "b.md"],
          {"a": [("zzzzzzzzzzz", "Intro video")]}, media_dir=_media(tmp_path, "zzzzzzzzzzz", "abcDEF12345"))
    a = (tmp_path / "stage" / "a.md").read_text()
    assert "Suomi" in a and "## Related videos" in a and 'data-yt-id="zzzzzzzzzzz"' in a
    b = (tmp_path / "stage" / "b.md").read_text()
    assert 'data-yt-id="abcDEF12345"' in b


def test_build_docs_produces_directory_urls(tmp_path):
    out = tmp_path / "out"
    build_docs(paths.CONTENT, out, {})
    assert (out / "docs" / "index.html").is_file()
    assert (out / "docs" / "getting_started" / "index.html").is_file()
    html = (out / "docs" / "getting_started" / "index.html").read_text()
    anchor = json.loads((Path(__file__).parent / "fixtures" / "rtd_anchors.json").read_text())["getting_started"][1]
    assert f'id="{anchor}"' in html


@pytest.fixture(scope="module")
def built_docs(tmp_path_factory):
    out = tmp_path_factory.mktemp("docs-out")
    build_docs(paths.CONTENT, out, {})
    return out


def test_docs_header_matches_the_landing_topbar(built_docs):
    from sitegen.i18n import strings
    site = strings(paths.CONTENT, "en")
    html = (built_docs / "docs" / "ai" / "index.html").read_text()
    header = html[html.index('<header'):html.index('</header>')]
    assert re.search(r'class="ab-brand" href="/"[^>]*>.*?<span>AndBible</span>', header, re.S)
    nav = header[header.index('class="ab-nav"'):]
    plain = re.sub(r'<span class="ab-github-facts">.*?</span></span>', "", nav, flags=re.S)  # badges are asserted below
    links = [(href, re.sub(r"<[^>]+>", "", label).strip())
             for href, label in re.findall(r'<a href="([^"]+)">(.*?)</a>', plain, re.S)]
    n = site["nav"]
    assert links[:5] == [("/blog/", n["blog"]), ("/docs/", n["docs"]), ("/videos/", n["videos"]),
                         (site["sections"]["support_url"], n["support"]), (site["footer"]["source_url"], n["github"])]
    assert "data-theme-toggle" in header and "data-md-component=\"search\"" in header
    assert nav.count("<svg") >= 5 and "ab-github-fact" in nav  # icons and the stars/licence badges
    assert 'data-md-component="palette"' not in html and 'data-md-component="source"' not in html


def test_docs_pages_apply_the_site_theme_before_first_paint(built_docs):
    html = (built_docs / "docs" / "ai" / "index.html").read_text()
    head = html[:html.index("</head>")]
    assert "andbible-docs-theme.js" in head
    assert (built_docs / "docs" / "assets" / "andbible-docs-theme.js").is_file()
    assert "andbible-theme" in (built_docs / "docs" / "assets" / "andbible-docs-theme.js").read_text()


BADGES = ("google-play", "f-droid", "amazon", "obtainium")


def test_install_badges_share_one_row_wrapper(built_docs):
    html = (built_docs / "docs" / "getting_started" / "index.html").read_text()
    wrapper = re.search(r'<div class="ab-badges">(.*?)</div>', html, re.S)
    assert wrapper, "install badges must sit in <div class=\"ab-badges\">"
    for name in BADGES:
        assert f"images/{name}-badge.png" in wrapper.group(1)
    assert "height=" not in wrapper.group(1), "height comes from the stylesheet, not per-image attributes"


def test_badge_images_have_one_aspect_ratio():
    from PIL import Image

    images = paths.CONTENT / "en" / "docs" / "images"
    ratios = [Image.open(images / f"{n}-badge.png").size for n in BADGES]
    assert max(w / h for w, h in ratios) - min(w / h for w, h in ratios) < 0.2, ratios
    assert len({h for _, h in ratios}) == 1, ratios


def _rule(css: str, selector: str) -> str:
    match = re.search(re.escape(selector) + r"\s*\{([^}]*)\}", css)
    assert match, f"no CSS rule for {selector}"
    return match.group(1)


def test_badge_css_keeps_one_row_with_equal_heights():
    css = (paths.WEBSITE / "theme" / "assets" / "andbible-docs.css").read_text()
    row = _rule(css, ".md-typeset .ab-badges")
    assert "display: flex" in row and "flex-wrap: nowrap" in row
    link = _rule(css, ".md-typeset .ab-badges a")
    assert "min-width: 0" in link and re.search(r"flex:\s*0 1 auto", link)
    img = _rule(css, ".md-typeset .ab-badges img")
    assert "height: auto" in img and "max-height: 50px" in img and "max-width: 100%" in img


def test_docs_theme_defines_every_token_the_embeds_use():
    css = (paths.WEBSITE / "theme" / "assets" / "andbible-docs.css").read_text()
    used = set(re.findall(r"var\(--([\w-]+)\)", (paths.ASSETS / "css" / "embeds.css").read_text()))
    assert used == {"radius", "video-bg", "video-fg"}
    block = re.search(r'\[data-md-color-scheme="default"\],\s*\[data-md-color-scheme="slate"\]\s*\{([^}]*)\}', css)
    assert block, "tokens must be defined for both colour schemes"
    for token in used:
        assert f"--{token}:" in block.group(1)


def test_related_videos_skip_ids_the_page_already_embeds(tmp_path):
    content = tmp_path / "content"
    (content / "en" / "docs").mkdir(parents=True)
    (content / "en" / "docs" / "a.md").write_text("# A\n\nhttps://youtu.be/abcDEF12345\n")
    (content / "en" / "docs" / "b.md").write_text("# B\n\nhttps://youtu.be/abcDEF12345\n\ntext\n")
    stage(content, "en", tmp_path / "stage", ["a.md", "b.md"],
          {"a": [("abcDEF12345", "Same"), ("otherVID1234", "Other")], "b": [("abcDEF12345", "Same")]},
          media_dir=_media(tmp_path, "abcDEF12345", "otherVID1234"))
    a = (tmp_path / "stage" / "a.md").read_text()
    assert a.count('data-yt-id="abcDEF12345"') == 1 and 'data-yt-id="otherVID1234"' in a
    b = (tmp_path / "stage" / "b.md").read_text()
    assert "Related videos" not in b and b.count("data-yt-id") == 1


def test_built_docs_never_embed_a_video_twice_on_one_page():
    site = paths.SITE / "docs"
    if not site.is_dir():
        pytest.skip("run `make site` first")
    for page in site.rglob("index.html"):
        ids = re.findall(r'data-yt-id="([^"]+)"', page.read_text(encoding="utf-8"))
        assert len(ids) == len(set(ids)), f"{page.relative_to(site)} embeds a video twice"


def _config(base=None):
    from sitegen.docs import _language_config
    base = base if base is not None else (paths.WEBSITE / "zensical.toml").read_text()
    config_dir = paths.BUILD / "en"
    return _language_config(base, "en", config_dir / "docs", config_dir / "site", {
        "site_name": "AndBible",
        "nav": {"theme_toggle": "t", "blog": "b", "docs": "d", "videos": "v", "support": "s", "github": "g",
                "github_stars": "gs", "github_license": "gl"},
        "sections": {"support_url": "https://x"}, "footer": {"source_url": "https://y", "privacy": "p"},
        "consent": {"text": "c", "accept": "a", "decline": "d"}})


def test_language_config_applies_every_override():
    config = tomllib.loads(_config())
    assert config["project"]["docs_dir"] == "docs" and config["project"]["site_dir"] == "site"
    assert config["project"]["site_url"] == "https://andbible.org/docs/"
    assert config["project"]["theme"]["language"] == "en"
    assert config["project"]["extra"]["site"]["brand"] == "AndBible"


def test_language_config_carries_asset_hashes_for_the_theme():
    from sitegen.home import asset_hashes
    assets = tomllib.loads(_config())["project"]["extra"]["assets"]
    assert {k: assets[k] for k in ("embeds_css", "lite_yt_js", "analytics_js")} == {
        "embeds_css": asset_hashes()["css/embeds.css"], "lite_yt_js": asset_hashes()["js/lite-yt.js"],
        "analytics_js": asset_hashes()["js/analytics.js"]}
    assert re.fullmatch(r"[0-9a-f]{8}", assets["docs_css"])


@pytest.mark.parametrize("drop, missing", [
    ("docs_dir", "docs_dir"), ("site_dir", "site_dir"), ("site_url", "site_url"),
    ("[project.theme]", r"\[project\.theme\]"), ("[project.extra]", r"\[project\.extra\]"),
])
def test_language_config_fails_when_an_override_cannot_be_applied(drop, missing):
    base = (paths.WEBSITE / "zensical.toml").read_text()
    broken = "\n".join(line for line in base.splitlines() if not line.strip().startswith(drop))
    with pytest.raises(ValueError, match=missing):
        _config(broken)


def test_docs_pages_load_embed_assets_with_content_hashes(built_docs):
    from sitegen.home import asset_hashes
    html = (built_docs / "docs" / "ai" / "index.html").read_text()
    assert f'/assets/css/embeds.css?v={asset_hashes()["css/embeds.css"]}"' in html
    assert f'/assets/js/lite-yt.js?v={asset_hashes()["js/lite-yt.js"]}"' in html
    assert f'/assets/js/analytics.js?v={asset_hashes()["js/analytics.js"]}"' in html
