"""Crops the Google Play phone screenshots into the screens shown in the home page's phone.

The Play images (fastlane/metadata/android/en-US/images/phoneScreenshots) are 900x1600 marketing frames
on an orange background. The page draws its own bezel, so only the display area is kept (status bar
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

SOURCE = paths.REPO / "fastlane/metadata/android/en-US/images/phoneScreenshots"
OUT = paths.ASSETS / "img" / "appshots"
# Display area inside the device frame of a 900x1600 Play screenshot: left, top (below the status bar), right, bottom.
CROP = (105, 145, 805, 1600)
WIDTH = 448  # 2x of the 224 px CSS screen width
# Play screenshot numbers in display order: the most representative first.
SHOTS = (3, 2, 1, 6, 8, 5)

RAW_SOURCE = paths.REPO / ".local/store-shots/en/raw/play-phone"
# Unframed 1440x3120 phone capture: drop the status bar on top and the gesture bar at the bottom.
RAW_CROP = (0, 110, 1440, 3050)
# File stems in display order: the most representative first.
RAW_SHOTS = ("studypad", "crossrefs", "strongs-parallel", "bookmarks-labels", "formatted-note", "map")


def build(source: Path = SOURCE, out: Path = OUT) -> list[Path]:
    """The old Play marketing frames (fastlane), cropped to their display area."""
    return _write([source / f"{number}_en-US.jpeg" for number in SHOTS], CROP, out)


def build_raw(source: Path = RAW_SOURCE, out: Path = OUT) -> list[Path]:
    """The unframed 6.0 phone captures; a missing one raises FileNotFoundError before anything is deleted."""
    files = [source / f"{stem}.png" for stem in RAW_SHOTS]
    missing = [f.name for f in files if not f.is_file()]
    if missing:
        raise FileNotFoundError(f"{source}: missing {', '.join(missing)}")
    return _write(files, RAW_CROP, out)


def _write(files: list[Path], crop: tuple[int, int, int, int], out: Path) -> list[Path]:
    out.mkdir(parents=True, exist_ok=True)
    for stale in out.glob("*.webp"):
        stale.unlink()
    written = []
    for position, file in enumerate(files, 1):
        with Image.open(file) as image:
            screen = image.convert("RGB").crop(crop)
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
