#!/usr/bin/env bash
# L1b move-readiness metric. Spec: docs/superpowers/specs/2026-10-17-l1b-d2-shared-logic-design.md §6 phase 0.
# Per domain, counts what blocks moving its files to :sharedLogic:
#   jvm      java./javax. imports
#   jvmapi   JVM-only API uses (synchronized, System., ::class.java, String.format, Thread, @JvmStatic, @Throws)
#            minus lines carrying `// L1-common-ok: <reason>`
#   jvm3p    JVM-only third-party imports
#   android  android/androidx imports outside the KMP allowlist, R.x.y references, BuildConfig
#   app      imports of :app symbols that are outside every domain and not in l1-move-allow.txt
# --check fails when a domain listed in l1-move-done.txt has a non-zero column.
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
DONE="$ROOT/scripts/l1-move-done.txt"
PLATFORM="$ROOT/scripts/l1-readiness-platform.txt"
. "$HERE/l1-lib.sh"

JVM='^import[[:space:]]+(java|javax)\.'
JVMAPI='synchronized\(|@Synchronized|(^|[^A-Za-z_.])System\.|::class\.java|String\.format|(^|[^A-Za-z_.])Thread[.(]|@JvmStatic|@Throws\('
OKMARK='//[[:space:]]*L1-common-ok:'
JVM3P='^import[[:space:]]+(org\.apache\.commons|org\.commonmark|org\.yaml|okhttp3|org\.json|com\.google|org\.koin\.java)\.'
ANDROID='^import[[:space:]]+(android|androidx)\.|(^|[^A-Za-z_.])R\.[a-z]+\.|BuildConfig'
KMPALLOW='^import[[:space:]]+androidx\.(room3|sqlite|annotation|collection)\.'

app_count() { printf '%s\n' "$@" | python3 -I "$HERE/l1-move-app.py" "$ROOT"; }
app_lines() { printf '%s\n' "$@" | python3 -I "$HERE/l1-move-app.py" "$ROOT" --lines; }

domains=$(awk -F'\t' 'NF>=2 && substr($1,1,1)!="@" {print $1}' "$MAP" | awk '!seen[$0]++')
[ -n "$ONLY" ] && domains="$ONLY"
fail=0
printf 'domain\tjvm\tjvmapi\tjvm3p\tandroid\tapp\n'
for d in $domains; do
  mapfile -t F < <(files_of "$d")
  j=$(count "$JVM" '' "${F[@]}")
  api=$(count "$JVMAPI" "$OKMARK" "${F[@]}")
  tp=$(count "$JVM3P" '' "${F[@]}")
  a=$(count "$ANDROID" "$KMPALLOW" "${F[@]}")
  if [ ${#F[@]} -eq 0 ]; then ap=0; else ap=$(app_count "${F[@]}"); fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$d" "$j" "$api" "$tp" "$a" "$ap"
  if [ $CHECK -eq 1 ] && grep -qxF "$d" "$DONE" 2>/dev/null && [ $((j+api+tp+a+ap)) -gt 0 ]; then
    echo "REGRESSION in move-done domain '$d':" >&2
    if [ ${#F[@]} -gt 0 ]; then
      { grep -a -n -E "$JVM|$JVMAPI|$JVM3P|$ANDROID" "${F[@]}" | grep -a -v -E "$OKMARK|:${KMPALLOW#^}" >&2 || true; }
      app_lines "${F[@]}" >&2
    fi
    fail=1
  fi
done
exit $fail
