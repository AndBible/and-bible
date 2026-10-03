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


@pytest.fixture(scope="module")
def stub_site(tmp_path_factory):
    out = tmp_path_factory.mktemp("stubs")
    run(out)
    return out


def js_target(site, path, hash_=""):
    script = re.search(r"<script>(.*?)</script>", (site / "404.html").read_text(), re.DOTALL).group(1)
    driver = (f"var location={{pathname:{json.dumps(path)},hash:{json.dumps(hash_)},"
              "replace:function(u){console.log(u)}};") + script
    return subprocess.run(["node", "-e", driver], check=True, capture_output=True, text=True).stdout.strip()


EXTRA = ["en/stable/releases/", "en/stable/releases", "en/latest/releases/", "en/stable/customisation/",
         "en/stable/customisation/custom_css/", "en/stable/customisation/index.html", "en/v5.0/ai.html",
         "en/latest/AI.html", "en/latest/ai/", "en/latest/Customisation/Custom_CSS.html", "en/stable/unknown.html",
         "en/stable/nodir/index.html", "en/stable/", "en/latest/index.html"]


@pytest.mark.skipif(shutil.which("node") is None, reason="node not installed")
@pytest.mark.parametrize("line", FIXTURE.read_text().split() + EXTRA)
def test_404_script_matches_python_mapping(stub_site, line):
    old = re.sub(r"^en/[^/]+/", "", line)
    expected = "https://andbible.org" + new_path(old, legacy_pages())
    assert js_target(stub_site, "/" + line, "#x") == expected + "#x"


@pytest.mark.skipif(shutil.which("node") is None, reason="node not installed")
def test_404_non_docs_path_goes_to_root(stub_site):
    assert js_target(stub_site, "/something/else") == "https://andbible.org/docs/"


@pytest.mark.parametrize("old, new", [("releases/", "/docs/releases/"), ("AI.html", "/docs/ai/"),
                                      ("ai/", "/docs/ai/"), ("customisation/", "/docs/customisation/")])
def test_new_path_tolerates_case_and_trailing_slash(old, new):
    assert new_path(old, legacy_pages()) == new


def test_generate_keeps_git_and_removes_stale_files(tmp_path):
    (tmp_path / ".git").mkdir()
    (tmp_path / ".git" / "config").write_text("[core]\n")
    (tmp_path / "stale.html").write_text("old")
    (tmp_path / "en" / "old").mkdir(parents=True)
    (tmp_path / "en" / "old" / "x.html").write_text("old")
    run(tmp_path)
    assert (tmp_path / ".git" / "config").read_text() == "[core]\n"
    assert not (tmp_path / "stale.html").exists()
    assert not (tmp_path / "en" / "old").exists()
    assert (tmp_path / "CNAME").is_file()


def test_noindex_only_on_404_not_on_stubs(stub_site):
    assert "noindex" not in (stub_site / "en/latest/ai.html").read_text()
    assert "noindex" not in (stub_site / "index.html").read_text()
    assert 'content="noindex"' in (stub_site / "404.html").read_text()
