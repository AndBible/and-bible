#!/usr/bin/env bash
# Prepares one language for capture: the demo backup (generated from demo-content/<lang>/content.yaml) and the
# Sword modules of demo-content/modules.yaml, both under .local/store-shots/ (STORE_SHOTS_WORK overrides).
#   scripts/store-shots/prepare-lang.sh <lang>        e.g. en
# The backup is regenerated when content.yaml or any generator source (sharedLogic/src/jvmTest/kotlin/net/bible/demo/)
# is newer than the zip, or with --force:  prepare-lang.sh <lang> --force
# Prints the demo backup path on its last line.
set -euo pipefail
lang="${1:?lang}"
force=0
[ "${2:-}" = "--force" ] && force=1
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
work="${STORE_SHOTS_WORK:-$repo/.local/store-shots}"
content="store-screenshots/demo-content/$lang/content.yaml"
[ -f "$repo/$content" ] || { echo "prepare-lang: $content missing" >&2; exit 1; }
zip="$work/$lang/demo.abdb.zip"
gen_newer() { [ -n "$(find "$repo/sharedLogic/src/jvmTest/kotlin/net/bible/demo" -type f -newer "$zip" -print -quit 2>/dev/null)" ]; }
if [ "$force" = 1 ] || [ ! -f "$zip" ] || [ "$repo/$content" -nt "$zip" ] || gen_newer; then
    mkdir -p "$work/$lang"
    (cd "$repo" && ./gradlew -q :sharedLogic:generateDemoBackup -PdemoContent="$content" -PdemoOut="$zip") >&2
fi
"$here/run.sh" modules --lang "$lang" --out "$work/modules" >&2
echo "$zip"
