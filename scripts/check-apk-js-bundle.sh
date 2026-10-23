#!/usr/bin/env bash
# Inspect ZIP entries in place: never extract or execute APK contents.
set -euo pipefail
exec python3 -I - "$@" <<'PY'
import collections
import hashlib
from html.parser import HTMLParser
import pathlib
import posixpath
import re
import sys
import zipfile

expect = sys.argv[2] if len(sys.argv) > 2 else "missing"
evidence = f"expect={expect}"


class ModuleScripts(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "script" and attrs.get("type") == "module" and "src" in attrs:
            self.sources.append(attrs["src"])


try:
    if len(sys.argv) != 3 or expect not in ("debug", "production"):
        raise ValueError("usage: check-apk-js-bundle.sh <apk> <debug|production>")
    apk = pathlib.Path(sys.argv[1])
    evidence += f" apk={apk} sha256={hashlib.sha256(apk.read_bytes()).hexdigest()}"
    prefix = "assets/bibleview-js/"
    with zipfile.ZipFile(apk) as archive:
        names = [name for name in archive.namelist() if name.startswith(prefix) and not name.endswith("/")]
        if any(count != 1 for count in collections.Counter(names).values()):
            raise ValueError("duplicate BibleView ZIP entries")
        parser = ModuleScripts()
        parser.feed(archive.read(prefix + "index.html").decode("utf-8"))
        if len(parser.sources) != 1:
            raise ValueError(f"expected one module entry, found {len(parser.sources)}")
        source = parser.sources[0]
        if not re.fullmatch(r"(?:\./)?assets/[^/]+\.js", source):
            raise ValueError(f"unexpected module entry path: {source}")
        entry = prefix + posixpath.normpath(source)
        main = archive.read(entry).decode("utf-8")
        # Known-format Vite title marker, not a JS parser or bundle-integrity check.
        labels = re.findall(r'document\.title\s*=\s*`[^`\n]* \((debug|production)\)`', main)
        js = [archive.read(name) for name in names if name.endswith(".js")]
        map_pattern = rb'(?:\/{2}|\/\*)[#@]\s*sourceMappingURL\s*=\s*'
        inline_pattern = map_pattern + rb'data:'
        inline_maps = sum(len(re.findall(inline_pattern, data)) for data in js)
        entry_inline_maps = len(re.findall(inline_pattern, main.encode("utf-8")))
        map_files = sum(name.endswith(".map") for name in names)
        map_refs = sum(len(re.findall(map_pattern, data)) for data in js)
        evidence += f" entry={entry} labels={labels} js_files={len(js)} inline_maps={inline_maps} entry_inline_maps={entry_inline_maps} map_refs={map_refs} map_files={map_files}"
        if labels != [expect]:
            raise ValueError("missing, ambiguous or mismatched main entry label")
        if expect == "debug" and entry_inline_maps == 0:
            raise ValueError("debug entry has no inline source maps")
        if expect == "production" and (map_refs or map_files):
            raise ValueError("production bundle contains source maps")
    print(f"PASS {evidence}")
except (OSError, ValueError, KeyError, zipfile.BadZipFile, RuntimeError) as error:
    print(f"FAIL {evidence} error={error}", file=sys.stderr)
    sys.exit(1)
PY
