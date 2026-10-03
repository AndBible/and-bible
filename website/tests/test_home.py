from pathlib import Path

import pytest

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
