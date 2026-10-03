import io
import os
import urllib.error
from pathlib import Path

import pytest
from PIL import Image

from sitegen import thumbnails
from sitegen.migrate.media import from_tar, optimise


def make(path: Path, size, mode="RGB", fmt="PNG", colors=None):
    img = Image.new(mode, size, colors or (200, 100, 50))
    img.save(path, fmt)
    return path


def test_large_jpeg_is_scaled_and_lossy(tmp_path):
    out = optimise(make(tmp_path / "a.jpg", (3000, 1500), fmt="JPEG"), tmp_path / "o", "a")
    with Image.open(out) as img:
        assert out.suffix == ".webp" and img.size == (1600, 800)
    assert out.read_bytes()[12:16] == b"VP8 "


def test_flat_png_stays_lossless(tmp_path):
    out = optimise(make(tmp_path / "chart.png", (800, 400)), tmp_path / "o", "chart")
    with Image.open(out) as img:
        assert img.size == (800, 400)
        assert img.convert("RGB").getpixel((10, 10)) == (200, 100, 50)
    assert out.read_bytes()[12:16] == b"VP8L"
    assert out.stat().st_size < 400_000


def test_small_image_is_not_upscaled(tmp_path):
    out = optimise(make(tmp_path / "s.png", (100, 50)), tmp_path / "o", "s")
    with Image.open(out) as img:
        assert img.size == (100, 50)


def test_pdf_is_copied(tmp_path):
    src = tmp_path / "flyer.pdf"
    src.write_bytes(b"%PDF-1.4 test")
    out = optimise(src, tmp_path / "o", "flyer")
    assert out.name == "flyer.pdf" and out.read_bytes() == b"%PDF-1.4 test"


def test_noisy_png_falls_back_to_lossy(tmp_path):
    img = Image.frombytes("RGB", (1600, 1200), os.urandom(1600 * 1200 * 3))
    img.save(tmp_path / "noise.png")
    out = optimise(tmp_path / "noise.png", tmp_path / "o", "noise")
    assert out.stat().st_size < 2_000_000  # white noise is ~1.6 MB even lossy; lossless would be ~5.8 MB
    assert out.read_bytes()[12:16] == b"VP8 "


def test_animated_gif_is_kept(tmp_path):
    frames = [Image.new("RGB", (20, 20), c).convert("P") for c in ((255, 0, 0), (0, 255, 0), (0, 0, 255))]
    src = tmp_path / "anim.gif"
    frames[0].save(src, save_all=True, append_images=frames[1:], duration=50, loop=0)
    out = optimise(src, tmp_path / "o", "anim")
    assert out.name == "anim.gif"
    with Image.open(out) as img:
        assert img.n_frames == 3


def test_still_gif_becomes_webp(tmp_path):
    out = optimise(make(tmp_path / "still.gif", (30, 30), mode="P", fmt="GIF", colors=1), tmp_path / "o", "still")
    assert out.suffix == ".webp"


def test_exif_orientation_applied_and_metadata_stripped(tmp_path):
    img = Image.new("RGB", (400, 200), (10, 20, 30))
    exif = Image.Exif()
    exif[0x0112] = 6  # rotate 90 clockwise on display
    exif[0x010F] = "SecretMaker"
    src = tmp_path / "rot.jpg"
    img.save(src, "JPEG", exif=exif)
    out = optimise(src, tmp_path / "o", "rot")
    with Image.open(out) as res:
        assert res.size == (200, 400)
        assert not res.getexif()
    assert b"SecretMaker" not in out.read_bytes()


def test_from_tar_maps_year_month_names(tmp_path):
    import tarfile

    tar = tmp_path / "m.tar"
    payload = tmp_path / "x.txt"
    payload.write_bytes(b"hi")
    with tarfile.open(tar, "w") as tf:
        tf.add(payload, arcname="2020/05/x.txt")
        tf.add(payload, arcname="uploads/2021/06/y.txt")
    mapping = from_tar(tar)
    assert set(mapping) == {"2020/05/x.txt", "2021/06/y.txt"}
    assert mapping["2020/05/x.txt"].read_bytes() == b"hi"


# --- thumbnails ---

def _jpeg_bytes(size=(640, 480)):
    buf = io.BytesIO()
    Image.new("RGB", size, (1, 2, 3)).save(buf, "JPEG")
    return buf.getvalue()


def test_referenced_ids_deduplicates_across_posts(tmp_path):
    content = tmp_path / "content"
    (content / "blog").mkdir(parents=True)
    (content / "blog" / "a.md").write_text("x\n\nhttps://youtu.be/dQw4w9WgXcQ\n")
    (content / "blog" / "b.md").write_text("https://www.youtube.com/watch?v=dQw4w9WgXcQ\n\nhttps://youtu.be/abcdefghijk\n")
    assert thumbnails.referenced_ids(content, tmp_path / "none.yaml") == {"dQw4w9WgXcQ", "abcdefghijk"}
    one = {"dQw4w9WgXcQ"}
    only = tmp_path / "c2"
    only.mkdir()
    (only / "a.md").write_text("https://youtu.be/dQw4w9WgXcQ\n")
    (only / "b.md").write_text("https://youtu.be/dQw4w9WgXcQ\n")
    assert thumbnails.referenced_ids(only, tmp_path / "none.yaml") == one


def test_referenced_ids_includes_videos_yaml(tmp_path):
    content = tmp_path / "content"
    content.mkdir()
    (content / "a.md").write_text("https://youtu.be/dQw4w9WgXcQ\n")
    yml = tmp_path / "videos.yaml"
    yml.write_text("- id: abcdefghijk\n  title: T\n- id: dQw4w9WgXcQ\n  title: U\n")
    assert thumbnails.referenced_ids(content, yml) == {"dQw4w9WgXcQ", "abcdefghijk"}


def test_fetch_downloads_and_skips_existing(tmp_path):
    calls = []

    def opener(url):
        calls.append(url)
        return _jpeg_bytes()

    (tmp_path / "videos").mkdir()
    (tmp_path / "videos" / "have_it_000.webp").write_bytes(b"x")
    got = thumbnails.fetch(["dQw4w9WgXcQ", "have_it_000"], tmp_path, opener=opener)
    assert got == ["dQw4w9WgXcQ"]
    assert calls == ["https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"]
    with Image.open(tmp_path / "videos" / "dQw4w9WgXcQ.webp") as img:
        assert img.size[0] == 480


def test_fetch_falls_back_to_mqdefault_on_404(tmp_path):
    calls = []

    def opener(url):
        calls.append(url)
        if "maxresdefault" in url or "hqdefault" in url:
            raise urllib.error.HTTPError(url, 404, "nf", {}, None)
        return _jpeg_bytes()

    assert thumbnails.fetch(["dQw4w9WgXcQ"], tmp_path, opener=opener) == ["dQw4w9WgXcQ"]
    assert calls[-1].endswith("/mqdefault.jpg")


def test_fetch_error_names_the_id(tmp_path):
    def opener(url):
        raise urllib.error.URLError("down")

    with pytest.raises(RuntimeError, match="dQw4w9WgXcQ"):
        thumbnails.fetch(["dQw4w9WgXcQ"], tmp_path, opener=opener)
    assert not (tmp_path / "videos" / "dQw4w9WgXcQ.webp").exists()
