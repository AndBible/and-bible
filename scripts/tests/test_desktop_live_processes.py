import importlib.util
import json
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("desktop_live_processes", Path(__file__).resolve().parents[1] / "desktop-live-processes.py")
HELPER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HELPER)


SCRIPT = Path(__file__).resolve().parents[1] / "desktop-live-processes.py"


class DesktopLiveProcessesTest(unittest.TestCase):
    def invoke(self, *args, timeout=8):
        return subprocess.run(
            [sys.executable, "-I", str(SCRIPT), *map(str, args)],
            capture_output=True,
            text=True,
            timeout=timeout,
        )

    def wait_for(self, predicate, timeout=3):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate():
                return True
            time.sleep(0.02)
        return predicate()

    def test_start_status_stop_runs_detached_session(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            started = self.invoke("start", run_dir, "sleep-test", "--", sys.executable, "-c", "import time; time.sleep(30)")
            self.assertEqual(started.returncode, 0, started.stderr)
            record_path = run_dir / "sleep-test.process.json"
            record = json.loads(record_path.read_text())
            self.assertGreater(record["pid"], 0)
            self.assertEqual(record["pgid"], record["pid"])
            self.assertGreater(record["starttime"], 0)

            status = self.invoke("status", run_dir, "sleep-test")
            self.assertEqual(status.returncode, 0, status.stderr)
            self.assertIn("running", status.stdout.lower())
            self.assertTrue(self.invoke("members", run_dir, "sleep-test").stdout.strip())

            stopped = self.invoke("stop", run_dir, "sleep-test")
            self.assertEqual(stopped.returncode, 0, stopped.stderr)
            self.assertIn("term", stopped.stdout.lower())
            self.assertTrue(self.wait_for(lambda: self.invoke("status", run_dir, "sleep-test").returncode == 3))

    def test_missing_or_partial_start_record_is_safe(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            for name in ("missing", "partial"):
                if name == "partial":
                    (run_dir / f"{name}.process.json").write_text("{")
                result = self.invoke("stop", run_dir, name)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("no-op", result.stdout.lower())

    def test_forged_starttime_does_not_signal_live_process(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            started = self.invoke("start", run_dir, "forged", "--", sys.executable, "-c", "import time; time.sleep(30)")
            self.assertEqual(started.returncode, 0, started.stderr)
            record_path = run_dir / "forged.process.json"
            record = json.loads(record_path.read_text())
            original_starttime = record["starttime"]
            record["starttime"] += 1
            record_path.write_text(json.dumps(record))

            stopped = self.invoke("stop", run_dir, "forged")
            self.assertEqual(stopped.returncode, 0, stopped.stderr)
            self.assertIn("no-op", stopped.stdout.lower())
            os.kill(record["pid"], 0)
            status = self.invoke("status", run_dir, "forged")
            self.assertEqual(status.returncode, 3)

            record["starttime"] = original_starttime
            record_path.write_text(json.dumps(record))
            self.assertEqual(self.invoke("stop", run_dir, "forged").returncode, 0)

    def test_proc_stat_parser_handles_comm_with_spaces_and_parentheses(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            started = self.invoke("start", run_dir, "comm-test", "--", sys.executable, "-c", "import time; time.sleep(30)")
            self.assertEqual(started.returncode, 0, started.stderr)
            record_path = run_dir / "comm-test.process.json"
            record = json.loads(record_path.read_text())
            # A command name may contain spaces and ')' in Linux proc stat.
            proc_stat = Path(f"/proc/{record['pid']}/stat")
            original = proc_stat.read_text()
            try:
                stat_path = run_dir / "synthetic.stat"
                fields = original[original.rfind(")") + 2 :].split()
                fields[19] = str(record["starttime"])
                stat_path.write_text(f"{record['pid']} (a name ) with spaces) { ' '.join(fields) }\n")
                self.assertEqual(HELPER.parse_proc_stat(stat_path.read_text())["starttime"], record["starttime"])
            finally:
                self.invoke("stop", run_dir, "comm-test")

    def test_record_persistence_failure_kills_started_child(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            real_popen = subprocess.Popen
            launched = []

            def capture_child(*args, **kwargs):
                child = real_popen(*args, **kwargs)
                launched.append(child)
                return child

            with mock.patch.object(HELPER.subprocess, "Popen", side_effect=capture_child):
                with mock.patch.object(HELPER.tempfile, "mkstemp", side_effect=OSError("disk full")):
                    with self.assertRaises(OSError):
                        HELPER.start(run_dir, "failed-write", [sys.executable, "-c", "import time; time.sleep(30)"])
            self.assertEqual(len(launched), 1)
            launched[0].wait(timeout=2)
            self.assertIsNone(HELPER.proc_identity(launched[0].pid))
            self.assertFalse((run_dir / "failed-write.process.json").exists())

    def test_duplicate_live_name_is_refused_and_unsafe_name_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            args = ("start", run_dir, "live-name", "--", sys.executable, "-c", "import time; time.sleep(30)")
            first = self.invoke(*args)
            self.assertEqual(first.returncode, 0, first.stderr)
            duplicate = self.invoke(*args)
            self.assertNotEqual(duplicate.returncode, 0)
            unsafe = self.invoke("status", run_dir, "../live-name")
            self.assertNotEqual(unsafe.returncode, 0)
            self.invoke("stop", run_dir, "live-name")


if __name__ == "__main__":
    unittest.main()
