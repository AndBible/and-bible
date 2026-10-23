#!/usr/bin/env python3
"""Merge the manifest entries of one scenario into the committed fixture manifest.

Usage: merge_manifest.py <committed manifest.json> <new manifest.json> <scenario>

`package_fixtures.py` writes a manifest of whatever is below the generated tree, which on the Mac may be only
some cells. To add one scenario without touching the committed zips, the committed manifest keeps all its
entries; the entries of <scenario> are taken from the new manifest (added or replaced). The result is written
back to the committed manifest in the same format as `package_fixtures.py` (sorted keys, entries sorted by
app, ios, scenario).
"""
import json
import sys


def merge(committed, new, scenario):
    keep = [e for e in committed if e["scenario"] != scenario]
    added = [e for e in new if e["scenario"] == scenario]
    if not added:
        raise SystemExit(f"no entries for scenario {scenario!r} in the new manifest")
    return sorted(keep + added, key=lambda e: (e["app"], e["ios"], e["scenario"]))


def main(argv):
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    with open(argv[1]) as f:
        committed = json.load(f)
    with open(argv[2]) as f:
        new = json.load(f)
    merged = merge(committed, new, argv[3])
    with open(argv[1], "w") as f:
        json.dump(merged, f, indent=2, sort_keys=True)
        f.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
