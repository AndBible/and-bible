"""Screen recording of the emulator for the video pipeline: `record` subcommand plus pure command builders."""
from __future__ import annotations

import subprocess
import sys
import threading
import time
from pathlib import Path

MAX_SECONDS = 180  # screenrecord's own limit
REMOTE = "/sdcard/andbible-rec.mp4"


def screenrecord_cmd(seconds: int, size: str | None, bitrate: int = 12_000_000, remote: str = REMOTE) -> list[str]:
    """argv of `screenrecord` as run by `adb shell`."""
    if seconds > MAX_SECONDS:
        raise ValueError(f"screenrecord is limited to {MAX_SECONDS} s, got {seconds}")
    if seconds < 1:
        raise ValueError(f"seconds must be positive, got {seconds}")
    argv = ["screenrecord", "--time-limit", str(seconds), "--bit-rate", str(bitrate)]
    if size:
        argv += ["--size", size]
    return argv + [remote]


def trim_cmd(src: Path, dst: Path, start: float, end: float) -> list[str]:
    """ffmpeg re-encode of [start, end] seconds to H.264 at the source resolution, no audio."""
    if end <= start:
        raise ValueError(f"empty range {start}..{end}")
    return ["ffmpeg", "-y", "-ss", f"{start:.3f}", "-to", f"{end:.3f}", "-i", str(src), "-an",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "medium", "-crf", "18", str(dst)]


def register(sub) -> None:
    p = sub.add_parser("screenrecord", help="record the emulator screen (demo-mode status bar) to an mp4")
    p.add_argument("--out", required=True, type=Path)
    p.add_argument("--seconds", required=True, type=int, help=f"maximum length (up to {MAX_SECONDS}); Enter stops earlier")
    p.add_argument("--seed", type=Path, help="demo .abdb.zip to reseed the app with first")
    p.add_argument("--night", action="store_true")
    p.add_argument("--landscape", action="store_true")
    p.add_argument("--size", help="WxH passed to screenrecord (default: native)")
    p.set_defaults(handler=cmd_record)


def should_stop_on_read(line: str, is_tty: bool) -> bool:
    """Enter stops the recording only from an interactive stdin; EOF or a non-tty never stops it early."""
    return is_tty and line != ""


def cmd_record(args) -> int:
    import shlex
    from .android_driver import Device, exit_demo_mode, prepare_device, set_night
    from .errors import ShotError
    REPO = Path(__file__).resolve().parents[3]

    try:
        remote_argv = screenrecord_cmd(args.seconds, args.size)
    except ValueError as e:
        raise ShotError(str(e)) from e
    dev = Device(REPO)
    if args.seed:
        try:
            subprocess.run([str(REPO / "scripts/store-shots/android-seed.sh"), str(args.seed), dev.pkg],
                           check=True, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        except subprocess.CalledProcessError as e:
            raise ShotError(f"seeding failed: {e.stderr or e}") from e
    args.out.parent.mkdir(parents=True, exist_ok=True)
    proc = None
    stopped = False
    try:
        prepare_device(dev, "en", args.landscape)
        if args.night:
            set_night(dev, True)
        proc = subprocess.Popen([str(REPO / "scripts/with-container-adb.sh"), "adb", "shell", shlex.join(remote_argv)],
                                stdin=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        print(f"recording up to {args.seconds} s: press Enter to stop", flush=True)
        stop = threading.Event()
        is_tty = sys.stdin.isatty()

        def watch() -> None:
            if should_stop_on_read(sys.stdin.readline() if is_tty else "", is_tty):
                stop.set()

        threading.Thread(target=watch, daemon=True).start()
        stop.wait(args.seconds + 1)
        # SIGINT lets screenrecord finalise the mp4 header.
        dev.shell("pkill -2 screenrecord", check=False)
        try:
            _, err = proc.communicate(timeout=30)
        except subprocess.TimeoutExpired as e:
            raise ShotError(f"screenrecord did not stop within 30 s: {e.stderr or ''}") from e
        stopped = True
        time.sleep(1)
        dev.adb("pull", REMOTE, str(args.out))
        print(args.out)
    finally:
        if not stopped:
            dev.shell("pkill -2 screenrecord", check=False)
        if proc and proc.poll() is None:
            proc.kill()
        dev.shell(f"rm -f {REMOTE}", check=False)
        restores = [lambda: exit_demo_mode(dev)]
        if args.night:
            restores.insert(0, lambda: set_night(dev, False))
        for restore in restores:
            try:
                restore()
            except Exception as e:  # noqa: BLE001
                print(f"record: cleanup failed: {e}", file=sys.stderr)
    return 0
