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
        self.assertEqual(info['AndBibleCommit'], '$(AND_BIBLE_COMMIT_HASH)')

    def test_document_types_have_a_name(self):
        # App Store Connect rejects an upload whose document type lacks CFBundleTypeName (ITMS-90149).
        with (app / 'Info.plist').open('rb') as source:
            info = plistlib.load(source)
        for doc_type in info['CFBundleDocumentTypes']:
            self.assertTrue(doc_type.get('CFBundleTypeName'), doc_type)

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
        self.assertIn('postBuildScripts:\n      - name: "Record source commit"', project)
        self.assertIn('inputFiles:\n          - $(TARGET_BUILD_DIR)/$(INFOPLIST_PATH)', project)
        self.assertIn('scripts/write-commit-info.sh', project)

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

    def test_release_native_link_gets_larger_heap(self):
        self.assertIn('if [ "$CONFIGURATION" = "Release" ]; then\n            EXTRA="-Pkotlin.native.disableCompilerDaemon=true -Pkotlin.native.jvmArgs=-Xmx8g"', project)
        self.assertIn('$EXTRA :sharedUi:embedAndSignAppleFrameworkForXcode', project)

    def test_release_requires_generated_version(self):
        self.assertIn('[ "$CONFIGURATION" = "Release" ] && [ "$MARKETING_VERSION" = "0.0.0" ]', project)
        workflow = (root / '.github/workflows/ios.yml').read_text()
        gen = workflow.index('scripts/ios-version-xcconfig.sh')
        self.assertLess(gen, workflow.index('xcodegen generate'))

    def run_script(self, props):
        with tempfile.TemporaryDirectory() as tmp:
            version_file = pathlib.Path(tmp) / 'version.properties'
            version_file.write_text(props)
            out = pathlib.Path(tmp) / 'Version.xcconfig'
            env = dict(os.environ, VERSION_FILE=str(version_file), VERSION_XCCONFIG=str(out))
            env.pop('AND_BIBLE_COMMIT', None)
            subprocess.run([str(root / 'scripts/ios-version-xcconfig.sh')], env=env, check=True)
            return out.read_text()

    def test_version_xcconfig(self):
        text = self.run_script('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
        self.assertIn('MARKETING_VERSION = 6.0.1228\n', text)

    def test_version_xcconfig_includes_git_commit_from_script_root(self):
        text = self.run_script('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
        commit = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
        self.assertIn(f'AND_BIBLE_COMMIT_HASH = {commit}\n', text)
        self.assertIn('AND_BIBLE_COMMIT_OVERRIDE = \n', text)

    def test_version_xcconfig_accepts_commit_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            version_file = pathlib.Path(tmp) / 'version.properties'
            version_file.write_text('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
            out = pathlib.Path(tmp) / 'Version.xcconfig'
            env = dict(os.environ, VERSION_FILE=str(version_file), VERSION_XCCONFIG=str(out), AND_BIBLE_COMMIT='a' * 40)
            subprocess.run([str(root / 'scripts/ios-version-xcconfig.sh')], env=env, check=True)
            self.assertIn(f'AND_BIBLE_COMMIT_HASH = {"a" * 40}\n', out.read_text())
            self.assertIn(f'AND_BIBLE_COMMIT_OVERRIDE = {"a" * 40}\n', out.read_text())

    def test_version_xcconfig_prefers_mac_owner_over_stub_git(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp) / 'and-bible'
            scripts = repo / 'scripts'
            scripts.mkdir(parents=True)
            (scripts / 'ios-version-xcconfig.sh').write_bytes((root / 'scripts/ios-version-xcconfig.sh').read_bytes())
            (scripts / 'ios-version-xcconfig.sh').chmod(0o755)
            for name in ['lib-version.sh', 'ios-commit.sh']:
                source = root / 'scripts' / name
                target = scripts / name
                target.write_bytes(source.read_bytes())
                target.chmod(0o755)
            (repo / 'version.properties').write_text('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
            subprocess.run(['git', 'init', '-q', str(repo)], check=True)
            subprocess.run(['git', '-C', str(repo), 'config', 'user.email', 'test@example.invalid'], check=True)
            subprocess.run(['git', '-C', str(repo), 'config', 'user.name', 'Test'], check=True)
            (repo / 'stub.txt').write_text('mac stub')
            subprocess.run(['git', '-C', str(repo), 'add', 'stub.txt'], check=True)
            subprocess.run(['git', '-C', str(repo), 'commit', '-qm', 'stub'], check=True)
            (repo.parent / '.mac-owner').write_text(f'container=test\nsource_revision={"b" * 40}\nsource_dirty=0\ntoken=test\n')
            out = pathlib.Path(tmp) / 'Version.xcconfig'
            env = dict(os.environ, VERSION_FILE=str(repo / 'version.properties'), VERSION_XCCONFIG=str(out))
            env.pop('AND_BIBLE_COMMIT', None)
            subprocess.run([str(scripts / 'ios-version-xcconfig.sh')], env=env, check=True)
            self.assertIn(f'AND_BIBLE_COMMIT_HASH = {"b" * 40}\n', out.read_text())
            resolved = subprocess.check_output([str(scripts / 'ios-commit.sh'), str(repo)], text=True, env=dict(env, AND_BIBLE_COMMIT_HASH='c' * 40))
            self.assertEqual(resolved, 'b' * 40)
            (repo.parent / '.mac-owner').write_text('container=test\nsource_revision=invalid\nsource_dirty=0\ntoken=test\n')
            self.assertEqual(subprocess.check_output([str(scripts / 'ios-commit.sh'), str(repo)], text=True), '')

    def test_version_xcconfig_is_blank_without_git(self):
        with tempfile.TemporaryDirectory() as tmp:
            isolated = pathlib.Path(tmp)
            scripts = isolated / 'scripts'
            scripts.mkdir()
            (scripts / 'ios-version-xcconfig.sh').write_bytes((root / 'scripts/ios-version-xcconfig.sh').read_bytes())
            (scripts / 'ios-version-xcconfig.sh').chmod(0o755)
            (scripts / 'lib-version.sh').write_bytes((root / 'scripts/lib-version.sh').read_bytes())
            (scripts / 'ios-commit.sh').write_bytes((root / 'scripts/ios-commit.sh').read_bytes())
            (scripts / 'ios-commit.sh').chmod(0o755)
            version_file = isolated / 'version.properties'
            version_file.write_text('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
            out = isolated / 'Version.xcconfig'
            env = dict(os.environ, VERSION_FILE=str(version_file), VERSION_XCCONFIG=str(out))
            env.pop('AND_BIBLE_COMMIT', None)
            subprocess.run([str(scripts / 'ios-version-xcconfig.sh')], cwd=isolated, env=env, check=True)
            self.assertIn('AND_BIBLE_COMMIT_HASH = \n', out.read_text())

    def test_version_xcconfig_rejects_invalid_commit_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            version_file = pathlib.Path(tmp) / 'version.properties'
            version_file.write_text('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=\n')
            out = pathlib.Path(tmp) / 'Version.xcconfig'
            env = dict(os.environ, VERSION_FILE=str(version_file), VERSION_XCCONFIG=str(out), AND_BIBLE_COMMIT='unknown')
            result = subprocess.run([str(root / 'scripts/ios-version-xcconfig.sh')], env=env, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)

    def test_version_xcconfig_ignores_suffix(self):
        text = self.run_script('versionMajorMinor=6.0\nversionCode=1228\nversionNameSuffix=-beta\n')
        self.assertIn('MARKETING_VERSION = 6.0.1228\n', text)
        self.assertNotIn('beta', text)

unittest.main(verbosity=2)
PY
