#!/usr/bin/env bash
# L1b move-readiness metric. Spec: docs/superpowers/specs/2026-10-17-l1b-d2-shared-logic-design.md §6 phase 0.
# Per domain, counts what blocks moving its files to :sharedLogic:
#   jvm      java./javax. imports, plus inline fully-qualified uses (java.util.concurrent.X()) on
#            non-import, non-comment lines
#   jvmapi   JVM-only API uses (synchronized, System., ::class.java, String.format, Thread, @JvmStatic, @Throws,
#            Math., Charsets., OutOfMemoryError, GlobalContext, @Volatile in files without
#            `import kotlin.concurrent.Volatile`)
#            minus lines carrying `// L1-common-ok: <reason>`
#   jvm3p    JVM-only third-party imports, plus inline fully-qualified uses (same rule as jvm)
#   android  android/androidx imports outside the KMP allowlist, R.x.y references, BuildConfig
#   app      imports of :app symbols that are outside every domain and not in l1-move-allow.txt
# The app column only sees explicit, non-wildcard imports (the spec defines it as imports of non-domain
# :app classes); same-package references and wildcard imports are not seen, compile catches the rest.
# l1-move-allow.txt lists only the files D2 moved to :sharedLogic (AiDatabaseLifetime and SyncDatabaseHooks
# included, ahead of their llm and cloudsync domains), so a stale import of them never counts.
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
JVMAPI='synchronized\(|@Synchronized|(^|[^A-Za-z_.])System\.|::class\.java|String\.format|(^|[^A-Za-z_.])Thread[.(]|@JvmStatic|@Throws\(|(^|[^A-Za-z_.])Math\.|(^|[^A-Za-z_.])Charsets\.|OutOfMemoryError|(^|[^A-Za-z_.])GlobalContext\b|org\.koin\.core\.context\.GlobalContext'
OKMARK='//[[:space:]]*L1-common-ok:'
JVM3P='^import[[:space:]]+(org\.apache\.commons|org\.commonmark|org\.yaml|okhttp3|org\.json|com\.google|org\.koin\.java|org\.koin\.android)\.'
ANDROID='^import[[:space:]]+(android|androidx)\.|(^|[^A-Za-z_.])R\.[a-z]+\.|BuildConfig'
# Inline fully-qualified uses: same packages, not preceded by an identifier char or dot, on lines that are
# neither import/package statements nor comment lines (so imports are not counted twice).
JVM_INLINE='(^|[^A-Za-z0-9_.])javax?\.[a-z]'
JVM3P_INLINE='(^|[^A-Za-z0-9_.])(org\.apache\.commons|org\.commonmark|org\.yaml|okhttp3|org\.json|com\.google|org\.koin\.java|org\.koin\.android)\.'
NONCODE='^[[:space:]]*((import|package)[[:space:]]|//|/\*|\*)'
KMPALLOW='^import[[:space:]]+androidx\.(room3|sqlite|annotation|collection)\.'

VOLATILE='(^|[^A-Za-z_.])@Volatile\b'
VOLIMPORT='^import[[:space:]]+kotlin\.concurrent\.Volatile'
# unqualified @Volatile lines: only files without the kotlin.concurrent.Volatile import are counted
volatile_files() { local f; for f in "$@"; do grep -a -q -E "$VOLIMPORT" "$f" 2>/dev/null || echo "$f"; done; }
volatile_count() { local vf; mapfile -t vf < <(volatile_files "$@"); count "$VOLATILE" "$OKMARK" "${vf[@]}"; }

app_count() { printf '%s\n' "$@" | python3 -I "$HERE/l1-move-app.py" "$ROOT"; }
app_lines() { printf '%s\n' "$@" | python3 -I "$HERE/l1-move-app.py" "$ROOT" --lines; }

domains=$(awk -F'\t' 'NF>=2 && substr($1,1,1)!="@" {print $1}' "$MAP" | awk '!seen[$0]++')
[ -n "$ONLY" ] && domains="$ONLY"
fail=0
printf 'domain\tjvm\tjvmapi\tjvm3p\tandroid\tapp\n'
for d in $domains; do
  mapfile -t F < <(files_of "$d")
  j=$(( $(count "$JVM" '' "${F[@]}") + $(count "$JVM_INLINE" "$NONCODE" "${F[@]}") ))
  api=$(( $(count "$JVMAPI" "$OKMARK" "${F[@]}") + $(volatile_count "${F[@]}") ))
  tp=$(( $(count "$JVM3P" '' "${F[@]}") + $(count "$JVM3P_INLINE" "$NONCODE" "${F[@]}") ))
  a=$(count "$ANDROID" "$KMPALLOW" "${F[@]}")
  if [ ${#F[@]} -eq 0 ]; then ap=0; else ap=$(app_count "${F[@]}"); fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$d" "$j" "$api" "$tp" "$a" "$ap"
  if [ $CHECK -eq 1 ] && grep -qxF "$d" "$DONE" 2>/dev/null && [ $((j+api+tp+a+ap)) -gt 0 ]; then
    echo "REGRESSION in move-done domain '$d':" >&2
    if [ ${#F[@]} -gt 0 ]; then
      { grep -a -n -E "$JVM|$JVMAPI|$JVM3P|$ANDROID|$JVM_INLINE|$JVM3P_INLINE" "${F[@]}" | grep -a -v -E "$OKMARK|:${KMPALLOW#^}" >&2 || true; }
      mapfile -t VF < <(volatile_files "${F[@]}")
      [ ${#VF[@]} -gt 0 ] && { grep -a -n -E "$VOLATILE" "${VF[@]}" | grep -a -v -E "$OKMARK" >&2 || true; }
      app_lines "${F[@]}" >&2
    fi
    fail=1
  fi
done
exit $fail
