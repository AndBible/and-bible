#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -I - "$ROOT" <<'PY'
import json
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

def png_facts(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', f'{path.name} is not a PNG'
    width, height = struct.unpack('>II', data[16:24])
    color_type = data[25]
    has_trns = False
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        has_trns = has_trns or kind == b'tRNS'
        offset += 12 + length
        if kind == b'IEND':
            break
    return width, height, color_type, has_trns

class PrimaryIconContractTest(unittest.TestCase):
    catalog = app / 'Assets.xcassets'
    iconset = catalog / 'AppIcon.appiconset'

    def test_catalog_exists(self):
        self.assertTrue((self.catalog / 'Contents.json').is_file())
        self.assertTrue((self.iconset / 'Contents.json').is_file())

    def test_marketing_icon_listed(self):
        images = json.loads((self.iconset / 'Contents.json').read_text())['images']
        self.assertTrue([i for i in images if i.get('idiom') == 'universal' and i.get('size') == '1024x1024'
                         and i.get('platform') == 'ios'], 'need a 1024x1024 universal ios icon')

    def test_listed_files_exist_with_stated_size_and_no_alpha(self):
        images = json.loads((self.iconset / 'Contents.json').read_text())['images']
        self.assertTrue(images)
        for image in images:
            with self.subTest(image=image):
                path = self.iconset / image['filename']
                self.assertTrue(path.is_file(), f'Missing {path.name}')
                points = int(image['size'].split('x')[0])
                scale = int(image.get('scale', '1x').rstrip('x'))
                width, height, color_type, has_trns = png_facts(path)
                self.assertEqual((width, height), (points * scale, points * scale))
                self.assertNotIn(color_type, (4, 6), 'alpha channel')
                self.assertFalse(has_trns, 'tRNS transparency')

    def test_project_names_the_primary_icon(self):
        project = (root / 'iosApp/project.yml').read_text()
        self.assertIn('ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon', project)

unittest.main(verbosity=2)
PY
