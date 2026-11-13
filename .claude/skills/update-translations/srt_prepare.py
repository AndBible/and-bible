#!/usr/bin/env python3
"""Prepare an English SRT for translation: writes tmp/translate-srt/<episode>-<name>.<LANG>.yaml
with the language's UI glossary and the numbered cue texts only (no timings).

Usage (run from repo root):
  srt_prepare.py <episode-subs-dir> <format>-<voice> LANG
Reads <episode-subs-dir>/<format>-<voice>.en.srt; the episode name is the subs dir's parent.
"""
import os
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from lang_codes import normalize_code
from srt_common import read_srt


def glossary(lang):
    """extract_glossary.py's markdown for LANG; empty text when there is no data."""
    r = subprocess.run([sys.executable, str(HERE / "extract_glossary.py"), lang],
                       capture_output=True, text=True, cwd=HERE.parents[2])
    text = r.stdout.strip() if r.returncode == 0 else ""
    if not text or text.startswith("No translated string pairs"):
        print(f"warning: no glossary for language '{lang}'; UI terms are unchecked", file=sys.stderr)
        return ""
    return text


def main():
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    subs, name, lang = Path(sys.argv[1]), sys.argv[2], normalize_code(sys.argv[3])
    src = subs / f"{name}.en.srt"
    cues = read_srt(src)
    episode = subs.resolve().parent.name
    out = Path("tmp/translate-srt") / f"{episode}-{name}.{lang}.yaml"
    out.parent.mkdir(parents=True, exist_ok=True)
    data = {
        "lang": lang,
        "instructions": "Translate each cue text; keep cue numbers and count; UI terms must "
                        "follow the glossary. Write the result as cues: [{n, text}] to a new YAML.",
        "glossary": glossary(lang),
        "cues": [{"n": i, "text": x} for i, (_, x) in enumerate(cues, 1)],
    }
    out.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=100),
                   encoding="utf-8")
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
