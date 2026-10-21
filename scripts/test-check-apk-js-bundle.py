#!/usr/bin/env python3
"""Black-box APK checker tests; no Android tools or build required."""
import pathlib
import subprocess
import tempfile
import unittest
import warnings
import zipfile

CHECKER = pathlib.Path(__file__).with_name("check-apk-js-bundle.sh")
PREFIX = "assets/bibleview-js/"
HTML = '<script type="module" src="./assets/main-test.js"></script>'
PRODUCTION = 'document.title=`${book}/${type}/${id} (production)`;'
DEBUG = 'document.title=`${book}/${type}/${id} (debug)`;\n//# sourceMappingURL=data:application/json;base64,e30='


class BundleCheckerTest(unittest.TestCase):
    def test_bundle_contract(self):
        cases = [
            ("production", {"index.html": HTML, "assets/main-test.js": PRODUCTION}, "production", 0),
            ("debug", {"index.html": HTML, "assets/main-test.js": DEBUG}, "debug", 0),
            ("debug-as-production", {"index.html": HTML, "assets/main-test.js": DEBUG}, "production", 1),
            ("production-as-debug", {"index.html": HTML, "assets/main-test.js": PRODUCTION}, "debug", 1),
            ("empty", {}, "production", 1),
            ("missing-entry", {"index.html": HTML}, "production", 1),
            ("missing-index", {"assets/main-test.js": PRODUCTION}, "production", 1),
            ("missing-label", {"index.html": HTML, "assets/main-test.js": "console.log('hello')"}, "production", 1),
            ("ambiguous-label", {"index.html": HTML, "assets/main-test.js": PRODUCTION + DEBUG}, "debug", 1),
            ("debug-no-map", {"index.html": HTML, "assets/main-test.js": DEBUG.split('\n')[0]}, "debug", 1),
            ("production-with-map", {"index.html": HTML, "assets/main-test.js": PRODUCTION, "assets/stale.js": DEBUG}, "production", 1),
            ("ambiguous-entry", {"index.html": HTML + HTML, "assets/main-test.js": PRODUCTION}, "production", 1),
            ("external-map", {"index.html": HTML, "assets/main-test.js": PRODUCTION + '\n//# sourceMappingURL=main.js.map'}, "production", 1),
            ("orphan-map", {"index.html": HTML, "assets/main-test.js": PRODUCTION, "assets/old.js.map": "{}"}, "production", 1),
            ("label-only-in-chunk", {"index.html": HTML, "assets/main-test.js": "console.log('hello')", "assets/chunk.js": PRODUCTION}, "production", 1),
            ("unsafe-entry", {"index.html": HTML.replace('./assets/main-test.js', '../outside.js')}, "production", 1),
            ("same-line-inline-map", {"index.html": HTML, "assets/main-test.js": PRODUCTION + '//# sourceMappingURL=data:application/json;base64,e30='}, "production", 1),
            ("block-inline-map", {"index.html": HTML, "assets/main-test.js": PRODUCTION + '/*# sourceMappingURL=data:application/json;base64,e30= */'}, "production", 1),
            ("block-external-map", {"index.html": HTML, "assets/main-test.js": PRODUCTION + '/*# sourceMappingURL=main.js.map */'}, "production", 1),
            ("debug-map-only-in-orphan", {"index.html": HTML, "assets/main-test.js": DEBUG.split('\n')[0], "assets/orphan.js": DEBUG}, "debug", 1),
            ("debug-same-line-map", {"index.html": HTML, "assets/main-test.js": DEBUG.replace('\n', '')}, "debug", 0),
            ("debug-block-map", {"index.html": HTML, "assets/main-test.js": DEBUG.split('\n')[0] + '/*# sourceMappingURL=data:application/json;base64,e30= */'}, "debug", 0),
            ("invalid-expectation", {"index.html": HTML, "assets/main-test.js": PRODUCTION}, "release", 1),
        ]
        with tempfile.TemporaryDirectory() as temp:
            for name, files, expectation, code in cases:
                with self.subTest(name=name):
                    apk = pathlib.Path(temp) / (name + ".apk")
                    with zipfile.ZipFile(apk, "w") as archive:
                        for path, content in files.items():
                            archive.writestr(PREFIX + path, content)
                    result = subprocess.run(["bash", str(CHECKER), str(apk), expectation], capture_output=True, text=True)
                    self.assertEqual(result.returncode, code, result.stdout + result.stderr)
                    self.assertIn("expect=", result.stdout + result.stderr)
            for name in ("missing.apk", "invalid.apk", "duplicate.apk"):
                apk = pathlib.Path(temp) / name
                if name == "invalid.apk":
                    apk.write_text("not a zip")
                elif name == "duplicate.apk":
                    with warnings.catch_warnings():
                        warnings.simplefilter("ignore", UserWarning)
                        with zipfile.ZipFile(apk, "w") as archive:
                            archive.writestr(PREFIX + "index.html", HTML)
                            archive.writestr(PREFIX + "index.html", HTML)
                            archive.writestr(PREFIX + "assets/main-test.js", PRODUCTION)
                result = subprocess.run(["bash", str(CHECKER), str(apk), "production"], capture_output=True, text=True)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
