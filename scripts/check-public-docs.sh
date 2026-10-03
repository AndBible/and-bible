#!/usr/bin/env bash
# Gate: public docs are self-contained and free of private/superrepo/session material.
set -euo pipefail
ab=$(cd "$(dirname "$0")/.." && pwd)
fail() { echo "FAIL: $*"; exit 1; }
d=$ab/docs/emulator-and-webview-debugging.md
[ -s "$d" ] || fail "docs/emulator-and-webview-debugging.md missing"
for f in "$ab/CLAUDE.md" "$d"; do
  grep -nE 'pebble-timetracking|AndroidMidiRecorder|ai-local|readthedocs|/home/dev/|tairaksinen|Claude-Session|sdk-api28' "$f" \
    && fail "private/superrepo reference in $(basename "$f")"
done
grep -q 'Java 17 (OpenJDK' "$ab/CLAUDE.md" && fail "stale Java prerequisite"
grep -q 'sharedUi' "$ab/CLAUDE.md" || fail "KMP/Compose structure section missing"
grep -q 'TEST_SDK' "$ab/CLAUDE.md" || fail "test-infra traps missing"
for s in webview-cdp.sh andbible-emu.sh seed-emulator-webview-debug.sh emulator-avds.sh with-container-adb.sh; do
  grep -q "scripts/$s" "$d" || fail "doc does not cover scripts/$s"
done
echo "PUBLIC DOCS OK"
