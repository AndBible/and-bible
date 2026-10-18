import json
import os
import tempfile
import unittest
import zipfile

import package_fixtures


def write(path, data=b"x"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


class PackageFixturesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        t = self.tmp.name
        self.gen, self.oracle, self.out = (os.path.join(t, n) for n in ("gen", "oracle", "out"))
        rich = os.path.join(self.gen, "1.0.2", "26", "rich")
        write(os.path.join(rich, "AndBible.store"), b"a")
        write(os.path.join(rich, "LocalStore.store-wal"), b"b")
        write(os.path.join(rich, "PreservedDatabases", "ai_settings.sqlite3"), b"c")
        write(os.path.join(self.gen, "1.0.2", "26", "stray", "notes.txt"))  # no AndBible.store: skipped
        write(os.path.join(self.gen, "1.0.1", "26", "fresh", "AndBible.store"), b"d")  # no oracle
        orc = os.path.join(self.oracle, "1.0.2", "26", "rich", "oracle")
        write(os.path.join(orc, "report.json"), json.dumps({"fingerprint": "abc"}).encode())
        write(os.path.join(orc, "ai_settings.sqlite3"), b"e")

    def test_zip_entries_and_manifest(self):
        manifest = package_fixtures.package(self.gen, self.oracle, self.out)
        self.assertEqual([(m["app"], m["scenario"]) for m in manifest],
                         [("1.0.1", "fresh"), ("1.0.2", "rich")])
        with zipfile.ZipFile(os.path.join(self.out, "1.0.2", "26", "rich.zip")) as z:
            self.assertEqual(sorted(z.namelist()),
                             ["AndBible.store", "LocalStore.store-wal", "PreservedDatabases/ai_settings.sqlite3",
                              "oracle/ai_settings.sqlite3", "oracle/report.json"])
        rich = manifest[1]
        self.assertEqual(rich["fingerprint"], "abc")
        self.assertEqual(rich["path"], "1.0.2/26/rich.zip")
        self.assertIsNone(manifest[0]["fingerprint"])
        with open(os.path.join(self.out, "manifest.json")) as f:
            self.assertEqual(json.load(f), manifest)
        self.assertFalse(os.path.exists(os.path.join(self.out, "1.0.2", "26", "stray.zip")))

    def test_deterministic(self):
        first = package_fixtures.package(self.gen, self.oracle, self.out)
        second = package_fixtures.package(self.gen, self.oracle, self.out + "2")
        self.assertEqual([m["sha256"] for m in first], [m["sha256"] for m in second])

    def test_missing_roots_give_empty_manifest(self):
        self.assertEqual(package_fixtures.package(os.path.join(self.tmp.name, "nope"), self.oracle, self.out), [])


if __name__ == "__main__":
    unittest.main()
