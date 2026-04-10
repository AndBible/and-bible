#!/usr/bin/env python3
"""
Restructure Play Store description YAML for a target language.

Merges new translations into the existing YAML file, preserving
the base file's key order and comments. Drops stale keys.

Usage (run from repo root):
  python3 .claude/skills/update-translations/restructure_playstore.py LANG
  python3 .claude/skills/update-translations/restructure_playstore.py LANG tmp/translate-results/fi_playstore.yml
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


def main():
    if len(sys.argv) < 2:
        print("Usage: restructure_playstore.py LANG [new_translations.yml ...]", file=sys.stderr)
        sys.exit(1)

    lang = sys.argv[1]
    new_files = sys.argv[2:]

    paths = resolve_paths(lang)
    base_path = paths['playstore_base']
    target_path = paths['playstore_target']

    if target_path is None:
        print(f"ERROR: No Play Store code for language '{lang}'", file=sys.stderr)
        sys.exit(1)

    if not os.path.exists(base_path):
        print(f"ERROR: Base file not found: {base_path}", file=sys.stderr)
        sys.exit(1)

    # Read base file to get key order and comments
    with open(base_path) as f:
        base_lines = f.readlines()
    with open(base_path) as f:
        base_data = yaml.safe_load(f) or {}

    # Read existing target
    existing = {}
    if os.path.exists(target_path):
        with open(target_path) as f:
            existing = yaml.safe_load(f) or {}

    # Read new translations to merge
    for new_file in new_files:
        if not os.path.exists(new_file):
            print(f"WARNING: File not found: {new_file}", file=sys.stderr)
            continue
        with open(new_file) as f:
            new_data = yaml.safe_load(f) or {}
        existing.update(new_data)

    # Build output following base file structure (comments + key order)
    output_lines = []
    base_keys = set(base_data.keys())

    for line in base_lines:
        stripped = line.rstrip()
        # Comment or blank line — pass through
        if stripped == '' or stripped.startswith('#'):
            output_lines.append(line.rstrip('\n'))
            continue

        # Try to extract YAML key from this line
        if ':' in line and not line[0].isspace():
            key = line.split(':')[0].strip()
            if key in base_keys and key in existing:
                val = existing.get(key)
                if val is None or str(val).strip() == '':
                    # Empty — use YAML block scalar to match base format
                    output_lines.append(f'{key}: ""')
                else:
                    val_str = str(val).strip()
                    # Check if value needs quoting
                    needs_quote = (
                        val_str.startswith('"') or
                        val_str.startswith("'") or
                        ':' in val_str or
                        val_str.startswith('{') or
                        val_str.startswith('[')
                    )
                    # Use block scalar (>) for long values
                    if len(val_str) > 80 or '\n' in val_str:
                        output_lines.append(f'{key}: >')
                        # Indent continuation lines
                        for subline in val_str.split('\n'):
                            output_lines.append(f'  {subline.strip()}')
                    elif needs_quote:
                        # Quote the value
                        escaped = val_str.replace('"', '\\"')
                        output_lines.append(f'{key}: "{escaped}"')
                    else:
                        output_lines.append(f'{key}: {val_str}')
            elif key in base_keys:
                # Key exists in base but not translated — write empty
                output_lines.append(f'{key}: ""')
            # else: stale key — skip
            continue

        # Continuation line (indented) — skip, we already wrote the value
        if line[0].isspace():
            continue

        output_lines.append(line.rstrip('\n'))

    # Write output
    with open(target_path, 'w') as f:
        f.write('\n'.join(output_lines) + '\n')

    filled = sum(1 for k in base_keys
                 if k in existing and existing[k] is not None
                 and str(existing[k]).strip())
    print(f"Restructured: {target_path}")
    print(f"  Keys: {filled}/{len(base_keys)} filled")
    print(f"  Comments preserved from base")


if __name__ == '__main__':
    main()
