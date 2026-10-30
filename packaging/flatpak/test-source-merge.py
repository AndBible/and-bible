#!/usr/bin/env python3
"""Exercise mirror deduplication without discarding differing pinned bytes."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('merge-gradle-sources.py')


class SourceMergeTest(unittest.TestCase):
    def merge(self, entries):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            inputs = []
            for index, entry in enumerate(entries):
                path = directory / f'{index}.json'
                path.write_text(json.dumps([entry]))
                inputs.append(str(path))
            output = directory / 'output.json'
            subprocess.run(['python3', '-I', str(SCRIPT), str(output), *inputs], check=True)
            return json.loads(output.read_text())

    def test_identical_mirrors_coalesce(self):
        entry = dict(type='file', url='https://central/a.pom', sha512='a',
                     dest='offline-repository/a', **{'dest-filename': 'a.pom'})
        result = self.merge([entry, dict(entry, url='https://portal/a.pom')])
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]['sha512'], 'a')

    def test_unknown_conflicting_mirrors_fail_closed(self):
        entry = dict(type='file', url='https://central/a.pom', sha512='a',
                     dest='offline-repository/a', **{'dest-filename': 'a.pom'})
        with self.assertRaises(subprocess.CalledProcessError):
            self.merge([entry, dict(entry, url='https://portal/a.pom', sha512='b')])

    def test_reviewed_marker_bytes_keep_distinct_repository_paths(self):
        entry = dict(type='file', url='https://central/a.pom',
                     sha512='2f9f0f5ec17b42578192e57281c01b05958950c1518cfce57ae8c5493580e770f5646f6c8a712ec3e9f235a5d3c529d19d1f6a1d9970ae5ac3c6e5fe29587c2d',
                     dest='offline-repository/a', **{'dest-filename': 'a.pom'})
        other = dict(entry, url='https://portal/a.pom',
                     sha512='cd5aa95e57a6b6c0d82631d37ac48f776ea70a73bfbb868c2f13ffd2966cf4025c45dace726f7e029a47255c505746f0aba0a955d928da6e0dad62cfa67c627e')
        result = self.merge([entry, other])
        self.assertEqual(len(result), 2)
        self.assertEqual({x['sha512'] for x in result}, {entry['sha512'], other['sha512']})
        self.assertEqual(len({x['dest'] for x in result}), 2)
        self.assertTrue(all(x['dest'].endswith('/a') for x in result))


if __name__ == '__main__':
    unittest.main()
