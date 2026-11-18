"""Round-trip tests: restructure.py and validate.py must not lose <plurals>/<string-array>."""
import os
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
    def run_restructure(self, target_text, chunk=None):
        with tempfile.TemporaryDirectory() as d:
            base, target = os.path.join(d, 'base.xml'), os.path.join(d, 'target.xml')
            open(base, 'w').write(BASE)
            open(target, 'w').write(target_text)
            argv = ['restructure.py', 'xx']
            if chunk:
                cp = os.path.join(d, 'chunk.xml')
                open(cp, 'w').write(chunk)
                argv.append(cp)
            paths = {'android_base': base, 'android_target': target}
            with mock.patch.object(restructure, 'resolve_paths', return_value=paths), \
                 mock.patch.object(restructure, 'get_android_mirror_paths', return_value=[]), \
                 mock.patch.object(sys, 'argv', argv):
                restructure.main()
            return open(target).read()

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

    def test_validate_detects_loss(self):
        base = ET.fromstring(BASE)
        lost = ET.fromstring('<resources><string name="a">x</string></resources>')
        prev = validate.block_names(ET.fromstring(TARGET))
        errs = validate.check_blocks(base, lost, prev)
        self.assertTrue(any('plurals' in e for e in errs))
        self.assertTrue(any('string-array' in e for e in errs))
        self.assertEqual(validate.check_blocks(base, ET.fromstring(TARGET), prev), [])


if __name__ == '__main__':
    unittest.main()
