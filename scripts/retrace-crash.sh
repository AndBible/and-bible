#!/usr/bin/env bash
# De-obfuscate the stack traces in a logcat file from an R8-obfuscated AndBible release build (#4160;
# docs/r8-obfuscation.md). Release builds print frames as `at a32.E(r8-map-id-<64 hex>:721)`: the map id names the
# exact mapping.txt of the build, so the matching mapping is picked from the local archive by that id.
#
# Usage: scripts/retrace-crash.sh [--mapping FILE] [--version-code N] <log>
#   <log>               logcat text (the in-app bug report's logcat.txt.gz / last_crash_logcat.txt.gz, a Play Console
#                       trace, ...); .gz is read as is; - reads stdin. Output: the same text, de-obfuscated, on stdout.
#   --mapping FILE      use this mapping.txt (or .txt.gz) instead of looking it up; refused when the log's map id is
#                       another build's
#   --version-code N    first fetch the mapping files of the GitHub release whose tag ends in -N (beta-N, production-N,
#                       ...) into the archive, with gh (read-only); GitHub CI builds only (Play, Samsung, ... builds
#                       are other builds: their mappings are in .local/release or the archive, see the docs)
# Archive: $ANDBIBLE_MAPPINGS_DIR (default <repo>/.local/r8-mappings) and <repo>/.local/release/*/android (the Play
# release's mapping copies), searched recursively for mapping*.txt / mapping*.txt.gz.
# Env: RETRACE (default: the Android SDK cmdline-tools retrace), GH (default gh), GH_REPO (default AndBible/and-bible).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
ARCHIVE="${ANDBIBLE_MAPPINGS_DIR:-$REPO/.local/r8-mappings}"
GH="${GH:-gh}"
GH_REPO="${GH_REPO:-AndBible/and-bible}"
die() { echo "retrace-crash: $*" >&2; exit 1; }
usage() { sed -n '2,/^set /p' "$0" | sed '$d; s/^# \{0,1\}//' >&2; exit 2; }

MAPPING= VC= LOG=
while [ $# -gt 0 ]; do
    case "$1" in
    --mapping) MAPPING="${2:?}"; shift 2 ;;
    --version-code) VC="${2:?}"; shift 2 ;;
    -h|--help) usage ;;
    -) LOG=-; shift ;;
    -*) usage ;;
    *) LOG="$1"; shift ;;
    esac
done
[ -n "$LOG" ] || usage

find_retrace() {
    if [ -n "${RETRACE:-}" ]; then echo "$RETRACE"; return; fi
    local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}" r
    for r in "$sdk/cmdline-tools/latest/bin/retrace" "$sdk"/cmdline-tools/*/bin/retrace; do
        [ -x "$r" ] && { echo "$r"; return; }
    done
    command -v retrace && return
    echo "retrace-crash: no retrace found: install the Android SDK cmdline-tools or set RETRACE" >&2
    return 1
}
cat_any() { case "$1" in *.gz) gzip -dc -- "$1" ;; *) cat -- "$1" ;; esac; }
map_id_of() { cat_any "$1" 2>/dev/null | head -n 20 | sed -n 's/^# pg_map_id: *//p' | head -n 1; }

W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
if [ "$LOG" = - ]; then cat > "$W/log.txt"; else [ -f "$LOG" ] || die "no such log: $LOG"; cat_any "$LOG" > "$W/log.txt"; fi

mapfile -t IDS < <(grep -o 'r8-map-id-[0-9a-f]\{8,\}' "$W/log.txt" | sed 's/^r8-map-id-//' | sort -u)
[ "${#IDS[@]}" -le 1 ] || die "the log mixes several builds (map ids ${IDS[*]}); split it per build first"
ID="${IDS[0]:-}"

if [ -n "$VC" ]; then
    dest="$ARCHIVE/$VC"
    if ! find "$dest" -name 'mapping*.txt*' 2>/dev/null | grep -q .; then
        tag=$("$GH" release list -R "$GH_REPO" --limit 400 --json tagName --jq '.[].tagName' | grep -E -- "-$VC\$" | head -n 1)
        [ -n "$tag" ] || die "no GitHub release with a tag ending in -$VC in $GH_REPO"
        mkdir -p "$dest"
        "$GH" release download "$tag" -R "$GH_REPO" -p 'mapping-*' -D "$dest" \
            || die "release $tag has no mapping files (releases before #4160 were not obfuscated)"
        echo "retrace-crash: fetched the mappings of $tag into $dest" >&2
    fi
fi

if [ -n "$MAPPING" ]; then
    [ -f "$MAPPING" ] || die "no such mapping: $MAPPING"
    got=$(map_id_of "$MAPPING")
    [ -z "$ID" ] || [ "$got" = "$ID" ] || die "$MAPPING is build $got, the log is build $ID: find the right mapping"
elif [ -n "$ID" ]; then
    while IFS= read -r -d '' f; do
        [ "$(map_id_of "$f")" = "$ID" ] && { MAPPING=$f; break; }
    done < <(find "$ARCHIVE" "$REPO"/.local/release/*/android \( -name 'mapping*.txt' -o -name 'mapping*.txt.gz' \) \
             -print0 2>/dev/null)
    [ -n "$MAPPING" ] || die "no mapping with map id $ID under $ARCHIVE or .local/release. A GitHub build: fetch it with --version-code <versionCode> (the bug report names the version). A Play build: Play Console, App bundle explorer, the version, Downloads, ReTrace mapping file (then --mapping). Other stores: their archived mapping (docs/r8-obfuscation.md)"
else
    die "the log has no r8-map-id frames: pass the build's mapping with --mapping (or --version-code plus --mapping)"
fi
echo "retrace-crash: mapping $MAPPING (map id ${ID:-$(map_id_of "$MAPPING")})" >&2

RETRACE_BIN=$(find_retrace) || exit 1
case "$MAPPING" in *.gz) gzip -dc -- "$MAPPING" > "$W/mapping.txt"; MAPPING="$W/mapping.txt" ;; esac
"$RETRACE_BIN" "$MAPPING" "$W/log.txt"
