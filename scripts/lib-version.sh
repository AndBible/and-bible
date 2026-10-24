#!/usr/bin/env bash
# Sourced library: reads and bumps the single version source, version.properties.
# VERSION_FILE can be overridden from the environment (tests, other checkouts).
# Provides: version_code, version_major_minor, version_name [suffix], version_set_code <n>.

: "${VERSION_FILE:=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/version.properties}"

# Prints the value of a key; fails if the key is missing.
_version_get() {
    local key="$1" line
    line=$(grep -m1 "^${key}=" "$VERSION_FILE") || { echo "version: no $key in $VERSION_FILE" >&2; return 1; }
    printf '%s\n' "${line#*=}"
}

version_code() { _version_get versionCode; }
version_major_minor() { _version_get versionMajorMinor; }

# version_name [suffix]: an explicit suffix (even an empty one) replaces the file's versionNameSuffix.
version_name() {
    local mm code suffix
    mm=$(version_major_minor) && code=$(version_code) || return 1
    if [ $# -ge 1 ]; then suffix="$1"; else suffix=$(_version_get versionNameSuffix) || return 1; fi
    printf '%s.%s%s\n' "$mm" "$code" "$suffix"
}

# version_set_code <n>: n must be an integer larger than the current code. Rewrites only that line.
version_set_code() {
    local new="${1:-}" cur tmp
    [[ "$new" =~ ^[0-9]+$ ]] || { echo "version: '$new' is not a non-negative integer" >&2; return 1; }
    cur=$(version_code) || return 1
    if [ $((10#$new)) -le $((10#$cur)) ]; then
        echo "version: new code $new must be larger than current $cur" >&2
        return 1
    fi
    tmp=$(mktemp "${VERSION_FILE}.XXXXXX") || return 1
    chmod 644 "$tmp"   # mktemp makes it 0600; keep the file readable like a normal checkout
    if sed "s/^versionCode=.*/versionCode=$((10#$new))/" "$VERSION_FILE" > "$tmp"; then
        mv "$tmp" "$VERSION_FILE"
    else
        rm -f "$tmp"; return 1
    fi
}
