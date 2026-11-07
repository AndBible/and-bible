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


def _zip_bytes():
    import io, zipfile
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w") as z:
        z.writestr("mods.d/x.conf", "[X]")
    return b.getvalue()


def _setup_root(tmp_path):
    root = tmp_path / "repo"
    (root / "app/src/main/res/raw").mkdir(parents=True)
    (root / "app/src/main/res/raw/repositories.txt").write_text(REPOS)
    (root / "store-screenshots/demo-content").mkdir(parents=True)
    (root / "store-screenshots/demo-content/modules.yaml").write_text("en:\n  - {initials: KJV, repo: CrossWire}\n")
    return root


class _Resp:
    def __init__(self, data): self.data = data
    def read(self): return self.data
    def __enter__(self): return self
    def __exit__(self, *a): return False


def test_fetch_refetches_corrupt_cached_zip(tmp_path, monkeypatch):
    from storeshots import modules_fetch
    root = _setup_root(tmp_path)
    out = tmp_path / "cache"
    out.mkdir()
    (out / "KJV.zip").write_bytes(b"not a zip")
    monkeypatch.setattr(modules_fetch.urllib.request, "urlopen", lambda url, timeout: _Resp(_zip_bytes()))
    assert modules_fetch.fetch("en", out, root) == [out / "KJV.zip"]
    import zipfile
    assert zipfile.is_zipfile(out / "KJV.zip")
    assert not list(out.glob("*.part"))


def test_fetch_rejects_corrupt_download_and_http_errors(tmp_path, monkeypatch):
    import http.client
    from storeshots import modules_fetch
    from storeshots.errors import ShotError
    root = _setup_root(tmp_path)
    out = tmp_path / "cache"
    monkeypatch.setattr(modules_fetch.urllib.request, "urlopen", lambda url, timeout: _Resp(b"<html>"))
    with pytest.raises(ShotError, match="KJV"):
        modules_fetch.fetch("en", out, root)
    assert not (out / "KJV.zip").exists() and not list(out.glob("*.part"))

    def boom(url, timeout):
        raise http.client.IncompleteRead(b"")
    monkeypatch.setattr(modules_fetch.urllib.request, "urlopen", boom)
    with pytest.raises(ShotError, match="KJV"):
        modules_fetch.fetch("en", out, root)
