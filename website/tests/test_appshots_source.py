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


def test_framed_source_crop_lands_inside_the_screen(monkeypatch, tmp_path):
    """Frame a magenta raw with the real framing code; the crop's corners must be screen pixels, not
    background, bezel or caption pixels (a stale crop box would show those)."""
    import sys
    from pathlib import Path

    import pytest

    repo = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(repo / "scripts/store-shots"))
    try:
        from storeshots import frame
        from storeshots.targets import TARGETS
        frame.style_dir()
    except Exception as e:  # noqa: BLE001 - submodule/fonts not available
        pytest.skip(f"framing code unavailable: {e}")
    finally:
        sys.path.remove(str(repo / "scripts/store-shots"))

    target = TARGETS["web-phone"]
    raw = tmp_path / "raw.png"
    Image.new("RGB", target.raw_size, (255, 0, 255)).save(raw)
    sub, _ = _patch(monkeypatch, tmp_path, False)
    frame.frame_shot(raw, target, "Marker", "en", sub / "01.webp")

    written = appshots.build(sub, tmp_path / "out")
    with Image.open(written[0]) as crop:
        crop = crop.convert("RGB")
    def kind(px):
        r, g, b = px
        if r > 200 and b > 200 and g < 60:
            return "marker"
        if max(px) < 40:
            return "black"  # letterbox/rounded corner of the screen itself
        return "other"      # background, bezel, caption: the crop box is off

    w, h = crop.size
    corner = round(w * 0.14)  # the screen's rounded corners legitimately show the bezel's edge
    inset = 3                 # and the last pixels blend through the lossy WebP
    ring = [(x, y) for x in range(corner, w - corner) for y in (inset, h - 1 - inset)] + \
           [(x, y) for y in range(corner, h - corner) for x in (inset, w - 1 - inset)]
    bad = [(p, crop.getpixel(p)) for p in ring if kind(crop.getpixel(p)) == "other"]
    assert not bad, f"crop border contains non-screen pixels, e.g. {bad[:3]}"
    # The screen content fills the crop: the middle row/column reach the marker within 5 % of the edges.
    mid_row = [kind(crop.getpixel((x, h // 2))) for x in range(w)]
    mid_col = [kind(crop.getpixel((w // 2, y))) for y in range(h)]
    assert mid_row.index("marker") < w * 0.05 and mid_row[::-1].index("marker") < w * 0.05
    assert mid_col.index("marker") < h * 0.05 and mid_col[::-1].index("marker") < h * 0.05
