#!/usr/bin/env bash
# Prints the full source commit for an iOS build, or nothing when unavailable.
set -euo pipefail
REPO="$(cd "${1:-$(dirname "$0")/..}" && pwd)"
if [ "${AND_BIBLE_COMMIT+x}" = x ]; then
    commit="$AND_BIBLE_COMMIT"
    [[ "$commit" =~ ^[[:xdigit:]]{40}$ ]] || { echo "ios commit: AND_BIBLE_COMMIT must be a 40-character git hash" >&2; exit 1; }
elif [ -f "$(dirname "$REPO")/.mac-owner" ]; then
    commit=$(awk -F= '$1 == "source_revision" { print substr($0, index($0, "=") + 1); exit }' "$(dirname "$REPO")/.mac-owner")
    [[ "$commit" =~ ^[[:xdigit:]]{40}$ ]] || commit=""
else
    commit=$(git -C "$REPO" rev-parse --verify HEAD 2>/dev/null || true)
    [[ "$commit" =~ ^[[:xdigit:]]{40}$ ]] || commit=""
fi
printf '%s' "$commit"
