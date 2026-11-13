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
from srt_common import format_srt, read_srt


def main():
    if len(sys.argv) != 4:
        print("Usage: srt_merge.py <english.srt> <cues.yaml> <out.srt>", file=sys.stderr)
        return 2
    en = read_srt(sys.argv[1])
    try:
        with open(sys.argv[2], encoding="utf-8-sig") as f:
            data = yaml.safe_load(f) or {}
        raw = data["cues"]
        cues = []
        for c in raw:
            if not isinstance(c["n"], int) or isinstance(c["n"], bool):
                raise ValueError(f"cue number {c['n']!r} is not an integer")
            cues.append({"n": c["n"], "text": c["text"]})
        cues.sort(key=lambda c: c["n"])
    except Exception as e:  # malformed YAML or shape
        print(f"cue file invalid: {type(e).__name__}: {e}".splitlines()[0])
        return 1
    if len(cues) != len(en) or [c["n"] for c in cues] != list(range(1, len(en) + 1)):
        print(f"cue count differs: English {len(en)}, cues file {len(cues)}")
        return 1
    for c in cues:
        if not isinstance(c["text"], str) or not c["text"].strip():
            print(f"cue {c['n']}: text must be a non-blank string, got {c['text']!r} "
                  "(double-quote every cue text; unquoted No/Yes/~ are not strings)")
            return 1
    merged = [(t, c["text"].strip()) for (t, _), c in zip(en, cues)]
    with open(sys.argv[3], "w", encoding="utf-8") as f:
        f.write(format_srt(merged))
    print(f"wrote {sys.argv[3]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
