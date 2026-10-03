#!/bin/bash
# 96-uv — install the uv Python package manager from its GitHub release.
#
# uv builds and tests the website (website/, `make site`). It is not in Ubuntu apt.
# Env: (none) — runs as root in the golden-build container. Installs: /usr/local/bin/uv, uvx
set -euo pipefail

UV_VERSION="0.12.22"

case "$(uname -m)" in
  x86_64)  UV_ARCH="x86_64-unknown-linux-gnu" ;;
  aarch64) UV_ARCH="aarch64-unknown-linux-gnu" ;;
  *) echo "96-uv: unsupported arch $(uname -m), skipping" >&2; exit 0 ;;
esac

TARBALL="uv-${UV_ARCH}.tar.gz"
URL="https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/${TARBALL}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl --proto '=https' --tlsv1.2 -fsSL "$URL" -o "$tmp/$TARBALL"
tar -xzf "$tmp/$TARBALL" -C "$tmp"
install -m 0755 "$tmp/uv-${UV_ARCH}/uv" "$tmp/uv-${UV_ARCH}/uvx" /usr/local/bin/
uv --version
