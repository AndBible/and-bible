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


def test_no_pending_redirects():
    import yaml
    from sitegen.paths import DATA

    assert not yaml.safe_load((DATA / "redirects.yaml").read_text()).get("pending")


def test_build_writes_upload_stubs_and_copies_media(tmp_path, monkeypatch):
    from sitegen import build, paths

    media = tmp_path / "media"
    (media / "blog").mkdir(parents=True)
    (media / "blog" / "a.webp").write_bytes(b"x")
    (media / ".git").mkdir()
    data = tmp_path / "data"
    data.mkdir()
    (data / "wp-uploads-redirects.yaml").write_text("/wp-content/uploads/2024/01/A.png/: /media/blog/a.webp\n")
    monkeypatch.setattr(paths, "MEDIA", media)
    out = tmp_path / "out"
    content = tmp_path / "content" / "en"
    content.mkdir(parents=True)
    (content / "site.yaml").write_text((paths.CONTENT / "en" / "site.yaml").read_text())
    build.build(tmp_path / "content", out, data, docs=False)
    assert (out / "wp-content/uploads/2024/01/A.png/index.html").is_file()
    assert (out / "media/blog/a.webp").is_file() and not (out / "media/.git").exists()
