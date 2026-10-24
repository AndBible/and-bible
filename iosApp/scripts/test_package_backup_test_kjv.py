import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).with_name("package-backup-test-kjv.py")
ENTRIES = [
    "mods.d/kjv.conf",
    "modules/texts/ztext/kjv/nt.bzs",
    "modules/texts/ztext/kjv/nt.bzv",
    "modules/texts/ztext/kjv/nt.bzz",
    "modules/texts/ztext/kjv/ot.bzs",
    "modules/texts/ztext/kjv/ot.bzv",
    "modules/texts/ztext/kjv/ot.bzz",
    "modules/texts/ztext/kjv/kjvpref.rtf",
]


class PackageBackupTestKjvTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "modules-root"
        self.output = self.root / "out" / "h2-kjv.zip"
        (self.source / "mods.d").mkdir(parents=True)
        (self.source / "modules/texts/ztext/kjv").mkdir(parents=True)
        self.payloads = {
            ENTRIES[0]: b"[KJV]\nDataPath=./modules/texts/ztext/kjv/\n",
            **{name: ("fixture:" + name).encode() for name in ENTRIES[1:]},
        }
        for name, data in self.payloads.items():
            (self.source / name).write_bytes(data)
        self.output.parent.mkdir()

    def run_packager(self):
        return subprocess.run(
            ["python3", "-I", str(SCRIPT), "--sword-dir", str(self.source), "--output", str(self.output)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_archives_exact_regular_module_bytes_deterministically(self):
        first = self.run_packager()
        self.assertEqual(first.returncode, 0, first.stderr)
        archive_bytes = self.output.read_bytes()
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(archive.namelist(), ENTRIES)
            for name, data in self.payloads.items():
                self.assertEqual(archive.read(name), data)
                info = archive.getinfo(name)
                self.assertEqual(info.compress_type, zipfile.ZIP_STORED)
                self.assertEqual(info.date_time, (2020, 1, 1, 0, 0, 0))
                self.assertEqual(info.external_attr >> 16, 0o100644)
        self.output.unlink()
        second = self.run_packager()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(self.output.read_bytes(), archive_bytes)
        self.assertIn(hashlib.sha256(archive_bytes).hexdigest(), second.stdout)

    def test_missing_file_fails_without_creating_output(self):
        (self.source / ENTRIES[-1]).unlink()
        result = self.run_packager()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Expected regular non-symlink module file: " + ENTRIES[-1], result.stderr)
        self.assertFalse(self.output.exists())

    def test_invalid_metadata_fails_without_creating_output(self):
        (self.source / ENTRIES[0]).write_text("[KJV]\nDataPath=./wrong/path/\n")
        result = self.run_packager()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Unexpected KJV metadata or DataPath", result.stderr)
        self.assertFalse(self.output.exists())

    def test_datapath_outside_kjv_section_is_rejected(self):
        (self.source / ENTRIES[0]).write_text("[KJV]\nName=KJV\n[Other]\nDataPath=./modules/texts/ztext/kjv/\n")
        result = self.run_packager()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Unexpected KJV metadata or DataPath", result.stderr)
        self.assertFalse(self.output.exists())

    def test_symlinked_module_file_fails_without_creating_output(self):
        target = self.source / "replacement"
        target.write_bytes(b"not a module file")
        (self.source / ENTRIES[1]).unlink()
        (self.source / ENTRIES[1]).symlink_to(target)
        result = self.run_packager()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Expected regular non-symlink module file: " + ENTRIES[1], result.stderr)
        self.assertFalse(self.output.exists())

    def test_existing_output_is_not_modified(self):
        original = b"existing destination"
        self.output.write_bytes(original)
        result = self.run_packager()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Output already exists; refusing to overwrite", result.stderr)
        self.assertEqual(self.output.read_bytes(), original)

    def test_dangling_output_symlink_is_not_replaced(self):
        target = self.root / "missing-target"
        self.output.symlink_to(target)
        result = self.run_packager()
        self.assertEqual(result.returncode, 1)
        self.assertIn("Output already exists; refusing to overwrite", result.stderr)
        self.assertTrue(self.output.is_symlink())
        self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main()
