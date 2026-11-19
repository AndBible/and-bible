#!/usr/bin/env bash
# Archive the R8 mapping of every release variant built in this tree, so crashes from that build can be retraced later
# (#4160; docs/r8-obfuscation.md). Run it right after building a release you hand out (Samsung, Huawei, Amazon,
# Accrescent, a manual GitHub build); `make accrescent` and scripts/release/android.sh keep theirs already.
#
# Usage: scripts/archive-r8-mappings.sh [--dest DIR]
#   copies app/build/outputs/mapping/<variant>/mapping.txt to
#   DIR/<versionCode>/<variant>/mapping-<commit>.txt.gz (DIR default $ANDBIBLE_MAPPINGS_DIR or <repo>/.local/r8-mappings),
#   where scripts/retrace-crash.sh finds it by the map id in the stack trace. Prints each archived file.
# Env: APP_BUILD_DIR (default <repo>/app/build).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
DEST="${ANDBIBLE_MAPPINGS_DIR:-$REPO/.local/r8-mappings}"
[ "${1:-}" = --dest ] && DEST="${2:?--dest needs a directory}"
BUILD="${APP_BUILD_DIR:-$REPO/app/build}"
die() { echo "archive-r8-mappings: $*" >&2; exit 1; }

VERSION_FILE="$REPO/version.properties"
# shellcheck source=lib-version.sh
. "$HERE/lib-version.sh"
VC=$(version_code) || die "no versionCode in $VERSION_FILE"
COMMIT=$(git -C "$REPO" rev-parse --short=10 HEAD) || die "not a git checkout"
[ -z "$(git -C "$REPO" status --porcelain --untracked-files=no -- app sharedCore sharedData sharedLogic sharedUi jsword 2>/dev/null)" ] \
    || echo "archive-r8-mappings: warning: tracked changes in the tree; the build may not match commit $COMMIT" >&2

n=0
for m in "$BUILD"/outputs/mapping/*/mapping.txt; do
    [ -s "$m" ] || continue
    variant=$(basename "$(dirname "$m")")
    out="$DEST/$VC/$variant/mapping-$COMMIT.txt.gz"
    mkdir -p "$(dirname "$out")"
    gzip -9 -c "$m" > "$out" || die "cannot write $out"
    echo "$out"
    n=$((n + 1))
done
[ "$n" -gt 0 ] || die "no mapping.txt under $BUILD/outputs/mapping (build a release variant first)"
