#!/usr/bin/env python3
"""Summarize Gradle JUnit XML results, including tasks that did not run."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET


TASKS = [("jsword", "jvmTest"), ("jsword", "parityTest")] + [
    (module, "jvmTest") for module in ("kdom", "sharedCore", "sharedData", "sharedLogic", "sharedUi")
]


def render(root: Path) -> str:
    lines = ["## Shared JVM tests", "", "| Module | Task | Tests | Failures | Errors | Skipped |",
             "| --- | --- | ---: | ---: | ---: | ---: |"]
    for module, task in TASKS:
        files = sorted((root / module / "build/test-results" / task).glob("TEST-*.xml"))
        if not files:
            lines.append(f"| {module} | {task} | unavailable | - | - | - |")
            continue
        counts = [0, 0, 0, 0]
        for file in files:
            suite = ET.parse(file).getroot()
            for i, key in enumerate(("tests", "failures", "errors", "skipped")):
                counts[i] += int(suite.get(key, "0"))
        lines.append(f"| {module} | {task} | {' | '.join(map(str, counts))} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        summary.write(render(Path.cwd()))
