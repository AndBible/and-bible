#!/usr/bin/env bash
set -euo pipefail

DEST_DIR="${HOME}/.sword"
ZIP_SOURCE=""
ENC_SOURCE=""
KEY="${TEST_MODULE_ENCRYPTION_KEY:-}"
CI_URL="${DOWNLOAD_TEST_MODULES_URL:-}"

usage() {
    cat <<'EOF'
Usage:
  scripts/bootstrap-test-modules.sh [options]

Options:
  --dest DIR                  Destination directory (default: ~/.sword)
  --zip PATH_OR_URL           Plain zip file source (local path or https URL)
  --encrypted-zip PATH_OR_URL Encrypted zip source (local path or https URL)
  --key VALUE                 Decryption key for encrypted zip
  --help                      Show this help

Behavior:
  1) If --zip is provided, installs directly from that zip.
  2) Else if --encrypted-zip is provided, decrypts then installs.
  3) Else if DOWNLOAD_TEST_MODULES_URL is set, uses CI-like encrypted flow
     with TEST_MODULE_ENCRYPTION_KEY.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dest)
            DEST_DIR="${2:-}"
            shift 2
            ;;
        --zip)
            ZIP_SOURCE="${2:-}"
            shift 2
            ;;
        --encrypted-zip)
            ENC_SOURCE="${2:-}"
            shift 2
            ;;
        --key)
            KEY="${2:-}"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

download_or_copy() {
    local source="$1"
    local target="$2"
    if [[ "$source" =~ ^https?:// ]]; then
        wget -qO "$target" "$source"
    else
        if [[ ! -f "$source" ]]; then
            echo "Source file does not exist: $source"
            exit 1
        fi
        cp "$source" "$target"
    fi
}

require_cmd() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Missing required command: $cmd"
        exit 1
    fi
}

require_cmd unzip
require_cmd wget

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

zip_path="${workdir}/testmods.zip"
enc_path="${workdir}/testmods.zip.enc"

if [[ -n "$ZIP_SOURCE" ]]; then
    echo "Installing test modules from plain zip source..."
    download_or_copy "$ZIP_SOURCE" "$zip_path"
elif [[ -n "$ENC_SOURCE" ]]; then
    echo "Installing test modules from encrypted zip source..."
    if [[ -z "$KEY" ]]; then
        echo "Missing decryption key. Provide --key or TEST_MODULE_ENCRYPTION_KEY."
        exit 1
    fi
    require_cmd openssl
    download_or_copy "$ENC_SOURCE" "$enc_path"
    openssl aes-256-cbc -d -a -pbkdf2 -in "$enc_path" -out "$zip_path" -pass "pass:${KEY}"
elif [[ -n "$CI_URL" ]]; then
    echo "Installing test modules using DOWNLOAD_TEST_MODULES_URL..."
    if [[ -z "$KEY" ]]; then
        echo "Missing TEST_MODULE_ENCRYPTION_KEY for CI-style encrypted archive."
        exit 1
    fi
    require_cmd openssl
    download_or_copy "$CI_URL" "$enc_path"
    openssl aes-256-cbc -d -a -pbkdf2 -in "$enc_path" -out "$zip_path" -pass "pass:${KEY}"
else
    echo "No source provided."
    usage
    exit 1
fi

mkdir -p "$DEST_DIR"
unzip -o -d "$DEST_DIR" "$zip_path" >/dev/null

echo "Test modules installed to: $DEST_DIR"
echo "Done."
