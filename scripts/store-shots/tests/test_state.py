import json
from pathlib import Path

import pytest
from PIL import Image

from storeshots.layout import store_path
from storeshots.locales import locale_for
from storeshots.manifest import Shot
from storeshots.state import RunState
from storeshots.targets import TARGETS


def mk(id, *targets):
    return Shot(id=id, workspace=id, action="none", action_arg=None, night=False,
                targets=list(targets), caption_key=id)


SHOTS = [mk(f"s{i}", "play-phone", "play-seven") for i in range(1, 6)]
TARGET_IDS = ["play-phone", "play-seven"]


def png(path: Path, color=(1, 2, 3), size=(4, 4)):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, color).save(path)
    return path


def framed_png(state, t, sid, color=(5, 5, 5)):
    """A valid framed image (exact target size, RGB) at the framed path of the shot."""
    return png(state.framed_path(t, sid), color, TARGETS[t].size)


def record_all(state, shots, skip=()):
    for s in shots:
        for t in s.targets:
            if (s.id, t) in skip:
                continue
            state.record(s.id, t, png(state.raw_path(t, s.id)))
            framed_png(state, t, s.id)


def test_store_path_layout():
    assert store_path(TARGETS["play-seven"], locale_for("iw"), 2) == Path("android/iw-IL/seven-inch/02.png")
    assert store_path(TARGETS["play-phone"], locale_for("en"), 10) == Path("android/en-US/phone/10.png")
    assert store_path(TARGETS["ios-iphone-69"], locale_for("zh-rCN"), 1) == Path("ios/zh-Hans/iphone-6.9/01.png")
    assert store_path(TARGETS["ios-iphone-65"], locale_for("en"), 3) == Path("ios/en-US/iphone-6.5/03.png")
    assert store_path(TARGETS["ios-ipad-13"], locale_for("de"), 1) == Path("ios/de-DE/ipad-13/01.png")
    assert store_path(TARGETS["web-mac"], locale_for("en"), 2) == Path("website/mac/02.webp")
    assert store_path(TARGETS["web-phone"], locale_for("en"), 1) == Path("website/phone/01.webp")


def test_store_path_without_listing_raises():
    with pytest.raises(ValueError, match="b\\+sr\\+Latn"):
        store_path(TARGETS["play-phone"], locale_for("b+sr+Latn"), 1)
    with pytest.raises(ValueError, match="ar"):
        store_path(TARGETS["ios-iphone-69"], locale_for("ar"), 1)


def test_resume_skips_recorded(tmp_path):
    state = RunState(tmp_path, "en")
    for i in (1, 2, 3):
        state.record(f"s{i}", "play-phone", png(state.raw_path("play-phone", f"s{i}")))
    again = RunState(tmp_path, "en")  # a new process reads state.json
    assert again.missing(SHOTS, ["play-phone"]) == [("s4", "play-phone"), ("s5", "play-phone")]
    assert len(again.missing(SHOTS, TARGET_IDS)) == 7
    assert json.loads((tmp_path / "en" / "state.json").read_text())


def test_missing_notices_deleted_raw_file(tmp_path):
    state = RunState(tmp_path, "en")
    p = png(state.raw_path("play-phone", "s1"))
    state.record("s1", "play-phone", p)
    p.unlink()
    assert ("s1", "play-phone") in state.missing(SHOTS, ["play-phone"])


def test_incomplete_language_not_promoted(tmp_path):
    dest = tmp_path / "store"
    old = png(dest / "android" / "en-US" / "phone" / "01.png", (9, 9, 9))
    before = old.read_bytes()
    state = RunState(tmp_path / "work", "en")
    record_all(state, SHOTS, skip={("s4", "play-seven")})
    assert state.promote(SHOTS, dest) is False
    assert old.read_bytes() == before
    assert sorted(p.name for p in (dest / "android/en-US/phone").iterdir()) == ["01.png"]
    assert not (dest / "android/en-US/seven-inch").exists()


def test_framed_missing_blocks_promotion(tmp_path):
    state = RunState(tmp_path / "work", "en")
    record_all(state, SHOTS)
    state.framed_path("play-seven", "s2").unlink()
    assert state.promote(SHOTS, tmp_path / "store") is False


def test_promote_replaces_folder_in_manifest_order(tmp_path):
    dest = tmp_path / "store"
    png(dest / "android/en-US/phone/09.png")  # stale from an older, longer run
    state = RunState(tmp_path / "work", "en")
    record_all(state, SHOTS)
    assert state.promote(SHOTS, dest) is True
    phone = sorted(p.name for p in (dest / "android/en-US/phone").iterdir())
    assert phone == ["01.png", "02.png", "03.png", "04.png", "05.png"]
    assert (dest / "android/en-US/seven-inch/03.png").read_bytes() == state.framed_path("play-seven", "s3").read_bytes()
    assert not list(dest.rglob("*.tmp*")) and not list(dest.rglob("*.old"))


def test_index_is_position_among_target_shots(tmp_path):
    shots = [mk("a", "play-phone"), mk("b", "play-seven"), mk("c", "play-seven"), mk("d", "play-phone")]
    state = RunState(tmp_path / "work", "iw")
    record_all(state, shots)
    assert state.promote(shots, tmp_path / "store") is True
    assert (tmp_path / "store/android/iw-IL/phone/02.png").read_bytes() == state.framed_path("play-phone", "d").read_bytes()
    assert (tmp_path / "store/android/iw-IL/seven-inch/02.png").read_bytes() == state.framed_path("play-seven", "c").read_bytes()


def test_cli_frame_status_promote_roundtrip(tmp_path, monkeypatch):
    """End to end on the real manifest: frame two recorded raws, status is incomplete, promote refuses."""
    from storeshots import cli
    from storeshots.pipeline import frame_platform, load_captions, submodule_dir
    from storeshots.manifest import load_manifest

    monkeypatch.setenv("STORE_SHOTS_WORK", str(tmp_path))
    sub = submodule_dir()
    shots = load_manifest(sub / "shots.yaml")
    state = RunState(tmp_path, "en")
    for sid in ("bookmarks-labels", "map"):
        raw = state.raw_path("play-phone", sid)
        raw.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", TARGETS["play-phone"].raw_size, (200, 200, 200)).save(raw)
        state.record(sid, "play-phone", raw)
    framed, skipped = frame_platform(state, shots, load_captions("en", sub), "android")
    assert [p.name for p in framed] == ["bookmarks-labels.png", "map.png"]
    assert ("strongs-parallel", "play-phone") in skipped
    assert cli.main(["status", "--lang", "en"]) == 1
    assert cli.main(["promote", "--lang", "en", "--dest", str(tmp_path / "out")]) == 1
    assert not (tmp_path / "out").exists()
    # web-phone reuses the play-phone raw
    framed, _ = frame_platform(state, shots, load_captions("en", sub), "web", {"map"})
    assert [p.parent.name for p in framed] == ["web-phone"]


def test_promote_skips_targets_without_a_listing(tmp_path):
    """`ar` has a Play folder but no App Store locale: only Play shots are required and promoted."""
    shots = [mk("a", "play-phone", "ios-iphone-69"), mk("b", "play-phone", "ios-iphone-69")]
    state = RunState(tmp_path / "work", "ar")
    for s in shots:
        state.record(s.id, "play-phone", png(state.raw_path("play-phone", s.id)))
        framed_png(state, "play-phone", s.id)
    assert state.promote(shots, tmp_path / "store") is True
    assert state.skipped_targets == ["ios-iphone-69"]
    assert (tmp_path / "store/android/ar/phone/02.png").exists()
    assert not (tmp_path / "store/ios").exists()


def test_status_ignores_targets_without_a_listing(tmp_path, monkeypatch, capsys):
    from storeshots import cli
    from storeshots import pipeline
    shots = [mk("a", "play-phone", "ios-iphone-69")]
    monkeypatch.setenv("STORE_SHOTS_WORK", str(tmp_path))
    monkeypatch.setattr(pipeline, "load_manifest", lambda p: shots)
    state = RunState(tmp_path, "ar")
    state.record("a", "play-phone", png(state.raw_path("play-phone", "a")))
    framed_png(state, "play-phone", "a")
    assert cli.main(["status", "--lang", "ar"]) == 0
    out = capsys.readouterr().out
    assert "ios-iphone-69: no listing for ar, skipped" in out and out.strip().endswith("complete")


def test_web_phone_falls_back_to_play_phone_raw(tmp_path, monkeypatch):
    from storeshots import cli
    from storeshots import pipeline
    shots = [mk("a", "play-phone", "web-phone")]
    monkeypatch.setenv("STORE_SHOTS_WORK", str(tmp_path))
    monkeypatch.setattr(pipeline, "load_manifest", lambda p: shots)
    state = RunState(tmp_path, "en")
    state.record("a", "play-phone", png(state.raw_path("play-phone", "a")))
    framed_png(state, "play-phone", "a")
    framed_png(state, "web-phone", "a")
    assert state.raw_for("a", "web-phone") == state.recorded("a", "play-phone")
    assert state.missing(shots, ["web-phone"]) == []
    assert cli.main(["status", "--lang", "en"]) == 0
    assert state.promote(shots, tmp_path / "store") is True
    assert (tmp_path / "store/website/phone/01.webp").exists()


def test_stale_framed_is_not_promoted(tmp_path):
    import os
    shots = [mk("a", "play-phone")]
    state = RunState(tmp_path / "work", "en")
    record_all(state, shots)
    raw, framed = state.raw_path("play-phone", "a"), state.framed_path("play-phone", "a")
    os.utime(framed, (1000, 1000))
    os.utime(raw, (2000, 2000))  # raw re-captured after framing
    assert state.promote(shots, tmp_path / "store") is False
    assert not (tmp_path / "store").exists()


def test_invalid_framed_is_not_promoted(tmp_path):
    shots = [mk("a", "play-phone")]
    state = RunState(tmp_path / "work", "en")
    record_all(state, shots)
    png(state.framed_path("play-phone", "a"), size=(10, 10))  # wrong size
    from storeshots.errors import ShotError
    with pytest.raises(ShotError):
        state.promote(shots, tmp_path / "store")
    assert not (tmp_path / "store").exists()
