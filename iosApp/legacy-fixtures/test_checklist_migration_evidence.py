import contextlib
import importlib.util
import io
import pathlib
import sqlite3
import tempfile
import unittest
import zipfile

MODULE = pathlib.Path(__file__).with_name("checklist-migration-evidence.py")
spec = importlib.util.spec_from_file_location("checklist_migration_evidence", MODULE)
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)
FIXTURE = pathlib.Path(__file__).parents[2] / "sharedLogic/src/jvmTest/resources/legacymigration/fixtures/1.0.2/26/extended.zip"


class MigrationEvidenceTest(unittest.TestCase):
    def test_matches_oracle_and_detects_missing_studypad_text(self):
        with tempfile.TemporaryDirectory() as directory:
            dbs = pathlib.Path(directory) / "databases"
            dbs.mkdir()
            with zipfile.ZipFile(FIXTURE) as fixture:
                with zipfile.ZipFile(io.BytesIO(fixture.read("oracle/AndBibleDatabaseBackup.abdb.zip"))) as oracle:
                    for name in ("bookmarks", "workspaces"):
                        (dbs / f"{name}.sqlite3").write_bytes(oracle.read(f"db/{name}.sqlite3"))
            with contextlib.redirect_stdout(io.StringIO()) as log:
                evidence.compare(directory, FIXTURE)
            self.assertIn("StudyPadTextEntryText: 3 records", log.getvalue())
            with sqlite3.connect(dbs / "bookmarks.sqlite3") as db:
                db.execute("DELETE FROM StudyPadTextEntryText")
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(AssertionError, "StudyPadTextEntryText"):
                evidence.compare(directory, FIXTURE)

    def test_missing_current_database_cannot_pass(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(AssertionError):
            evidence.compare(directory, FIXTURE)


if __name__ == "__main__":
    unittest.main()
