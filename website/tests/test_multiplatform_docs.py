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
