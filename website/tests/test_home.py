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
    build(content, out)
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
