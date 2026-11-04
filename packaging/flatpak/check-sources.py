#!/usr/bin/env python3
"""Fail when a freshly generated Gradle source list needs files the committed list lacks."""
import json
import posixpath
import sys


def keys(path):
    with open(path, encoding="utf-8") as source:
        return {
            (posixpath.normpath(f'{i["dest"]}/{i["dest-filename"]}'), i.get("sha512") or i.get("sha256"))
            for i in json.load(source)
        }


committed, generated = keys(sys.argv[1]), keys(sys.argv[2])
missing = sorted(generated - committed)
if missing:
    print(f"packaging/flatpak/gradle-sources.json is stale: {len(missing)} entries missing or changed:", file=sys.stderr)
    for path, _ in missing[:40]:
        print(f"  {path}", file=sys.stderr)
    if len(missing) > 40:
        print(f"  ... and {len(missing) - 40} more", file=sys.stderr)
    print("Run: bash packaging/flatpak/update-sources.sh  and commit the result.", file=sys.stderr)
    sys.exit(1)
print(f"gradle-sources.json covers all {len(generated)} generated entries.")
