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


def test_phone_images_follow_effective_theme(content, tmp_path):
    out = tmp_path / "out"
    build(content, out, data=tmp_path / "data", docs=False)
    html = (out / "index.html").read_text()
    assert "<picture" not in html
    assert 'class="getapp__phone getapp__phone--light"' in html
    assert 'class="getapp__phone getapp__phone--dark"' in html
    css = (Path(__file__).resolve().parents[1] / "assets" / "css" / "site.css").read_text()
    assert ".getapp__phone--dark { display: none; }" in css
    assert ':root[data-theme="dark"] .getapp__phone--dark { display: block; }' in css
    assert ':root[data-theme="dark"] .getapp__phone--light { display: none; }' in css
    assert ':root:not([data-theme="light"]) .getapp__phone--dark { display: block; }' in css
    assert ':root:not([data-theme="light"]) .getapp__phone--light { display: none; }' in css


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
