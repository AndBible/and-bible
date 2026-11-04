import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "shared-test-summary.py"


class SummaryTest(unittest.TestCase):
    def test_counts_suites_and_reports_missing_tasks(self):
        spec = importlib.util.spec_from_file_location("summary", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results = root / "jsword/build/test-results/jvmTest"
            results.mkdir(parents=True)
            (results / "TEST-a.xml").write_text('<testsuite tests="5" failures="1" errors="1" skipped="2"/>')
            (results / "TEST-b.xml").write_text('<testsuite tests="3" failures="0" errors="0" skipped="1"/>')
            summary = module.render(root)
            self.assertIn('| jsword | jvmTest | 8 | 1 | 1 | 3 |', summary)
            self.assertIn('| jsword | parityTest | unavailable |', summary)
            self.assertIn('| sharedUi | jvmTest | unavailable |', summary)


if __name__ == "__main__":
    unittest.main()
