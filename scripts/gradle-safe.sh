#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NODE_VERSION="${NODE_VERSION:-20.19.4}"
NODE_DIST="node-v${NODE_VERSION}-linux-x64"
NODE_DIR="${NODE_DIR:-${HOME}/.cache/andbible-tools/${NODE_DIST}}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-home-andbible}"
SANDBOX_HOME="${SANDBOX_HOME:-/tmp/andbible-home}"

require_cmd() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Missing required command: $cmd"
        exit 1
    fi
}

download_file() {
    local url="$1"
    local target="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$url" -o "$target"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "$target" "$url"
    else
        echo "Missing required command: curl or wget"
        exit 1
    fi
}

compute_sha256() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print $1}'
    else
        echo "Missing required command: sha256sum or shasum"
        exit 1
    fi
}

verify_node_archive_checksum() {
    local version="$1"
    local archive="$2"
    local archive_path="$3"
    local sums_path="$4"
    local sums_url="https://nodejs.org/dist/v${version}/SHASUMS256.txt"

    download_file "$sums_url" "$sums_path"
    local expected actual
    expected="$(awk -v name="$archive" '$2 == name { print $1 }' "$sums_path")"
    if [[ -z "$expected" ]]; then
        echo "Failed to find checksum for ${archive} in SHASUMS256.txt"
        exit 1
    fi

    actual="$(compute_sha256 "$archive_path")"
    if [[ "$actual" != "$expected" ]]; then
        echo "Checksum verification failed for ${archive}"
        echo "Expected: $expected"
        echo "Actual:   $actual"
        exit 1
    fi
}

verify_owned_by_current_user() {
    local path="$1"
    local current_uid owner_uid
    current_uid="$(id -u)"
    if owner_uid="$(stat -c '%u' "$path" 2>/dev/null)"; then
        :
    elif owner_uid="$(stat -f '%u' "$path" 2>/dev/null)"; then
        :
    else
        echo "Failed to determine owner for path: $path"
        exit 1
    fi
    if [[ "$owner_uid" != "$current_uid" ]]; then
        echo "Refusing to use path not owned by current user: $path"
        exit 1
    fi
}

ensure_node20() {
    if command -v node >/dev/null 2>&1; then
        local current_major
        current_major="$(node -v | sed -E 's/^v([0-9]+).*/\1/')"
        if [[ "$current_major" == "20" ]]; then
            return 0
        fi
    fi

    if [[ "$(uname -s)" != "Linux" ]]; then
        echo "Node.js 20 is required in PATH on non-Linux platforms."
        exit 1
    fi

    require_cmd tar
    require_cmd stat

    local node_bin="${NODE_DIR}/bin/node"
    local node_parent
    node_parent="$(dirname "${NODE_DIR}")"
    mkdir -p "$node_parent"
    if [[ -e "$node_parent" ]]; then
        verify_owned_by_current_user "$node_parent"
    fi

    if [[ -x "$node_bin" ]]; then
        verify_owned_by_current_user "${NODE_DIR}"
        verify_owned_by_current_user "$node_bin"
        local bundled_major
        bundled_major="$("$node_bin" -v | sed -E 's/^v([0-9]+).*/\1/')"
        if [[ "$bundled_major" != "20" ]]; then
            rm -rf "$NODE_DIR"
        fi
    fi

    if [[ ! -x "$node_bin" ]]; then
        local archive="${NODE_DIST}.tar.xz"
        local archive_url="https://nodejs.org/dist/v${NODE_VERSION}/${archive}"
        local tmp_dir extracted_dir sums_path
        tmp_dir="$(mktemp -d "${node_parent}/.node-download-XXXXXX")"
        archive="${tmp_dir}/${archive}"
        extracted_dir="${tmp_dir}/${NODE_DIST}"
        sums_path="${tmp_dir}/SHASUMS256.txt"
        echo "Downloading ${NODE_DIST} to ${NODE_DIR}..."
        download_file "$archive_url" "$archive"
        verify_node_archive_checksum "$NODE_VERSION" "${NODE_DIST}.tar.xz" "$archive" "$sums_path"
        tar -xf "$archive" -C "$tmp_dir"
        if [[ ! -x "${extracted_dir}/bin/node" ]]; then
            echo "Node archive did not contain expected binary: ${extracted_dir}/bin/node"
            rm -rf "$tmp_dir"
            exit 1
        fi
        rm -rf "$NODE_DIR"
        mv "$extracted_dir" "$NODE_DIR"
        chmod -R go-w "$NODE_DIR" || true
        rm -rf "$tmp_dir"
    fi

    verify_owned_by_current_user "${NODE_DIR}"
    verify_owned_by_current_user "$node_bin"
    export PATH="${NODE_DIR}/bin:${PATH}"
}

ensure_node20
require_cmd stat
mkdir -p "$GRADLE_USER_HOME" "$SANDBOX_HOME"
verify_owned_by_current_user "$GRADLE_USER_HOME"
verify_owned_by_current_user "$SANDBOX_HOME"

if [[ "${JAVA_TOOL_OPTIONS:-}" == *"-Duser.home="* ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}"
else
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+${JAVA_TOOL_OPTIONS} }-Duser.home=${SANDBOX_HOME}"
fi

export GRADLE_USER_HOME

cd "$REPO_ROOT"
require_cmd node
require_cmd npm

if [[ "$#" -eq 0 ]]; then
    set -- testStandardGoogleplayDebugUnitTest
fi

echo "Using node: $(node -v)"
echo "Using npm:  $(npm -v)"
echo "Using JAVA_TOOL_OPTIONS: ${JAVA_TOOL_OPTIONS}"
echo "Using GRADLE_USER_HOME: ${GRADLE_USER_HOME}"
echo "Running: ./gradlew --console plain $*"

./gradlew --console plain "$@"
