"""sitegen.thumbnails: refresh, variant preference, letterbox cropping. Injected opener, no network."""

from __future__ import annotations

import io
import urllib.error

import pytest
from PIL import Image

from sitegen import paths, thumbnails

ID_A = "aaaaaaaaaaa"
ID_B = "bbbbbbbbbbb"


def jpeg(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=95)
    return buf.getvalue()


def scene(size=(480, 270), seed=0) -> Image.Image:
    """A bright, varied picture (stripes) so it never looks like a bar."""
    img = Image.new("RGB", size)
    for y in range(size[1]):
        img.paste(((y * 3 + seed) % 200 + 55, 120, (y * 7) % 200 + 40), (0, y, size[0], y + 1))
    return img


def letterboxed(bar=45, size=(480, 360)) -> Image.Image:
    img = Image.new("RGB", size, (0, 0, 0))
    img.paste(scene((size[0], size[1] - 2 * bar)), (0, bar))
    return img


def http_error(url):
    return urllib.error.HTTPError(url, 404, "nf", {}, None)


@pytest.fixture
def env(tmp_path, monkeypatch):
    content = tmp_path / "content"
    content.mkdir()
    (content / "p.md").write_text(f"https://youtu.be/{ID_A}\n")
    data = tmp_path / "data"
    data.mkdir()
    (data / "videos.yaml").write_text(f"- id: {ID_B}\n  title: T\n")
    media = tmp_path / "media"
    monkeypatch.setattr(paths, "CONTENT", content)
    monkeypatch.setattr(paths, "DATA", data)
    return media


def dims(media, vid):
    with Image.open(media / "videos" / f"{vid}.webp") as img:
        return img.size


def serving(image_bytes, calls=None):
    def opener(url):
        if calls is not None:
            calls.append(url)
        return image_bytes
    return opener


# --- variants ---

def test_maxres_is_preferred_and_not_cropped(tmp_path):
    calls = []
    big = scene((1280, 720))
    thumbnails.fetch([ID_A], tmp_path, serving(jpeg(big), calls))
    assert calls == [f"https://i.ytimg.com/vi/{ID_A}/maxresdefault.jpg"]
    assert dims(tmp_path, ID_A) == (480, 270)


def test_falls_back_maxres_to_hq_to_mq(tmp_path):
    calls = []

    def opener(url):
        calls.append(url.rsplit("/", 1)[1])
        if "mqdefault" not in url:
            raise http_error(url)
        return jpeg(scene((320, 180)))

    thumbnails.fetch([ID_A], tmp_path, opener)
    assert calls == ["maxresdefault.jpg", "hqdefault.jpg", "mqdefault.jpg"]
    assert dims(tmp_path, ID_A) == (320, 180)


def test_non_404_error_is_not_a_fallback(tmp_path):
    def opener(url):
        raise urllib.error.HTTPError(url, 500, "boom", {}, None)

    with pytest.raises(RuntimeError, match=ID_A):
        thumbnails.fetch([ID_A], tmp_path, opener)


# --- letterbox ---

def test_hq_black_bars_are_cropped_to_16_9(tmp_path):
    def opener(url):
        if "maxres" in url:
            raise http_error(url)
        return jpeg(letterboxed())

    thumbnails.fetch([ID_A], tmp_path, opener)
    with Image.open(tmp_path / "videos" / f"{ID_A}.webp") as img:
        assert img.size == (480, 270)
        gray = img.convert("L")
        # the real defect: black rows left at the edges
        assert gray.crop((0, 0, 480, 1)).getextrema()[1] > 40
        assert gray.crop((0, 269, 480, 270)).getextrema()[1] > 40


def test_crop_letterbox_unit_removes_exactly_the_bars():
    out = thumbnails.crop_letterbox(letterboxed(bar=45))
    assert out.size == (480, 270)


def test_dark_scene_without_bars_is_not_cropped():
    img = Image.new("RGB", (480, 360))
    for y in range(360):  # dark gradient, brightest row at the top edge is already content
        img.paste((20 + y // 8, 20, 20), (0, y, 480, y + 1))
    assert thumbnails.crop_letterbox(img).size == (480, 360)


def test_content_in_top_rows_blocks_cropping():
    img = letterboxed(bar=45)
    img.paste((200, 200, 200), (0, 0, 480, 3))  # a logo/line in the top rows: not a bar
    assert thumbnails.crop_letterbox(img).size == (480, 360)


def test_single_sided_bar_is_not_cropped():
    img = scene((480, 360))
    img.paste((0, 0, 0), (0, 0, 480, 60))
    assert thumbnails.crop_letterbox(img).size == (480, 360)


def test_thin_bars_below_threshold_are_left():
    img = letterboxed(bar=5)
    assert thumbnails.crop_letterbox(img).size == (480, 360)


# --- refresh behaviour ---

def test_default_fetches_only_missing(env):
    (env / "videos").mkdir(parents=True)
    (env / "videos" / f"{ID_A}.webp").write_bytes(b"keep")
    calls = []
    assert thumbnails.main(["--media-dir", str(env)], serving(jpeg(scene()), calls)) == 0
    assert (env / "videos" / f"{ID_A}.webp").read_bytes() == b"keep"
    assert len(calls) == 1 and ID_B in calls[0]
    assert dims(env, ID_B) == (480, 270)


def test_refresh_overwrites_and_accepts_url(env, capsys):
    (env / "videos").mkdir(parents=True)
    (env / "videos" / f"{ID_A}.webp").write_bytes(b"old")
    code = thumbnails.main(["--media-dir", str(env), "--refresh", f"https://youtu.be/{ID_A}"],
                           serving(jpeg(scene())))
    assert code == 0
    assert dims(env, ID_A) == (480, 270)
    assert not (env / "videos" / f"{ID_B}.webp").exists()
    assert "refreshed 1 (changed 1), failed 0" in capsys.readouterr().out


def test_unchanged_is_reported(env, capsys):
    opener = serving(jpeg(scene()))
    thumbnails.main(["--media-dir", str(env), "--refresh", ID_A], opener)
    capsys.readouterr()
    assert thumbnails.main(["--media-dir", str(env), "--refresh", ID_A], opener) == 0
    out = capsys.readouterr().out
    assert f"{ID_A}: unchanged" in out and "refreshed 1 (changed 0), failed 0" in out


def test_all_refreshes_every_referenced_id(env):
    calls = []
    assert thumbnails.main(["--media-dir", str(env), "--all"], serving(jpeg(scene()), calls)) == 0
    assert sorted(c.split("/")[4] for c in calls) == [ID_A, ID_B]


def test_failure_keeps_old_file_and_continues(env, capsys):
    (env / "videos").mkdir(parents=True)
    (env / "videos" / f"{ID_A}.webp").write_bytes(b"old")

    def opener(url):
        if ID_A in url:
            raise urllib.error.URLError("down")
        return jpeg(scene())

    code = thumbnails.main(["--media-dir", str(env), "--all"], opener)
    assert code == 1
    assert (env / "videos" / f"{ID_A}.webp").read_bytes() == b"old"
    assert dims(env, ID_B) == (480, 270)
    assert not list((env / "videos").glob(".*tmp"))
    err = capsys.readouterr()
    assert f"{ID_A}: FAILED" in err.err and "failed 1" in err.out


def test_corrupt_image_keeps_old_file(env):
    (env / "videos").mkdir(parents=True)
    (env / "videos" / f"{ID_A}.webp").write_bytes(b"old")
    assert thumbnails.main(["--media-dir", str(env), "--refresh", ID_A], serving(b"not an image")) == 1
    assert (env / "videos" / f"{ID_A}.webp").read_bytes() == b"old"


def test_unknown_id_is_an_error_before_any_download(env, capsys):
    calls = []
    code = thumbnails.main(["--media-dir", str(env), "--refresh", "zzzzzzzzzzz"], serving(b"", calls))
    assert code == 2 and calls == []
    assert "zzzzzzzzzzz" in capsys.readouterr().err


def test_garbage_argument_is_an_error(env, capsys):
    assert thumbnails.main(["--media-dir", str(env), "--refresh", "nope"], serving(b"")) == 2


def test_dry_run_writes_and_downloads_nothing(env, capsys):
    calls = []
    assert thumbnails.main(["--media-dir", str(env), "--all", "--dry-run"], serving(b"", calls)) == 0
    assert calls == [] and not env.exists()
    out = capsys.readouterr().out
    assert f"would refresh {ID_A}" in out and f"would refresh {ID_B}" in out


def test_bars_of_a_non_16_9_upload_are_cropped():
    # n8Y8N27uFzY's hqdefault: a 3:2 custom thumbnail, 20 black rows (5.6%) on each side
    assert thumbnails.crop_letterbox(letterboxed(bar=20)).size == (480, 320)
