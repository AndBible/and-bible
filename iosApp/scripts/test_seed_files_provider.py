"""Behavioral safety tests of the actual host script, without a Mac or simulator."""
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().with_name("seed-files-provider.sh")
UDID = "D14D29F9-3993-4502-B21C-43B86249C24E"
LOCAL = "group.com.apple.FileProvider.LocalStorage"


class SeedFilesProviderTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        self.groups = self.home / "Library/Developer/CoreSimulator/Devices" / UDID
        self.groups /= "data/Containers/Shared/AppGroup"
        self.root = self.provider("local", LOCAL)
        self.cloud = self.provider("cloud", "group.com.apple.CloudDocs")
        self.photos = self.provider("photos", "group.com.apple.Photos")
        for root in (self.root, self.cloud, self.photos):
            (root / "unrelated").mkdir()
            (root / "unrelated/sentinel").write_bytes(b"preserve me")
        self.source = self.home / "db-backup.abdb.zip"
        self.source.write_bytes(b"original archive payload\x00\xff")
        bin_dir = self.home / "bin"
        bin_dir.mkdir()
        # Only the simulator listing is faked. The real script handles all filesystem decisions.
        xcrun = bin_dir / "xcrun"
        self.simulator_listing = self.home / "simulator-listing.json"
        self.set_simulators([{"udid": UDID, "state": "Booted"}])
        xcrun.write_text(
            "#!/bin/sh\n"
            "if [ \"$*\" != 'simctl list devices available -j' ]; then exit 97; fi\n"
            "exec cat \"$SIMULATOR_LISTING\"\n"
        )
        xcrun.chmod(0o755)
        self.env = dict(os.environ, HOME=str(self.home), PATH=str(bin_dir) + os.pathsep + os.environ["PATH"],
                        SIMULATOR_LISTING=str(self.simulator_listing))

    def set_simulators(self, devices):
        self.simulator_listing.write_text(json.dumps({"devices": {"runtime": devices}}))

    def provider(self, name, identifier):
        group = self.groups / name
        root = group / "File Provider Storage"
        root.mkdir(parents=True)
        with (group / ".com.apple.mobile_container_manager.metadata.plist").open("wb") as handle:
            plistlib.dump({"MCMMetadataIdentifier": identifier}, handle)
        return root

    def invoke(self, *args):
        return subprocess.run(["bash", str(SCRIPT), *map(str, args)], env=self.env,
                              cwd=self.home, capture_output=True, text=True, timeout=10)

    def snapshot(self):
        # Include links without following them, so fail-before-mutation checks see every owned path.
        return sorted((str(path.relative_to(self.home)),
                       ("link", os.readlink(path)) if path.is_symlink() else
                       ("dir",) if path.is_dir() else ("file", path.read_bytes()))
                      for path in self.home.rglob("*") )

    def assert_preserved(self):
        for root in (self.root, self.cloud, self.photos):
            self.assertEqual((root / "unrelated/sentinel").read_bytes(), b"preserve me")
        self.assertFalse((self.cloud / "H2 Fixtures").exists())
        self.assertFalse((self.photos / "H2 Fixtures").exists())

    def test_exact_metadata_and_bytes_then_owned_only_cleanup(self):
        result = self.invoke(UDID, self.source, "H2 Fixtures")
        self.assertEqual(result.returncode, 0, result.stderr)
        destination = self.root / "H2 Fixtures" / self.source.name
        self.assertEqual(Path(result.stdout.strip()), destination)
        self.assertEqual(destination.read_bytes(), self.source.read_bytes())
        (self.root / "H2 Backup").mkdir()
        (self.root / "H2 Backup/stale").write_bytes(b"owned")
        result = self.invoke("--clean", UDID)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / "H2 Fixtures").exists())
        self.assertEqual(list((self.root / "H2 Backup").iterdir()), [])
        self.assert_preserved()

    def test_collision_refused_without_mutation(self):
        first = self.invoke(UDID, self.source, "H2 Fixtures")
        self.assertEqual(first.returncode, 0, first.stderr)
        before = self.snapshot()
        result = self.invoke(UDID, self.source, "H2 Fixtures")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.snapshot(), before)
        self.assert_preserved()

    def test_owned_symlink_refused_before_clean_or_seed_mutation(self):
        backup = self.root / "H2 Backup"
        backup.mkdir()
        (backup / "must-survive").write_bytes(b"owned but not yet authorized")
        (self.root / "H2 Fixtures").symlink_to(self.cloud / "unrelated", target_is_directory=True)
        before = self.snapshot()
        for args in (("--clean", UDID), (UDID, self.source, "H2 Backup")):
            with self.subTest(args=args):
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)
        self.assert_preserved()

    def test_ambiguous_metadata_refused_before_mutation(self):
        self.provider("second-local", LOCAL)
        before = self.snapshot()
        for args in (("--clean", UDID), (UDID, self.source, "H2 Fixtures")):
            with self.subTest(args=args):
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)

    def test_missing_local_metadata_does_not_choose_cloud_or_photos(self):
        shutil.rmtree(self.root.parent)
        before = self.snapshot()
        result = self.invoke(UDID, self.source, "H2 Fixtures")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.snapshot(), before)

    def test_known_shutdown_simulator_refused_without_mutation(self):
        self.set_simulators([{"udid": UDID, "state": "Shutdown"}])
        before = self.snapshot()
        for args in (("--clean", UDID), (UDID, self.source, "H2 Fixtures")):
            with self.subTest(args=args):
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)
        self.assert_preserved()

    def test_duplicate_matching_simulator_entries_refused_without_mutation(self):
        self.set_simulators([{"udid": UDID, "state": "Booted"},
                             {"udid": UDID.lower(), "state": "Booted"}])
        before = self.snapshot()
        for args in (("--clean", UDID), (UDID, self.source, "H2 Fixtures")):
            with self.subTest(args=args):
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)
        self.assert_preserved()

    def test_symlink_source_refused_without_mutation(self):
        source_link = self.home / "linked-backup.zip"
        source_link.symlink_to(self.source)
        before = self.snapshot()
        result = self.invoke(UDID, source_link, "H2 Fixtures")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.snapshot(), before)
        self.assert_preserved()

    def test_symlink_provider_storage_root_refused_without_mutation(self):
        actual_root = self.root.with_name("actual-provider-storage")
        self.root.rename(actual_root)
        self.root.symlink_to(actual_root, target_is_directory=True)
        before = self.snapshot()
        for args in (("--clean", UDID), (UDID, self.source, "H2 Fixtures")):
            with self.subTest(args=args):
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)
        self.assertEqual((actual_root / "unrelated/sentinel").read_bytes(), b"preserve me")
        self.assertFalse((actual_root / "H2 Fixtures").exists())
        self.assertFalse((actual_root / "H2 Backup").exists())

    def test_non_directory_owned_target_refused_without_mutation(self):
        target = self.root / "H2 Fixtures"
        target.write_bytes(b"leave this file alone")
        (self.root / "H2 Backup").mkdir()
        (self.root / "H2 Backup/sentinel").write_bytes(b"preserve owned data")
        before = self.snapshot()
        for args in (("--clean", UDID), (UDID, self.source, "H2 Backup")):
            with self.subTest(args=args):
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)
        self.assertEqual(target.read_bytes(), b"leave this file alone")
        self.assertEqual((self.root / "H2 Backup/sentinel").read_bytes(), b"preserve owned data")
        self.assert_preserved()

    def test_invalid_inputs_refused_without_mutation(self):
        for args in ((UDID, self.home / "missing.zip", "H2 Fixtures"),
                     (UDID, self.source, "unrelated"),
                     ("booted", self.source, "H2 Fixtures"),
                     ("00000000-0000-0000-0000-000000000000", self.source, "H2 Fixtures")):
            with self.subTest(args=args):
                before = self.snapshot()
                result = self.invoke(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.snapshot(), before)


if __name__ == "__main__":
    unittest.main()
