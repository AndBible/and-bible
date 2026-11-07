"""Contact sheets for the maintainer's look review: every framed image of a platform on one PNG."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from .manifest import Shot, load_manifest
from .state import RunState, work_root as default_work_root
from .targets import PLATFORM_PREFIX, TARGETS

MAX_WIDTH = 2400
TILE_HEIGHT = 520
GAP = 24
LABEL_HEIGHT = 40
BACKGROUND = (232, 232, 232)


@dataclass(frozen=True)
class Tile:
    label: str      # "<target> / <shot id>"
    path: Path


def contact_tiles(lang: str, work_root: Path, shots: list[Shot],
                  targets: list[str] | None = None) -> dict[str, list[Tile]]:
    """Existing framed images per platform, targets in TARGETS order and shots in manifest order."""
    state = RunState(work_root, lang)
    out: dict[str, list[Tile]] = {p: [] for p in PLATFORM_PREFIX}
    for platform, prefix in PLATFORM_PREFIX.items():
        for tid in TARGETS:
            if not tid.startswith(prefix) or (targets is not None and tid not in targets):
                continue
            for shot in shots:
                if tid in shot.targets and state.framed_path(tid, shot.id).exists():
                    out[platform].append(Tile(f"{tid} / {shot.id}", state.framed_path(tid, shot.id)))
    return out


def _font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.load_default(size=size)
    except TypeError:  # Pillow < 10.1
        return ImageFont.load_default()


def _render(tiles: list[Tile], out: Path) -> Path:
    font = _font(22)
    placed, x, y, row_h = [], GAP, GAP, 0
    for tile in tiles:
        with Image.open(tile.path) as src:
            img = src.convert("RGB")
        scale = min(1.0, TILE_HEIGHT / img.height, (MAX_WIDTH - 2 * GAP) / img.width)
        img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
        if x + img.width + GAP > MAX_WIDTH and x > GAP:
            x, y, row_h = GAP, y + row_h + LABEL_HEIGHT + GAP, 0
        placed.append((tile, img, x, y))
        x += img.width + GAP
        row_h = max(row_h, img.height)
    height = y + row_h + LABEL_HEIGHT + GAP
    sheet = Image.new("RGB", (MAX_WIDTH, height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    for tile, img, px, py in placed:
        sheet.paste(img, (px, py))
        draw.text((px, py + img.height + 8), tile.label, fill=(40, 40, 40), font=font)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


def contact_sheet(lang: str, out: Path, work_root: Path | None = None, targets: list[str] | None = None,
                  shots: list[Shot] | None = None) -> list[Path]:
    """One `contact-<platform>.png` in `out` per platform that has framed images; returns the files."""
    if shots is None:
        from .pipeline import submodule_dir
        shots = load_manifest(submodule_dir() / "shots.yaml")
    tiles = contact_tiles(lang, Path(work_root) if work_root else default_work_root(), shots, targets)
    return [_render(t, Path(out) / f"contact-{platform}.png") for platform, t in tiles.items() if t]


def register(sub) -> None:
    p = sub.add_parser("contact", help="contact sheets (one PNG per platform) of a language's framed images")
    p.add_argument("--lang", required=True)
    p.add_argument("--out", help="folder (default store-screenshots/review/<lang>/)")
    p.set_defaults(handler=cmd_contact)


def cmd_contact(args) -> int:
    from .pipeline import submodule_dir
    out = Path(args.out) if args.out else submodule_dir() / "review" / args.lang
    paths = contact_sheet(args.lang, out)
    for p in paths:
        print(p)
    if not paths:
        print(f"no framed images for {args.lang}: run `frame` first")
    return 0 if paths else 1
