#!/usr/bin/env python3
"""
Mirror full Android locale directories into their alias directories.

Some devices resolve to a different resource directory than the one the
translation tooling maintains (Android's legacy "in" vs modern "id", generic
Serbian "b+sr" vs "b+sr+RS").  The alias directory must therefore hold exactly
the same files as its source, otherwise those users see English fallbacks.

Alias -> source map:
  values-id    mirrors values-in       (Indonesian)
  values-b+sr  mirrors values-b+sr+RS  (Serbian, Cyrillic; b+sr+Latn is Latin)

Usage (run from repo root):
  python3 .claude/skills/update-translations/locale_aliases.py          # mirror
  python3 .claude/skills/update-translations/locale_aliases.py --check  # exit 1 if different
"""
import os
import shutil
import sys

# alias directory suffix -> source directory suffix (values-<suffix> under res/)
LOCALE_ALIASES = {
    "id": "in",
    "b+sr": "b+sr+RS",
}

DEFAULT_RES_DIR = os.path.join("app", "src", "main", "res")


def _files(directory):
    """Relative paths of all files below directory (empty if it does not exist)."""
    found = set()
    for root, _dirs, names in os.walk(directory):
        for name in names:
            found.add(os.path.relpath(os.path.join(root, name), directory))
    return found


def _read(path):
    with open(path, "rb") as f:
        return f.read()


def diff_alias(res_dir, alias, source):
    """Return human-readable differences between values-<alias> and values-<source>."""
    src_dir = os.path.join(res_dir, f"values-{source}")
    dst_dir = os.path.join(res_dir, f"values-{alias}")
    src_files, dst_files = _files(src_dir), _files(dst_dir)
    problems = []
    for rel in sorted(src_files - dst_files):
        problems.append(f"values-{alias}/{rel} missing (source values-{source})")
    for rel in sorted(dst_files - src_files):
        problems.append(f"values-{alias}/{rel} is not in values-{source}")
    for rel in sorted(src_files & dst_files):
        if _read(os.path.join(src_dir, rel)) != _read(os.path.join(dst_dir, rel)):
            problems.append(f"values-{alias}/{rel} differs from values-{source}/{rel}")
    return problems


def mirror_alias(res_dir, alias, source):
    """Make values-<alias> a byte-for-byte copy of values-<source>."""
    src_dir = os.path.join(res_dir, f"values-{source}")
    dst_dir = os.path.join(res_dir, f"values-{alias}")
    if not os.path.isdir(src_dir):
        raise FileNotFoundError(src_dir)
    os.makedirs(dst_dir, exist_ok=True)
    src_files = _files(src_dir)
    for rel in _files(dst_dir) - src_files:
        os.remove(os.path.join(dst_dir, rel))
    for rel in sorted(src_files):
        dst = os.path.join(dst_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(src_dir, rel), dst)


def mirror_all(res_dir=DEFAULT_RES_DIR, aliases=None):
    for alias, source in (aliases or LOCALE_ALIASES).items():
        mirror_alias(res_dir, alias, source)


def check_all(res_dir=DEFAULT_RES_DIR, aliases=None):
    problems = []
    for alias, source in (aliases or LOCALE_ALIASES).items():
        problems += diff_alias(res_dir, alias, source)
    return problems


def main():
    if "--check" in sys.argv[1:]:
        problems = check_all()
        for p in problems:
            print(f"  ✗ {p}")
        sys.exit(1 if problems else 0)
    mirror_all()
    for alias, source in LOCALE_ALIASES.items():
        print(f"Mirrored values-{source} -> values-{alias}")


if __name__ == "__main__":
    main()
