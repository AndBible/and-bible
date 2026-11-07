#!/usr/bin/env bash
# Prepares one language for capture: the demo backup (generated from demo-content/<lang>/content.yaml) and the
# Sword modules of demo-content/modules.yaml, both under .local/store-shots/ (STORE_SHOTS_WORK overrides).
#   scripts/store-shots/prepare-lang.sh <lang>        e.g. en
# Prints the demo backup path on its last line.
set -euo pipefail
lang="${1:?lang}"
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
work="${STORE_SHOTS_WORK:-$repo/.local/store-shots}"
content="store-screenshots/demo-content/$lang/content.yaml"
[ -f "$repo/$content" ] || { echo "prepare-lang: $content missing" >&2; exit 1; }
zip="$work/$lang/demo.abdb.zip"
if [ ! -f "$zip" ] || [ "$repo/$content" -nt "$zip" ]; then
    mkdir -p "$work/$lang"
    (cd "$repo" && ./gradlew -q :sharedLogic:generateDemoBackup -PdemoContent="$content" -PdemoOut="$zip") >&2
fi
"$here/run.sh" modules --lang "$lang" --out "$work/modules" >&2
echo "$zip"
