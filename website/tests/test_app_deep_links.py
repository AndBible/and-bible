import re
from collections import Counter
from pathlib import Path

import pytest

from sitegen import paths

CATALOG = paths.REPO / "sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/DocsLink.kt"
DOCS_DIR = CATALOG.parent
GENERATED_TOC = DOCS_DIR / "UserGuideToc.kt"
BASE_URL_ALLOWLIST = {
    # Fallback for a help link with an unknown topic: the guide's home (#4159).
    paths.REPO / "sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/HelpLinks.kt",
    paths.REPO / "sharedUi/src/commonMain/kotlin/net/bible/sharedui/app/ports/DocumentDialogsCore.kt",
    paths.REPO / "sharedUi/src/commonMain/kotlin/net/bible/sharedui/help/HelpScreen.kt",
}
ROOTS = [paths.REPO / module / "src" for module in
         ("app", "sharedCore", "sharedLogic", "sharedUi", "desktopApp")]
ENTRY = re.compile(r'\b([A-Z][A-Z0-9_]*)\("([a-z0-9_/.-]+)"(?:, "([a-z0-9_-]+)")?\)[,;]')
CALL = re.compile(r'DocsLinks\s*\.\s*page\s*\(')
LITERAL_CALL = re.compile(r'DocsLinks\.page\(\s*"([a-z0-9_/.-]+)"\s*(?:,\s*"([a-z0-9_-]+)"\s*)?\)')
CATALOG_CALL = re.compile(r'DocsLinks\s*\.\s*page\s*\(\s*DocsLink\.[A-Z][A-Z0-9_]*\s*\)')


def scan(catalog=CATALOG):
    """Read literal enum entries only; unexpected entry syntax fails closed."""
    text = catalog.read_text(encoding="utf-8")
    entries = list(ENTRY.finditer(text))
    declarations = re.findall(r'\b[A-Z][A-Z0-9_]*\s*\(', text)
    assert entries and len(entries) == len(declarations), f"{catalog}: unscannable catalog entry"
    assert len({m[1] for m in entries}) == len(entries), f"{catalog}: duplicate entry name"
    return [(catalog, text.count("\n", 0, m.start()) + 1, m[2], m[3]) for m in entries]


def uncataloged_calls(roots):
    problems = []
    for root in roots:
        for kt in sorted(root.rglob("*.kt")):
            if kt in (CATALOG, DOCS_DIR / "DocsLinks.kt"):
                continue
            text = kt.read_text(encoding="utf-8")
            if not any("test" in part.lower() for part in kt.relative_to(root).parts):
                base_url_names = {"DocsLinks"}
                base_url_names.update(re.findall(
                    r"import\s+net\.bible\.sharedcore\.docs\.DocsLinks\s+as\s+(\w+)", text))
                direct_base_url_imports = re.findall(
                    r"import\s+net\.bible\.sharedcore\.docs\.DocsLinks\.BASE_URL(?:\s+as\s+(\w+))?", text)
                base_url_names.update(alias or "BASE_URL" for alias in direct_base_url_imports)
                uses_base_url = any(re.search(rf"\b{re.escape(name)}\s*\.\s*BASE_URL\b", text)
                                    for name in base_url_names if name != "BASE_URL")
                uses_base_url |= any(re.search(rf"\b{re.escape(alias or 'BASE_URL')}\b", text)
                                     for alias in direct_base_url_imports)
                if uses_base_url and kt not in BASE_URL_ALLOWLIST:
                    number = next(n for n, line in enumerate(text.splitlines(), 1)
                                  if re.search(r"BASE_URL|import .*DocsLinks", line))
                    problems.append(f"{kt}:{number}: use a DocsLink catalog entry")
            aliases = re.finditer(r'import\s+net\.bible\.sharedcore\.docs\.DocsLinks(?:\.page)?\s+as\s+\w+', text)
            for alias in aliases:
                number = text.count("\n", 0, alias.start()) + 1
                problems.append(f"{kt}:{number}: do not alias the docs URL builder")
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


# Intentional additions since the pre-catalog snapshot; never rewrite the historical fixture.
ADDED_TARGETS = {
    "DESKTOP_UPDATE_CHECKS": "https://andbible.org/docs/desktop/#update-checks",
    "RELEASE_6_0": "https://andbible.org/docs/releases/release_6_0/",
    "RELEASE_5_0": "https://andbible.org/docs/releases/release_5_0/",
    "RELEASE_4_1": "https://andbible.org/docs/releases/release_4_1/",
    "RELEASE_4_0": "https://andbible.org/docs/releases/release_4_0/",
    "CONTRIBUTE": "https://andbible.org/docs/contribute/",
    "CONTRIBUTE_TRANSLATING": "https://andbible.org/docs/contribute/#translating-the-user-interface",
    "FAQ_ADD_MODULE": "https://andbible.org/docs/faq/#please-add-module-x-to-andbible",
    "FAQ_TEXT_ISSUE": "https://andbible.org/docs/faq/#i-found-a-text-issue-in-a-bible-commentary-module",
}
ADDED_CALL_SITES = {
    "sharedCore/src/commonMain/kotlin/net/bible/sharedcore/docs/UserGuideToc.kt": ["CONTRIBUTE"],
    "app/src/main/java/net/bible/service/common/CommonUtils.kt": ["CONTRIBUTE_TRANSLATING"],
    "app/src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt": [
        "CONTRIBUTE", "SUPPORT", "FAQ_ADD_MODULE", "FAQ_TEXT_ISSUE"],
    "sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/toolbar/ReadingDrawerMenu.kt": [
        "SUPPORT", "CONTRIBUTE"],
    "sharedUi/src/commonMain/kotlin/net/bible/sharedui/app/settings/SettingsHost.kt": ["DISCRETE_BUILD"],
}


def test_catalog_preserves_original_urls():
    baseline = Path(__file__).parent / "fixtures/app_docs_links_before_catalog.tsv"
    old = [line.split("\t")[2] for line in baseline.read_text().splitlines() if not line.startswith("#")]
    current = [f"https://andbible.org/docs/{page}/" + (f"#{anchor}" if anchor else "")
               for _, _, page, anchor in scan()]
    assert Counter(current) == Counter(set(old) | set(ADDED_TARGETS.values()))


def test_migrated_call_sites_preserve_original_targets():
    baseline = Path(__file__).parent / "fixtures/app_docs_links_before_catalog.tsv"
    by_source = {}
    for line in baseline.read_text().splitlines():
        if not line.startswith("#"):
            source, name, _ = line.split("\t")
            by_source.setdefault(source, []).append(name)
    for source, names in ADDED_CALL_SITES.items():
        by_source.setdefault(source, []).extend(names)
    for source, names in by_source.items():
        kt = paths.REPO / source
        if kt == GENERATED_TOC:
            current = re.findall(r'DocsLink\.([A-Z][A-Z0-9_]*)\.url', kt.read_text(encoding="utf-8"))
            # The TOC order follows zensical.toml (pinned by test_app_toc.py); here only the targets must survive.
            assert Counter(current) == Counter(names)
            continue
        if "test" in kt.parts:
            continue
        current = re.findall(r'DocsLink\.([A-Z][A-Z0-9_]*)', kt.read_text(encoding="utf-8"))
        if source.endswith("/HelpAndTips.kt"):
            # The old when-map is now typed Item metadata; HelpAndTipsTest pins item order.
            assert Counter(current) == Counter(names), source
        else:
            assert current == names, source


@pytest.mark.parametrize(("source", "target"), [
    ("app/src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt", "CONTRIBUTE"),
    ("app/src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt", "SUPPORT"),
    ("app/src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt", "FAQ_ADD_MODULE"),
    ("app/src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt", "FAQ_TEXT_ISSUE"),
    ("sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/toolbar/ReadingDrawerMenu.kt", "SUPPORT"),
    ("sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/toolbar/ReadingDrawerMenu.kt", "CONTRIBUTE"),
    ("sharedUi/src/commonMain/kotlin/net/bible/sharedui/app/settings/SettingsHost.kt", "DISCRETE_BUILD"),
])
def test_migrated_call_site_guard_rejects_changed_new_targets(tmp_path, monkeypatch, source, target):
    baseline = Path(__file__).parent / "fixtures/app_docs_links_before_catalog.tsv"
    sources = {line.split("\t")[0] for line in baseline.read_text().splitlines()
               if not line.startswith("#")} | set(ADDED_CALL_SITES) | {source}
    for name in sources:
        fixture = tmp_path / name
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text((paths.REPO / name).read_text(encoding="utf-8"))
    changed = tmp_path / source
    text = changed.read_text(encoding="utf-8")
    assert f"DocsLink.{target}" in text
    changed.write_text(text.replace(f"DocsLink.{target}", "DocsLink.WRONG_TARGET"))
    monkeypatch.setitem(globals(), "GENERATED_TOC", tmp_path / GENERATED_TOC.relative_to(paths.REPO))
    monkeypatch.setattr(paths, "REPO", tmp_path)
    with pytest.raises(AssertionError, match=re.escape(source)):
        test_migrated_call_sites_preserve_original_targets()


def test_generated_toc_targets_are_cataloged():
    catalog_entries = {match[1]: match[2] for match in ENTRY.finditer(CATALOG.read_text(encoding="utf-8"))}
    toc = GENERATED_TOC.read_text(encoding="utf-8")
    generated = re.findall(r'Page\("([a-z0-9_/.-]+)".*?DocsLink\.([A-Z][A-Z0-9_]*)\.url', toc)
    assert generated
    for stem, name in generated:
        assert name == stem.upper().replace("/", "_").replace("-", "_")
        assert catalog_entries[name] == stem
    assert len(generated) == len(re.findall(r'\bPage\("', toc)), "unscannable generated TOC page"


def test_every_catalog_page_is_a_user_guide_page():
    """In-app help items name a DocsLink topic; the Help screen highlights the guide page with that stem (#4159)."""
    toc_stems = set(re.findall(r'\bPage\("([a-z0-9_/.-]+)"', GENERATED_TOC.read_text(encoding="utf-8")))
    assert toc_stems
    # Release notes are linked by the updater, not offered as topics in the in-app guide.
    release_pages = {"releases/release_6_0", "releases/release_5_0", "releases/release_4_1", "releases/release_4_0"}
    assert sorted({page for _, _, page, _ in scan()} - toc_stems - release_pages) == []


def test_docs_base_url_is_used_only_at_the_documentation_roots():
    assert uncataloged_calls(ROOTS) == []


def test_docs_base_url_allowlist_is_exact():
    assert all(path.is_file() for path in BASE_URL_ALLOWLIST)
    assert all("BASE_URL" in path.read_text(encoding="utf-8") for path in BASE_URL_ALLOWLIST)


def test_every_app_docs_link_exists():
    assert paths.SITE.is_dir(), "run `make site` first"
    assert missing(paths.SITE, scan()) == []


def test_no_hardcoded_docs_urls_left():
    stale = [f"{kt}:{n}" for root in ROOTS for kt in root.rglob("*.kt")
             if kt != DOCS_DIR / "DocsLinks.kt" and not any("test" in p.lower() for p in kt.relative_to(root).parts)
             for n, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1)
             if "docs.andbible.org" in line or "andbible.org/docs/" in line]
    assert stale == []


WIKI_TEST_SOURCE_SETS = {
    "test", "tests", "androidTest", "androidUnitTest", "androidInstrumentedTest",
    "commonTest", "jvmTest", "iosTest", "iosArm64Test", "iosSimulatorArm64Test",
    "__tests__", "iosAppUITests", "iosAppUnitTests",
}


def is_wiki_test_source(source, root):
    directories = source.relative_to(root).parts[:-1]
    # Roots are either a module's src directory or the native/web app directory.
    if directories and directories[0] == "src":
        directories = directories[1:]
    return bool(directories and directories[0] in WIKI_TEST_SOURCE_SETS)


def legacy_wiki_links(roots):
    return [f"{source}:{n}: use a DocsLink catalog entry instead of the GitHub wiki"
            for root in roots for source in sorted(root.rglob("*"))
            if source.suffix in {".kt", ".java", ".xml", ".swift", ".ts", ".vue", ".yaml", ".html"}
            and not is_wiki_test_source(source, root)
            for n, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1)
            if re.search(r"github\.com/AndBible/and-bible/wiki\b", line, re.IGNORECASE)]


def test_no_github_wiki_urls_in_app_code():
    assert legacy_wiki_links([*ROOTS, paths.REPO / "iosApp", paths.REPO / "app/bibleview-js"]) == []


@pytest.mark.parametrize("suffix", [".kt", ".java", ".xml", ".swift", ".ts", ".vue", ".yaml", ".html"])
def test_legacy_wiki_link_is_reported(tmp_path, suffix):
    source = tmp_path / f"X{suffix}"
    source.write_text('\n"https://github.com/AndBible/and-bible/wiki/Support"\n')
    assert legacy_wiki_links([tmp_path]) == [
        f"{source}:2: use a DocsLink catalog entry instead of the GitHub wiki"]


@pytest.mark.parametrize("relative_source", [
    "iosApp/iosApp/Help.swift",
    "app/bibleview-js/src/Help.vue",
    "app/bibleview-js/index.html",
])
def test_wiki_guard_scans_native_and_web_app_roots(tmp_path, monkeypatch, relative_source):
    source = tmp_path / relative_source
    source.parent.mkdir(parents=True)
    source.write_text('"https://github.com/AndBible/and-bible/wiki/Support"')
    monkeypatch.setattr(paths, "REPO", tmp_path)
    # Exercise the repository gate, not just the scanner with a hand-picked root.
    with pytest.raises(AssertionError, match="use a DocsLink catalog entry"):
        test_no_github_wiki_urls_in_app_code()


@pytest.mark.parametrize("directory", ["latest", "contest", "Testament", "testimonials"])
def test_wiki_guard_does_not_skip_production_paths_containing_test(tmp_path, directory):
    source = tmp_path / "main" / directory / "Help.kt"
    source.parent.mkdir(parents=True)
    source.write_text('"https://github.com/AndBible/and-bible/wiki/Support"')
    assert legacy_wiki_links([tmp_path]) == [
        f"{source}:1: use a DocsLink catalog entry instead of the GitHub wiki"]


@pytest.mark.parametrize("relative_source", [
    "main/test/Help.kt", "main/tests/Help.kt", "main/commonTest/Help.kt",
    "src/components/test/Help.vue",
])
def test_wiki_guard_does_not_treat_nested_production_directories_as_source_sets(tmp_path, relative_source):
    source = tmp_path / relative_source
    source.parent.mkdir(parents=True)
    source.write_text('"https://github.com/AndBible/and-bible/wiki/Support"')
    assert legacy_wiki_links([tmp_path]) == [
        f"{source}:1: use a DocsLink catalog entry instead of the GitHub wiki"]


@pytest.mark.parametrize("ending", [")", "<"])
def test_wiki_guard_reports_bare_wiki_before_punctuation(tmp_path, ending):
    source = tmp_path / "Help.kt"
    source.write_text(f"https://github.com/AndBible/and-bible/wiki{ending}")
    assert legacy_wiki_links([tmp_path]) == [
        f"{source}:1: use a DocsLink catalog entry instead of the GitHub wiki"]


@pytest.mark.parametrize("directory", [
    "test", "tests", "androidTest", "androidUnitTest", "androidInstrumentedTest",
    "commonTest", "jvmTest", "iosTest", "iosArm64Test", "iosSimulatorArm64Test",
    "__tests__", "iosAppUITests", "iosAppUnitTests", "src/__tests__",
])
def test_wiki_guard_skips_real_test_source_sets(tmp_path, directory):
    source = tmp_path / directory / "Help.kt"
    source.parent.mkdir(parents=True)
    source.write_text('"https://github.com/AndBible/and-bible/wiki/Support"')
    assert legacy_wiki_links([tmp_path]) == []


def test_wiki_guard_ignores_similarly_named_non_wiki_path(tmp_path):
    (tmp_path / "Help.kt").write_text('"https://github.com/AndBible/and-bible/wikipedia"')
    assert legacy_wiki_links([tmp_path]) == []


def test_wiki_guard_ignores_test_fixtures_and_other_repositories(tmp_path):
    tests = tmp_path / "commonTest"
    tests.mkdir()
    (tests / "X.kt").write_text('"https://github.com/AndBible/and-bible/wiki/Support"')
    (tmp_path / "X.kt").write_text('"https://github.com/Other/project/wiki/Support"')
    assert legacy_wiki_links([tmp_path]) == []


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
    'val u = DocsLinks.BASE_URL + "ai/"\n',
    'val u = DocsLinks   .  BASE_URL + "ai/"\n',
    'import net.bible.sharedcore.docs.DocsLinks.BASE_URL\nval u = BASE_URL + "ai/"\n',
])
def test_an_uncataloged_call_is_reported(tmp_path, source):
    kt = tmp_path / "X.kt"
    kt.write_text(source)
    assert uncataloged_calls([tmp_path]) == [f"{kt}:1: use a DocsLink catalog entry"]


def test_catalog_accepts_indentation_without_skipping_targets(tmp_path):
    catalog = tmp_path / "DocsLink.kt"
    for source in ['    GOOD("ai"),\n        BAD("removed");\n', '    GOOD("ai"), BAD("removed");\n']:
        catalog.write_text(source)
        assert [(page, anchor) for _, _, page, anchor in scan(catalog)] == [("ai", None), ("removed", None)]


def test_builder_alias_is_rejected(tmp_path):
    kt = tmp_path / "X.kt"
    kt.write_text('import net.bible.sharedcore.docs.DocsLinks as Links\nval u = Links.page("removed")')
    assert uncataloged_calls([tmp_path]) == [f"{kt}:1: do not alias the docs URL builder"]


@pytest.mark.parametrize("entry", ['    BAD(page);', '    BAD("ai", anchor);', '        BAD("ai", anchor);'])
def test_an_unscannable_catalog_entry_is_reported(tmp_path, entry):
    catalog = tmp_path / "DocsLink.kt"
    catalog.write_text('    GOOD("ai"),\n' + entry)
    with pytest.raises(AssertionError, match="unscannable catalog entry"):
        scan(catalog)
