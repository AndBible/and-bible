#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RECOMMENDED_NODE="20.19.4"
RECOMMENDED_JAVA_MAJOR="17"

status=0

pass() {
    echo "[OK] $1"
}

warn() {
    echo "[WARN] $1"
}

fail() {
    echo "[FAIL] $1"
    status=1
}

if command -v node >/dev/null 2>&1; then
    node_version="$(node -v | sed 's/^v//')"
    node_major="${node_version%%.*}"
    if [[ "$node_major" == "20" ]]; then
        pass "Node.js version is $node_version (major 20)"
        if [[ "$node_version" != "$RECOMMENDED_NODE" ]]; then
            warn "Recommended Node.js is $RECOMMENDED_NODE (.nvmrc), current is $node_version"
        fi
    else
        fail "Node.js major must be 20 (current: $node_version)"
    fi
else
    fail "Node.js is not installed or not in PATH"
fi

if command -v npm >/dev/null 2>&1; then
    npm_version="$(npm -v)"
    npm_major="${npm_version%%.*}"
    if [[ "$npm_major" -ge 10 ]]; then
        pass "npm version is $npm_version"
    else
        warn "npm is older than recommended (current: $npm_version, recommended: 10.x+)"
    fi
else
    fail "npm is not installed or not in PATH"
fi

if command -v java >/dev/null 2>&1; then
    java_line="$(java -version 2>&1 | head -n1)"
    java_major="$(echo "$java_line" | sed -E 's/.*"([0-9]+).*/\1/')"
    if [[ "$java_major" == "$RECOMMENDED_JAVA_MAJOR" ]]; then
        pass "Java major is $java_major"
    else
        fail "Java major must be $RECOMMENDED_JAVA_MAJOR (current: $java_line)"
    fi
else
    fail "Java is not installed or not in PATH"
fi

if [[ -n "${ANDROID_SDK_ROOT:-}" || -n "${ANDROID_HOME:-}" ]]; then
    pass "Android SDK environment variable is set"
else
    warn "ANDROID_SDK_ROOT/ANDROID_HOME not set (Android builds may fail)"
fi

if [[ -d "$HOME/.sword" ]]; then
    pass "Found ~/.sword test modules directory"
else
    warn "Missing ~/.sword test modules directory (run ./scripts/bootstrap-test-modules.sh; some unit tests will fail without it)"
fi

if [[ -f "${REPO_ROOT}/.nvmrc" ]]; then
    expected_node="$(tr -d '[:space:]' < "${REPO_ROOT}/.nvmrc")"
    if [[ "$expected_node" == "$RECOMMENDED_NODE" ]]; then
        pass ".nvmrc is present ($expected_node)"
    else
        warn ".nvmrc exists but differs from recommendation (value: $expected_node)"
    fi
else
    warn ".nvmrc missing in repository root"
fi

echo
if [[ "$status" -eq 0 ]]; then
    echo "Environment check passed."
else
    echo "Environment check has blocking issues."
fi

exit "$status"
