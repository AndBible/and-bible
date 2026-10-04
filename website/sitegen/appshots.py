"""Crops the Google Play phone screenshots into the screens shown in the home page's phone.

The Play images (fastlane/metadata/android/en-US/images/phoneScreenshots) are 900x1600 marketing frames
on an orange background. The page draws its own bezel, so only the display area is kept (status bar
dropped) and written to assets/img/appshots/NN.webp. Re-run after the Play screenshots change:
    cd website && uv run python -m sitegen.appshots
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


def build(source: Path = SOURCE, out: Path = OUT) -> list[Path]:
    out.mkdir(parents=True, exist_ok=True)
    for stale in out.glob("*.webp"):
        stale.unlink()
    written = []
    for position, number in enumerate(SHOTS, 1):
        with Image.open(source / f"{number}_en-US.jpeg") as image:
            screen = image.convert("RGB").crop(CROP)
        height = round(screen.height * WIDTH / screen.width)
        target = out / f"{position:02d}.webp"
        screen.resize((WIDTH, height), Image.LANCZOS).save(target, "WEBP", quality=78, method=6)
        written.append(target)
    return written


if __name__ == "__main__":
    for path in build():
        print(path.relative_to(paths.REPO), path.stat().st_size, "bytes")
