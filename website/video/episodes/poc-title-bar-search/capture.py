#!/usr/bin/env python3
"""Scripted capture of the clips of this episode on the container emulator (see NOTES.md for the pinned build).

    uv run --no-project --with pyyaml python website/video/episodes/poc-title-bar-search/capture.py

Reseeds the demo data, drives the real app from the reading view through search (burger menu, Find, a typed
query, the results sheet, a result, Back) while `storeshots screenrecord` records, then trims the recording into
the clips under .local/video-media/poc-title-bar-search/captures/. A retake needs only this script.
Prerequisites: emulator booted, debug APK installed, Sword modules pushed, the BSB search index created once.
"""
import json
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(REPO / "scripts/store-shots"))

from storeshots.android_driver import Device, open_app  # noqa: E402
from storeshots.record import trim_cmd  # noqa: E402

EPISODE = "poc-title-bar-search"
MEDIA = REPO / ".local/video-media" / EPISODE
RAW = REPO / ".local/video-cache/raw-captures" / EPISODE / "search-session.mp4"
DEMO = REPO / ".local/store-shots/en/demo.abdb.zip"
QUERY = "light"

# Tap targets of the 1440x3120 phone AVD (the results sheet is not visible to uiautomator).
BURGER = (84, 243)
FIND = (253, 831)
FIRST_RESULT = (400, 1500)
CLOSE_SEARCH = (84, 243)


def main() -> int:
    dev = Device(REPO)
    subprocess.run([str(REPO / "scripts/store-shots/android-seed.sh"), str(DEMO), dev.pkg], check=True)
    open_app(dev)
    dev.shell("settings put system show_touches 1")
    RAW.parent.mkdir(parents=True, exist_ok=True)
    rec = subprocess.Popen([str(REPO / "scripts/store-shots/run.sh"), "screenrecord", "--out", str(RAW), "--seconds", "45", "--size", "1080x2340"],
                           stdin=subprocess.DEVNULL)
    try:
        for _ in range(60):  # wait until screenrecord runs on the device
            if dev.shell("pidof screenrecord", check=False).strip():
                break
            time.sleep(0.5)
        t0 = time.monotonic()
        marks: dict[str, float] = {}

        def mark(name: str) -> None:
            marks[name] = round(time.monotonic() - t0, 2)

        time.sleep(2.0)
        mark("tap_menu"); dev.tap(*BURGER); time.sleep(2.0)
        mark("tap_find"); dev.tap(*FIND); time.sleep(2.0)
        mark("type"); dev.shell(f"input text {QUERY}"); time.sleep(1.5)
        mark("enter"); dev.shell("input keyevent 66"); time.sleep(5.0)
        mark("tap_result"); dev.tap(*FIRST_RESULT); time.sleep(3.5)
        mark("tap_close_search"); dev.tap(*CLOSE_SEARCH); time.sleep(2.0)
        mark("back"); dev.shell("input keyevent 4"); time.sleep(3.5)
        mark("end")
    finally:
        dev.shell("pkill -2 screenrecord", check=False)
        rec.wait(timeout=90)
        dev.shell("settings put system show_touches 0", check=False)
    (RAW.parent / "marks.json").write_text(json.dumps(marks, indent=2))
    captures = MEDIA / "captures"
    captures.mkdir(parents=True, exist_ok=True)
    cuts = {  # clip: (from mark, offset, to mark, offset)
        "search-open": ("tap_menu", -0.8, "enter", 0.0),
        "search-results": ("enter", -0.3, "tap_result", 0.0),
        "result-and-back": ("tap_result", -0.5, "end", 0.0),
    }
    for name, (a, da, b, db) in cuts.items():
        subprocess.run(trim_cmd(RAW, captures / f"{name}.mp4", max(0.0, marks[a] + da), marks[b] + db),
                       check=True, capture_output=True)
        print(captures / f"{name}.mp4")
    return 0


if __name__ == "__main__":
    sys.exit(main())
