"""Tests for locale_aliases: alias dirs are byte-exact mirrors of their sources."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import locale_aliases as la  # noqa: E402


class MirrorTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.res = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)

    def write(self, d, name, data):
        p = self.res / f"values-{d}" / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)

    def test_copies_bytes_and_removes_stale_files(self):
        self.write("in", "strings.xml", "<resources>ä\r\n</resources>".encode())
        self.write("in", "arrays.xml", b"<resources/>")
        self.write("id", "strings.xml", b"old")
        self.write("id", "gone.xml", b"stale")
        la.mirror_all(str(self.res), {"id": "in"})
        self.assertEqual((self.res / "values-id/strings.xml").read_bytes(),
                         (self.res / "values-in/strings.xml").read_bytes())
        self.assertTrue((self.res / "values-id/arrays.xml").exists())
        self.assertFalse((self.res / "values-id/gone.xml").exists())

    def test_creates_missing_alias_dir(self):
        self.write("b+sr+RS", "strings.xml", b"x")
        la.mirror_all(str(self.res), {"b+sr": "b+sr+RS"})
        self.assertEqual((self.res / "values-b+sr/strings.xml").read_bytes(), b"x")

    def test_missing_source_raises(self):
        with self.assertRaises(FileNotFoundError):
            la.mirror_all(str(self.res), {"id": "in"})

    def test_check_reports_each_kind_of_difference(self):
        self.write("in", "strings.xml", b"a")
        self.write("in", "new.xml", b"n")
        self.write("id", "strings.xml", b"b")
        self.write("id", "extra.xml", b"e")
        aliases = {"id": "in"}
        self.assertEqual(len(la.check_all(str(self.res), aliases)), 3)
        la.mirror_all(str(self.res), aliases)
        self.assertEqual(la.check_all(str(self.res), aliases), [])

    def test_repo_aliases_are_in_sync(self):
        repo = Path(__file__).resolve().parents[4]
        self.assertEqual(la.check_all(str(repo / la.DEFAULT_RES_DIR)), [])


if __name__ == "__main__":
    unittest.main()
