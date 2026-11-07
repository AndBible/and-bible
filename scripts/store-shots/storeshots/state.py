"""Resumable per-language run state and promotion of complete sets into the store layout."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path

from .layout import extension, target_folder
from .locales import locale_for
from .manifest import Shot
from .targets import TARGETS


def repo_root() -> Path:
    here = Path(__file__).resolve().parent
    try:
        out = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=here, capture_output=True,
                             text=True, check=True).stdout.strip()
        return Path(out)
    except (OSError, subprocess.CalledProcessError):
        return here.parents[2]


def work_root() -> Path:
    return Path(os.environ.get("STORE_SHOTS_WORK") or repo_root() / ".local" / "store-shots")


class RunState:
    """`<root>/<lang>/state.json` plus raw/ and framed/ folders per target."""

    def __init__(self, root: Path, lang: str):
        self.lang = lang
        self.dir = Path(root) / lang
        self.file = self.dir / "state.json"
        self.raws: dict[str, dict[str, str]] = {}  # target -> shot -> raw path
        if self.file.exists():
            self.raws = json.loads(self.file.read_text()).get("raw", {})

    def raw_path(self, target_id: str, shot_id: str) -> Path:
        return self.dir / "raw" / target_id / f"{shot_id}.png"

    def framed_path(self, target_id: str, shot_id: str) -> Path:
        return self.dir / "framed" / target_id / f"{shot_id}.{extension(TARGETS[target_id])}"

    def record(self, shot_id: str, target_id: str, raw: Path) -> None:
        self.raws.setdefault(target_id, {})[shot_id] = str(raw)
        self.dir.mkdir(parents=True, exist_ok=True)
        tmp = self.file.with_suffix(".json.tmp")
        tmp.write_text(json.dumps({"raw": self.raws}, indent=1, sort_keys=True))
        tmp.replace(self.file)

    def recorded(self, shot_id: str, target_id: str) -> Path | None:
        p = self.raws.get(target_id, {}).get(shot_id)
        return Path(p) if p and Path(p).exists() else None

    def missing(self, shots: list[Shot], targets: list[str]) -> list[tuple[str, str]]:
        return [(s.id, t) for t in targets for s in shots
                if t in s.targets and self.recorded(s.id, t) is None]

    def _jobs(self, shots: list[Shot]) -> dict[str, list[Shot]]:
        locale = locale_for(self.lang)
        out: dict[str, list[Shot]] = {}
        for tid, target in TARGETS.items():
            used = [s for s in shots if tid in s.targets]
            if used and (target.store != "website" or locale.app == "en"):
                out[tid] = used
        return out

    def promote(self, shots: list[Shot], dest_root: Path) -> bool:
        """Copy framed images into dest_root's store layout, only if every shot of every target
        is recorded and framed. Replaces each target folder atomically (rename); returns False and
        touches nothing otherwise."""
        locale = locale_for(self.lang)
        jobs = self._jobs(shots)
        for tid, used in jobs.items():
            for s in used:
                if self.recorded(s.id, tid) is None or not self.framed_path(tid, s.id).exists():
                    return False
        dest_root = Path(dest_root)
        for tid, used in jobs.items():
            target = TARGETS[tid]
            try:
                folder = dest_root / target_folder(target, locale)
            except ValueError:
                continue  # no listing for this locale and store: English fallback is used there
            folder.parent.mkdir(parents=True, exist_ok=True)
            tmp = folder.with_name(folder.name + ".tmp")
            shutil.rmtree(tmp, ignore_errors=True)
            tmp.mkdir()
            for i, s in enumerate(used, 1):
                shutil.copyfile(self.framed_path(tid, s.id), tmp / f"{i:02d}.{extension(target)}")
            old = folder.with_name(folder.name + ".old")
            shutil.rmtree(old, ignore_errors=True)
            if folder.exists():
                folder.rename(old)
            tmp.rename(folder)
            shutil.rmtree(old, ignore_errors=True)
        return True
