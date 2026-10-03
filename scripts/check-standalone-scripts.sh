#!/usr/bin/env bash
# Gate: the emulator/CDP scripts work from any clone of and-bible, with no superrepo around.
# Runs them from a scratch clone of the CURRENT commit (uncommitted changes are not seen).
set -euo pipefail
src=$(cd "$(dirname "$0")/.." && pwd)
t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
git clone -q --no-checkout "$src" "$t/ab" && git -C "$t/ab" checkout -q "$(git -C "$src" rev-parse HEAD)"
s=$t/ab/scripts
fail() { echo "FAIL: $*"; exit 1; }
names="webview-cdp.sh cdp.mjs adb-localabstract-proxy.py with-container-adb.sh andbible-emu.sh seed-emulator-webview-debug.sh emulator-avds.sh"
for n in $names; do [ -f "$s/$n" ] || fail "scripts/$n missing"; done
grep -nE 'pebble-timetracking|AndroidMidiRecorder|sdk-api28|AndBible/and-bible/|ai-local' $(for n in $names; do echo "$s/$n"; done) \
  && fail "superrepo/AMR/side-SDK reference left"
for n in webview-cdp.sh with-container-adb.sh andbible-emu.sh seed-emulator-webview-debug.sh emulator-avds.sh; do
  bash -n "$s/$n" || fail "syntax: $n"
done
python3 -m py_compile "$s/adb-localabstract-proxy.py" || fail "syntax: proxy"
node --check "$s/cdp.mjs" || fail "syntax: cdp.mjs"
# usage paths must work without a device and without the superrepo
# (usage exits nonzero, so capture the output first: pipefail would fail a direct pipe into grep)
out=$(cd /tmp && env -u ANDROID_ADB_SERVER_PORT "$s/andbible-emu.sh" 2>&1 || true)
grep -qi usage <<<"$out" || fail "andbible-emu.sh usage"
out=$(cd /tmp && "$s/webview-cdp.sh" 2>&1 || true)
grep -qiE 'usage|package' <<<"$out" || fail "webview-cdp.sh usage"
out=$(cd /tmp && "$s/emulator-avds.sh" --help 2>&1 || true)
grep -qi 'screenshot_phone' <<<"$out" || fail "emulator-avds.sh --help"
echo "SCRIPTS OK"
