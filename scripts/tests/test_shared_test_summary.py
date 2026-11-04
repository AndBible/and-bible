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

    def test_coverage_reads_root_line_counter_and_handles_missing_data(self):
        spec = importlib.util.spec_from_file_location("summary", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, xml in {
                "": '<report><package><counter type="LINE" missed="99" covered="1"/></package><counter type="LINE" missed="3" covered="7"/></report>',
                "sharedCore": '<report><counter type="LINE" missed="0" covered="0"/></report>',
                "sharedData": '<report><counter type="BRANCH" missed="1" covered="1"/></report>',
            }.items():
                report = root / name / "build/reports/kover" / ("reportJvm.xml" if name else "reportShared.xml")
                report.parent.mkdir(parents=True, exist_ok=True)
                report.write_text(xml)
            summary = module.render(root)
            self.assertIn('| merged | 7 | 3 | 70.0% |', summary)
            self.assertIn('| sharedCore | 0 | 0 | n/a |', summary)
            self.assertIn('| sharedData | unavailable | - | - |', summary)
            self.assertIn('| sharedLogic | unavailable | - | - |', summary)

    def test_malformed_coverage_is_not_silently_ignored(self):
        spec = importlib.util.spec_from_file_location("summary", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "build/reports/kover/reportShared.xml"
            report.parent.mkdir(parents=True)
            report.write_text('<report>')
            with self.assertRaises(module.ET.ParseError):
                module.render(root)


if __name__ == "__main__":
    unittest.main()
