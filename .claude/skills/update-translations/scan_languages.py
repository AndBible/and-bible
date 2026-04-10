#!/usr/bin/env python3
"""
Scan all translation languages and report which ones need work.

Usage (run from repo root):
  python3 .claude/skills/update-translations/scan_languages.py                 # tier 1 (default)
  python3 .claude/skills/update-translations/scan_languages.py --tier 1,2      # tier 1+2
  python3 .claude/skills/update-translations/scan_languages.py --all-tiers     # all tiers
  python3 .claude/skills/update-translations/scan_languages.py --needs-work    # only missing
  python3 .claude/skills/update-translations/scan_languages.py --only de,fr,es
  python3 .claude/skills/update-translations/scan_languages.py --exclude ar,ko
"""
import xml.etree.ElementTree as ET
import json
import sys
import os

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lang_codes import (
    LANGUAGES, resolve_paths, get_translatable_languages,
)


def parse_args():
    needs_work = '--needs-work' in sys.argv
    output_json = '--json' in sys.argv
    all_tiers = '--all-tiers' in sys.argv
    tiers = None if all_tiers else [1]  # Default: tier 1 only
    only = None
    exclude = None

    for i, arg in enumerate(sys.argv):
        if arg == '--tier' and i + 1 < len(sys.argv):
            tiers = [int(t) for t in sys.argv[i + 1].split(',')]
        elif arg == '--only' and i + 1 < len(sys.argv):
            only = [c.strip() for c in sys.argv[i + 1].split(',')]
        elif arg == '--exclude' and i + 1 < len(sys.argv):
            exclude = [c.strip() for c in sys.argv[i + 1].split(',')]

    return needs_work, tiers, output_json, only, exclude


def count_android_missing(base_names, target_path):
    """Count missing Android strings. Returns (missing_count, total_target)."""
    if not os.path.exists(target_path):
        return len(base_names), 0
    try:
        tree = ET.parse(target_path)
        target_names = {e.get('name') for e in tree.getroot() if e.tag == 'string'}
        missing = len(base_names - target_names)
        return missing, len(target_names)
    except ET.ParseError:
        return -1, 0  # Parse error


def count_vuejs_missing(base_keys, target_path):
    """Count missing Vue.js keys. Returns (missing_count, total_target)."""
    if target_path is None:
        return None, None  # No Vue.js file expected
    if not os.path.exists(target_path):
        return len(base_keys), 0
    try:
        with open(target_path) as f:
            target = yaml.safe_load(f) or {}
        target_keys = set(target.keys())
        missing = len(base_keys - target_keys)
        return missing, len(target_keys)
    except yaml.YAMLError:
        return -1, 0


def count_playstore_missing(base_keys, target_path):
    """Count missing Play Store YAML keys. Returns (missing_count, total_target)."""
    if target_path is None:
        return None, None  # No Play Store file for this language
    if not os.path.exists(target_path):
        return len(base_keys), 0
    try:
        with open(target_path) as f:
            target = yaml.safe_load(f) or {}
        # Count keys that are missing or have empty/whitespace-only values
        filled_keys = {k for k, v in target.items()
                       if v is not None and str(v).strip()}
        missing = len(base_keys - filled_keys)
        return missing, len(filled_keys)
    except yaml.YAMLError:
        return -1, 0


def main():
    needs_work, tiers, output_json, only, exclude = parse_args()

    # Parse base files once
    android_base_path = 'app/src/main/res/values/strings.xml'
    vuejs_base_path = 'app/bibleview-js/src/lang/default.yaml'

    if not os.path.exists(android_base_path):
        print(f"ERROR: Base file not found: {android_base_path}", file=sys.stderr)
        print("Are you running from the repo root?", file=sys.stderr)
        sys.exit(1)

    base_tree = ET.parse(android_base_path)
    android_base_names = {e.get('name') for e in base_tree.getroot() if e.tag == 'string'}

    with open(vuejs_base_path) as f:
        vuejs_base = yaml.safe_load(f)
    vuejs_base_keys = set(vuejs_base.keys())

    # Play Store base
    playstore_base_path = 'play/playstore-description.yml'
    playstore_base_keys = set()
    if os.path.exists(playstore_base_path):
        with open(playstore_base_path) as f:
            playstore_base = yaml.safe_load(f) or {}
        # Only count keys that have non-empty values in the base
        playstore_base_keys = {k for k, v in playstore_base.items()
                               if v is not None and str(v).strip()}

    # Get language list
    languages = get_translatable_languages(tiers=tiers)
    if only:
        languages = [c for c in languages if c in only]
    if exclude:
        languages = [c for c in languages if c not in exclude]

    # Scan each language
    results = []
    for code in sorted(languages, key=lambda c: (LANGUAGES[c][1], LANGUAGES[c][0])):
        name, tier = LANGUAGES[code][0], LANGUAGES[code][1]
        paths = resolve_paths(code)

        android_missing, android_total = count_android_missing(
            android_base_names, paths['android_target'])
        vuejs_missing, vuejs_total = count_vuejs_missing(
            vuejs_base_keys, paths['vuejs_target'])
        playstore_missing, playstore_total = count_playstore_missing(
            playstore_base_keys, paths['playstore_target'])

        total_missing = ((android_missing or 0) + (vuejs_missing or 0)
                         + (playstore_missing or 0))

        if needs_work and total_missing == 0:
            continue

        results.append({
            'lang': code,
            'name': name,
            'tier': tier,
            'android_missing': android_missing,
            'android_total': android_total,
            'vuejs_missing': vuejs_missing,
            'vuejs_total': vuejs_total,
            'playstore_missing': playstore_missing,
            'playstore_total': playstore_total,
            'total_missing': total_missing,
        })

    if output_json:
        print(json.dumps(results, indent=2, ensure_ascii=False))
        return

    # Print table
    android_base_count = len(android_base_names)
    vuejs_base_count = len(vuejs_base_keys)
    playstore_base_count = len(playstore_base_keys)

    print(f"Base: {android_base_count} Android strings, {vuejs_base_count} Vue.js keys, {playstore_base_count} Play Store keys")
    filters = []
    if tiers:
        filters.append(f"tier {','.join(str(t) for t in tiers)}")
    if only:
        filters.append(f"only {','.join(only)}")
    if exclude:
        filters.append(f"exclude {','.join(exclude)}")
    if filters:
        print(f"Filter: {'; '.join(filters)}")
    if needs_work:
        print(f"Showing: only languages with missing translations")
    print()

    # Header
    fmt = "{:<12} {:<24} {:>4}  {:>8}  {:>8}  {:>8}  {:>6}"
    print(fmt.format("Lang", "Name", "Tier", "Android", "Vue.js", "Play", "Total"))
    print("-" * 78)

    total_android = 0
    total_vuejs = 0
    total_playstore = 0
    total_all = 0

    for r in results:
        android_str = str(r['android_missing']) if r['android_missing'] is not None else 'N/A'
        vuejs_str = str(r['vuejs_missing']) if r['vuejs_missing'] is not None else 'N/A'
        playstore_str = str(r['playstore_missing']) if r['playstore_missing'] is not None else 'N/A'

        print(fmt.format(
            r['lang'], r['name'], r['tier'],
            android_str, vuejs_str, playstore_str, r['total_missing'],
        ))

        total_android += r['android_missing'] or 0
        total_vuejs += r['vuejs_missing'] or 0
        total_playstore += r['playstore_missing'] or 0
        total_all += r['total_missing']

    print("-" * 78)
    print(f"TOTAL: {len(results)} languages, {total_all} missing strings "
          f"({total_android} Android + {total_vuejs} Vue.js + {total_playstore} Play Store)")


if __name__ == '__main__':
    main()
