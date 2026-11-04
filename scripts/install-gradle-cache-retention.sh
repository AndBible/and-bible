#!/usr/bin/env bash
# Atomically install the shared-home retention policy; never remove cache entries.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"
SOURCE="$ROOT/.jailbee/gradle-cache-retention.init.gradle"
TARGET="$DEST/andbible-cache-retention.init.gradle"
mkdir -p "$DEST"
if [[ -f "$TARGET" ]] && cmp -s "$SOURCE" "$TARGET"; then
    exit 0
fi
TEMP="$(mktemp "$DEST/.andbible-cache-retention.XXXXXX")"
trap 'rm -f "$TEMP"' EXIT
cp "$SOURCE" "$TEMP"
chmod 644 "$TEMP"
mv -f "$TEMP" "$TARGET"
