#!/usr/bin/env bash
# Contract test for the iOS privacy manifest (plan 2026-10-24-i4b, Task 7; scan evidence in
# docs/superpowers/status/2026-10-24-i4b-required-reason-scan.md).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -I - "$ROOT" <<'PY'
import pathlib
import plistlib
import sys
import unittest

root = pathlib.Path(sys.argv.pop())
manifest = root / 'iosApp/iosApp/PrivacyInfo.xcprivacy'
project = (root / 'iosApp/project.yml').read_text()

EXPECTED = {
    'NSPrivacyAccessedAPICategoryUserDefaults': {'CA92.1'},
    'NSPrivacyAccessedAPICategoryDiskSpace': {'85F4.1', 'E174.1'},
    'NSPrivacyAccessedAPICategoryFileTimestamp': {'C617.1'},
    'NSPrivacyAccessedAPICategorySystemBootTime': {'35F9.1'},
}

def load():
    with manifest.open('rb') as source:
        return plistlib.load(source)

class PrivacyManifestTest(unittest.TestCase):
    def test_no_tracking_and_no_collected_data(self):
        data = load()
        self.assertIs(data['NSPrivacyTracking'], False)
        self.assertEqual(data['NSPrivacyTrackingDomains'], [])
        self.assertEqual(data['NSPrivacyCollectedDataTypes'], [])

    def test_exact_categories_and_reasons(self):
        entries = load()['NSPrivacyAccessedAPITypes']
        categories = [e['NSPrivacyAccessedAPIType'] for e in entries]
        self.assertEqual(len(categories), len(set(categories)), 'duplicate category')
        actual = {e['NSPrivacyAccessedAPIType']: e['NSPrivacyAccessedAPITypeReasons'] for e in entries}
        for reasons in actual.values():
            self.assertEqual(len(reasons), len(set(reasons)), 'duplicate reason')
        self.assertEqual({k: set(v) for k, v in actual.items()}, EXPECTED)

    def test_project_yml_puts_manifest_in_resources(self):
        self.assertIn('path: iosApp/PrivacyInfo.xcprivacy', project)
        idx = project.index('path: iosApp/PrivacyInfo.xcprivacy')
        self.assertIn('buildPhase: resources', project[idx:idx + 120])
        # not listed twice: the generic source entry must exclude it
        self.assertIn('PrivacyInfo.xcprivacy', project[:idx])

unittest.main(argv=['test'], verbosity=2)
PY
