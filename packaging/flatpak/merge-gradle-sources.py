#!/usr/bin/env python3
"""Merge Maven sources, preserving differing bytes under separate repo roots."""
from collections import defaultdict
import json
import sys

# These marker POMs were inspected: only descriptive metadata differs, not dependencies.
# Any new collision needs review instead of arbitrary repository precedence.
REVIEWED_EQUIVALENT_DIGESTS = frozenset({
    '2f9f0f5ec17b42578192e57281c01b05958950c1518cfce57ae8c5493580e770f5646f6c8a712ec3e9f235a5d3c529d19d1f6a1d9970ae5ac3c6e5fe29587c2d',
    'cd5aa95e57a6b6c0d82631d37ac48f776ea70a73bfbb868c2f13ffd2966cf4025c45dace726f7e029a47255c505746f0aba0a955d928da6e0dad62cfa67c627e',
})

sources = defaultdict(dict)
for path in sorted(sys.argv[2:]):
    with open(path, encoding="utf-8") as source:
        for item in json.load(source):
            key = (item.get("dest"), item.get("dest-filename"))
            digest = item.get("sha512") or item.get("sha256")
            if item.get("type") != "file" or not digest:
                raise ValueError(f"Unpinned Maven source: {item}")
            candidates = sources[key]
            previous = candidates.get(digest)
            if previous is None or item["url"] < previous["url"]:
                candidates[digest] = item
merged = []
for key, candidates in sorted(sources.items()):
    if len(candidates) > 1 and frozenset(candidates) != REVIEWED_EQUIVALENT_DIGESTS:
        raise ValueError(f'Conflicting Maven content requires review: {key}')
    for index, (_, item) in enumerate(sorted(candidates.items())):
        item = dict(item)
        if index:
            root, separator, relative = item["dest"].partition("/")
            item["dest"] = f"{root}-alternate-{index}{separator}{relative}"
        merged.append(item)
if not merged:
    raise ValueError("No Gradle sources generated")
with open(sys.argv[1], "w", encoding="utf-8") as output:
    json.dump(merged, output, indent=4)
    output.write("\n")
