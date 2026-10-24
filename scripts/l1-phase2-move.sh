#!/usr/bin/env bash
# L1b phase 2 move/trial script. Plan: docs/superpowers/plans/ (L1b phase 2, Task 1).
#   --list            print the relative paths (under net/bible/) that phase 2 moves, sorted
#   --apply           git mv them from :app into :sharedLogic commonMain (refuses on a dirty tracked tree)
#   --trial <logdir>  --apply, one Gradle compile run, errors extracted, then ALWAYS git reset --hard HEAD
# The moved set is every domain of l1-readiness-domains.txt (files_of already excludes the platform list).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
cd "$ROOT"
SRC="$ROOT/app/src/main/java/net/bible"
DST="$ROOT/sharedLogic/src/commonMain/kotlin/net/bible"
MAP="$ROOT/scripts/l1-readiness-domains.txt"
PLATFORM="$ROOT/scripts/l1-readiness-platform.txt"
. "$HERE/l1-lib.sh"

# :sharedLogic Android main compile task (kotlin multiplatform android library plugin)
ANDROID_COMPILE=":sharedLogic:compileAndroidMain"

list() {
  local d
  awk -F'\t' 'NF>=2 && substr($1,1,1)!="#" && substr($1,1,1)!="@" {print $1}' "$MAP" | awk '!seen[$0]++' |
    while read -r d; do files_of "$d"; done | sed "s|^$SRC/||" | LC_ALL=C sort -u
}

require_clean() {
  if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "refusing: working tree has tracked changes (git status --porcelain)" >&2
    exit 2
  fi
}

apply_moves() {
  local p n=0 i
  mapfile -t PATHS < <(list)
  for p in "${PATHS[@]}"; do
    mkdir -p "$(dirname "$DST/$p")"
    for i in 1 2 3 4 5; do
      # the statusline's git status briefly holds index.lock
      git mv "app/src/main/java/net/bible/$p" "sharedLogic/src/commonMain/kotlin/net/bible/$p" && break
      [ "$i" -eq 5 ] && { echo "git mv failed 5 times: $p" >&2; exit 1; }
      sleep 1
    done
    n=$((n+1))
  done
  for p in "${PATHS[@]}"; do
    [ -e "$SRC/$p" ] && { echo "source path remains after move: $p" >&2; exit 1; }
  done
  echo "moved $n files"
}

MODE="${1:-}"
case "$MODE" in
  --list) list;;
  --apply) require_clean; apply_moves;;
  --trial)
    [ $# -eq 2 ] || { echo "usage: $0 --trial <logdir>" >&2; exit 2; }
    LOGDIR="$2"
    require_clean
    mkdir -p "$LOGDIR"
    trap 'git reset -q --hard HEAD' EXIT
    apply_moves
    rc=0
    ./gradlew -Dorg.gradle.native=false --continue :sharedLogic:compileKotlinJvm "$ANDROID_COMPILE" \
      :sharedLogic:compileKotlinIosSimulatorArm64 :sharedLogic:compileTestKotlinIosSimulatorArm64 \
      :app:compileStandardGoogleplayDebugUnitTestKotlin > "$LOGDIR/trial.log" 2>&1 || rc=$?
    grep -a -A3 '^e: ' "$LOGDIR/trial.log" > "$LOGDIR/errors.txt" || true
    echo "gradle exit $rc; $(grep -a -c '^e: ' "$LOGDIR/trial.log" || true) error lines (see $LOGDIR/errors.txt)"
    ;;
  *) echo "usage: $0 --list | --apply | --trial <logdir>" >&2; exit 2;;
esac
