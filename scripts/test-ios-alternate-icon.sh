#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -I - "$ROOT" <<'PY'
import pathlib
import plistlib
import struct
import sys
import unittest

root = pathlib.Path(sys.argv.pop())
app = root / 'iosApp/iosApp'

class AlternateIconContractTest(unittest.TestCase):
    def test_plist(self):
        with (app / 'Info.plist').open('rb') as source:
            info = plistlib.load(source)
        for key, name in [('CFBundleIcons', 'CalculatorIcon'), ('CFBundleIcons~ipad', 'CalculatorIcon-76')]:
            with self.subTest(key=key):
                icon = info.get(key, {}).get('CFBundleAlternateIcons', {}).get('CalculatorIcon')
                self.assertIsNotNone(icon, f'{key} must declare CalculatorIcon')
                self.assertEqual(icon['CFBundleIconFiles'], [name])
                self.assertIs(icon['UIPrerenderedIcon'], False)

    def test_png_sizes_and_opacity(self):
        for filename, size in [('CalculatorIcon.png', 60), ('CalculatorIcon@2x.png', 120),
                               ('CalculatorIcon@3x.png', 180), ('CalculatorIcon-76.png', 76),
                               ('CalculatorIcon-76@2x.png', 152)]:
            with self.subTest(filename=filename):
                self.assertTrue((app / filename).is_file(), f'Missing {filename}')
                data = (app / filename).read_bytes()
                self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
                self.assertEqual(struct.unpack('>II', data[16:24]), (size, size))
                self.assertNotIn(data[25], (4, 6))
                offset = 8
                while offset + 12 <= len(data):
                    length = struct.unpack('>I', data[offset:offset + 4])[0]
                    kind = data[offset + 4:offset + 8]
                    self.assertNotEqual(kind, b'tRNS')
                    offset += 12 + length
                    if kind == b'IEND':
                        break

    def test_xcodegen_includes_app_resources(self):
        # XcodeGen automatically assigns PNGs under this source directory to Resources.
        project = (root / 'iosApp/project.yml').read_text()
        self.assertIn('- path: iosApp', project)

unittest.main(verbosity=2)
PY
