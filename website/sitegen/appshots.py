"""Crops the store phone screenshots into the screens shown in the home page's phone.

Source: store-screenshots/website/phone/NN.webp (private submodule, 1080x1920 framed shots made by
scripts/store-shots), else the old Play images (fastlane/metadata/android/en-US/images/phoneScreenshots,
900x1600 marketing frames on an orange background) when the submodule is not checked out (CI). The page draws its own bezel, so only the display area is kept (status bar
dropped) and written to assets/img/appshots/NN.webp. Re-run after the Play screenshots change:
    cd website && uv run python -m sitegen.appshots --play

Since the 6.0 beta the phone shows the unframed 6.0 store-screenshot captures instead
(`build_raw`, 1440x3120 PNGs from the store-screenshots work, by default the shared
`.local/store-shots/en/raw/play-phone/`; not in git). Only the cropped WebPs are committed:
    cd website && uv run python -m sitegen.appshots [--source DIR]
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

from sitegen import paths

SUBMODULE_SOURCE = paths.REPO / "store-screenshots/website/phone"
FASTLANE_SOURCE = paths.REPO / "fastlane/metadata/android/en-US/images/phoneScreenshots"


def default_source() -> Path:
    """The submodule folder when it holds images, else the fastlane folder."""
    if SUBMODULE_SOURCE.is_dir() and any(SUBMODULE_SOURCE.glob("*.webp")):
        return SUBMODULE_SOURCE
    return FASTLANE_SOURCE


SOURCE = default_source()
FRAMED_SIZE = (1080, 1920)  # web-phone target in scripts/store-shots/storeshots/targets.py
OUT = paths.ASSETS / "img" / "appshots"
# Display area inside the device frame of a 900x1600 Play screenshot: left, top (below the status bar), right, bottom.
CROP = (105, 145, 805, 1600)
FRAMES = paths.REPO / "store-screenshots/style/frames.yaml"
WIDTH = 448  # 2x of the 224 px CSS screen width
# Play screenshot numbers in display order: the most representative first.
SHOTS = (3, 2, 1, 6, 8, 5)

RAW_SOURCE = paths.REPO / ".local/store-shots/en/raw/play-phone"
# Unframed 1440x3120 phone capture: drop the status bar on top and the gesture bar at the bottom.
RAW_CROP = (0, 110, 1440, 3050)
# File stems in display order: the most representative first.
RAW_SHOTS = ("studypad", "crossrefs", "strongs-parallel", "bookmarks-labels", "formatted-note", "map")


def framed_screen_rect(frames: Path = FRAMES, size: tuple[int, int] = FRAMED_SIZE) -> tuple[int, int, int, int]:
    """Display area (left, top, right, bottom) of a framed phone shot, clipped to the canvas.

    Mirrors the placement maths of storeshots.frame.frame_shot (caption band, device scale, centring)
    so the crop follows the frame definition in frames.yaml instead of hard-coded pixels."""
    import yaml

    cfg = yaml.safe_load(frames.read_text())["phone"]
    width, height = size
    max_font = round(min(size) * 0.062)
    pad = round(max_font * 0.55)
    band = round(pad + 2 * max_font * 1.22 + pad * 0.3)
    fw, fh = cfg["size"]
    avail = height - band - (0 if cfg["max_overflow"] else round(height * 0.035))
    scale = min(width * cfg["max_width"] / fw, avail * (1 + cfg["max_overflow"]) / fh)
    sx, sy, sw, sh = (round(v * scale) for v in cfg["screen"])
    x = (width - round(fw * scale)) // 2
    return x + sx, band + sy, x + sx + sw, min(height, band + sy + sh)


def _shots(source: Path) -> list[tuple[Path, tuple[int, int, int, int]]]:
    """(image, crop box) in display order for either source layout."""
    framed = sorted(source.glob("[0-9][0-9].webp"))
    if framed:
        box = framed_screen_rect()
        return [(path, box) for path in framed]
    return [(source / f"{number}_en-US.jpeg", CROP) for number in SHOTS]


def build(source: Path = SOURCE, out: Path = OUT) -> list[Path]:
    """The framed store-screenshots web-phone set, else the old Play marketing frames (fastlane), cropped to their display area."""
    return _write(_shots(source), out)


def build_raw(source: Path = RAW_SOURCE, out: Path = OUT) -> list[Path]:
    """The unframed 6.0 phone captures; a missing one raises FileNotFoundError before anything is deleted."""
    files = [source / f"{stem}.png" for stem in RAW_SHOTS]
    missing = [f.name for f in files if not f.is_file()]
    if missing:
        raise FileNotFoundError(f"{source}: missing {', '.join(missing)}")
    return _write([(f, RAW_CROP) for f in files], out)


def _write(shots: list[tuple[Path, tuple[int, int, int, int]]], out: Path) -> list[Path]:
    out.mkdir(parents=True, exist_ok=True)
    for stale in out.glob("*.webp"):
        stale.unlink()
    written = []
    for position, (file, box) in enumerate(shots, 1):
        with Image.open(file) as image:
            screen = image.convert("RGB").crop(box)
        height = round(screen.height * WIDTH / screen.width)
        target = out / f"{position:02d}.webp"
        screen.resize((WIDTH, height), Image.LANCZOS).save(target, "WEBP", quality=78, method=6)
        written.append(target)
    return written


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--play", action="store_true", help="use the old fastlane Play frames")
    parser.add_argument("--source", type=Path, help="directory of the unframed phone PNGs")
    args = parser.parse_args()
    for path in build() if args.play else build_raw(args.source or RAW_SOURCE):
        print(path.relative_to(paths.REPO), path.stat().st_size, "bytes")
