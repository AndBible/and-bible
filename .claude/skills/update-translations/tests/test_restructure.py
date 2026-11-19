"""Round-trip tests for attributed strings and resource key preservation."""
import io
import os
import subprocess
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import restructure  # noqa: E402
import validate  # noqa: E402

BASE = """<?xml version='1.0' encoding='UTF-8'?>
<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Search -->
  <string name="a">A</string>
  <plurals name="verse_count">
    <item quantity="one">%d verse</item>
    <item quantity="other">%d verses</item>
  </plurals>
  <string-array name="arr" tools:ignore="X">
    <item>one</item>
  </string-array>
  <string name="b">B</string>
</resources>
"""
TARGET = """<?xml version='1.0' encoding='UTF-8'?>
<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Search -->
  <string name="a">AA</string>
  <plurals name="verse_count">
    <!-- keep me -->
    <item quantity="one">%d jae</item>
    <item quantity="other">%d jaetta</item>
  </plurals>
  <string-array name="arr" tools:ignore="X">
    <item>yksi</item>
  </string-array>
</resources>
"""


class RoundTrip(unittest.TestCase):
    def run_restructure(self, target_text, chunk=None, base_text=BASE, keep_stale=False):
        with tempfile.TemporaryDirectory() as d:
            base, target = os.path.join(d, 'base.xml'), os.path.join(d, 'target.xml')
            Path(base).write_text(base_text)
            Path(target).write_text(target_text)
            argv = ['restructure.py', 'xx']
            if keep_stale:
                argv.append('--keep-stale')
            if chunk:
                cp = os.path.join(d, 'chunk.xml')
                Path(cp).write_text(chunk)
                argv.append(cp)
            paths = {'android_base': base, 'android_target': target}
            with mock.patch.object(restructure, 'resolve_paths', return_value=paths), \
                 mock.patch.object(restructure, 'get_android_mirror_paths', return_value=[]), \
                 mock.patch.object(sys, 'argv', argv):
                restructure.main()
            return Path(target).read_text()

    def test_plurals_and_arrays_survive(self):
        out = self.run_restructure(TARGET, '<string name="b">BB</string>\n')
        self.assertIn('<!-- keep me -->', out)
        self.assertIn('%d jaetta', out)
        self.assertIn('<string-array name="arr" tools:ignore="X">', out)
        self.assertIn('<string name="b">BB</string>', out)
        # order follows base: plurals between a and b
        self.assertLess(out.index('<string name="a">'), out.index('<plurals'))
        self.assertLess(out.index('</plurals>'), out.index('<string name="b">'))

    def test_new_plurals_from_chunk_override(self):
        chunk = ('<plurals name="verse_count"><item quantity="one">%d X</item>'
                 '<item quantity="other">%d Y</item></plurals>\n')
        out = self.run_restructure(TARGET, chunk)
        self.assertIn('%d Y', out)
        self.assertNotIn('jaetta', out)

    def test_attributed_base_strings_survive(self):
        for entry in (
            '<string name="a" translatable="false">A</string>',
            '<string formatted="false" name="a">A</string>',
            '<string name="a" formatted="false">\n    A\n  </string>',
            '<string\n    name="a" formatted="false">A</string>',
        ):
            with self.subTest(entry=entry):
                out = self.run_restructure(TARGET, base_text=BASE.replace(
                    '<string name="a">A</string>', entry))
                self.assertIn('<string name="a">AA</string>', out)

    def test_attributed_target_strings_are_copied_verbatim(self):
        for entry in (
            '<string name="a" formatted="false">AA &amp; <b>bold</b></string>',
            '<string translatable="false" name="a">\n    AA\n  </string>',
            '<string tools:ignore="StringFormatInvalid" name="a">AA</string>',
            "<string formatted='false' name='a'>AA</string>",
            '<string name="a" formatted="false" />',
        ):
            with self.subTest(entry=entry):
                target = TARGET.replace('<string name="a">AA</string>', entry)
                out = self.run_restructure(target)
                self.assertIn(entry, out)
                root = ET.fromstring(out)
                self.assertIsNotNone(root.find('string[@name="a"]'))
                self.assertIsNotNone(root.find('plurals[@name="verse_count"]'))
                self.assertIsNotNone(root.find('string-array[@name="arr"]'))

    def test_chunk_string_attributes_survive_xml_and_fallback_loading(self):
        entry = '<string formatted="false" name="b">BB &amp; <b>bold</b></string>'
        for prefix in ('', 'not XML & broken <\n'):
            with self.subTest(prefix=prefix):
                out = self.run_restructure(TARGET, prefix + entry)
                self.assertIn(entry, out)

    def test_chunk_keeps_adjacent_and_multiline_strings(self):
        chunk = ('<string name="a">New A</string><string name="b" formatted="false">\n'
                 '  BB &amp; <b>bold</b>\n</string>')
        out = self.run_restructure(TARGET, chunk)
        root = ET.fromstring(out)
        self.assertEqual(root.find('string[@name="a"]').text, 'New A')
        self.assertIsNotNone(root.find('string[@name="b"]'))
        self.assertEqual(root.find('string[@name="b"]').get('formatted'), 'false')
        self.assertIn('>BB &amp; <b>bold</b></string>', out)

    def test_target_preserves_adjacent_and_multiline_opening_tags(self):
        target = TARGET.replace('<string name="a">AA</string>',
                                '<string\n    formatted="false" name="a">AA</string>'
                                '<string name="b">BB</string>')
        out = self.run_restructure(target)
        self.assertIn('<string\n    formatted="false" name="a">AA</string>', out)
        self.assertIn('<string name="b">BB</string>', out)

    def test_self_closing_target_does_not_consume_next_string(self):
        target = TARGET.replace('<string name="a">AA</string>',
                                '<string name="a" formatted="false" />\n'
                                '  <string name="b">BB</string>')
        out = self.run_restructure(target)
        root = ET.fromstring(out)
        self.assertIsNone(root.find('string[@name="a"]').text)
        self.assertEqual(root.find('string[@name="b"]').text, 'BB')
        self.assertLess(out.index('</plurals>'), out.index('<string name="b">'))

    def test_chunk_ignores_commented_out_strings(self):
        chunk = ('<!-- <string name="a">Do not use</string> -->\n'
                 '<string name="b">BB</string>')
        root = ET.fromstring(self.run_restructure(TARGET, chunk))
        self.assertEqual(root.find('string[@name="a"]').text, 'AA')

    def test_target_only_namespace_survives(self):
        target = TARGET.replace('name="a">', 'name="a" tools:ignore="X">')
        base = BASE.replace(' xmlns:tools="http://schemas.android.com/tools"', '')
        base = base.replace(' tools:ignore="X"', '')
        root = ET.fromstring(self.run_restructure(target, base_text=base))
        self.assertEqual(root.find('string[@name="a"]').get(
            '{http://schemas.android.com/tools}ignore'), 'X')

    def test_self_closing_base_string_does_not_skip_next_entry(self):
        base = BASE.replace('<string name="a">A</string>', '<string name="a" formatted="false" />')
        root = ET.fromstring(self.run_restructure(TARGET, base_text=base))
        self.assertIsNotNone(root.find('string[@name="a"]'))
        self.assertIsNotNone(root.find('plurals[@name="verse_count"]'))

    def test_keep_stale_preserves_attributes(self):
        entry = '<string name="old" formatted="false">Old</string>'
        target = TARGET.replace('</resources>', entry + '\n</resources>')
        out = self.run_restructure(target, keep_stale=True)
        self.assertIn(entry, out)

    def test_validate_detects_loss(self):
        base = ET.fromstring(BASE)
        lost = ET.fromstring('<resources><string name="a">x</string></resources>')
        prev = validate.block_names(ET.fromstring(TARGET))
        errs = validate.check_blocks(base, lost, prev)
        self.assertTrue(any('plurals' in e for e in errs))
        self.assertTrue(any('string-array' in e for e in errs))
        self.assertEqual(validate.check_blocks(base, ET.fromstring(TARGET), prev), [])


class KeyPreservation(unittest.TestCase):
    def run_validation(self, previous, current,
                       base_text='<resources><string name="a">A</string></resources>'):
        with tempfile.TemporaryDirectory() as d:
            base, target = Path(d) / 'base.xml', Path(d) / 'target.xml'
            base.write_text(base_text)
            target.write_text(previous)
            for args in (
                ['init', '-q'], ['add', 'target.xml'],
                ['-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                 '-c', 'commit.gpgsign=false', 'commit', '-qm', 'Before rewrite'],
            ):
                subprocess.run(['git', *args], cwd=d, check=True, capture_output=True)
            target.write_text(current)
            paths = {
                'android_base': str(base), 'android_target': str(target),
                'vuejs_base': None, 'vuejs_target': None,
                'playstore_base': None, 'playstore_target': None,
            }
            output = io.StringIO()
            with mock.patch.object(validate, 'resolve_paths', return_value=paths), \
                 mock.patch.object(validate, 'LOCALE_ALIASES', {}), \
                 mock.patch.object(sys, 'argv', ['validate.py', 'xx']), \
                 mock.patch('sys.stdout', output), \
                 self.assertRaises(SystemExit) as result:
                validate.main()
            return result.exception.code, output.getvalue()

    def test_validator_rejects_lost_keys_even_when_counts_do_not_drop(self):
        for tag in ('string', 'plurals', 'string-array'):
            with self.subTest(tag=tag):
                before = ('<resources><string name="a">A</string>'
                          f'<{tag} name="old">old</{tag}></resources>')
                after = ('<resources><string name="a">A</string>'
                         f'<{tag} name="new">new</{tag}></resources>')
                code, output = self.run_validation(before, after, base_text=before)
                self.assertEqual(code, 1, output)
                self.assertIn('old', output)
                self.assertIn('committed', output)

    def test_validator_rejects_lost_attributed_string_still_in_base(self):
        before = ('<resources><string name="a">A</string>'
                  '<string formatted="false" name="old">Old</string></resources>')
        after = '<resources><string name="a">A</string></resources>'
        code, output = self.run_validation(before, after, base_text=before)
        self.assertEqual(code, 1, output)
        self.assertIn("<string> keys lost vs committed version: ['old']", output)

    def test_validator_allows_stale_key_removal_with_warning(self):
        for tag in ('string', 'plurals', 'string-array'):
            with self.subTest(tag=tag):
                before = ('<resources><string name="a">A</string>'
                          f'<{tag} name="old">Old</{tag}></resources>')
                after = '<resources><string name="a">A</string></resources>'
                code, output = self.run_validation(before, after)
                self.assertEqual(code, 0, output)
                self.assertIn('WARNINGS', output)
                self.assertIn(f'<{tag}> stale keys removed', output)
                self.assertIn('old', output)
                self.assertNotIn('ERRORS', output)

    def test_validator_allows_preserved_keys_and_additions(self):
        before = '<resources><string name="a">A</string></resources>'
        after = before.replace('</resources>', '<string name="new">New</string></resources>')
        code, output = self.run_validation(before, after)
        self.assertEqual(code, 0, output)


if __name__ == '__main__':
    unittest.main()
