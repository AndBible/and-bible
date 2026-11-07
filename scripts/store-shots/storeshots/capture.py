"""Glue between the platform capture drivers and the run state: the `plan` and `record` subcommands.

`plan` writes the JSON a driver walks through (the iOS XCUITest reads it from tmp/store-shots/plan.json):
the shots of one target, optionally only the light or only the dark ones, with the language as a BCP-47 tag.
`record` imports a driver's output folder (`<shot id>.png` files) into the per-language RunState after checking
each raw's pixel size; `--also` records the same raw for targets framed from the same capture (iPhone 6.5")."""
from __future__ import annotations

import json
import shutil
from pathlib import Path

from .errors import ShotError
from .locales import locale_for
from .manifest import Shot, load_manifest
from .state import RunState, work_root
from .targets import TARGETS
from .validate import validate_raw


def plan_for(shots: list[Shot], lang: str, target_id: str, night: bool | None) -> dict:
    if target_id not in TARGETS:
        raise ShotError("plan", f"unknown target {target_id}")
    chosen = [s for s in shots if target_id in s.targets and (night is None or s.night == night)]
    return {
        "lang": locale_for(lang).bcp47,
        "shots": [{"id": s.id, "workspace": s.workspace, "action": s.action, "action_arg": s.action_arg,
                   "night": s.night} for s in chosen],
    }


def import_raws(state: RunState, shots: list[Shot], target_id: str, src: Path,
                also: list[str] | None = None) -> list[str]:
    """Record every `<shot>.png` in src that belongs to target_id; returns the recorded shot ids.
    A raw of the wrong size raises ShotError before anything is recorded for it."""
    target = TARGETS[target_id]
    wanted = {s.id for s in shots if target_id in s.targets}
    got = []
    for png in sorted(Path(src).glob("*.png")):
        if png.stem not in wanted:
            continue
        validate_raw(png, target)
        for tid in [target_id, *(also or [])]:
            if TARGETS[tid].raw_size != target.raw_size:
                raise ShotError(png.stem, f"{tid} is not framed from {target_id} raws (raw sizes differ)")
            dest = state.raw_path(tid, png.stem)
            dest.parent.mkdir(parents=True, exist_ok=True)
            if dest.resolve() != png.resolve():
                shutil.copyfile(png, dest)
            state.record(png.stem, tid, dest)
        got.append(png.stem)
    return got


def _manifest():
    from .pipeline import submodule_dir
    return load_manifest(submodule_dir() / "shots.yaml")


def cmd_plan(args) -> int:
    night = {"any": None, "light": False, "dark": True}[args.mode]
    plan = plan_for(_manifest(), args.lang, args.target, night)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(plan, indent=1, ensure_ascii=False))
    print(f"{out}: {len(plan['shots'])} shots ({', '.join(s['id'] for s in plan['shots'])})")
    return 0


def cmd_record(args) -> int:
    state = RunState(work_root(), args.lang)
    got = import_raws(state, _manifest(), args.target, Path(args.dir), args.also or [])
    print(f"recorded {len(got)} raws for {args.target}: {', '.join(got)}")
    return 0


def register(sub) -> None:
    p = sub.add_parser("plan", help="write the shot plan of one target as JSON")
    p.add_argument("--lang", required=True)
    p.add_argument("--target", required=True)
    p.add_argument("--mode", choices=["any", "light", "dark"], default="any")
    p.add_argument("--out", required=True)
    p.set_defaults(handler=cmd_plan)
    p = sub.add_parser("record", help="import a driver's <shot>.png raws into the run state")
    p.add_argument("--lang", required=True)
    p.add_argument("--target", required=True)
    p.add_argument("--dir", required=True)
    p.add_argument("--also", action="append", help="another target framed from the same raws")
    p.set_defaults(handler=cmd_record)
