import pytest

from sitegen.home import environment
from sitegen.redirects import load, write_stubs


def test_stub_preserves_hash_and_has_fallbacks(tmp_path):
    (tmp_path / "privacy").mkdir()
    (tmp_path / "privacy" / "index.html").write_text("ok")
    write_stubs(environment(), {"/privacy.html": "/privacy/"}, tmp_path)
    html = (tmp_path / "privacy.html").read_text()
    assert 'http-equiv="refresh" content="0; url=/privacy/"' in html
    assert 'location.replace("/privacy/" + location.hash)' in html
    assert '<a href="/privacy/">' in html
    assert 'name="robots" content="noindex"' in html


def test_directory_paths_get_index_html(tmp_path):
    write_stubs(environment(), {"/sponsor/": "https://shop.andbible.org/page/info"}, tmp_path)
    assert (tmp_path / "sponsor" / "index.html").is_file()


def test_missing_site_target_fails(tmp_path):
    with pytest.raises(ValueError, match="/nowhere/"):
        write_stubs(environment(), {"/old/": "/nowhere/"}, tmp_path)


def test_stub_never_overwrites_a_real_page(tmp_path):
    (tmp_path / "blog").mkdir()
    (tmp_path / "blog" / "index.html").write_text("real")
    (tmp_path / "x").mkdir()
    (tmp_path / "x" / "index.html").write_text("t")
    with pytest.raises(ValueError, match="would overwrite"):
        write_stubs(environment(), {"/blog/": "/x/"}, tmp_path)


@pytest.mark.parametrize("bad", [{"privacy.html": "/privacy/"}, {"/a/": "ftp://x"}])
def test_invalid_entries_rejected(tmp_path, bad):
    path = tmp_path / "r.yaml"
    path.write_text("\n".join(f"{k}: {v}" for k, v in bad.items()))
    with pytest.raises(ValueError):
        load(path)


def test_repository_redirects_load():
    from sitegen.paths import DATA

    mapping = load(DATA / "redirects.yaml")
    assert mapping["/privacy.html"] == "/privacy/"


@pytest.mark.xfail(strict=True, reason="Tasks 12 and 18 resolve the pending redirects")
def test_no_pending_redirects():
    import yaml
    from sitegen.paths import DATA

    assert not yaml.safe_load((DATA / "redirects.yaml").read_text()).get("pending")
