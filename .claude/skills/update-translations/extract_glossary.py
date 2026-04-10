#!/usr/bin/env python3
"""
Extract terminology glossary from existing translations.

Compares short English base strings with their translations to build
a terminology mapping table. Useful for ensuring consistency when
translating new strings.

Usage (run from repo root):
  python3 .claude/skills/update-translations/extract_glossary.py LANG

Example:
  python3 .claude/skills/update-translations/extract_glossary.py it
"""
import xml.etree.ElementTree as ET
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lang_codes import resolve_paths
from collections import Counter

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def main():
    if len(sys.argv) < 2:
        print("Usage: extract_glossary.py LANG", file=sys.stderr)
        sys.exit(1)

    lang = sys.argv[1]

    paths = resolve_paths(lang)
    android_base = paths['android_base']
    android_target = paths['android_target']
    vuejs_base = paths['vuejs_base']
    vuejs_target = paths['vuejs_target']

    # Collect all base→target string pairs
    pairs = []

    # Android XML
    if os.path.exists(android_base) and os.path.exists(android_target):
        base_tree = ET.parse(android_base)
        base_map = {}
        for e in base_tree.getroot():
            if e.tag == 'string':
                base_map[e.get('name')] = (e.text or '').strip()

        target_tree = ET.parse(android_target)
        for e in target_tree.getroot():
            if e.tag == 'string':
                name = e.get('name')
                if name in base_map:
                    base_text = base_map[name]
                    target_text = (e.text or '').strip()
                    if base_text and target_text:
                        pairs.append((base_text, target_text))

    # Vue.js YAML
    if vuejs_target and os.path.exists(vuejs_base) and os.path.exists(vuejs_target):
        with open(vuejs_base) as f:
            base_yaml = yaml.safe_load(f) or {}
        with open(vuejs_target) as f:
            target_yaml = yaml.safe_load(f) or {}

        for key in base_yaml:
            str_key = str(key)
            base_val = str(base_yaml[key]).strip() if base_yaml[key] else ''
            # Handle YAML boolean coercion for 'yes'/'no' keys
            target_val = None
            if key in target_yaml:
                target_val = str(target_yaml[key]).strip()
            elif str_key in target_yaml:
                target_val = str(target_yaml[str_key]).strip()
            if base_val and target_val:
                pairs.append((base_val, target_val))

    if not pairs:
        print(f"No translated string pairs found for '{lang}'")
        sys.exit(0)

    # Strategy 1: Direct mappings from short strings (1-3 words)
    direct_mappings = {}  # english_lower → Counter of translations
    for base, target in pairs:
        word_count = len(base.split())
        if 1 <= word_count <= 3 and not any(c in base for c in '%$\\'):
            key = base.lower().rstrip('…:.')
            if key not in direct_mappings:
                direct_mappings[key] = Counter()
            target_clean = target.lower().rstrip('…:.')
            direct_mappings[key][target_clean] += 1

    # Strategy 2: Scan all pairs for known domain terms
    domain_terms = [
        'bookmark', 'bookmarks', 'label', 'labels', 'study pad', 'study pads',
        'workspace', 'workspaces', 'window', 'windows', 'document', 'documents',
        'verse', 'verses', 'chapter', 'chapters', 'cross-reference', 'cross-references',
        'footnote', 'footnotes', "strong's", 'morphology', 'dictionary',
        'commentary', 'commentaries', 'bible', 'reading plan',
        'my notes', 'my documents', 'memorization', 'memorize',
        'search', 'download', 'backup', 'restore', 'settings', 'preferences',
        'create', 'delete', 'edit', 'open', 'close', 'save', 'cancel',
        'share', 'copy', 'reset', 'export', 'import',
        'AI', 'prompt', 'prompts', 'model', 'provider', 'agent',
        'page', 'pages', 'tool', 'tools', 'permission', 'permissions',
    ]

    # For each domain term, find short strings that ARE the term
    term_translations = {}
    for term in domain_terms:
        key = term.lower()
        if key in direct_mappings:
            most_common = direct_mappings[key].most_common(1)[0]
            term_translations[term] = (most_common[0], most_common[1])

    # Also check for terms that appear as part of strings
    # by looking for exact matches in short strings we already have
    for term in domain_terms:
        if term in term_translations:
            continue
        key = term.lower()
        # Look for plural/singular variants
        for mapped_key, counter in direct_mappings.items():
            if mapped_key == key or mapped_key == key + 's' or mapped_key.rstrip('s') == key:
                most_common = counter.most_common(1)[0]
                if term not in term_translations:
                    term_translations[term] = (most_common[0], most_common[1])

    # Print results
    print(f"Terminology glossary for '{lang}' ({len(pairs)} translated string pairs analyzed)")
    print()

    if term_translations:
        print("## Domain Terms")
        print()
        print("| English | {} | Occurrences |".format(lang.upper()))
        print("|---------|---------|-------------|")
        for term in sorted(term_translations.keys(), key=str.lower):
            trans, count = term_translations[term]
            print(f"| {term} | {trans} | {count} |")
        print()

    # Print all short-string mappings
    print("## All Short String Mappings (1-3 words)")
    print()
    print("| English | {} | Count |".format(lang.upper()))
    print("|---------|---------|-------|")
    for key in sorted(direct_mappings.keys()):
        most_common = direct_mappings[key].most_common(1)[0]
        trans, count = most_common
        if count >= 1:
            print(f"| {key} | {trans} | {count} |")


if __name__ == '__main__':
    main()
