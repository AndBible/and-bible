from pathlib import Path

import pytest

from storeshots.modules_fetch import module_url, parse_repositories, modules_for

REPOS = """Installer.1=sword-https,CrossWire,crosswire.org,/ftpmirror/pub/sword/packages/rawzip,/ftpmirror/pub/sword/raw,,,
Installer.3=sword-https,AndBible Extra,andbible.github.io,/andbible-extra/zip,/andbible-extra,,,
Installer.9=sword-https,Lockman (CrossWire),crosswire.org,/ftpmirror/pub/sword/lockmanpackages,/ftpmirror/pub/sword/lockmanraw,,,
"""


def test_parse_and_url():
    repos = parse_repositories(REPOS)
    assert module_url(repos, "CrossWire", "KJV") == "https://crosswire.org/ftpmirror/pub/sword/packages/rawzip/KJV.zip"
    assert module_url(repos, "Lockman (CrossWire)", "NASB") == \
        "https://crosswire.org/ftpmirror/pub/sword/lockmanpackages/NASB.zip"
    with pytest.raises(KeyError, match="Nowhere"):
        module_url(repos, "Nowhere", "X")


def test_modules_for_language(tmp_path):
    y = tmp_path / "modules.yaml"
    y.write_text("en:\n  - {initials: KJV, repo: CrossWire}\nfi:\n  - {initials: FinPR, repo: CrossWire}\n")
    assert modules_for(y, "en") == [("KJV", "CrossWire")]
    with pytest.raises(KeyError, match="de"):
        modules_for(y, "de")


def test_real_repository_list_has_every_repo_of_modules_yaml():
    root = Path(__file__).resolve().parents[3]
    repos = parse_repositories((root / "app/src/main/res/raw/repositories.txt").read_text())
    my = root / "store-screenshots/demo-content/modules.yaml"
    if not my.exists():
        pytest.skip("store-screenshots submodule not checked out")
    for initials, repo in modules_for(my, "en"):
        module_url(repos, repo, initials)
