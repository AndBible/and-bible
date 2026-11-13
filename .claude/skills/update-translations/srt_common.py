"""Shared SRT parsing for the subtitle scripts (stdlib only)."""
import re

TIMING = re.compile(r"^\d{2}:\d{2}:\d{2},\d{3} --> \d{2}:\d{2}:\d{2},\d{3}\s*$")


def read_srt(path):
    with open(path, encoding="utf-8-sig") as f:
        return parse_srt(f.read())


def parse_srt(text):
    """Return [(timing_line, cue_text)]; cue text keeps its inner newlines."""
    cues = []
    for block in re.split(r"\n\s*\n", text.lstrip("\ufeff").replace("\r\n", "\n").strip()):
        lines = block.split("\n")
        if lines and lines[0].strip().isdigit():
            lines = lines[1:]
        if not lines:
            continue
        timing, body = lines[0], "\n".join(lines[1:])
        cues.append((timing, body))
    return cues


def format_srt(cues):
    return "\n".join(f"{i}\n{t}\n{x}\n" for i, (t, x) in enumerate(cues, 1))
