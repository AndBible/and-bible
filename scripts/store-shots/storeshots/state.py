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
from .validate import validate_final

RAW_FALLBACK = {"web-phone": "play-phone"}


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
        self.skipped_targets: list[str] = []
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

    def raw_for(self, shot_id: str, target_id: str) -> Path | None:
        """The raw a target is framed from: its own, or for web-phone the play-phone raw when it has none."""
        raw = self.recorded(shot_id, target_id)
        if raw is None and target_id in RAW_FALLBACK:
            raw = self.recorded(shot_id, RAW_FALLBACK[target_id])
        return raw

    def missing(self, shots: list[Shot], targets: list[str]) -> list[tuple[str, str]]:
        return [(s.id, t) for t in targets for s in shots
                if t in s.targets and self.raw_for(s.id, t) is None]

    def framed_ok(self, target_id: str, shot_id: str) -> bool:
        """A framed image exists and is not older than the raw it was made from."""
        framed, raw = self.framed_path(target_id, shot_id), self.raw_for(shot_id, target_id)
        return framed.exists() and raw is not None and framed.stat().st_mtime >= raw.stat().st_mtime

    def jobs(self, shots: list[Shot]) -> dict[str, list[Shot]]:
        """Targets this language is delivered for, with their shots. Targets whose store has no listing
        for the locale (see `skipped_targets`) are left out, as are website targets for non-English."""
        locale = locale_for(self.lang)
        out: dict[str, list[Shot]] = {}
        self.skipped_targets = []
        for tid, target in TARGETS.items():
            used = [s for s in shots if tid in s.targets]
            if not used or (target.store == "website" and locale.app != "en"):
                continue
            try:
                target_folder(target, locale)
            except ValueError:
                self.skipped_targets.append(tid)
                continue
            out[tid] = used
        return out

    def promote(self, shots: list[Shot], dest_root: Path) -> bool:
        """Copy framed images into dest_root's store layout, only if every shot of every target with a
        listing is recorded, framed after its raw was captured, and a valid final image. Replaces each target
        folder atomically (rename); returns False and touches nothing otherwise. Targets without a listing
        for the locale are left out and named in `skipped_targets` (the English set is used there)."""
        locale = locale_for(self.lang)
        jobs = self.jobs(shots)
        for tid, used in jobs.items():
            for s in used:
                if not self.framed_ok(tid, s.id):
                    return False
        for tid, used in jobs.items():
            for s in used:
                validate_final(self.framed_path(tid, s.id), TARGETS[tid])
        dest_root = Path(dest_root)
        for tid, used in jobs.items():
            target = TARGETS[tid]
            folder = dest_root / target_folder(target, locale)
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
