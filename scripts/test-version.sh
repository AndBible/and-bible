#!/usr/bin/env bash
# Tests for scripts/lib-version.sh on a temp copy of version.properties.
# Run: scripts/test-version.sh
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
failn=0
fail() { echo "FAIL: $*"; failn=$((failn + 1)); }
eq() { [ "$2" = "$3" ] || fail "$1: expected '$2', got '$3'"; }

if [ ! -f "$HERE/lib-version.sh" ]; then fail "scripts/lib-version.sh is missing"; echo "$failn failure(s)"; exit 1; fi

export VERSION_FILE="$T/version.properties"
printf 'versionMajorMinor=6.0\nversionCode=1227\nversionNameSuffix=\n' > "$VERSION_FILE"
# shellcheck source=lib-version.sh
. "$HERE/lib-version.sh"

eq version_code 1227 "$(version_code)"
eq version_major_minor 6.0 "$(version_major_minor)"
eq version_name 6.0.1227 "$(version_name)"
eq "version_name -alpha" 6.0.1227-alpha "$(version_name -alpha)"

# A suffix in the file is the default, an explicit argument overrides it.
cp "$VERSION_FILE" "$T/orig"
printf 'versionMajorMinor=6.0\nversionCode=1227\nversionNameSuffix=-beta\n' > "$VERSION_FILE"
eq "file suffix" 6.0.1227-beta "$(version_name)"
eq "explicit suffix" 6.0.1227-alpha "$(version_name -alpha)"
cp "$T/orig" "$VERSION_FILE"

# Raising the code changes only that line.
version_set_code 1300 || fail "version_set_code 1300 failed"
eq "code after set" 1300 "$(version_code)"
printf 'versionMajorMinor=6.0\nversionCode=1300\nversionNameSuffix=\n' > "$T/expected"
cmp -s "$VERSION_FILE" "$T/expected" || fail "file differs from expected after set"
ls "$T" | grep -q 'version.properties.' && fail "temp file left behind"

# Lower, equal, non-numeric and empty values fail and leave the file unchanged.
cp "$VERSION_FILE" "$T/before"
for bad in 5 1300 abc 1x -3 "" 01301 0 ""; do
    if version_set_code "$bad" 2>/dev/null; then fail "version_set_code '$bad' succeeded"; fi
    cmp -s "$VERSION_FILE" "$T/before" || fail "file changed after version_set_code '$bad'"
done

# Non-canonical files: CRLF line endings and a trailing space in the suffix read like Gradle's trimmed values.
printf 'versionMajorMinor=6.0\r\nversionCode=1227\r\nversionNameSuffix=-beta \r\n' > "$VERSION_FILE"
eq "CRLF code" 1227 "$(version_code)"
eq "CRLF major.minor" 6.0 "$(version_major_minor)"
eq "CRLF + trailing-space suffix" 6.0.1227-beta "$(version_name)"
# A leading-zero code is rejected instead of giving another name than Gradle's integer parse.
printf 'versionMajorMinor=6.0\nversionCode=01228\nversionNameSuffix=\n' > "$VERSION_FILE"
if version_code 2>/dev/null; then fail "leading-zero versionCode accepted"; fi
if version_name 2>/dev/null; then fail "version_name accepted a leading-zero code"; fi
cp "$T/orig" "$VERSION_FILE"

# CI pins: the alpha step feeds the suffix to Gradle and the VNAME file; the build step passes the args.
WF="$HERE/../.github/workflows/build-apk.yml"
grep -q 'echo "GRADLE_VERSION_ARGS=-PversionNameSuffix=-alpha" >> \$GITHUB_ENV' "$WF" || fail "alpha step does not write -PversionNameSuffix=-alpha to GITHUB_ENV"
grep -Eq '\./gradlew .*\$GRADLE_VERSION_ARGS .*assembleStandardGithubRelease' "$WF" || fail "Gradle build step does not pass \$GRADLE_VERSION_ARGS"

[ "$failn" -eq 0 ] && echo "all version tests passed" || { echo "$failn failure(s)"; exit 1; }
