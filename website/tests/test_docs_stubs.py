import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

from sitegen.migrate.docs_stubs import legacy_pages, new_path

FIXTURE = Path(__file__).parent / "fixtures" / "rtd_urls.txt"


def run(out):
    subprocess.run([sys.executable, "-m", "sitegen.migrate.docs_stubs", "--out", str(out)], check=True,
                   cwd=Path(__file__).resolve().parents[1])


def test_every_legacy_url_has_a_stub_to_an_existing_page(tmp_path):
    from sitegen import paths
    run(tmp_path)
    for line in FIXTURE.read_text().split():
        stub = tmp_path / (line + "index.html" if line.endswith("/") else line)
        html = stub.read_text()
        target = re.search(r'url=(https://andbible\.org[^"]*)"', html).group(1)
        local = paths.SITE / target.removeprefix("https://andbible.org/") / "index.html"
        assert local.is_file(), f"{line} -> {target} does not exist"
        assert "location.hash" in html
        assert f'<link rel="canonical" href="{target}">' in html


def test_cname_readme_and_404(tmp_path):
    run(tmp_path)
    assert (tmp_path / "CNAME").read_text() == "docs.andbible.org\n"
    assert "location.pathname" in (tmp_path / "404.html").read_text()
    assert "AndBible/and-bible" in (tmp_path / "README.md").read_text()
    for index in ("index.html", "en/index.html", "en/latest/index.html"):
        assert 'url=https://andbible.org/docs/"' in (tmp_path / index).read_text()


@pytest.mark.parametrize("old, new", [
    ("ai.html", "/docs/ai/"),
    ("index.html", "/docs/"),
    ("", "/docs/"),
    ("customisation/index.html", "/docs/customisation/"),
    ("customisation/custom_css.html", "/docs/customisation/custom_css/"),
    ("releases/index.html", "/docs/releases/"),
    ("releases/release_5_0.html", "/docs/releases/release_5_0/"),
    ("no_such_page.html", "/docs/"),
    ("nodir/index.html", "/docs/"),
])
def test_new_path_mapping(old, new):
    assert new_path(old, legacy_pages()) == new


def test_old_app_deep_links_resolve(tmp_path):
    from sitegen import paths
    run(tmp_path)
    stub = (tmp_path / "en/latest/ai.html").read_text()
    assert "location.replace(" in stub and "+ location.hash" in stub
    target = re.search(r'location\.replace\("([^"]+)"', stub).group(1)
    assert target == "https://andbible.org/docs/ai/"
    assert (paths.SITE / "docs" / "ai" / "index.html").is_file()
    assert 'id="setting-permissions"' in (paths.SITE / "docs" / "ai" / "index.html").read_text()


@pytest.mark.skipif(shutil.which("node") is None, reason="node not installed")
@pytest.mark.parametrize("path, hash_, expected", [
    ("/en/stable/ai.html", "#setting-permissions", "https://andbible.org/docs/ai/#setting-permissions"),
    ("/en/latest/ai.html", "", "https://andbible.org/docs/ai/"),
    ("/en/v5.0/customisation/custom_css.html", "", "https://andbible.org/docs/customisation/custom_css/"),
    ("/en/stable/customisation/index.html", "", "https://andbible.org/docs/customisation/"),
    ("/en/stable/releases/release_5_0.html", "#x", "https://andbible.org/docs/releases/release_5_0/#x"),
    ("/en/stable/index.html", "", "https://andbible.org/docs/"),
    ("/en/stable/unknown.html", "#x", "https://andbible.org/docs/#x"),
    ("/en/stable/", "", "https://andbible.org/docs/"),
    ("/something/else", "", "https://andbible.org/docs/"),
])
def test_404_script_matches_python_mapping(tmp_path, path, hash_, expected):
    run(tmp_path)
    script = re.search(r"<script>(.*?)</script>", (tmp_path / "404.html").read_text(), re.DOTALL).group(1)
    driver = (f"var location={{pathname:{json.dumps(path)},hash:{json.dumps(hash_)},"
              "replace:function(u){console.log(u)}};") + script
    out = subprocess.run(["node", "-e", driver], check=True, capture_output=True, text=True).stdout.strip()
    assert out == expected
