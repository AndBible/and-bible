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

BRIDGE='runBlocking|blockingDb[[:space:]]*[({]|(^|[^A-Za-z_.])blocking[[:space:]]*\{'
MARKER='L1-edge|L1-pending'
ANDROID='^import[[:space:]]+(android\.|androidx\.)'
ALLOW='^import[[:space:]]+androidx\.(room3|sqlite|annotation|collection)\.'
GLOBALS='CommonUtils|BibleApplication|(^|[^A-Za-z_.])R\.(string|plurals|id|drawable|color|raw|array|integer|layout|xml)\.|^import[[:space:]]+net\.bible\.android\.(view|activity|common\.resource)\.'
JVMLIBS='^import[[:space:]]+(org\.json|okhttp3)\.'
JAVAIO='^import[[:space:]]+java\.(io|util)\.'

files_of() { # domain -> file list (platform files excluded)
  local d="$1"
  awk -F'\t' -v d="$d" '$1==d {print $2}' "$MAP" | while read -r p; do
    if [ -d "$SRC/$p" ]; then find "$SRC/$p" -type f \( -name '*.kt' -o -name '*.java' \); else echo "$SRC/$p"; fi
  done | while read -r f; do
    rel="${f#$SRC/}"
    if ! awk -F'\t' '{print $1}' "$PLATFORM" | grep -qxF "$rel"; then echo "$f"; fi
  done
}
count() { # pattern [exclude-pattern] files...
  local pat="$1" excl="$2"; shift 2
  [ $# -eq 0 ] && { echo 0; return; }
  # `|| true`: grep exits 1 on no match, which pipefail would turn into a script abort
  if [ -n "$excl" ]; then { grep -a -h -E "$pat" "$@" 2>/dev/null || true; } | { grep -a -v -E "$excl" || true; } | wc -l
  else { grep -a -h -E "$pat" "$@" 2>/dev/null || true; } | wc -l; fi
}
# markers are counted repo-wide in :app main sources, attributed to the domain named in L1-pending(<d>)
pending_for() { { grep -a -r -h -E "L1-pending\($1\)" "$SRC" 2>/dev/null || true; } | wc -l; }

domains=$(awk -F'\t' 'NF>=2 {print $1}' "$MAP" | awk '!seen[$0]++')
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
  if [ $CHECK -eq 1 ] && grep -qxF "$d" "$DONE" 2>/dev/null && [ $((b+a+g+j)) -gt 0 ]; then
    echo "REGRESSION in done domain '$d':" >&2
    grep -a -n -E "$BRIDGE|$ANDROID|$GLOBALS|$JVMLIBS" "${F[@]}" | grep -a -v -E "$MARKER|$ALLOW" >&2 || true
    fail=1
  fi
done
exit $fail
