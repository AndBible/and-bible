#!/usr/bin/env python3
"""
Find missing translations for a target language in AndBible.

Usage (run from repo root):
  python3 .claude/skills/update-translations/find_missing.py LANG

Example:
  python3 ~/.claude/skills/update-translations/find_missing.py pt
  python3 ~/.claude/skills/update-translations/find_missing.py de

Output:
  Prints summary counts and lists missing strings as name|||English value
  for both Android XML and Vue.js YAML.
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
        print("Usage: find_missing.py LANG", file=sys.stderr)
        print("  LANG = language code (e.g. pt, de, el, sv)", file=sys.stderr)
        sys.exit(1)

    lang = sys.argv[1]

    paths = resolve_paths(lang)
    android_base = paths['android_base']
    android_target = paths['android_target']
    vuejs_base = paths['vuejs_base']
    vuejs_target = paths['vuejs_target']

    # --- Android ---
    print("=" * 60)
    print(f"ANDROID: {android_target}")
    print("=" * 60)

    if not os.path.exists(android_base):
        print(f"ERROR: Base file not found: {android_base}", file=sys.stderr)
        print("Are you running from the repo root?", file=sys.stderr)
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

    if os.path.exists(android_target):
        target_tree = ET.parse(android_target)
        target_names = {e.get('name', '') for e in target_tree.getroot() if e.tag == 'string'}
        target_order = [e.get('name', '') for e in target_tree.getroot() if e.tag == 'string']
    else:
        target_names = set()
        target_order = []
        print(f"  (target file does not exist — all strings missing)")

    missing_android = [n for n in base_order if n not in target_names]
    extra_android = target_names - set(base_order)
    order_matches = (base_order == target_order) if len(base_order) == len(target_order) else False

    print(f"  Base: {len(base_order)} strings")
    print(f"  Target: {len(target_names)} strings")
    print(f"  Missing: {len(missing_android)}")
    print(f"  Extra (stale): {len(extra_android)}")
    print(f"  Order matches base: {order_matches}")

    if extra_android:
        print(f"\n  Stale strings (in target but not base):")
        for name in sorted(extra_android):
            print(f"    {name}")

    if missing_android:
        print(f"\n  Missing Android strings ({len(missing_android)}):")
        print("  ---")
        for name in missing_android:
            print(f"  {name}|||{base_strings[name]}")

    # --- Vue.js ---
    print()
    print("=" * 60)
    if vuejs_target is None:
        print(f"VUE.JS: (no Vue.js translations for '{lang}')")
        print("=" * 60)
        print(f"  Note: This language has no Vue.js translation file.")
        missing_yaml = []
        empty_yaml = []
    else:
        print(f"VUE.JS: {vuejs_target}")
        print("=" * 60)

        if not os.path.exists(vuejs_base):
            print(f"ERROR: Base file not found: {vuejs_base}", file=sys.stderr)
            sys.exit(1)

        with open(vuejs_base) as f:
            base_yaml = yaml.safe_load(f)

        base_keys = list(base_yaml.keys())

        if os.path.exists(vuejs_target):
            with open(vuejs_target) as f:
                target_yaml = yaml.safe_load(f) or {}
            target_keys = set(target_yaml.keys())
        else:
            target_yaml = {}
            target_keys = set()
            print(f"  (target file does not exist — all keys missing)")

        missing_yaml = [k for k in base_keys if k not in target_keys]
        extra_yaml = target_keys - set(base_keys)
        empty_yaml = [k for k in target_yaml if target_yaml.get(k) in (None, '')]
        yaml_order = list(target_yaml.keys()) if target_yaml else []
        yaml_order_matches = (base_keys == yaml_order) if len(base_keys) == len(yaml_order) else False

        print(f"  Base: {len(base_keys)} keys")
        print(f"  Target: {len(target_keys)} keys")
        print(f"  Missing: {len(missing_yaml)}")
        print(f"  Empty values: {len(empty_yaml)}")
        print(f"  Extra (stale): {len(extra_yaml)}")
        print(f"  Order matches base: {yaml_order_matches}")

        if extra_yaml:
            print(f"\n  Stale keys (in target but not base):")
            for k in sorted(extra_yaml):
                print(f"    {k}")

        if empty_yaml:
            print(f"\n  Empty values:")
            for k in empty_yaml:
                print(f"    {k}|||{base_yaml.get(k, '???')}")

        if missing_yaml:
            print(f"\n  Missing Vue.js keys ({len(missing_yaml)}):")
            print("  ---")
            for k in missing_yaml:
                val = base_yaml[k]
                print(f"  {k}|||{val}")

    # --- Play Store ---
    print()
    print("=" * 60)
    playstore_base = paths['playstore_base']
    playstore_target = paths['playstore_target']
    missing_playstore = []

    if playstore_target is None:
        print(f"PLAY STORE: (no Play Store translations for '{lang}')")
        print("=" * 60)
    else:
        print(f"PLAY STORE: {playstore_target}")
        print("=" * 60)

        if not os.path.exists(playstore_base):
            print(f"ERROR: Base file not found: {playstore_base}", file=sys.stderr)
            sys.exit(1)

        with open(playstore_base) as f:
            ps_base = yaml.safe_load(f) or {}
        # Only translatable keys: those with non-empty values in base
        ps_base_keys = {k: v for k, v in ps_base.items()
                        if v is not None and str(v).strip()}

        if os.path.exists(playstore_target):
            with open(playstore_target) as f:
                ps_target = yaml.safe_load(f) or {}
        else:
            ps_target = {}
            print(f"  (target file does not exist — all keys missing)")

        ps_filled = {k for k, v in ps_target.items()
                     if v is not None and str(v).strip()}
        missing_playstore = [k for k in ps_base_keys if k not in ps_filled]
        extra_playstore = set(ps_target.keys()) - set(ps_base.keys())

        print(f"  Base: {len(ps_base_keys)} translatable keys")
        print(f"  Target filled: {len(ps_filled)}")
        print(f"  Missing/empty: {len(missing_playstore)}")
        print(f"  Extra (stale): {len(extra_playstore)}")

        if extra_playstore:
            print(f"\n  Stale keys (in target but not base):")
            for k in sorted(extra_playstore):
                print(f"    {k}")

        if missing_playstore:
            print(f"\n  Missing Play Store keys ({len(missing_playstore)}):")
            print("  ---")
            for k in missing_playstore:
                val = str(ps_base_keys[k]).strip()
                # Truncate long values for display
                if len(val) > 80:
                    val = val[:77] + '...'
                print(f"  {k}|||{val}")

    # --- Summary ---
    print()
    print("=" * 60)
    total_missing = len(missing_android) + len(missing_yaml) + len(empty_yaml) + len(missing_playstore)
    if total_missing == 0:
        print(f"ALL TRANSLATIONS COMPLETE for '{lang}'")
    else:
        parts = []
        if missing_android:
            parts.append(f"{len(missing_android)} Android")
        if missing_yaml:
            parts.append(f"{len(missing_yaml)} Vue.js")
        if empty_yaml:
            parts.append(f"{len(empty_yaml)} empty")
        if missing_playstore:
            parts.append(f"{len(missing_playstore)} Play Store")
        print(f"TOTAL MISSING: {total_missing} ({', '.join(parts)})")
    print("=" * 60)

if __name__ == '__main__':
    main()
