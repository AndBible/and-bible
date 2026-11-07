"""uiautomator dump helpers for the Android capture driver: find a node by exact text, regex, content
description or resource id and return the centre of its bounds (to tap with `adb shell input tap`)."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET

_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


class NodeNotFound(LookupError):
    pass


def _centre(bounds: str) -> tuple[int, int]:
    m = _BOUNDS.fullmatch(bounds)
    if not m:
        raise ValueError(f"bad bounds {bounds!r}")
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_node(xml: str, text: str | None = None, text_re: str | None = None, desc: str | None = None,
              res_id: str | None = None) -> tuple[int, int]:
    """Centre of the first node (document order) matching every given key; NodeNotFound names the keys."""
    root = ET.fromstring(xml.encode() if isinstance(xml, str) else xml)
    pattern = re.compile(text_re) if text_re else None
    for node in root.iter("node"):
        a = node.attrib
        if text is not None and a.get("text") != text:
            continue
        if pattern is not None and not pattern.fullmatch(a.get("text", "")):
            continue
        if desc is not None and a.get("content-desc") != desc:
            continue
        if res_id is not None and a.get("resource-id") != res_id:
            continue
        if text is None and pattern is None and desc is None and res_id is None:
            continue
        return _centre(a["bounds"])
    keys = {k: v for k, v in (("text", text), ("text_re", text_re), ("desc", desc), ("res_id", res_id)) if v}
    raise NodeNotFound(f"no node with {keys}")


def main(argv: list[str]) -> int:
    """`python -m storeshots.android_actions <dump.xml> text=..|re=..|desc=..|id=..` prints 'x y'."""
    xml = open(argv[0], encoding="utf-8").read()
    kw = {}
    for arg in argv[1:]:
        k, v = arg.split("=", 1)
        kw[{"text": "text", "re": "text_re", "desc": "desc", "id": "res_id"}[k]] = v
    try:
        x, y = find_node(xml, **kw)
    except NodeNotFound as e:
        print(e, file=sys.stderr)
        return 1
    print(x, y)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
