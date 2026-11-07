import re

import pytest

from sitegen import paths
from sitegen.docs import build_docs


@pytest.fixture(scope="module")
def built_docs(tmp_path_factory):
    out = tmp_path_factory.mktemp("docs-out")
    build_docs(paths.CONTENT, out, {})
    return out


def test_platform_differences_page_is_built(built_docs):
    page = built_docs / "docs" / "platform_differences" / "index.html"
    assert page.is_file()
    html = page.read_text()
    for anchor in ("sync", "reading-and-display", "files-and-sharing",
                   "notifications-and-background-work", "install-and-updates"):
        assert f'id="{anchor}"' in html, anchor
    for word in ("iCloud Drive", "Google Drive", "Nextcloud", "WebDAV"):
        assert word in html, word


def _page(built_docs, name):
    path = built_docs / "docs" / (name if name != "index" else "") / "index.html"
    return path.read_text()


def test_getting_started_has_an_install_section_per_platform(built_docs):
    html = _page(built_docs, "getting_started")
    for anchor in ("android", "iphone-and-ipad", "mac", "windows", "linux",
                   "installation", "linux-setup", "windows-11-setup",
                   "mirroring-and-using-app-on-any-computer"):
        assert f'id="{anchor}"' in html, anchor


def test_getting_started_states_the_install_facts(built_docs):
    html = _page(built_docs, "getting_started")
    for word in ("iOS 16", "Apple Silicon", "releases/latest", ".msi", ".deb",
                 "AppImage", "chmod +x", "SmartScreen"):
        assert word in html, word
    assert "Waydroid" not in html


def test_docs_home_is_not_android_only(built_docs):
    html = _page(built_docs, "index")
    assert "Android app" not in html
    assert "Windows" in html and "iPhone" in html


def test_desktop_page_is_not_beta(built_docs):
    html = _page(built_docs, "desktop")
    h1 = re.search(r"<h1[^>]*>(.*?)</h1>", html, re.S).group(1)
    title = re.search(r"<title>(.*?)</title>", html, re.S).group(1)
    assert "beta" not in (h1 + title).lower()
    assert "beta" not in html.lower()
    for word in ("Mac", "App Store", "releases/latest", 'id="known-limitations"', 'id="reporting-a-bug"'):
        assert word in html, word
    assert "Flathub" not in html and "winget" not in html


def test_desktop_beta_page_is_gone_and_url_redirects(built_docs):
    assert not (built_docs / "docs" / "desktop-beta" / "index.html").exists()
    from sitegen import redirects
    mapping = redirects.load(paths.DATA / "redirects.yaml")
    assert mapping["/docs/desktop-beta/"] == "/docs/desktop/"
