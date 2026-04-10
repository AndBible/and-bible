#!/usr/bin/env python3
"""
Restructure target Vue.js YAML translation file to match base key order.

Usage (run from repo root):
  python3 .claude/skills/update-translations/restructure_yaml.py LANG [NEW_TRANS_FILE]

Arguments:
  LANG            Language code (e.g. pt, de, el, sv)
  NEW_TRANS_FILE  Optional path to file with new translations as YAML key: value lines.
                  If omitted, only restructures existing translations to match base order.

Examples:
  # Restructure existing translations to match base layout:
  python3 .claude/skills/update-translations/restructure_yaml.py pt

  # Merge new translations and restructure:
  python3 .claude/skills/update-translations/restructure_yaml.py pt /tmp/claude/pt_vuejs.yaml
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lang_codes import resolve_paths
try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


_YAML_BOOL_MAP = {True: 'yes', False: 'no'}


def _yaml_key_to_str(key):
    """Convert YAML-parsed key back to string (handles boolean coercion)."""
    if isinstance(key, bool):
        return _YAML_BOOL_MAP.get(key, str(key))
    return str(key)


def needs_quoting(key, value):
    """Determine if a YAML value needs quoting."""
    if not isinstance(value, str):
        return True  # Non-string values (booleans from YAML parsing) always need quoting
    # Boolean-like keys (yes, no, true, false) need quoting
    if isinstance(key, str) and key.lower() in ('yes', 'no', 'true', 'false', 'on', 'off'):
        return True
    # Values with format specifiers, colons, special chars
    if any(c in str(value) for c in ['%', ':', '{', '}', '[', ']', '#', '&', '*', '!', '|', '>', "'", '"', '@', '`']):
        return True
    # Values that start with special chars
    if value and value[0] in ('-', '?', ' '):
        return True
    # Empty string
    if value == '':
        return True
    return False


def format_yaml_value(key, value):
    """Format a YAML value with appropriate quoting."""
    if not isinstance(value, str):
        # Non-string (e.g. boolean from YAML parsing 'yes' key) — quote it
        return f'"{value}"'
    if needs_quoting(key, value):
        # Use double quotes, escape internal double quotes
        escaped = value.replace('\\', '\\\\').replace('"', '\\"')
        return f'"{escaped}"'
    return value


def main():
    if len(sys.argv) < 2:
        print("Usage: restructure_yaml.py LANG [NEW_TRANS_FILE]", file=sys.stderr)
        sys.exit(1)

    lang = sys.argv[1]
    new_trans_file = sys.argv[2] if len(sys.argv) > 2 else None

    paths = resolve_paths(lang)
    base_path = paths['vuejs_base']
    target_path = paths['vuejs_target']
    if target_path is None:
        print(f"ERROR: Language '{lang}' has no Vue.js translation file.", file=sys.stderr)
        sys.exit(1)

    if not os.path.exists(base_path):
        print(f"ERROR: Base file not found: {base_path}", file=sys.stderr)
        sys.exit(1)

    # --- 1. Read base to get key order and comment ---
    with open(base_path) as f:
        base_lines = f.readlines()
    # Extract key names from raw lines (not yaml.safe_load) to avoid
    # boolean coercion: YAML parses 'yes' as True, 'no' as False etc.
    base_keys = []
    for line in base_lines:
        line = line.strip()
        if line and not line.startswith('#') and ':' in line:
            key = line.split(':')[0].strip()
            if key:
                base_keys.append(key)

    # Extract leading comment from base file
    leading_comment = ''
    for line in base_lines:
        if line.startswith('#'):
            leading_comment += line
        else:
            break

    # --- 2. Read existing target translations ---
    existing = {}
    if os.path.exists(target_path):
        with open(target_path) as f:
            raw = yaml.safe_load(f) or {}
        existing = {_yaml_key_to_str(k): v for k, v in raw.items()}
        print(f"Existing translations: {len(existing)}")
    else:
        print(f"Target file not found (creating new): {target_path}")

    # --- 3. Read new translations (if provided) ---
    new_translations = {}
    if new_trans_file:
        if not os.path.exists(new_trans_file):
            print(f"ERROR: New translations file not found: {new_trans_file}", file=sys.stderr)
            sys.exit(1)
        with open(new_trans_file) as f:
            raw = yaml.safe_load(f) or {}
        new_translations = {_yaml_key_to_str(k): v for k, v in raw.items()}
        print(f"New translations loaded: {len(new_translations)}")

    # --- 4. Merge: existing + new (new overrides existing) ---
    all_translations = {**existing, **new_translations}

    # --- 5. Drop stale keys ---
    stale = set(all_translations.keys()) - set(base_keys)
    if stale:
        print(f"Dropping {len(stale)} stale keys (not in base): {sorted(stale)[:5]}{'...' if len(stale) > 5 else ''}")
        for key in stale:
            del all_translations[key]

    # --- 6. Write output in base key order ---
    output_lines = []
    if leading_comment:
        output_lines.append(leading_comment)

    written = 0
    missing = 0
    for key in base_keys:
        if key in all_translations:
            value = all_translations[key]
            formatted = format_yaml_value(key, value)
            output_lines.append(f"{key}: {formatted}\n")
            written += 1
        else:
            missing += 1

    with open(target_path, 'w') as f:
        f.writelines(output_lines)

    print(f"Keys: {written}/{len(base_keys)} (missing: {missing})")
    print(f"Written to: {target_path}")

    # --- 7. Verify round-trip ---
    with open(target_path) as f:
        verify = yaml.safe_load(f) or {}
    if len(verify) != written:
        print(f"WARNING: Round-trip check failed! Written {written} but read back {len(verify)}")
        sys.exit(1)


if __name__ == '__main__':
    main()
