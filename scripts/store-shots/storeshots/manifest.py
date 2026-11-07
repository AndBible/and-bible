"""Shot manifest (`store-screenshots/shots.yaml`): which UI state to capture for which targets."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml

from .targets import TARGETS

ACTIONS = ("none", "book_chooser", "label_dialog", "verse_menu", "note", "search", "workspaces")
ACTIONS_WITH_ARG = ("label_dialog", "verse_menu", "search")
PLAY_RANGE = (2, 8)
APPSTORE_MAX = 10


class ManifestError(ValueError):
    pass


@dataclass(frozen=True)
class Shot:
    id: str
    workspace: str            # workspace name in the demo backup (== id)
    action: str               # scripted UI action after switching workspace
    action_arg: str | None
    night: bool
    targets: list[str]
    caption_key: str          # key in captions/<lang>.yaml


def check_counts(shots: list[Shot]) -> None:
    for tid, target in TARGETS.items():
        n = sum(tid in s.targets for s in shots)
        if target.store == "play" and n and not PLAY_RANGE[0] <= n <= PLAY_RANGE[1]:
            raise ManifestError(f"{tid}: {n} shots, Play allows {PLAY_RANGE[0]}-{PLAY_RANGE[1]}")
        if target.store == "appstore" and n > APPSTORE_MAX:
            raise ManifestError(f"{tid}: {n} shots, the App Store allows at most {APPSTORE_MAX}")


def load_manifest(path: Path) -> list[Shot]:
    data = yaml.safe_load(Path(path).read_text())
    shots: list[Shot] = []
    seen: set[str] = set()
    for raw in data["shots"]:
        sid = raw["id"]
        if sid in seen:
            raise ManifestError(f"duplicate shot id: {sid}")
        seen.add(sid)
        action = raw.get("action", "none")
        if action not in ACTIONS:
            raise ManifestError(f"{sid}: unknown action {action!r} (allowed: {', '.join(ACTIONS)})")
        arg = raw.get("action_arg")
        if action in ACTIONS_WITH_ARG and not arg:
            raise ManifestError(f"{sid}: action {action} needs an action_arg")
        targets = list(raw["targets"])
        for t in targets:
            if t not in TARGETS:
                raise ManifestError(f"{sid}: unknown target {t!r}")
        shots.append(Shot(sid, raw.get("workspace", sid), action, arg, bool(raw.get("night", False)),
                          targets, raw.get("caption_key", sid)))
    check_counts(shots)
    return shots
