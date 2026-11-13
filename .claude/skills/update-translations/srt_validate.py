#!/usr/bin/env python3
"""Check a translated SRT against the English one: same cue count, identical timing
lines, no empty cue text. Exit 0 when fine, 1 printing the first offending cue.

Usage: srt_validate.py <english.srt> <translated.srt>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srt_common import read_srt


def problem(en, tr):
    if len(en) != len(tr):
        return f"cue count differs: English {len(en)}, translation {len(tr)}"
    for i, ((et, _), (tt, tx)) in enumerate(zip(en, tr), 1):
        if et.strip() != tt.strip():
            return f"cue {i}: timing changed ({et.strip()} -> {tt.strip()})"
        if not tx.strip():
            return f"cue {i}: empty text"
    return None


def main():
    if len(sys.argv) != 3:
        print("Usage: srt_validate.py <english.srt> <translated.srt>", file=sys.stderr)
        return 2
    read = read_srt
    msg = problem(read(sys.argv[1]), read(sys.argv[2]))
    if msg:
        print(f"INVALID {sys.argv[2]}: {msg}")
        return 1
    print(f"OK {sys.argv[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
