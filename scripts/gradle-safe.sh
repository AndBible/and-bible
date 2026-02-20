#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NODE_VERSION="${NODE_VERSION:-20.19.4}"
NODE_DIST="node-v${NODE_VERSION}-linux-x64"
NODE_DIR="${NODE_DIR:-/tmp/${NODE_DIST}}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-home-andbible}"
SANDBOX_HOME="${SANDBOX_HOME:-/tmp/andbible-home}"

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

    if [[ ! -x "${NODE_DIR}/bin/node" ]]; then
        local archive="${NODE_DIST}.tar.xz"
        echo "Downloading ${archive} to /tmp..."
        cd /tmp
        if [[ ! -f "$archive" ]]; then
            wget -q "https://nodejs.org/dist/v${NODE_VERSION}/${archive}"
        fi
        tar -xf "$archive"
    fi

    export PATH="${NODE_DIR}/bin:${PATH}"
}

ensure_node20
mkdir -p "$GRADLE_USER_HOME" "$SANDBOX_HOME"

if [[ "${JAVA_TOOL_OPTIONS:-}" == *"-Duser.home="* ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}"
else
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+${JAVA_TOOL_OPTIONS} }-Duser.home=${SANDBOX_HOME}"
fi

export GRADLE_USER_HOME

cd "$REPO_ROOT"

if [[ "$#" -eq 0 ]]; then
    set -- testStandardGoogleplayDebugUnitTest
fi

echo "Using node: $(node -v)"
echo "Using npm:  $(npm -v)"
echo "Using JAVA_TOOL_OPTIONS: ${JAVA_TOOL_OPTIONS}"
echo "Using GRADLE_USER_HOME: ${GRADLE_USER_HOME}"
echo "Running: ./gradlew --console plain $*"

./gradlew --console plain "$@"
