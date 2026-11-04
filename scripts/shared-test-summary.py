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
    lines += ["", "## JVM line coverage", "", "| Module | Covered | Missed | Coverage |",
              "| --- | ---: | ---: | ---: |"]
    for module in ("merged", "sharedCore", "sharedData", "sharedLogic"):
        report = (root / "build/reports/kover/reportShared.xml" if module == "merged" else
                  root / module / "build/reports/kover/reportJvm.xml")
        if not report.is_file():
            lines.append(f"| {module} | unavailable | - | - |")
            continue
        counter = ET.parse(report).getroot().find("counter[@type='LINE']")
        if counter is None:
            lines.append(f"| {module} | unavailable | - | - |")
            continue
        covered, missed = int(counter.attrib["covered"]), int(counter.attrib["missed"])
        total = covered + missed
        percentage = f"{covered / total:.1%}" if total else "n/a"
        lines.append(f"| {module} | {covered} | {missed} | {percentage} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        summary.write(render(Path.cwd()))
