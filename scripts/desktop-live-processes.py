#!/usr/bin/env python3
"""Start and stop owned Linux desktop process groups using /proc identity."""

import argparse
import json
import os
import re
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path

NAME_RE = re.compile(r"[A-Za-z0-9-]+\Z")
WAIT_SECONDS = 5


def parse_proc_stat(text):
    """Parse fields after comm, which may contain spaces and ')' characters."""
    close = text.rfind(")")
    if close < 0:
        raise ValueError("malformed proc stat")
    fields = text[close + 2 :].split()
    if len(fields) <= 19:
        raise ValueError("malformed proc stat")
    return {"state": fields[0], "pgid": int(fields[2]), "starttime": int(fields[19])}


def proc_identity(pid):
    try:
        return parse_proc_stat(Path(f"/proc/{pid}/stat").read_text())
    except (OSError, ValueError, IndexError):
        return None


def validate(run_dir, name):
    if not NAME_RE.fullmatch(name):
        raise ValueError("name must contain only letters, digits, and hyphens")
    path = Path(run_dir)
    if not path.is_dir():
        raise ValueError("run directory must already exist")
    return path


def record_path(path, name):
    return path / f"{name}.process.json"


def load_record(path):
    try:
        record = json.loads(path.read_text())
        if not all(type(record[key]) is int and record[key] > 0 for key in ("pid", "pgid", "starttime")):
            return None
        return record
    except (OSError, ValueError, KeyError, TypeError):
        return None


def matches(record):
    if not record:
        return None
    identity = proc_identity(record["pid"])
    if not identity or identity["state"] == "Z":
        return None
    if identity["starttime"] != record["starttime"] or identity["pgid"] != record["pgid"]:
        return None
    try:
        if os.getpgid(record["pid"]) != record["pid"] or record["pgid"] != record["pid"]:
            return None
    except (ProcessLookupError, PermissionError):
        return None
    return identity


def terminate_owned(record, child=None):
    """Terminate only while the recorded leader still has the same identity."""
    if not matches(record):
        return
    try:
        os.killpg(record["pgid"], signal.SIGTERM)
    except ProcessLookupError:
        return
    deadline = time.monotonic() + WAIT_SECONDS
    while time.monotonic() < deadline and matches(record):
        time.sleep(0.05)
    if matches(record):
        os.killpg(record["pgid"], signal.SIGKILL)
    if child is not None:
        try:
            child.wait(timeout=1)
        except subprocess.TimeoutExpired:
            pass


def start(path, name, command):
    target = record_path(path, name)
    lock = path / f".{name}.process.lock"
    try:
        fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        raise ValueError("start already in progress")
    os.close(fd)
    try:
        existing = load_record(target)
        if matches(existing):
            raise ValueError("process with this name is already alive")
        log = open(path / f"{name}.log", "ab", buffering=0)
        try:
            child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                     start_new_session=True, close_fds=True)
        finally:
            log.close()
        identity = proc_identity(child.pid)
        if identity is None or identity["pgid"] != child.pid or os.getpgid(child.pid) != child.pid:
            child.wait(timeout=1)
            raise RuntimeError("child exited or did not become its own process-group leader")
        record = {"pid": child.pid, "pgid": child.pid, "starttime": identity["starttime"]}
        temporary = None
        try:
            fd, temporary = tempfile.mkstemp(prefix=f".{name}.", suffix=".tmp", dir=path)
            with os.fdopen(fd, "w") as stream:
                json.dump(record, stream)
                stream.write("\n")
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, target)
        except BaseException:
            terminate_owned(record, child)
            raise
        finally:
            if temporary is not None:
                try:
                    os.unlink(temporary)
                except FileNotFoundError:
                    pass
        print(f"started pid={child.pid} pgid={identity['pgid']}")
    finally:
        lock.unlink(missing_ok=True)


def status(path, name):
    record = load_record(record_path(path, name))
    identity = matches(record)
    if identity:
        print(f"running pid={record['pid']} pgid={record['pgid']}")
        return 0
    print("not running (identity not verified)")
    return 3


def members(path, name):
    record = load_record(record_path(path, name))
    if not matches(record):
        print("no-op: process identity is missing, dead, or unverifiable", file=sys.stderr)
        return 3
    found = []
    for entry in Path("/proc").iterdir():
        if entry.name.isdecimal():
            identity = proc_identity(int(entry.name))
            if identity and identity["state"] != "Z" and identity["pgid"] == record["pgid"]:
                found.append(int(entry.name))
    for pid in sorted(found):
        print(pid)
    return 0


def stop(path, name):
    record = load_record(record_path(path, name))
    identity = matches(record)
    if not identity:
        print("no-op: process identity is missing, dead, or unverifiable")
        return
    try:
        os.killpg(record["pgid"], signal.SIGTERM)
    except ProcessLookupError:
        print("no-op: process group is gone")
        return
    deadline = time.monotonic() + WAIT_SECONDS
    while time.monotonic() < deadline:
        if matches(record) is None:
            print("stopped with SIGTERM; descendants may remain (leader identity is gone)")
            return
        time.sleep(0.05)
    if matches(record):
        os.killpg(record["pgid"], signal.SIGKILL)
        print("stopped with SIGKILL after timeout")
    else:
        print("stopped with SIGTERM; descendants may remain (leader identity is gone)")


def main(argv=None):
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="action", required=True)
    for action in ("start", "stop", "status", "members"):
        sub = subparsers.add_parser(action)
        sub.add_argument("run_dir")
        sub.add_argument("name")
        if action == "start":
            sub.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    try:
        path = validate(args.run_dir, args.name)
        if args.action == "start":
            command = args.command[1:] if args.command[:1] == ["--"] else args.command
            if not command:
                raise ValueError("start requires -- followed by a command")
            start(path, args.name, command)
        elif args.action == "stop":
            stop(path, args.name)
        elif args.action == "status":
            return status(path, args.name)
        elif args.action == "members":
            return members(path, args.name)
        else:
            stop(path, args.name)
        return 0
    except (OSError, ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
