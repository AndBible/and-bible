import subprocess
import sys
from pathlib import Path

from PIL import Image

from storeshots.fastlane import TYPES, check, copy

REPO_SHOTS = Path(__file__).resolve().parents[1]


def png(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", (4, 4)).save(path)


def make_src(root, locale, types=("phone", "seven-inch", "ten-inch"), n=2):
    for t in types:
        for i in range(1, n + 1):
            png(root / "android" / locale / t / f"{i:02d}.png")


def run_cli(*args):
    return subprocess.run([sys.executable, "-m", "storeshots.cli", "fastlane", *args], cwd=REPO_SHOTS,
                          capture_output=True, text=True)


def test_check_names_partial_locale_and_exits_1(tmp_path):
    src = tmp_path / "src"
    make_src(src, "de-DE")
    make_src(src, "fi-FI", types=("phone",))          # some, not all three types
    make_src(src, "sv-SE", n=1)                       # a type with fewer than 2 images
    ok, partial = check(src)
    assert partial == ["fi-FI", "sv-SE"] and ok == {"de-DE": {"phone": 2, "seven-inch": 2, "ten-inch": 2}}
    r = run_cli("--check", "--src", str(src), "--dest", str(tmp_path / "dest"))
    assert r.returncode == 1 and "fi-FI" in r.stderr + r.stdout and "sv-SE" in r.stderr + r.stdout


def test_check_passes_for_complete_tree(tmp_path):
    src = tmp_path / "src"
    make_src(src, "de-DE")
    r = run_cli("--check", "--src", str(src), "--dest", str(tmp_path / "dest"))
    assert r.returncode == 0 and "de-DE" in r.stdout


def test_copy_replaces_images_and_keeps_text_metadata(tmp_path):
    src, dest = tmp_path / "src", tmp_path / "dest"
    make_src(src, "de-DE")
    base = dest / "de-DE"
    (base / "full_description.txt").parent.mkdir(parents=True)
    (base / "full_description.txt").write_text("keep me")
    png(base / "images" / "phoneScreenshots" / "old.png")   # replaced
    png(base / "images" / "featureGraphic.png")              # not a screenshot folder: untouched
    copied = copy(src, dest)
    assert copied == {"de-DE": 6}
    assert not (base / "images/phoneScreenshots/old.png").exists()
    for folder in TYPES.values():
        assert sorted(p.name for p in (base / "images" / folder).iterdir()) == ["01.png", "02.png"]
    assert (base / "full_description.txt").read_text() == "keep me"
    assert (base / "images/featureGraphic.png").exists()
