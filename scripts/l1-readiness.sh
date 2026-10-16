#!/usr/bin/env bash
# L1a migration-readiness metric. Spec: docs/superpowers/specs/2026-10-09-l1a-migration-readiness-design.md §2.1
# Counts per domain: bridges (unmarked), L1-edge / L1-pending markers, Android imports outside the
# KMP allowlist, app globals, JVM-only libraries, and (counted only) java.io/java.util imports.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
ONLY=""; CHECK=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; shift 2;;
    --domain) ONLY="$2"; shift 2;;
    --check) CHECK=1; shift;;
    *) echo "unknown arg $1" >&2; exit 2;;
  esac
done
SRC="$ROOT/app/src/main/java/net/bible"
MAP="$ROOT/scripts/l1-readiness-domains.txt"
DONE="$ROOT/scripts/l1-readiness-done.txt"
PLATFORM="$ROOT/scripts/l1-readiness-platform.txt"

BRIDGE='runBlocking|blockingDb[[:space:]]*[({]|blockingAtEdge[[:space:]]*[({]|(^|[^A-Za-z_.])blocking[[:space:]]*\{'
MARKER='L1-edge|L1-pending'
ANDROID='^import[[:space:]]+(android\.|androidx\.)'
ALLOW='^import[[:space:]]+androidx\.(room3|sqlite|annotation|collection)\.'
GLOBALS='CommonUtils|BibleApplication|(^|[^A-Za-z_.])R\.[a-z]+\.|^import[[:space:]]+net\.bible\.android\.(view|activity|common\.resource)\.'
JVMLIBS='^import[[:space:]]+(org\.json|okhttp3)\.'
JAVAIO='^import[[:space:]]+java\.(io|util)\.'

. "$HERE/l1-lib.sh"
# markers are counted repo-wide in :app main sources, attributed to the domain named in L1-pending(<d>)
pending_for() { { grep -a -r -h -E "L1-pending\($1\)" "$SRC" 2>/dev/null || true; } | wc -l; }

domains=$(awk -F'\t' 'NF>=2 && substr($1,1,1)!="@" {print $1}' "$MAP" | awk '!seen[$0]++')
[ -n "$ONLY" ] && domains="$ONLY"
fail=0
printf 'domain\tbridges\tedge\tpending\tandroid\tglobals\tjvmlibs\tjavaio\n'
for d in $domains; do
  mapfile -t F < <(files_of "$d")
  b=$(count "$BRIDGE" "$MARKER" "${F[@]}")
  e=$(count 'L1-edge' '' "${F[@]}")
  p=$(pending_for "$d")
  a=$(count "$ANDROID" "$ALLOW" "${F[@]}")
  g=$(count "$GLOBALS" '' "${F[@]}")
  j=$(count "$JVMLIBS" '' "${F[@]}")
  io=$(count "$JAVAIO" '' "${F[@]}")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$d" "$b" "$e" "$p" "$a" "$g" "$j" "$io"
  # A done domain has no bridges of its own and no callers still waiting on it (spec §2: callers in
  # done domains are converted, not bridged), so its L1-pending count must be zero too.
  if [ $CHECK -eq 1 ] && grep -qxF "$d" "$DONE" 2>/dev/null && [ $((b+p+a+g+j)) -gt 0 ]; then
    echo "REGRESSION in done domain '$d':" >&2
    [ ${#F[@]} -gt 0 ] && { grep -a -n -E "$BRIDGE|$ANDROID|$GLOBALS|$JVMLIBS" "${F[@]}" | grep -a -v -E "$MARKER|:${ALLOW#^}" >&2 || true; }
    grep -a -r -n -E "L1-pending\($d\)" "$SRC" >&2 || true
    fail=1
  fi
done
if [ $CHECK -eq 1 ] && grep -qxF '@no-pending' "$DONE" 2>/dev/null; then
  # The sentinel covers every marker, including files in unmapped domains or with unknown labels.
  if grep -a -r -n -F 'L1-pending' "$SRC" >&2; then echo "REGRESSION: L1-pending markers remain (@no-pending)" >&2; fail=1; fi
fi
if [ $CHECK -eq 1 ] && grep -qxF '@no-unmarked-bridges' "$DONE" 2>/dev/null; then
  # Every bridge under SRC needs a same-line L1-edge. BlockingDb.kt holds the definitions, so it is excluded by path.
  unmarked=$(grep -a -r -n -E "$BRIDGE" "$SRC" --include='*.kt' --include='*.java' \
    | grep -a -v -F '/service/db/BlockingDb.kt:' | { grep -a -v -E 'L1-edge' || true; })
  if [ -n "$unmarked" ]; then
    echo "$unmarked" >&2
    echo "REGRESSION: unmarked bridges remain (@no-unmarked-bridges)" >&2
    fail=1
  fi
fi
exit $fail
