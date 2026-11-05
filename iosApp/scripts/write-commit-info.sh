#!/usr/bin/env bash
# Refresh the built app's commit metadata on every Xcode build; never edit the source plist.
set -euo pipefail
PLIST="$TARGET_BUILD_DIR/$INFOPLIST_PATH"
# Preserve only an explicit generation-time override across a source-only Mac sync.
if [ "${AND_BIBLE_COMMIT+x}" != x ] && [ -n "${AND_BIBLE_COMMIT_OVERRIDE:-}" ]; then
    export AND_BIBLE_COMMIT="$AND_BIBLE_COMMIT_OVERRIDE"
fi
COMMIT=$("$SRCROOT/../scripts/ios-commit.sh" "$SRCROOT/..")
PLIST_BUDDY="${PLISTBUDDY:-/usr/libexec/PlistBuddy}"
"$PLIST_BUDDY" -c "Delete :AndBibleCommit" "$PLIST" >/dev/null 2>&1 || true
if [ -n "$COMMIT" ]; then
    "$PLIST_BUDDY" -c "Add :AndBibleCommit string $COMMIT" "$PLIST"
fi
