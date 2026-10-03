import re
from pathlib import Path

import pytest

from sitegen import paths
from sitegen.build import build
from sitegen.i18n import languages, prefix, resolve, strings

SITE_YAML = Path(__file__).resolve().parents[1] / "content" / "en" / "site.yaml"


@pytest.fixture
def content(tmp_path):
    en = tmp_path / "content" / "en"
    en.mkdir(parents=True)
    en.joinpath("site.yaml").write_text(SITE_YAML.read_text())
    return tmp_path / "content"


def test_languages_english_first(content):
    fi = content / "fi"
    fi.mkdir()
    fi.joinpath("site.yaml").write_text("hero:\n  eyebrow: Ilmainen\n")
    assert languages(content) == ["en", "fi"]
    assert prefix("en") == "" and prefix("fi") == "/fi"
    merged = strings(content, "fi")
    assert merged["hero"]["eyebrow"] == "Ilmainen"
    assert merged["hero"]["docs_button"] == "Read the docs"  # English fallback


def test_resolve_falls_back_to_english(content):
    (content / "en" / "docs").mkdir()
    (content / "en" / "docs" / "a.md").write_text("A")
    (content / "fi").mkdir()
    assert resolve(content, "fi", "docs/a.md") == content / "en" / "docs" / "a.md"
    with pytest.raises(FileNotFoundError):
        resolve(content, "fi", "docs/missing.md")


def test_home_page_structure(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert '<html lang="en"' in html
    assert 'class="hero__panel"' in html
    assert 'class="getapp"' in html
    for url in ["play.google.com", "f-droid.org", "apps.apple.com"]:
        assert url in html
    assert "<em>free</em>" in html
    assert 'href="/docs/"' in html and 'href="/videos/"' in html and 'href="/blog/"' in html
    assert 'rel="canonical" href="https://andbible.org/"' in html
    assert (out / "assets" / "css" / "site.css").is_file()
    assert (out / "CNAME").read_text() == "andbible.org\n"


def test_css_defines_both_themes():
    css = (Path(__file__).resolve().parents[1] / "assets" / "css" / "site.css").read_text()
    assert "--ground: #f7f1e3" in css
    assert "--ground: #161616" in css
    assert "prefers-color-scheme: dark" in css
    assert ':root[data-theme="dark"]' in css and ':root[data-theme="light"]' in css
    assert "prefers-reduced-motion" in css


CSS = (Path(__file__).resolve().parents[1] / "assets" / "css").glob("*.css")


def test_phone_shows_app_screens_in_order(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    shots = re.findall(r'<li class="phone__shot"><img src="(/assets/img/appshots/\d\d\.webp)\?v=', html)
    assert shots == [f"/assets/img/appshots/{n:02d}.webp" for n in range(1, len(shots) + 1)] and len(shots) >= 2
    assert 'data-appshots aria-hidden="true"' in html
    assert html.count('loading="lazy"') >= len(shots) - 1  # only the first screen is eager
    assert "js/appshots.js" in html
    for path in shots:
        assert (out / path.lstrip("/")).exists()


def test_appshots_crop_to_the_display(tmp_path):
    from PIL import Image

    from sitegen import appshots

    source = tmp_path / "src"
    source.mkdir()
    for number in appshots.SHOTS:
        Image.new("RGB", (900, 1600), (250, 140, 0)).save(source / f"{number}_en-US.jpeg")
    written = appshots.build(source, tmp_path / "out")
    assert [p.name for p in written] == [f"{n:02d}.webp" for n in range(1, len(appshots.SHOTS) + 1)]
    with Image.open(written[0]) as image:
        assert image.size == (appshots.WIDTH, round(1455 * appshots.WIDTH / 700))


def test_no_literal_colours_outside_token_blocks():
    import re

    literal = re.compile(r"#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(")
    for path in CSS:
        depth_root = False
        for n, line in enumerate(path.read_text().splitlines(), 1):
            stripped = line.strip()
            if re.match(r":root|@media \(prefers-color-scheme: dark\)", stripped):
                depth_root = True
            if not depth_root:
                assert not literal.search(line), f"{path.name}:{n}: literal colour {line!r}"
            if stripped == "}" and not line.startswith(" "):
                depth_root = False


def test_missing_asset_hash_fails_loudly():
    from sitegen.home import environment

    env = environment()
    assert env.from_string("{{ asset_url('css/site.css') }}").render().startswith("/assets/css/site.css?v=")
    with pytest.raises(KeyError):
        env.from_string("{{ asset_url('css/nope.css') }}").render()


def test_home_title_and_description_come_from_meta(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert "<title>AndBible: Free &amp; open source Bible study</title>" in html
    assert "Android and iOS, no ads, no tracking." in html  # meta description
    assert "Open source · No ads · No tracking" in html  # new eyebrow


def _luminance(hex_colour: str) -> float:
    channels = [int(hex_colour[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    r, g, b = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def _contrast(a: str, b: str) -> float:
    hi, lo = sorted((_luminance(a), _luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def test_current_page_marker_meets_wcag_aa_in_both_themes():
    css = (Path(__file__).resolve().parents[1] / "assets" / "css" / "site.css").read_text()
    rule = re.search(r"\.pager__num\[aria-current\]\s*\{([^}]*)\}", css).group(1)
    background = re.search(r"background:\s*var\(--([\w-]+)\)", rule).group(1)
    foreground = re.search(r"(?<![-\w])color:\s*var\(--([\w-]+)\)", rule).group(1)
    for block in (re.search(r':root, :root\[data-theme="light"\] \{(.*?)\n\}', css, re.S).group(1),
                  re.search(r':root\[data-theme="dark"\] \{(.*?)\n\}', css, re.S).group(1)):
        token = lambda name: re.search(rf"--{name}:\s*(#[0-9a-fA-F]{{6}})", block).group(1)
        assert _contrast(token(background), token(foreground)) >= 4.5


def test_footer_has_no_translate_link(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert "Translating-User-Interface" not in html and "Help translate" not in html
    assert not [k for k in strings(content, "en")["footer"] if "translate" in k]


def test_embeds_css_is_linked_with_its_own_content_hash_not_imported(content, tmp_path):
    from sitegen.home import asset_hashes
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert f'href="/assets/css/embeds.css?v={asset_hashes()["css/embeds.css"]}"' in html
    assert f'src="/assets/js/lite-yt.js?v={asset_hashes()["js/lite-yt.js"]}"' in html
    assert "@import" not in (paths.ASSETS / "css" / "site.css").read_text()


def test_phone_nav_is_visible_without_javascript():
    css = (paths.ASSETS / "css" / "site.css").read_text()
    block = re.search(r"@media \(max-width: 719px\) and \(scripting: none\) \{(.*?)\n\}", css, re.S)
    assert block, "no `scripting: none` rule: the phone menu stays hidden when JS is off"
    assert ".topbar nav { display: flex;" in block.group(1) and "[data-menu-toggle] { display: none; }" in block.group(1)


def test_store_entries_have_no_unused_icon_key(content):
    for lang in languages(content):
        for store in strings(content, lang)["getapp"]["stores"]:
            assert "icon" not in store, store


def test_font_licence_names_the_real_copyright_holders():
    notice = (paths.ASSETS / "fonts" / "OFL.txt").read_text(encoding="utf-8")
    serif = notice.split("== Inter ==")[0]
    assert "Adobe" in serif and "Reserved Font Name" in serif and "Google Inc." not in serif
    assert "The Inter Project Authors" in notice.split("== Inter ==")[1]


def _catalog(data, rows):
    data.mkdir(exist_ok=True)
    (data / "videos.yaml").write_text("".join(
        f'- {{id: "{vid}", title: "Title {vid}", topic: "Getting started", published: "{day}"{", short: true" if short else ""}}}\n'
        for vid, day, short in rows))


def test_home_teases_the_three_newest_videos_newest_first(content, tmp_path):
    _catalog(tmp_path / "data", [("oldest00001", "2024-01-01", False), ("third000003", "2025-03-01", False),
                                 ("newest00005", "2026-09-01", True), ("second00002", "2026-02-01", False),
                                 ("fourth00004", "2025-01-01", False)])
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    teaser = html.split('<div class="video-grid">')[1].split("</section>")[0]
    assert re.findall(r'data-yt-id="([^"]+)"', teaser) == ["newest00005", "second00002", "third000003"]
    assert 'class="yt yt--short yt--card"' in teaser and teaser.count("yt--card") == 3
    assert 'href="/videos/"' in teaser  # the catalog link stays below the cards
    assert "youtube-nocookie" not in html and "i.ytimg.com" not in html  # first-party until the click


def test_home_without_videos_has_no_empty_grid(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert "video-grid" not in html and 'href="/videos/"' in html


def test_home_teaser_shows_the_date_but_never_the_version(content, tmp_path):
    _catalog(tmp_path / "data", [("newest00005", "2026-09-01", True), ("second00002", "2026-02-01", False)])
    cat = tmp_path / "data" / "videos.yaml"
    cat.write_text(cat.read_text().replace('"2026-09-01"', '"2026-09-01", version: "5.1"'))
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    teaser = (out / "index.html").read_text().split('<div class="video-grid">')[1].split("</section>")[0]
    assert '<time datetime="2026-09-01">1 Sep 2026</time>' in teaser
    assert '<time datetime="2026-02-01">1 Feb 2026</time>' in teaser
    assert "v5.1" not in teaser and "·" not in teaser
    assert "yt__meta" in (out / "videos" / "index.html").read_text() and "v5.1 · " in (out / "videos" / "index.html").read_text()


def test_support_card_has_decorative_art(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert re.search(r'<img class="support__art" src="/assets/img/support-tree\.webp\?v=\w+" alt=""', html)
    assert (out / "assets/img/support-tree.webp").exists()
