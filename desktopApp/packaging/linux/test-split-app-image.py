#!/usr/bin/env python3
"""Tests for split-app-image.py on a small fake jpackage app image."""
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('split-app-image.py')
OWN_JARS = ['desktopApp-6.0.1.jar', 'sharedUi-jvm.jar', 'kdom-jvm-0.1.0-SNAPSHOT.jar']


def make_image(root, own_jar_content=b'own', runtime_content=b'jre', helper_mode=0o755):
    files = {
        'bin/andbible': (b'#!launcher', 0o755),
        'lib/libapplauncher.so': (b'so', 0o644),
        'lib/andbible.png': (b'png', 0o644),
        'lib/app/andbible.cfg': (b'[Application]\napp.classpath=$APPDIR/' + own_jar_content, 0o644),
        'lib/app/desktopApp-6.0.1-0a1b2c.jar': (own_jar_content + b'1', 0o644),
        'lib/app/sharedUi-jvm-3d4e5f.jar': (own_jar_content + b'2', 0o644),
        'lib/app/kdom-jvm-0.1.0-SNAPSHOT-99aa.jar': (own_jar_content + b'3', 0o644),
        'lib/app/skiko-awt-runtime-linux-x64-0.144.6-77bb.jar': (own_jar_content + b'4', 0o644),
        # A third-party jar whose name starts like an own one must stay in base.
        'lib/app/sharedUi-jvm-extras-1.0-1234.jar': (b'3rd', 0o644),
        'lib/app/koin-core-jvm-4.1.0-abcd.jar': (b'koin', 0o644),
        'lib/app/libskiko-linux-x64.so': (b'skiko', 0o644),
        'lib/app/resources/kcef-natives/jcef_helper': (b'helper', helper_mode),
        'lib/app/resources/kcef-natives/libcef.so': (b'cef', 0o644),
        'lib/runtime/release': (b'JAVA_VERSION="17"', 0o644),
        'lib/runtime/lib/libjvm.so': (runtime_content, 0o644),
    }
    for name, (content, mode) in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        path.chmod(mode)
    (root / 'lib/runtime/lib/libjvm-link.so').symlink_to('libjvm.so')


def read_archive(path):
    data = subprocess.run(['zstd', '-dc', str(path)], check=True, capture_output=True).stdout
    with tarfile.open(fileobj=io.BytesIO(data)) as tar:
        return {m.name: (m.mode, m.linkname if m.issym() else tar.extractfile(m).read())
                for m in tar.getmembers() if not m.isdir()}


class SplitAppImageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.extra = self.dir / 'org.andbible.AndBible.desktop'
        self.extra.write_text('[Desktop Entry]\n')

    def tearDown(self):
        self.tmp.cleanup()

    def split(self, name, version='6.0.1', own_jars=OWN_JARS, check=True, **image):
        root = self.dir / name / 'image'
        make_image(root, **image)
        out = self.dir / name / 'out'
        args = ['python3', '-I', str(SCRIPT), '--image', str(root), '--out', str(out), '--version', version,
                '--extra', f'{self.extra}=share/applications/org.andbible.AndBible.desktop']
        for jar in own_jars:
            args += ['--own-jar', jar]
        result = subprocess.run(args, capture_output=True, text=True)
        if check:
            self.assertEqual(result.returncode, 0, result.stderr)
        if result.returncode != 0:
            return result
        manifest = json.loads((out / f'andbible-desktop-{version}-linux-x64-split.json').read_text())
        return dict(
            root=root, manifest=manifest,
            app_file=out / f'andbible-desktop-{version}-linux-x64-app.tar.zst',
            base_file=out / manifest['base']['file'],
        )

    def test_app_holds_per_release_files_and_base_the_rest(self):
        result = self.split('a')
        app, base = read_archive(result['app_file']), read_archive(result['base_file'])
        self.assertEqual(sorted(app), [
            'bin/andbible', 'lib/andbible.png', 'lib/app/andbible.cfg',
            'lib/app/desktopApp-6.0.1-0a1b2c.jar', 'lib/app/kdom-jvm-0.1.0-SNAPSHOT-99aa.jar',
            'lib/app/sharedUi-jvm-3d4e5f.jar', 'lib/app/skiko-awt-runtime-linux-x64-0.144.6-77bb.jar',
            'lib/base-id', 'lib/libapplauncher.so', 'share/applications/org.andbible.AndBible.desktop',
        ])
        self.assertIn('lib/app/sharedUi-jvm-extras-1.0-1234.jar', base)
        self.assertIn('lib/app/resources/kcef-natives/libcef.so', base)
        self.assertEqual(app['lib/base-id'][1].decode().strip(), result['manifest']['base_id'])

    def test_archives_together_reproduce_the_image(self):
        result = self.split('a')
        app, base = read_archive(result['app_file']), read_archive(result['base_file'])
        self.assertFalse(set(app) & set(base))
        merged = {**base, **{k: v for k, v in app.items()
                             if k not in ('lib/base-id', 'share/applications/org.andbible.AndBible.desktop')}}
        root = result['root']
        expected = {}
        for path in root.rglob('*'):
            if path.is_symlink():
                expected[path.relative_to(root).as_posix()] = (0o777, str(path.readlink()))
            elif path.is_file():
                expected[path.relative_to(root).as_posix()] = (path.stat().st_mode & 0o777, path.read_bytes())
        self.assertEqual(merged, expected)
        self.assertEqual(base['lib/app/resources/kcef-natives/jcef_helper'][0], 0o755)

    def test_new_release_with_only_app_changes_keeps_the_identical_base(self):
        first = self.split('a', version='6.0.1')
        second = self.split('b', version='6.0.1', own_jar_content=b'changed')
        self.assertEqual(first['manifest']['base_id'], second['manifest']['base_id'])
        self.assertEqual(first['base_file'].read_bytes(), second['base_file'].read_bytes())
        self.assertNotEqual(first['manifest']['app']['sha256'], second['manifest']['app']['sha256'])

    def test_runtime_content_or_mode_change_gives_a_new_base(self):
        first = self.split('a')['manifest']['base_id']
        self.assertNotEqual(first, self.split('b', runtime_content=b'jre2')['manifest']['base_id'])
        self.assertNotEqual(first, self.split('c', helper_mode=0o644)['manifest']['base_id'])

    def test_own_jar_missing_from_image_fails(self):
        result = self.split('a', own_jars=OWN_JARS + ['sharedLogic-jvm.jar'], check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('sharedLogic-jvm.jar matches 0 files', result.stderr)


if __name__ == '__main__':
    unittest.main()
