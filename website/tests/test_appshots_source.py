from PIL import Image

from sitegen import appshots


def _patch(monkeypatch, tmp_path, with_submodule):
    sub = tmp_path / "store-screenshots/website/phone"
    old = tmp_path / "fastlane"
    old.mkdir()
    sub.mkdir(parents=True)
    if with_submodule:
        Image.new("RGB", (1080, 1920), "white").save(sub / "01.webp")
    monkeypatch.setattr(appshots, "SUBMODULE_SOURCE", sub)
    monkeypatch.setattr(appshots, "FASTLANE_SOURCE", old)
    return sub, old


def test_source_prefers_submodule_with_images(monkeypatch, tmp_path):
    sub, _ = _patch(monkeypatch, tmp_path, True)
    assert appshots.default_source() == sub


def test_source_falls_back_to_fastlane_when_submodule_empty_or_absent(monkeypatch, tmp_path):
    _, old = _patch(monkeypatch, tmp_path, False)  # folder exists but holds no images
    assert appshots.default_source() == old
    monkeypatch.setattr(appshots, "SUBMODULE_SOURCE", tmp_path / "missing")
    assert appshots.default_source() == old


def test_build_from_framed_source_crops_the_screen(monkeypatch, tmp_path):
    sub, _ = _patch(monkeypatch, tmp_path, True)
    Image.new("RGB", (1080, 1920), "white").save(sub / "02.webp")
    written = appshots.build(sub, tmp_path / "out")
    assert [p.name for p in written] == ["01.webp", "02.webp"]
    left, top, right, bottom = appshots.framed_screen_rect()
    with Image.open(written[0]) as image:
        assert image.size == (appshots.WIDTH, round((bottom - top) * appshots.WIDTH / (right - left)))


def test_framed_screen_rect_is_inside_the_canvas():
    left, top, right, bottom = appshots.framed_screen_rect()
    assert 0 < left < right <= 1080 and 0 < top < bottom <= 1920
    assert (right - left) / (bottom - top) < 0.5  # phone portrait screen
