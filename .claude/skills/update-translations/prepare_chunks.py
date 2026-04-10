#!/usr/bin/env python3
"""
Split missing translations into agent-ready chunk files.

Reads base and target translation files, finds missing strings,
and splits them into chunk files ready to be copied into sub-agent prompts.

Usage (run from repo root):
  python3 .claude/skills/update-translations/prepare_chunks.py LANG
  python3 .claude/skills/update-translations/prepare_chunks.py LANG --chunk-size 100

Output:
  /tmp/claude/chunks/{LANG}_android_chunk_{N}.txt  (name|||English value format)
  /tmp/claude/chunks/{LANG}_vuejs.txt              (name|||English value format)
  Summary printed to stdout.
"""
import xml.etree.ElementTree as ET
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lang_codes import resolve_paths

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def main():
    if len(sys.argv) < 2:
        print("Usage: prepare_chunks.py LANG [--chunk-size N]", file=sys.stderr)
        sys.exit(1)

    # Parse args
    chunk_size = 130
    args = [a for a in sys.argv[1:]]
    lang = args[0]

    for i, arg in enumerate(args):
        if arg == '--chunk-size' and i + 1 < len(args):
            chunk_size = int(args[i + 1])

    paths = resolve_paths(lang)
    android_base = paths['android_base']
    android_target = paths['android_target']
    vuejs_base = paths['vuejs_base']
    vuejs_target = paths['vuejs_target']

    output_dir = 'tmp/translate-chunks'
    os.makedirs(output_dir, exist_ok=True)

    # --- Android ---
    if not os.path.exists(android_base):
        print(f"ERROR: Base file not found: {android_base}", file=sys.stderr)
        sys.exit(1)

    base_tree = ET.parse(android_base)
    base_strings = {}
    base_order = []
    for e in base_tree.getroot():
        if e.tag == 'string':
            name = e.get('name')
            text = e.text or ''
            base_strings[name] = text
            base_order.append(name)

    target_names = set()
    if os.path.exists(android_target):
        target_tree = ET.parse(android_target)
        target_names = {e.get('name') for e in target_tree.getroot() if e.tag == 'string'}

    missing_android = [(n, base_strings[n]) for n in base_order if n not in target_names]

    android_chunks = []
    if missing_android:
        for i in range(0, len(missing_android), chunk_size):
            chunk = missing_android[i:i + chunk_size]
            android_chunks.append(chunk)

        for idx, chunk in enumerate(android_chunks, 1):
            path = os.path.join(output_dir, f'{lang}_android_chunk_{idx}.txt')
            with open(path, 'w') as f:
                for name, value in chunk:
                    f.write(f'{name}|||{value}\n')

    # --- Vue.js ---
    missing_yaml = []
    vuejs_path = None
    if vuejs_target is None:
        pass  # Language has no Vue.js translations
    else:
        if not os.path.exists(vuejs_base):
            print(f"ERROR: Base file not found: {vuejs_base}", file=sys.stderr)
            sys.exit(1)

        with open(vuejs_base) as f:
            base_yaml = yaml.safe_load(f)

        base_keys = list(base_yaml.keys())

        target_yaml = {}
        if os.path.exists(vuejs_target):
            with open(vuejs_target) as f:
                target_yaml = yaml.safe_load(f) or {}

        missing_yaml = [(str(k), str(base_yaml[k])) for k in base_keys
                         if k not in target_yaml or not target_yaml.get(k)]

        if missing_yaml:
            vuejs_path = os.path.join(output_dir, f'{lang}_vuejs.txt')
            with open(vuejs_path, 'w') as f:
                for key, value in missing_yaml:
                    f.write(f'{key}|||{value}\n')

    # --- Play Store ---
    playstore_base_path = paths['playstore_base']
    playstore_target = paths['playstore_target']
    missing_playstore = []
    playstore_path = None

    if playstore_target is not None and os.path.exists(playstore_base_path):
        with open(playstore_base_path) as f:
            ps_base = yaml.safe_load(f) or {}
        ps_base_keys = {k: str(v).strip() for k, v in ps_base.items()
                        if v is not None and str(v).strip()}

        ps_target = {}
        if os.path.exists(playstore_target):
            with open(playstore_target) as f:
                ps_target = yaml.safe_load(f) or {}

        ps_filled = {k for k, v in ps_target.items()
                     if v is not None and str(v).strip()}
        missing_playstore = [(k, ps_base_keys[k]) for k in ps_base_keys
                             if k not in ps_filled]

        if missing_playstore:
            playstore_path = os.path.join(output_dir, f'{lang}_playstore.txt')
            with open(playstore_path, 'w') as f:
                for key, value in missing_playstore:
                    f.write(f'{key}|||{value}\n')

    # --- Summary ---
    total_missing = len(missing_android) + len(missing_yaml) + len(missing_playstore)
    if total_missing == 0:
        print(f"Nothing missing for '{lang}'. All translations complete.")
        sys.exit(0)

    print(f"Language: {lang}")
    print(f"Chunk size: {chunk_size}")
    print(f"Output directory: {output_dir}")
    print()

    if missing_android:
        print(f"Android: {len(missing_android)} missing → {len(android_chunks)} chunks")
        for idx, chunk in enumerate(android_chunks, 1):
            path = os.path.join(output_dir, f'{lang}_android_chunk_{idx}.txt')
            print(f"  {path} ({len(chunk)} strings)")
    else:
        print("Android: complete")

    if vuejs_target is None:
        print("Vue.js: N/A (no Vue.js translations for this language)")
    elif missing_yaml:
        print(f"Vue.js: {len(missing_yaml)} missing → 1 file")
        print(f"  {vuejs_path} ({len(missing_yaml)} keys)")
    else:
        print("Vue.js: complete")

    if playstore_target is None:
        print("Play Store: N/A (no Play Store code for this language)")
    elif missing_playstore:
        print(f"Play Store: {len(missing_playstore)} missing → 1 file")
        print(f"  {playstore_path} ({len(missing_playstore)} keys)")
    else:
        print("Play Store: complete")

    file_count = len(android_chunks) + (1 if missing_yaml else 0) + (1 if missing_playstore else 0)
    print()
    print(f"Total: {total_missing} missing strings in {file_count} files")


if __name__ == '__main__':
    main()
