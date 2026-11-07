"""Copies the store-screenshots Android images into fastlane's metadata tree before `supply`."""
from __future__ import annotations

import shutil
from pathlib import Path

# store-screenshots/android/<play>/<folder>  ->  fastlane/metadata/android/<play>/images/<folder>
TYPES = {"phone": "phoneScreenshots", "seven-inch": "sevenInchScreenshots", "ten-inch": "tenInchScreenshots"}
MIN_IMAGES = 2
IMAGE_SUFFIXES = (".png", ".jpg", ".jpeg")


def _count(folder: Path) -> int:
    return sum(1 for p in folder.glob("*") if p.suffix.lower() in IMAGE_SUFFIXES) if folder.is_dir() else 0


def scan(src: Path) -> dict[str, dict[str, int]]:
    """locale -> image count per type, for every locale folder under `src/android/` that has any image."""
    root = Path(src) / "android"
    out = {}
    for loc in sorted(p for p in root.iterdir() if p.is_dir()) if root.is_dir() else []:
        counts = {t: _count(loc / t) for t in TYPES}
        if any(counts.values()):
            out[loc.name] = counts
    return out


def check(src: Path) -> tuple[dict[str, dict[str, int]], list[str]]:
    """(complete locales with counts, names of partial locales)."""
    ok, partial = {}, []
    for loc, counts in scan(src).items():
        if all(n >= MIN_IMAGES for n in counts.values()):
            ok[loc] = counts
        else:
            partial.append(loc)
    return ok, partial


def copy(src: Path, dest: Path) -> dict[str, int]:
    """Replace the three screenshot folders of every complete locale; nothing else in dest is touched."""
    ok, _ = check(src)
    copied = {}
    for loc in ok:
        n = 0
        for folder, name in TYPES.items():
            target = Path(dest) / loc / "images" / name
            shutil.rmtree(target, ignore_errors=True)
            target.mkdir(parents=True)
            for img in sorted(p for p in (Path(src) / "android" / loc / folder).iterdir()
                              if p.suffix.lower() in IMAGE_SUFFIXES):
                shutil.copyfile(img, target / img.name)
                n += 1
        copied[loc] = n
    return copied


def register(sub) -> None:
    p = sub.add_parser("fastlane", help="copy store-screenshots/android into fastlane/metadata/android")
    p.add_argument("--check", action="store_true", help="only report counts; exit 1 on a partial locale")
    p.add_argument("--src", help="store-screenshots folder (default: the submodule)")
    p.add_argument("--dest", help="fastlane/metadata/android folder (default: the repository's)")
    p.set_defaults(handler=cmd_fastlane)


def cmd_fastlane(args) -> int:
    import sys
    from .pipeline import submodule_dir
    from .state import repo_root
    src = Path(args.src) if args.src else submodule_dir()
    dest = Path(args.dest) if args.dest else repo_root() / "fastlane" / "metadata" / "android"
    ok, partial = check(src)
    for loc, counts in {**ok, **{p: scan(src)[p] for p in partial}}.items():
        print(f"{loc}: " + ", ".join(f"{t} {n}" for t, n in counts.items()))
    if partial:
        print("partial screenshot sets (need all three types with at least "
              f"{MIN_IMAGES} images): {', '.join(partial)}", file=sys.stderr)
    if args.check:
        return 1 if partial else 0
    if partial:
        return 1
    copied = copy(src, dest)
    print(f"copied {sum(copied.values())} images for {len(copied)} locales into {dest}")
    return 0
