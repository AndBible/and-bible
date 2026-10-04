"""Guards for the Zensical partials the docs theme copies, and the theme key shared with the landing page."""

import re
from pathlib import Path

import zensical

from sitegen import paths

SHIPPED = Path(zensical.__file__).parent / "templates" / "partials"


def test_nav_copy_equals_shipped_nav_apart_from_the_drawer_links():
    ours = (paths.THEME / "partials" / "nav.html").read_text()
    ours = re.sub(r'  <div class="ab-drawer-links">.*?</div>\n', "", ours, flags=re.S)
    ours = re.sub(r"\A\{#-.*?-#\}\n", "", ours, flags=re.S)
    shipped = re.sub(r"\A\{#-.*?-#\}\n", "", (SHIPPED / "nav.html").read_text(), flags=re.S)
    assert ours == shipped, "Zensical changed partials/nav.html: re-diff theme/partials/nav.html"


def test_header_copy_still_matches_what_it_relies_on():
    shipped = (SHIPPED / "header.html").read_text()
    for needed in ('include "partials/search.html"', '.icons/', 'for="__drawer"', 'for="__search"',
                   'data-md-component="header"', "md-header__inner"):
        assert needed in shipped, f"shipped header.html lost {needed!r}: re-diff theme/partials/header.html"
    assert (SHIPPED / "search.html").is_file()


def _theme_constants(path: Path) -> tuple[str, set[str]]:
    text = path.read_text()
    key = re.search(r'var key = "([^"]+)"', text)
    assert key, f"{path}: no storage key"
    return key[1], set(re.findall(r'"(light|dark)"', text))


def test_landing_and_docs_scripts_share_the_storage_key_and_values():
    landing = _theme_constants(paths.WEBSITE / "assets" / "js" / "theme.js")
    docs = _theme_constants(paths.THEME / "assets" / "andbible-docs-theme.js")
    assert landing == docs == ("andbible-theme", {"light", "dark"})
