#!/usr/bin/env python3
"""
Validate translations for a target language in AndBible.

Checks:
  - Coverage (missing strings count = 0)
  - Format specifier preservation (%s, %1$s, %d, etc.)
  - Android XML string order matches base
  - Vue.js YAML key order matches base
  - No empty translation values
  - XML well-formedness (via ET.parse)

Usage (run from repo root):
  python3 .claude/skills/update-translations/validate.py LANG

Example:
  python3 ~/.claude/skills/update-translations/validate.py pt
"""
import xml.etree.ElementTree as ET
import re
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lang_codes import resolve_paths

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def count_format_specifiers(s):
    """Count format specifiers like %s, %d, %1$s, %2$d, etc."""
    if not isinstance(s, str):
        return []
    return re.findall(r'%(?:\d+\$)?[sdfe]', s)


def main():
    if len(sys.argv) < 2:
        print("Usage: validate.py LANG", file=sys.stderr)
        sys.exit(1)

    lang = sys.argv[1]
    errors = []
    warnings = []

    paths = resolve_paths(lang)
    android_base = paths['android_base']
    android_target = paths['android_target']
    vuejs_base = paths['vuejs_base']
    vuejs_target = paths['vuejs_target']

    # ===== ANDROID =====
    print("Validating Android XML...")

    if not os.path.exists(android_target):
        errors.append(f"Android target file missing: {android_target}")
    else:
        # XML well-formedness
        try:
            target_tree = ET.parse(android_target)
        except ET.ParseError as e:
            errors.append(f"Android XML parse error: {e}")
            target_tree = None

        if target_tree is not None:
            base_tree = ET.parse(android_base)

            base_strings = {}
            base_order = []
            for e in base_tree.getroot():
                if e.tag == 'string':
                    name = e.get('name')
                    base_strings[name] = e.text or ''
                    base_order.append(name)

            target_strings = {}
            target_order = []
            for e in target_tree.getroot():
                if e.tag == 'string':
                    name = e.get('name')
                    target_strings[name] = e.text or ''
                    target_order.append(name)

            # Coverage
            missing = [n for n in base_order if n not in target_strings]
            if missing:
                errors.append(f"Android: {len(missing)} missing strings: {missing[:10]}{'...' if len(missing) > 10 else ''}")

            extra = set(target_strings.keys()) - set(base_order)
            if extra:
                warnings.append(f"Android: {len(extra)} stale strings (in target, not in base): {sorted(extra)[:10]}")

            # Order
            if base_order != target_order and len(base_order) == len(target_order):
                first_diff = next((i for i, (b, t) in enumerate(zip(base_order, target_order)) if b != t), None)
                if first_diff is not None:
                    errors.append(f"Android: String order mismatch starting at position {first_diff}: base={base_order[first_diff]}, target={target_order[first_diff]}")
            elif len(base_order) != len(target_order):
                warnings.append(f"Android: Different string count (base={len(base_order)}, target={len(target_order)}) — order check skipped")

            # Format specifiers
            for name in target_strings:
                if name in base_strings:
                    base_fmts = count_format_specifiers(base_strings[name])
                    target_fmts = count_format_specifiers(target_strings[name])
                    if sorted(base_fmts) != sorted(target_fmts):
                        errors.append(f"Android format mismatch in '{name}': base={base_fmts}, target={target_fmts}")

            # Empty values
            empty = [n for n, v in target_strings.items() if not v or not v.strip()]
            if empty:
                warnings.append(f"Android: {len(empty)} empty translation values: {empty[:10]}")

            # Unescaped apostrophes (AAPT will reject these with
            # "Invalid unicode escape sequence in string"). Android requires \'
            # outside of "..."-quoted string values.
            unescaped = []
            for name, val in target_strings.items():
                if not val:
                    continue
                # If the entire value is wrapped in double quotes, apostrophes
                # don't need escaping inside.
                if val.startswith('"') and val.endswith('"') and len(val) >= 2:
                    continue
                if re.search(r"(?<!\\)'", val):
                    unescaped.append(name)
            if unescaped:
                errors.append(
                    f"Android: {len(unescaped)} string(s) with unescaped apostrophe "
                    f"(AAPT will reject): {unescaped[:10]}"
                )

            print(f"  Strings: {len(target_strings)}/{len(base_strings)}")

    # ===== VUE.JS =====
    print("Validating Vue.js YAML...")

    if vuejs_target is None:
        print("  Vue.js: N/A (no Vue.js file for this language)")
    elif not os.path.exists(vuejs_target):
        errors.append(f"Vue.js target file missing: {vuejs_target}")
    else:
        try:
            with open(vuejs_base) as f:
                base_yaml = yaml.safe_load(f)
            with open(vuejs_target) as f:
                target_yaml = yaml.safe_load(f) or {}
        except yaml.YAMLError as e:
            errors.append(f"Vue.js YAML parse error: {e}")
            base_yaml = None
            target_yaml = None

        if base_yaml is not None and target_yaml is not None:
            base_keys = list(base_yaml.keys())
            target_keys = list(target_yaml.keys())

            # Coverage
            missing_keys = [k for k in base_keys if k not in target_yaml]
            if missing_keys:
                errors.append(f"Vue.js: {len(missing_keys)} missing keys: {missing_keys[:10]}")

            extra_keys = set(target_yaml.keys()) - set(base_keys)
            if extra_keys:
                warnings.append(f"Vue.js: {len(extra_keys)} stale keys: {sorted(extra_keys)[:10]}")

            # Order
            if base_keys != target_keys and len(base_keys) == len(target_keys):
                first_diff = next((i for i, (b, t) in enumerate(zip(base_keys, target_keys)) if b != t), None)
                if first_diff is not None:
                    errors.append(f"Vue.js: Key order mismatch at position {first_diff}: base={base_keys[first_diff]}, target={target_keys[first_diff]}")

            # Format specifiers
            for k in target_yaml:
                if k in base_yaml:
                    base_fmts = count_format_specifiers(str(base_yaml[k]))
                    target_fmts = count_format_specifiers(str(target_yaml[k]))
                    if sorted(base_fmts) != sorted(target_fmts):
                        errors.append(f"Vue.js format mismatch in '{k}': base={base_fmts}, target={target_fmts}")

            # Empty values
            empty_keys = [k for k in target_yaml if target_yaml[k] in (None, '')]
            if empty_keys:
                warnings.append(f"Vue.js: {len(empty_keys)} empty values: {empty_keys[:10]}")

            print(f"  Keys: {len(target_keys)}/{len(base_keys)}")

    # ===== PLAY STORE =====
    print("Validating Play Store YAML...")

    playstore_base = paths['playstore_base']
    playstore_target = paths['playstore_target']

    if playstore_target is None:
        print("  Play Store: N/A (no Play Store code for this language)")
    elif not os.path.exists(playstore_target):
        warnings.append(f"Play Store target file missing: {playstore_target}")
    else:
        try:
            with open(playstore_base) as f:
                ps_base_data = yaml.safe_load(f) or {}
            with open(playstore_target) as f:
                ps_target_data = yaml.safe_load(f) or {}
        except yaml.YAMLError as e:
            errors.append(f"Play Store YAML parse error: {e}")
            ps_base_data = None
            ps_target_data = None

        if ps_base_data is not None and ps_target_data is not None:
            ps_base_keys = {k for k, v in ps_base_data.items()
                           if v is not None and str(v).strip()}
            ps_filled = {k for k, v in ps_target_data.items()
                         if v is not None and str(v).strip()}
            ps_missing = ps_base_keys - ps_filled
            if ps_missing:
                warnings.append(f"Play Store: {len(ps_missing)} missing/empty keys: {sorted(ps_missing)[:10]}")

            # Check template variables are preserved
            for k in ps_target_data:
                if k in ps_base_data:
                    base_val = str(ps_base_data[k])
                    target_val = str(ps_target_data[k])
                    # Check {{ variable }} placeholders
                    base_vars = set(re.findall(r'\{\{(\w+)\}\}', base_val))
                    target_vars = set(re.findall(r'\{\{(\w+)\}\}', target_val))
                    if base_vars and base_vars != target_vars:
                        errors.append(f"Play Store template var mismatch in '{k}': "
                                     f"base={base_vars}, target={target_vars}")

            # Length checks
            title = str(ps_target_data.get('title', '')).strip()
            if title and len(title) > 30:
                warnings.append(f"Play Store: title too long ({len(title)} > 30 chars)")
            short = str(ps_target_data.get('short_description', '')).strip()
            if short and len(short) > 80:
                warnings.append(f"Play Store: short_description too long ({len(short)} > 80 chars)")

            print(f"  Keys filled: {len(ps_filled)}/{len(ps_base_keys)}")

    # ===== RESULTS =====
    print()
    if warnings:
        print(f"WARNINGS ({len(warnings)}):")
        for w in warnings:
            print(f"  ⚠ {w}")
        print()

    if errors:
        print(f"ERRORS ({len(errors)}):")
        for e in errors:
            print(f"  ✗ {e}")
        print()
        print("VALIDATION FAILED")
        sys.exit(1)
    else:
        print(f"VALIDATION PASSED for '{lang}'")
        if warnings:
            print(f"  ({len(warnings)} warnings)")
        sys.exit(0)

if __name__ == '__main__':
    main()
