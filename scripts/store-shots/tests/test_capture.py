import json
from pathlib import Path

import pytest
from PIL import Image

from storeshots.capture import import_raws, plan_for
from storeshots.errors import ShotError
from storeshots.manifest import Shot
from storeshots.state import RunState
from storeshots.targets import TARGETS


def mk(id, targets, night=False, action="none", arg=None, ws=None):
    return Shot(id=id, workspace=ws or id.title(), action=action, action_arg=arg, night=night,
                targets=list(targets), caption_key=id)


SHOTS = [
    mk("a", ["ios-iphone-69", "ios-iphone-65"], ws="Character of God"),
    mk("b", ["ios-iphone-69", "ios-iphone-65"], night=True),
    mk("c", ["ios-ipad-13"], action="search", arg="love"),
]


def test_plan_filters_target_and_night():
    plan = plan_for(SHOTS, "fi", "ios-iphone-69", night=False)
    assert plan["lang"] == "fi"
    assert [s["id"] for s in plan["shots"]] == ["a"]
    assert plan["shots"][0] == {"id": "a", "workspace": "Character of God", "action": "none",
                                "action_arg": None, "night": False}
    assert [s["id"] for s in plan_for(SHOTS, "fi", "ios-iphone-69", night=True)["shots"]] == ["b"]
    assert [s["id"] for s in plan_for(SHOTS, "fi", "ios-iphone-69", night=None)["shots"]] == ["a", "b"]


def test_plan_lang_is_bcp47_tag():
    assert plan_for(SHOTS, "zh-rCN", "ios-ipad-13", None)["lang"] == "zh-CN"
    assert plan_for(SHOTS, "pt-rBR", "ios-ipad-13", None)["lang"] == "pt-BR"


def raw(path: Path, size):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (9, 9, 9)).save(path)
    return path


def test_import_records_valid_raws_and_shared_targets(tmp_path):
    src = tmp_path / "in"
    raw(src / "a.png", TARGETS["ios-iphone-69"].raw_size)
    raw(src / "b.png", TARGETS["ios-iphone-69"].raw_size)
    state = RunState(tmp_path / "work", "en")
    got = import_raws(state, SHOTS, "ios-iphone-69", src, also=["ios-iphone-65"])
    assert sorted(got) == ["a", "b"]
    for t in ("ios-iphone-69", "ios-iphone-65"):
        assert state.missing(SHOTS[:2], [t]) == []
    assert state.recorded("a", "ios-iphone-65").read_bytes() == (src / "a.png").read_bytes()


def test_import_rejects_wrong_size_and_records_nothing_for_it(tmp_path):
    src = tmp_path / "in"
    raw(src / "a.png", (1179, 2556))
    state = RunState(tmp_path / "work", "en")
    with pytest.raises(ShotError) as e:
        import_raws(state, SHOTS, "ios-iphone-69", src)
    assert "a" in str(e.value) and "1320x2868" in str(e.value)
    assert state.recorded("a", "ios-iphone-69") is None


def test_import_ignores_unknown_and_unassigned_files(tmp_path):
    src = tmp_path / "in"
    raw(src / "zzz.png", TARGETS["ios-ipad-13"].raw_size)
    raw(src / "a.png", TARGETS["ios-ipad-13"].raw_size)  # a is not an iPad shot
    raw(src / "c.png", TARGETS["ios-ipad-13"].raw_size)
    state = RunState(tmp_path / "work", "en")
    assert import_raws(state, SHOTS, "ios-ipad-13", src) == ["c"]
