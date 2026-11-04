#!/usr/bin/env python3
"""The installer targets the active home and never touches unrelated shared files."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / "scripts/install-gradle-cache-retention.sh"
SOURCE = ROOT / ".jailbee/gradle-cache-retention.init.gradle"


class RetentionInstallerTest(unittest.TestCase):
    def test_active_home_atomic_idempotent_and_preserves_neighbors(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp) / "gradle home"
            init = home / "init.d"
            init.mkdir(parents=True)
            neighbor = init / "unrelated.init.gradle"
            neighbor.write_text("// leave this alone\n")
            env = dict(os.environ, GRADLE_USER_HOME=str(home))
            subprocess.run(["bash", str(INSTALLER)], env=env, check=True)
            target = init / "andbible-cache-retention.init.gradle"
            self.assertEqual(SOURCE.read_bytes(), target.read_bytes())
            previous = target.stat().st_mtime_ns
            subprocess.run(["bash", str(INSTALLER)], env=env, check=True)
            self.assertEqual(previous, target.stat().st_mtime_ns)
            self.assertEqual("// leave this alone\n", neighbor.read_text())
            self.assertEqual({neighbor.name, target.name}, {p.name for p in init.iterdir()})
            target.write_text("// old policy\n")
            subprocess.run(["bash", str(INSTALLER)], env=env, check=True)
            self.assertEqual(SOURCE.read_bytes(), target.read_bytes())


if __name__ == "__main__":
    unittest.main()
