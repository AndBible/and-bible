#!/usr/bin/env bash
# Release Gradle build that signs from the opened secrets bundle: requires ANDBIBLE_SIGNING_PROPERTIES (a path inside the
# lib-secrets RAM dir, never put on argv) and runs `gradlew --no-daemon <args>`, so no daemon outlives the build holding
# the passwords. Spec docs/superpowers/specs/2026-11-10-release-internal-one-touch-design.md §5.1.
#
# Usage: ANDBIBLE_SIGNING_PROPERTIES=<ram dir>/keystore.properties scripts/release/gradle-release.sh <gradle args...>
# Env: GRADLEW (default <repo>/gradlew; tests use a stub).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
[ -n "${ANDBIBLE_SIGNING_PROPERTIES:-}" ] || { echo "gradle-release: ANDBIBLE_SIGNING_PROPERTIES is not set" >&2; exit 1; }
[ -f "$ANDBIBLE_SIGNING_PROPERTIES" ] && [ -r "$ANDBIBLE_SIGNING_PROPERTIES" ] \
    || { echo "gradle-release: ANDBIBLE_SIGNING_PROPERTIES points to a missing or unreadable file" >&2; exit 1; }
export ANDBIBLE_SIGNING_PROPERTIES
cd "$REPO" || exit 1
exec "${GRADLEW:-$REPO/gradlew}" --no-daemon "$@"
