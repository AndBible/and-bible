#!/usr/bin/env python3
"""Write a translated SRT from the English SRT's timings and a cues YAML
(`cues: [{n: 1, text: ...}, ...]`, as written by the translating agent).
Fails (exit 1, nothing written) when the cue count differs.

Usage: srt_merge.py <english.srt> <cues.yaml> <out.srt>
"""
import os
import sys

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srt_common import format_srt, parse_srt


def main():
    if len(sys.argv) != 4:
        print("Usage: srt_merge.py <english.srt> <cues.yaml> <out.srt>", file=sys.stderr)
        return 2
    en = parse_srt(open(sys.argv[1], encoding="utf-8").read())
    data = yaml.safe_load(open(sys.argv[2], encoding="utf-8")) or {}
    cues = sorted(data.get("cues") or [], key=lambda c: c["n"])
    if len(cues) != len(en) or [c["n"] for c in cues] != list(range(1, len(en) + 1)):
        print(f"cue count differs: English {len(en)}, cues file {len(cues)}")
        return 1
    merged = [(t, str(c["text"]).strip()) for (t, _), c in zip(en, cues)]
    with open(sys.argv[3], "w", encoding="utf-8") as f:
        f.write(format_srt(merged))
    print(f"wrote {sys.argv[3]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
