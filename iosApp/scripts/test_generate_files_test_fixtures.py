"""Self-check for the H2 fixtures. Run: python3 -I iosApp/scripts/test_generate_files_test_fixtures.py"""
import hashlib
import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gen", HERE / "generate-files-test-fixtures.py")
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)
MODULES = HERE.parents[1] / "jsword/src/commonTest/resources/k4-modules/space dir/ä"


class H2FixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = pathlib.Path(cls.tmp.name)
        gen.incoming_fixtures(cls.out)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def names(self, name):
        with zipfile.ZipFile(self.out / name) as z:
            return z.namelist()

    def read(self, name, entry):
        with zipfile.ZipFile(self.out / name) as z:
            return z.read(entry)

    def test_cipher_is_locked_relocated_and_byte_identical(self):
        conf = self.read("h2-cipher.zip", "mods.d/H2Cipher.conf").decode()
        self.assertIn("[H2Cipher]", conf)
        self.assertIn("DataPath=./modules/texts/ztext/h2cipher\n", conf)
        self.assertIn("CipherKey=\n", conf)
        self.assertNotIn("k4-Test-key", conf)
        self.assertIn("ModDrv=zText", conf)
        for n in ("ot", "nt"):
            for ext in ("bzs", "bzv", "bzz"):
                self.assertEqual(self.read("h2-cipher.zip", f"modules/texts/ztext/h2cipher/{n}.{ext}"),
                                 (MODULES / f"modules/K4Cipher/{n}.{ext}").read_bytes())
        self.assertEqual(len(self.names("h2-cipher.zip")), 7)

    def test_genbook_is_unchanged_k4(self):
        self.assertEqual(self.read("h2-genbook.zip", "mods.d/K4GenBook.conf"), (MODULES / "mods.d/K4GenBook.conf").read_bytes())
        for ext in ("idx", "dat", "bdt"):
            self.assertEqual(self.read("h2-genbook.zip", f"modules/K4GenBook/K4GenBook.{ext}"),
                             (MODULES / f"modules/K4GenBook/K4GenBook.{ext}").read_bytes())

    def test_maps_is_isolated_and_categorised(self):
        conf = self.read("h2-maps.zip", "mods.d/H2Maps.conf").decode()
        self.assertIn("[H2Maps]", conf)
        self.assertIn("DataPath=./modules/H2Maps/H2Maps\n", conf)
        self.assertIn("Category=Maps\n", conf)
        self.assertIn("ModDrv=RawGenBook", conf)
        for ext in ("idx", "dat", "bdt"):
            self.assertEqual(self.read("h2-maps.zip", f"modules/H2Maps/H2Maps.{ext}"),
                             (MODULES / f"modules/K4GenBook/K4GenBook.{ext}").read_bytes())

    def test_dictionary_unchanged(self):
        for n in ("zdx", "zdt", "idx", "dat"):
            self.assertEqual(self.read("h2-dict.zip", f"modules/K4ZLD/K4ZLD.{n}"), (MODULES / f"modules/K4ZLD/K4ZLD.{n}").read_bytes())
        self.assertEqual(self.names("h2-dict.zip")[0], "mods.d/K4ZLD.conf")

    def test_bookmarks_csv_creation_order_differs_from_bible_order(self):
        lines = (self.out / "h2-bookmarks.csv").read_text(encoding="utf-8").splitlines()
        self.assertEqual(lines[0], "osisRef;createdAt;notes")
        rows = [line.split(";") for line in lines[1:]]
        self.assertEqual([r[0] for r in rows], ["Rev.1.1", "Gen.1.1", "John.3.16"])
        self.assertEqual(sorted(r[1] for r in rows), [r[1] for r in rows])
        self.assertEqual(len({r[2] for r in rows}), 3)

    def test_deterministic(self):
        with tempfile.TemporaryDirectory() as other:
            gen.incoming_fixtures(pathlib.Path(other))
            for name in ("h2-cipher.zip", "h2-genbook.zip", "h2-maps.zip", "h2-dict.zip", "f-rawtext.zip"):
                self.assertEqual(hashlib.sha256((self.out / name).read_bytes()).digest(),
                                 hashlib.sha256((pathlib.Path(other) / name).read_bytes()).digest())


if __name__ == "__main__":
    unittest.main()
