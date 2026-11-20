#!/usr/bin/env bash
# Archive the R8 mapping of release variants built in this tree, so crashes from that build can be retraced later
# (#4160; docs/r8-obfuscation.md). Run it right after building a release you hand out (Samsung, Huawei, Amazon, a
# manual GitHub build); `make accrescent` runs it, scripts/release/android.sh keeps the Play mapping itself.
#
# Usage: scripts/archive-r8-mappings.sh [--dest DIR] [VARIANT ...]
#   copies app/build/outputs/mapping/<variant>/mapping.txt (the named variants, e.g. standardSamsungRelease; default
#   every variant there) to DIR/<versionCode>/<variant>/mapping-<commit>.txt.gz (DIR default $ANDBIBLE_MAPPINGS_DIR or
#   <repo>/.local/r8-mappings), where scripts/retrace-crash.sh finds it by the map id in the stack trace. versionCode and
#   commit are the tree's (version.properties, HEAD): a mapping older than the HEAD commit was built from an earlier
#   commit and is skipped with a warning. Prints each archived file.
# Env: APP_BUILD_DIR (default <repo>/app/build).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
DEST="${ANDBIBLE_MAPPINGS_DIR:-$REPO/.local/r8-mappings}"
BUILD="${APP_BUILD_DIR:-$REPO/app/build}"
die() { echo "archive-r8-mappings: $*" >&2; exit 1; }
VARIANTS=()
while [ $# -gt 0 ]; do
    case "$1" in
    --dest) DEST="${2:?--dest needs a directory}"; shift 2 ;;
    -*) die "unknown option $1 (usage: archive-r8-mappings.sh [--dest DIR] [VARIANT ...])" ;;
    *) VARIANTS+=("$1"); shift ;;
    esac
done

VERSION_FILE="$REPO/version.properties"
# shellcheck source=lib-version.sh
. "$HERE/lib-version.sh"
VC=$(version_code) || die "no versionCode in $VERSION_FILE"
COMMIT=$(git -C "$REPO" rev-parse --short=10 HEAD) || die "not a git checkout"
COMMIT_TIME=$(git -C "$REPO" log -1 --format=%ct HEAD)
[ -z "$(git -C "$REPO" status --porcelain --untracked-files=no -- app sharedCore sharedData sharedLogic sharedUi jsword 2>/dev/null)" ] \
    || echo "archive-r8-mappings: warning: tracked changes in the tree; the build may not match commit $COMMIT" >&2

if [ "${#VARIANTS[@]}" = 0 ]; then
    for m in "$BUILD"/outputs/mapping/*/mapping.txt; do [ -s "$m" ] && VARIANTS+=("$(basename "$(dirname "$m")")"); done
fi
n=0
for variant in "${VARIANTS[@]}"; do
    m="$BUILD/outputs/mapping/$variant/mapping.txt"
    [ -s "$m" ] || die "no mapping for $variant at $m"
    if [ "$(stat -c %Y "$m" 2>/dev/null || stat -f %m "$m")" -lt "$COMMIT_TIME" ]; then
        echo "archive-r8-mappings: skipped $variant: its mapping predates commit $COMMIT (built from an earlier commit)" >&2
        continue
    fi
    out="$DEST/$VC/$variant/mapping-$COMMIT.txt.gz"
    mkdir -p "$(dirname "$out")"
    gzip -9 -c "$m" > "$out" || die "cannot write $out"
    echo "$out"
    n=$((n + 1))
done
[ "$n" -gt 0 ] || die "no mapping.txt archived from $BUILD/outputs/mapping (build a release variant first)"
