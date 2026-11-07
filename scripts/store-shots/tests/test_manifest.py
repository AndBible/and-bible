from pathlib import Path

import pytest
import yaml

from storeshots.manifest import ACTIONS, ManifestError, load_manifest
from storeshots.targets import TARGETS

REPO = Path(__file__).resolve().parents[3]
SUB = REPO / "store-screenshots"


def write(tmp_path, shots):
    p = tmp_path / "shots.yaml"
    p.write_text(yaml.safe_dump({"shots": shots}))
    return p


def shot(**kw):
    base = {"id": "a", "targets": ["play-phone"]}
    base.update(kw)
    return base


def test_defaults(tmp_path):
    s, _ = load_manifest(write(tmp_path, [shot(id="x-y"), shot(id="z")]))
    assert (s.workspace, s.action, s.action_arg, s.night, s.caption_key) == ("x-y", "none", None, False, "x-y")


def test_manifest_rejects_unknown_target_and_duplicate_ids(tmp_path):
    with pytest.raises(ManifestError, match="play-watch"):
        load_manifest(write(tmp_path, [shot(targets=["play-watch"])]))
    with pytest.raises(ManifestError, match="duplicate.*a"):
        load_manifest(write(tmp_path, [shot(), shot()]))


def test_rejects_unknown_action_and_missing_arg(tmp_path):
    with pytest.raises(ManifestError, match="teleport"):
        load_manifest(write(tmp_path, [shot(action="teleport")]))
    for action in ("verse_menu", "label_dialog", "search"):
        with pytest.raises(ManifestError, match="action_arg"):
            load_manifest(write(tmp_path, [shot(action=action)]))
    assert "workspaces" in ACTIONS


def test_limits_enforced_by_loader(tmp_path):
    one = [shot(id="only")]
    with pytest.raises(ManifestError, match="play-phone.*2"):
        load_manifest(write(tmp_path, one))  # Play needs at least 2
    many = [shot(id=f"s{i}", targets=["ios-iphone-69"]) for i in range(11)]
    with pytest.raises(ManifestError, match="ios-iphone-69.*10"):
        load_manifest(write(tmp_path, many))
    nine = [shot(id=f"p{i}") for i in range(9)]
    with pytest.raises(ManifestError, match="play-phone.*8"):
        load_manifest(write(tmp_path, nine))


def test_play_counts_within_limits():
    shots = load_manifest(SUB / "shots.yaml")
    for tid, t in TARGETS.items():
        n = sum(tid in s.targets for s in shots)
        if t.store == "play":
            assert 2 <= n <= 8, tid
        elif t.store == "appstore":
            assert 1 <= n <= 10, tid


def test_real_manifest_matches_agreed_list():
    shots = load_manifest(SUB / "shots.yaml")
    assert [s.id for s in shots] == [
        "bookmarks-labels", "formatted-note", "label-assign", "crossrefs", "map", "strongs-parallel",
        "studypad", "book-chooser", "rtl-parallel", "night-split", "workspaces", "search"]
    by = {s.id: s for s in shots}
    assert by["search"].action == "search" and by["search"].action_arg == "love"
    assert by["night-split"].night is True
    assert by["formatted-note"].action_arg == "Jer.9.24"


def test_every_caption_exists_and_is_short():
    shots = load_manifest(SUB / "shots.yaml")
    captions = yaml.safe_load((SUB / "captions" / "en.yaml").read_text())["captions"]
    for s in shots:
        assert s.caption_key in captions, s.id
        assert 0 < len(captions[s.caption_key]) <= 40, s.id
