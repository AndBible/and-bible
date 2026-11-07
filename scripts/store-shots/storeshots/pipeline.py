"""Framing of recorded raws and the `frame`, `status` and `promote` subcommands."""
from __future__ import annotations

import argparse
from pathlib import Path

import yaml

from . import frame
from .errors import ShotError
from .locales import locale_for
from .manifest import load_manifest
from .state import RunState, repo_root, work_root
from .targets import PLATFORM_PREFIX, TARGETS, platform_targets
from .validate import validate_final


def submodule_dir() -> Path:
    return repo_root() / "store-screenshots"


def load_captions(lang: str, sub: Path) -> dict[str, str]:
    path = sub / "captions" / f"{lang}.yaml"
    if not path.exists():
        raise ShotError("captions", f"{path} not found")
    return yaml.safe_load(path.read_text())["captions"]


def frame_platform(state: RunState, shots, captions: dict[str, str], platform: str,
                   only: set[str] | None = None) -> tuple[list[Path], list[tuple[str, str]]]:
    """Frame every recorded raw of the platform's targets. Returns (framed, not_recorded).
    web-phone reuses the play-phone raw when it has none of its own."""
    tag = locale_for(state.lang).app
    framed, skipped = [], []
    for target in platform_targets(platform):
        for shot in shots:
            if target.id not in shot.targets or (only and shot.id not in only):
                continue
            raw = state.recorded(shot.id, target.id)
            if raw is None and target.id == "web-phone":
                raw = state.recorded(shot.id, "play-phone")
            if raw is None:
                skipped.append((shot.id, target.id))
                continue
            if shot.caption_key not in captions:
                raise ShotError(shot.id, f"no caption {shot.caption_key!r} in captions/{state.lang}.yaml")
            out = state.framed_path(target.id, shot.id)
            frame.frame_shot(raw, target, captions[shot.caption_key], tag, out)
            validate_final(out, target)
            framed.append(out)
    return framed, skipped


def cmd_frame(args) -> int:
    sub = submodule_dir()
    shots = load_manifest(sub / "shots.yaml")
    state = RunState(work_root(), args.lang)
    only = set(args.shots.split(",")) if args.shots else None
    framed, skipped = frame_platform(state, shots, load_captions(args.lang, sub), args.platform, only)
    for p in framed:
        print(f"framed {p}")
    for shot, target in skipped:
        print(f"no raw yet: {shot} / {target}")
    return 0


def cmd_status(args) -> int:
    sub = submodule_dir()
    shots = load_manifest(sub / "shots.yaml")
    state = RunState(work_root(), args.lang)
    complete = True
    for tid in TARGETS:
        used = [s for s in shots if tid in s.targets]
        if not used:
            continue
        missing = state.missing(used, [tid])
        have_framed = sum(state.framed_path(tid, s.id).exists() for s in used)
        complete &= not missing and have_framed == len(used)
        print(f"{tid}: raw {len(used) - len(missing)}/{len(used)}, framed {have_framed}/{len(used)}"
              + (f", missing: {', '.join(s for s, _ in missing)}" if missing else ""))
    print("complete" if complete else "incomplete")
    return 0 if complete else 1


def cmd_promote(args) -> int:
    sub = submodule_dir()
    shots = load_manifest(sub / "shots.yaml")
    state = RunState(work_root(), args.lang)
    dest = Path(args.dest) if args.dest else (sub / "review" / args.lang if args.review else sub)
    if not state.promote(shots, dest):
        print(f"{args.lang}: incomplete, nothing promoted (see `status`)")
        return 1
    print(f"promoted {args.lang} into {dest}")
    return 0


def register(sub) -> None:
    p = sub.add_parser("frame", help="frame all recorded raws of a platform")
    p.add_argument("--lang", required=True)
    p.add_argument("--platform", required=True, choices=sorted(PLATFORM_PREFIX))
    p.add_argument("--shots", help="comma separated shot ids")
    p.set_defaults(handler=cmd_frame)
    p = sub.add_parser("status", help="recorded/framed counts per target")
    p.add_argument("--lang", required=True)
    p.set_defaults(handler=cmd_status)
    p = sub.add_parser("promote", help="copy a complete language into the store layout")
    p.add_argument("--lang", required=True)
    p.add_argument("--review", action="store_true", help="promote into store-screenshots/review/<lang>")
    p.add_argument("--dest", help="explicit destination root")
    p.set_defaults(handler=cmd_promote)
