#!/usr/bin/env python3
"""
Restructure target translation XML to match base file layout.

Usage (run from repo root):
  python3 .claude/skills/update-translations/restructure.py LANG [NEW_TRANS_FILES...]

Arguments:
  LANG              Language code (e.g. pt, de, el, sv)
  NEW_TRANS_FILES   Optional: one or more files with new translations as <string> XML lines,
                    or a directory containing *.xml files.
                    If omitted, only restructures existing translations to match base order.

Options:
  --keep-stale      Keep strings that exist in target but not in base (appended at end).
                    Default: stale strings are dropped with a warning.

Examples:
  # Restructure existing translations to match base layout:
  python3 .claude/skills/update-translations/restructure.py pt

  # Merge new translations and restructure:
  python3 .claude/skills/update-translations/restructure.py pt /tmp/claude/pt_new_translations.xml

  # Merge from multiple files:
  python3 .claude/skills/update-translations/restructure.py pt chunk1.xml chunk2.xml

  # Merge from a directory of chunk files:
  python3 .claude/skills/update-translations/restructure.py pt /tmp/claude/chunks/

NEVER run via bash -c or heredoc — the <!-- in comments
gets corrupted by bash history expansion.
"""
import xml.etree.ElementTree as ET
import re
import sys
import os
import glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lang_codes import resolve_paths, get_android_mirror_paths

CS = "<" + "!--"  # comment start - avoid literal for bash safety
CE = "--" + ">"   # comment end


def fix_xml_entities(value):
    """Fix double-escaped and bare XML entities that agents sometimes produce."""
    # Fix double-escapes first (e.g. &amp;amp; → &amp;)
    value = value.replace('&amp;amp;', '&amp;')
    value = value.replace('&amp;lt;', '&lt;')
    value = value.replace('&amp;gt;', '&gt;')
    value = value.replace('&amp;apos;', '&apos;')
    value = value.replace('&amp;quot;', '&quot;')
    # Fix bare ampersands (& not followed by a valid XML entity reference)
    value = re.sub(r'&(?!(amp|lt|gt|apos|quot);|#)', '&amp;', value)
    return value


def load_translations_from_file(path):
    """Parse <string name="...">...</string> entries from a file.

    Supports both single-line and multiline <string> entries.
    Multiline values are collapsed to single-line (whitespace normalized)
    to match Android resource conventions.
    """
    translations = {}
    with open(path, 'r') as f:
        content = f.read()

    # Fix bare ampersands before XML parsing so parser doesn't choke
    content = re.sub(r'&(?!(amp|lt|gt|apos|quot);|#)', '&amp;', content)

    # First try XML parsing (handles multiline strings correctly)
    try:
        root = ET.fromstring('<resources>' + content + '</resources>')
        for elem in root.findall('string'):
            name = elem.get('name')
            if name:
                # Use ET.tostring to preserve XML entities (&amp; &lt; etc.)
                raw = ET.tostring(elem, encoding='unicode', method='xml')
                start = raw.index('>') + 1
                end = raw.rindex('</')
                text = raw[start:end].strip()
                # Collapse internal whitespace (multiline → single line)
                text = re.sub(r'\s+', ' ', text)
                translations[name] = fix_xml_entities(text)
        if translations:
            return translations
    except ET.ParseError:
        pass

    # Fallback: line-by-line regex (for files that aren't valid XML)
    for line in content.splitlines():
        line = line.strip()
        m = re.match(r'<string name="([^"]+)">(.*)</string>', line, re.DOTALL)
        if m:
            translations[m.group(1)] = fix_xml_entities(m.group(2))
    return translations


def main():
    if len(sys.argv) < 2:
        print("Usage: restructure.py LANG [NEW_TRANS_FILES...] [--keep-stale]", file=sys.stderr)
        print("  LANG = language code (e.g. pt, de, el)", file=sys.stderr)
        print("  NEW_TRANS_FILES = files or directory with new <string> translations", file=sys.stderr)
        sys.exit(1)

    # Parse arguments
    keep_stale = '--keep-stale' in sys.argv
    args = [a for a in sys.argv[1:] if a != '--keep-stale']

    lang = args[0]
    trans_paths = args[1:]

    paths = resolve_paths(lang)
    base_path = paths['android_base']
    target_path = paths['android_target']

    # --- 1. Load new translations from all specified sources ---
    new_translations = {}
    for path in trans_paths:
        if os.path.isdir(path):
            # Directory: glob *.xml files, filter to current language only
            all_xml_files = sorted(glob.glob(os.path.join(path, '*.xml')))
            # Only load files that belong to this language (prefix match)
            xml_files = [f for f in all_xml_files
                         if os.path.basename(f).startswith(f'{lang}_') or
                            os.path.basename(f).startswith(f'{lang}.')]
            skipped = [os.path.basename(f) for f in all_xml_files if f not in xml_files]
            if skipped:
                print(f"  Skipping {len(skipped)} file(s) not matching language '{lang}': {skipped[:5]}{'...' if len(skipped) > 5 else ''}")
            if not xml_files:
                print(f"Warning: no *.xml files matching '{lang}' found in {path}", file=sys.stderr)
                print(f"  Expected filenames starting with '{lang}_' (e.g. {lang}_chunk_1.xml)", file=sys.stderr)
            for xml_file in xml_files:
                loaded = load_translations_from_file(xml_file)
                new_translations.update(loaded)
                print(f"  Loaded {len(loaded)} strings from {os.path.basename(xml_file)}")
        elif os.path.isfile(path):
            loaded = load_translations_from_file(path)
            new_translations.update(loaded)
        else:
            print(f"Warning: path not found: {path}", file=sys.stderr)

    if new_translations:
        print(f"New translations loaded: {len(new_translations)}")

    # --- 2. Parse existing target translations (before overwriting!) ---
    existing = {}
    existing_string_arrays = {}  # name -> raw XML lines (for <string-array> preservation)
    try:
        target_tree = ET.parse(target_path)
        for e in target_tree.getroot():
            if e.tag == 'string':
                name = e.get('name')
                text = ET.tostring(e, encoding='unicode', method='xml')
                start = text.index('>') + 1
                end = text.rindex('</')
                existing[name] = text[start:end]
        # Also extract raw <string-array> blocks from target file
        with open(target_path, 'r') as tf:
            target_lines = tf.readlines()
        ti = 0
        while ti < len(target_lines):
            tline = target_lines[ti].strip()
            sa_match = re.match(r'<string-array\s+name="([^"]+)"', tline)
            if sa_match:
                sa_name = sa_match.group(1)
                sa_block = []
                # Handle self-closing <string-array .../> on a single line
                if re.search(r'/\s*>\s*$', target_lines[ti].rstrip()):
                    sa_block.append(target_lines[ti])
                else:
                    while ti < len(target_lines):
                        sa_block.append(target_lines[ti])
                        if '</string-array>' in target_lines[ti]:
                            break
                        ti += 1
                existing_string_arrays[sa_name] = sa_block
            ti += 1
        if existing_string_arrays:
            print(f"Existing string-arrays preserved: {len(existing_string_arrays)}")
        print(f"Existing translations: {len(existing)}")
    except FileNotFoundError:
        print(f"Target file not found (creating new): {target_path}")

    # --- 3. Merge: existing + new (new overrides existing) ---
    all_translations = {**existing, **new_translations}

    # --- 4. Read base file to get structure ---
    base_names = set()
    base_tree = ET.parse(base_path)
    for e in base_tree.getroot():
        if e.tag == 'string':
            base_names.add(e.get('name'))

    # --- 5. Handle stale strings ---
    stale = set(all_translations.keys()) - base_names
    if stale:
        if keep_stale:
            print(f"Keeping {len(stale)} stale strings (not in base): {sorted(stale)[:5]}{'...' if len(stale) > 5 else ''}")
        else:
            print(f"Dropping {len(stale)} stale strings (not in base): {sorted(stale)[:5]}{'...' if len(stale) > 5 else ''}")
            for name in stale:
                del all_translations[name]

    # --- 6. Walk base file and emit restructured output ---
    with open(base_path, 'r') as f:
        base_lines = f.readlines()

    output_lines = []
    output_lines.append("<?xml version='1.0' encoding='UTF-8'?>\n")

    emitted = set()
    i = 0
    while i < len(base_lines):
        line = base_lines[i]
        stripped = line.strip()

        # Skip XML declaration (we wrote our own above)
        if stripped.startswith('<?xml'):
            i += 1
            continue

        # Multi-line comment block (spanning lines)
        if stripped.startswith(CS) and CE not in stripped:
            block = []
            j = i
            while j < len(base_lines):
                block.append(base_lines[j])
                if CE in base_lines[j]:
                    break
                j += 1
            block_text = ''.join(block)
            # Skip copyright/author header comments, keep all others
            if 'Copyright' not in block_text and '@author' not in block_text:
                # Normalize first line to Transifex style: "  <!--TEXT..." (no padding after <!--)
                first = block[0]
                m_first = re.match(r'^\s*' + re.escape(CS) + r' ?(.*\n?)$', first)
                if m_first:
                    block[0] = '  ' + CS + m_first.group(1)
                # Normalize last line: "...TEXT-->" (no padding before -->)
                last = block[-1]
                m_last = re.match(r'^(.*?) *' + re.escape(CE) + r'(\s*)$', last)
                if m_last:
                    block[-1] = m_last.group(1) + CE + m_last.group(2)
                for bl in block:
                    output_lines.append(bl)
            i = j + 1
            continue

        # Resources open/close tags
        if stripped == '<resources>':
            output_lines.append('<resources>\n')
            i += 1
            continue
        if stripped == '</resources>':
            i += 1
            continue

        # Single-line comment — normalize to Transifex style: <!--TEXT--> (no inner padding)
        if stripped.startswith(CS) and CE in stripped:
            inner = stripped[len(CS):stripped.rindex(CE)]
            inner = inner.strip()
            output_lines.append('  ' + CS + inner + CE + '\n')
            i += 1
            continue

        # Blank line — skip (Transifex output does not preserve blank lines between groups)
        if stripped == '':
            i += 1
            continue

        # Multi-line <string> in base (opening without closing on same line)
        m = re.match(r'\s*<string name="([^"]+)">', stripped)
        if m and '</string>' not in stripped:
            name = m.group(1)
            # Skip all continuation lines until </string>
            while i < len(base_lines) and '</string>' not in base_lines[i]:
                i += 1
            # Emit as single line from our translations dict
            if name in all_translations:
                output_lines.append('  <string name="{}">{}</string>\n'.format(
                    name, all_translations[name]))
                emitted.add(name)
            i += 1
            continue

        # Single-line <string>
        m = re.match(r'\s*<string name="([^"]+)">(.*)</string>', stripped)
        if m:
            name = m.group(1)
            if name in all_translations:
                output_lines.append('  <string name="{}">{}</string>\n'.format(
                    name, all_translations[name]))
                emitted.add(name)
            i += 1
            continue

        # <string-array> — preserve from target if it exists
        sa_match = re.match(r'\s*<string-array\s+name="([^"]+)"', stripped)
        if sa_match:
            sa_name = sa_match.group(1)
            # Skip the <string-array> block in base
            while i < len(base_lines) and '</string-array>' not in base_lines[i]:
                i += 1
            i += 1  # skip the closing </string-array> line
            # Emit the target's version if it exists
            if sa_name in existing_string_arrays:
                for sa_line in existing_string_arrays[sa_name]:
                    output_lines.append(sa_line)
            continue

        # Anything else — skip
        i += 1

    # --- 7. Append stale strings if --keep-stale ---
    if keep_stale:
        unemitted = set(all_translations.keys()) - emitted
        if unemitted:
            output_lines.append('\n')
            output_lines.append('  ' + CS + ' Additional translations ' + CE + '\n')
            for name in sorted(unemitted):
                output_lines.append('  <string name="{}">{}</string>\n'.format(
                    name, all_translations[name]))

    output_lines.append('</resources>\n')

    # --- 8. Write output (primary target + any configured mirror targets) ---
    targets = [target_path] + get_android_mirror_paths(lang)
    for tpath in targets:
        os.makedirs(os.path.dirname(tpath), exist_ok=True)
        with open(tpath, 'w') as f:
            f.writelines(output_lines)

    # --- 9. Self-check ---
    comment_count = sum(1 for l in output_lines if CS in l and 'Additional' not in l)
    string_count = sum(1 for l in output_lines if '<string name=' in l)
    print(f"Strings: {string_count} (emitted: {len(emitted)})")
    print(f"Comments: {comment_count}")
    print(f"Written to: {target_path}")
    for mirror in targets[1:]:
        print(f"Mirror written to: {mirror}")
    if comment_count == 0:
        print("WARNING: No comments found in output - restructure likely broken!")
        sys.exit(1)


if __name__ == '__main__':
    main()
