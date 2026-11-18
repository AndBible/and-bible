import re
from collections import Counter
from pathlib import Path

import pytest

from sitegen import paths

CATALOG = paths.REPO / "sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/DocsLink.kt"
DOCS_DIR = CATALOG.parent
# UserGuideToc is generated from docs/tutorials.yaml; app_toc --check guards it.
# Its generator is migrated separately. Keep checking its targets against the catalog below.
GENERATED_TOC = DOCS_DIR / "UserGuideToc.kt"
ROOTS = [paths.REPO / module / "src" for module in
         ("app", "sharedCore", "sharedLogic", "sharedUi", "desktopApp")]
ENTRY = re.compile(r'^    ([A-Z][A-Z0-9_]*)\("([a-z0-9_/.-]+)"(?:, "([a-z0-9_-]+)")?\)[,;]$', re.MULTILINE)
CALL = re.compile(r'DocsLinks\s*\.\s*page\s*\(')
LITERAL_CALL = re.compile(r'DocsLinks\.page\(\s*"([a-z0-9_/.-]+)"\s*(?:,\s*"([a-z0-9_-]+)"\s*)?\)')
CATALOG_CALL = re.compile(r'DocsLinks\s*\.\s*page\s*\(\s*DocsLink\.[A-Z][A-Z0-9_]*\s*\)')


def scan(catalog=CATALOG):
    """Read literal enum entries only; unexpected entry syntax fails closed."""
    text = catalog.read_text(encoding="utf-8")
    entries = list(ENTRY.finditer(text))
    declarations = re.findall(r'^    [A-Z][A-Z0-9_]*\s*\(', text, re.MULTILINE)
    assert entries and len(entries) == len(declarations), f"{catalog}: unscannable catalog entry"
    assert len({m[1] for m in entries}) == len(entries), f"{catalog}: duplicate entry name"
    return [(catalog, text.count("\n", 0, m.start()) + 1, m[2], m[3]) for m in entries]


def uncataloged_calls(roots):
    problems = []
    for root in roots:
        for kt in sorted(root.rglob("*.kt")):
            if kt in (CATALOG, DOCS_DIR / "DocsLinks.kt", GENERATED_TOC):
                continue
            text = kt.read_text(encoding="utf-8")
            checked = {m.start() for m in CATALOG_CALL.finditer(text)}
            for match in CALL.finditer(text):
                if match.start() not in checked:
                    number = text.count("\n", 0, match.start()) + 1
                    problems.append(f"{kt}:{number}: use a DocsLink catalog entry")
    return problems


def missing(site, links):
    problems = []
    for kt, number, page, anchor in links:
        html = site / "docs" / page / "index.html"
        if not html.is_file():
            problems.append(f"{kt}:{number}: no docs page {page!r}")
        elif anchor and f'id="{anchor}"' not in html.read_text(encoding="utf-8"):
            problems.append(f"{kt}:{number}: /docs/{page}/ has no #{anchor}")
    return problems


def test_catalog_preserves_original_urls():
    baseline = Path(__file__).parent / "fixtures/app_docs_links_before_catalog.tsv"
    old = [line.split("\t")[2] for line in baseline.read_text().splitlines() if not line.startswith("#")]
    current = [f"https://andbible.org/docs/{page}/" + (f"#{anchor}" if anchor else "")
               for _, _, page, anchor in scan()]
    assert Counter(current) == Counter(set(old))


def test_generated_toc_targets_are_cataloged():
    targets = {(page, anchor) for _, _, page, anchor in scan()}
    generated = LITERAL_CALL.findall(GENERATED_TOC.read_text(encoding="utf-8"))
    assert generated
    assert {(page, anchor or None) for page, anchor in generated} <= targets


def test_every_app_docs_link_exists():
    assert paths.SITE.is_dir(), "run `make site` first"
    assert missing(paths.SITE, scan()) == []


def test_no_hardcoded_docs_urls_left():
    stale = [f"{kt}:{n}" for root in ROOTS for kt in root.rglob("*.kt")
             if kt != DOCS_DIR / "DocsLinks.kt" and not any("test" in p.lower() for p in kt.relative_to(root).parts)
             for n, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1)
             if "docs.andbible.org" in line or "andbible.org/docs/" in line]
    assert stale == []


def test_no_uncataloged_docs_calls():
    assert uncataloged_calls(ROOTS) == []


def test_a_bad_anchor_is_reported(tmp_path):
    catalog = tmp_path / "DocsLink.kt"
    catalog.write_text('    BAD("getting_started", "no-such-heading");\n')
    problems = missing(paths.SITE, scan(catalog))
    assert problems == [f"{catalog}:1: /docs/getting_started/ has no #no-such-heading"]


def test_a_missing_page_is_reported(tmp_path):
    assert missing(tmp_path, [(CATALOG, 1, "removed", None)]) == [f"{CATALOG}:1: no docs page 'removed'"]


@pytest.mark.parametrize("source", [
    'val u = DocsLinks.page("ai")\n',
    'val u = DocsLinks.page(\n    "ai", "no-such")\n',
    'val u = DocsLinks.page(page = "ai", anchor = "no-such")\n',
    'val u = DocsLinks.page(name, "no-such")\n',
    'val u = DocsLinks.page("ai", "x$y")\n',
])
def test_an_uncataloged_call_is_reported(tmp_path, source):
    kt = tmp_path / "X.kt"
    kt.write_text(source)
    assert uncataloged_calls([tmp_path]) == [f"{kt}:1: use a DocsLink catalog entry"]


@pytest.mark.parametrize("entry", ['    BAD(page);', '    BAD("ai", anchor);'])
def test_an_unscannable_catalog_entry_is_reported(tmp_path, entry):
    catalog = tmp_path / "DocsLink.kt"
    catalog.write_text('    GOOD("ai"),\n' + entry)
    with pytest.raises(AssertionError, match="unscannable catalog entry"):
        scan(catalog)
