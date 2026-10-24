#!/usr/bin/env bash
# Contract test for the iOS Debug/Release configuration split (spec 2026-10-24-i4-release-design.md 5.7).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -I - "$ROOT" <<'PY'
import os
import pathlib
import plistlib
import subprocess
import sys
import tempfile
import unittest

root = pathlib.Path(sys.argv.pop())
app = root / 'iosApp/iosApp'
project = (root / 'iosApp/project.yml').read_text()

class ReleaseConfigTest(unittest.TestCase):
    def test_info_plist_uses_build_settings(self):
        with (app / 'Info.plist').open('rb') as source:
            info = plistlib.load(source)
        self.assertEqual(info['CFBundleShortVersionString'], '$(MARKETING_VERSION)')
        self.assertEqual(info['CFBundleVersion'], '$(CURRENT_PROJECT_VERSION)')
        self.assertEqual(info['CFBundleDisplayName'], '$(APP_DISPLAY_NAME)')
        self.assertEqual(info['AndBibleICloudContainer'], '$(ICLOUD_CONTAINER)')

    def test_no_literal_icloud_container(self):
        self.assertNotIn('iCloud.org.andbible.ios', (app / 'Info.plist').read_text())
        text = (app / 'iosApp.entitlements').read_text()
        self.assertNotIn('iCloud.org.andbible.ios', text)
        with (app / 'iosApp.entitlements').open('rb') as source:
            ent = plistlib.load(source)
        self.assertEqual(ent['com.apple.developer.icloud-container-identifiers'], ['$(ICLOUD_CONTAINER)'])
        self.assertEqual(ent['com.apple.developer.ubiquity-container-identifiers'], ['$(ICLOUD_CONTAINER)'])

    def test_project_yml_configs(self):
        self.assertIn('configs:', project)
        for line in ['PRODUCT_BUNDLE_IDENTIFIER: org.andbible.ios.dev', 'PRODUCT_BUNDLE_IDENTIFIER: org.andbible.ios\n',
                     'PRODUCT_NAME: AndBibleDev', 'PRODUCT_NAME: AndBible\n',
                     'APP_DISPLAY_NAME: AndBible Dev', 'APP_DISPLAY_NAME: AndBible\n',
                     'ICLOUD_CONTAINER: iCloud.org.andbible.ios.dev', 'ICLOUD_CONTAINER: iCloud.org.andbible.ios\n',
                     'APP_PRODUCT_NAME: AndBibleDev', 'APP_PRODUCT_NAME: AndBible\n']:
            self.assertIn(line, project)
        self.assertIn('TEST_HOST: $(BUILT_PRODUCTS_DIR)/$(APP_PRODUCT_NAME).app/$(APP_PRODUCT_NAME)', project)
        self.assertNotIn('AndBibleDev.app/AndBibleDev', project)
        self.assertIn('Config/Base.xcconfig', project)
        self.assertIn('archive:\n      config: Release', project)
        self.assertIn('profile:\n      config: Release', project)
        self.assertIn('DEVELOPMENT_TEAM[sdk=iphonesimulator*]', project)

    def test_base_xcconfig(self):
        text = (root / 'iosApp/Config/Base.xcconfig').read_text()
        for line in ['MARKETING_VERSION = 0.0.0', 'CURRENT_PROJECT_VERSION = 1',
                     '#include? "Version.xcconfig"', '#include? "Secrets.xcconfig.local"']:
            self.assertIn(line, text)
        example = (root / 'iosApp/Config/Secrets.example.xcconfig').read_text()
        self.assertIn('DEVELOPMENT_TEAM =', example)

    def test_gitignore(self):
        text = (root / '.gitignore').read_text().splitlines()
        self.assertIn('iosApp/Config/Secrets.xcconfig.local', text)
        self.assertIn('iosApp/Config/Version.xcconfig', text)

    def run_script(self, props):
        with tempfile.TemporaryDirectory() as tmp:
            version_file = pathlib.Path(tmp) / 'version.properties'
            version_file.write_text(props)
            out = pathlib.Path(tmp) / 'Version.xcconfig'
            env = dict(os.environ, VERSION_FILE=str(version_file), VERSION_XCCONFIG=str(out))
            subprocess.run([str(root / 'scripts/ios-version-xcconfig.sh')], env=env, check=True)
            return out.read_text()

    def test_version_xcconfig(self):
        text = self.run_script('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
        self.assertIn('MARKETING_VERSION = 6.0.1228\n', text)

    def test_version_xcconfig_ignores_suffix(self):
        text = self.run_script('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=-beta\n')
        self.assertIn('MARKETING_VERSION = 6.0.1228\n', text)
        self.assertNotIn('beta', text)

unittest.main(verbosity=2)
PY
