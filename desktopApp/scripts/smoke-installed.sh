#!/usr/bin/env bash
# Smoke test for an installed (or unpacked) AndBible desktop package.
# Usage: smoke-installed.sh <launcher-path>   (needs DISPLAY; SMOKE_TIMEOUT seconds, default 60)
#
# The launcher starts the real app by default. Each run uses a fresh temporary profile
# (XDG_DATA_HOME/CONFIG/CACHE under mktemp -d), so the user's real data is never touched.
#
# Environment:
#   SMOKE_SWORD_DIR=<dir>  optional; a dir with mods.d/ and modules/, copied into the profile's
#                          sword dir before launch. Readiness = "[desktop] reading ready " (a reading
#                          pane's page is client-ready).
#   (unset)                empty library. Readiness = "I/DesktopAppStartup: Databases open" and the
#                          process still alive 5 s later (Welcome screen, no reading pane yet).
#   SMOKE_POC=1            PoC check instead: -Dandbible.poc.scenario=single -Dandbible.poc.spike=true
#                          and the "[spike] ... first verses" line.
#
# Fails on a log line "already running" (another instance holds the lock) and on a jcef_helper left
# behind. Stops the app with SIGTERM, then SIGKILL after 10 s. Exit 0 only if all of that held.
set -u
launcher="${1:?usage: smoke-installed.sh <launcher-path>}"
timeout_s="${SMOKE_TIMEOUT:-60}"
sword_dir="${SMOKE_SWORD_DIR:-}"
poc="${SMOKE_POC:-0}"
[ -x "$launcher" ] || { echo "FAIL: launcher not executable: $launcher"; exit 1; }
[ -n "${DISPLAY:-}" ] || { echo "FAIL: DISPLAY is not set"; exit 1; }
if [ -n "$sword_dir" ] && { [ ! -d "$sword_dir/mods.d" ] || [ ! -d "$sword_dir/modules" ]; }; then
  echo "FAIL: SMOKE_SWORD_DIR needs mods.d/ and modules/: $sword_dir"; exit 1
fi

tmp="$(mktemp -d)" || { echo "FAIL: mktemp -d failed"; exit 1; }
log="$tmp/app.log"
pid=""
cleanup() {
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    pkill -KILL -P "$pid" 2>/dev/null
    kill -KILL "$pid" 2>/dev/null
  fi
  rm -rf "$tmp"
}
trap cleanup EXIT

data="$tmp/data"; config="$tmp/config"; cache="$tmp/cache"
if [ -n "$sword_dir" ]; then
  mkdir -p "$data/andbible/sword" && cp -R "$sword_dir/." "$data/andbible/sword/" \
    || { echo "FAIL: could not seed the sword dir from $sword_dir"; exit 1; }
fi

if [ "$poc" = 1 ]; then
  pat='\[spike\].*first verses'
  java_opts="-Dandbible.poc.scenario=single -Dandbible.poc.spike=true ${JAVA_TOOL_OPTIONS:-}"
  export JAVA_TOOL_OPTIONS="$java_opts"
elif [ -n "$sword_dir" ]; then
  pat='\[desktop\] reading ready '
else
  pat='I/DesktopAppStartup: Databases open'
fi

# The subshell execs the launcher, so $! is the launcher's own pid.
( export XDG_DATA_HOME="$data" XDG_CONFIG_HOME="$config" XDG_CACHE_HOME="$cache"
  exec "$launcher" ) >"$log" 2>&1 &
pid=$!

rc=1
seen=-1
for ((i = 0; i < timeout_s; i++)); do
  kill -0 "$pid" 2>/dev/null || break
  if grep -q 'already running' "$log"; then break; fi
  if grep -q -- "$pat" "$log"; then
    if [ "$seen" -lt 0 ]; then seen=$i; fi
    # Module or PoC readiness is immediate; an empty library must still be alive 5 s later.
    if [ -n "$sword_dir" ] || [ "$poc" = 1 ] || [ $((i - seen)) -ge 5 ]; then rc=0; break; fi
  fi
  sleep 1
done
if grep -q 'already running' "$log"; then
  rc=1; echo "FAIL: app reports 'already running' (another instance holds the lock)"
elif [ "$rc" -eq 0 ]; then
  echo "OK: $(grep -m1 -- "$pat" "$log")"
else
  echo "FAIL: no readiness line ('$pat') within ${timeout_s}s, or the app exited first"
  tail -30 "$log"
fi

# Stop the app and its helper processes (children first): SIGTERM, wait up to 10 s, then SIGKILL.
pkill -TERM -P "$pid" 2>/dev/null
kill -TERM "$pid" 2>/dev/null
for _ in $(seq 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
kill -KILL "$pid" 2>/dev/null
wait "$pid" 2>/dev/null
pid=""
sleep 1
if pgrep -u "$(id -u)" -x jcef_helper >/dev/null; then
  echo "FAIL: jcef_helper left running"; pkill -KILL -u "$(id -u)" -x jcef_helper; rc=1
fi
exit "$rc"
