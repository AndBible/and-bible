#!/usr/bin/env bash
# Smoke test for an installed (or unpacked) AndBible desktop package.
# Usage: smoke-installed.sh <launcher-path>   (needs DISPLAY; SMOKE_TIMEOUT seconds, default 60)
# Starts the launcher with the D1 spike switch, waits for the "[spike] ... first verses" line,
# kills the app and checks that no jcef_helper is left. Exit 0 only if all of that held.
set -u
launcher="${1:?usage: smoke-installed.sh <launcher-path>}"
timeout_s="${SMOKE_TIMEOUT:-60}"
[ -x "$launcher" ] || { echo "FAIL: launcher not executable: $launcher"; exit 1; }
[ -n "${DISPLAY:-}" ] || { echo "FAIL: DISPLAY is not set"; exit 1; }

log="$(mktemp)"
trap 'rm -f "$log"' EXIT
JAVA_TOOL_OPTIONS="-Dandbible.poc.spike=true ${JAVA_TOOL_OPTIONS:-}" "$launcher" >"$log" 2>&1 &
pid=$!

rc=1
for _ in $(seq "$timeout_s"); do
  if grep -q '\[spike\].*first verses' "$log"; then rc=0; break; fi
  kill -0 "$pid" 2>/dev/null || break
  sleep 1
done
if [ "$rc" -eq 0 ]; then echo "OK: $(grep -m1 '\[spike\].*first verses' "$log")"
else echo "FAIL: no first-verses line within ${timeout_s}s"; tail -30 "$log"; fi

# Stop the app and its helper processes (children first).
pkill -TERM -P "$pid" 2>/dev/null
kill -TERM "$pid" 2>/dev/null
for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
kill -KILL "$pid" 2>/dev/null
wait "$pid" 2>/dev/null
sleep 1
if pgrep -u "$(id -u)" -x jcef_helper >/dev/null; then
  echo "FAIL: jcef_helper left running"; pkill -KILL -u "$(id -u)" -x jcef_helper; rc=1
fi
exit "$rc"
