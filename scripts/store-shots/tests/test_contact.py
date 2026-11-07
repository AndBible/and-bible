from pathlib import Path

from PIL import Image

from storeshots.contact import contact_sheet, contact_tiles
from storeshots.manifest import Shot
from storeshots.state import RunState


def mk(id, *targets):
    return Shot(id=id, workspace=id, action="none", action_arg=None, night=False,
                targets=list(targets), caption_key=id)


def framed(state, target, shot, size):
    p = state.framed_path(target, shot)
    p.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (200, 100, 50)).save(p)


def test_contact_sheet_lists_every_shot(tmp_path):
    shots = [mk("verse", "play-phone", "ios-iphone-69", "web-phone"), mk("search", "play-phone", "ios-iphone-69"),
             mk("split", "play-seven")]
    state = RunState(tmp_path / "work", "en")
    for t, size in (("play-phone", (270, 480)), ("play-seven", (480, 270)), ("ios-iphone-69", (330, 717))):
        for s in shots:
            if t in s.targets:
                framed(state, t, s.id, size)
    # web-phone is declared but not framed yet: no tile, no crash
    tiles = contact_tiles("en", tmp_path / "work", shots)
    assert [t.label for t in tiles["android"]] == ["play-phone / verse", "play-phone / search", "play-seven / split"]
    assert [t.label for t in tiles["ios"]] == ["ios-iphone-69 / verse", "ios-iphone-69 / search"]
    assert tiles["web"] == []

    out = tmp_path / "review"
    written = contact_sheet("en", out, tmp_path / "work", shots=shots)
    assert sorted(p.name for p in written) == ["contact-android.png", "contact-ios.png"]  # none for empty web
    with Image.open(out / "contact-android.png") as img:
        assert img.mode == "RGB" and img.width <= 2400 and img.height > 100
        assert img.getpixel((2, 2)) == (232, 232, 232)  # light grey background
