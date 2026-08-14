#!/bin/bash
# 95-typst — install the typst CLI from a GitHub release binary.
#
# typst is not packaged in Ubuntu apt, so we download the prebuilt
# statically-linked musl binary from the upstream GitHub releases and
# drop it into /usr/local/bin. Used by the ai-local ostickethelper tool
# for PDF receipt generation.
#
# Env: (none required) — runs as root in the golden-build container.
# Installs: /usr/local/bin/typst
set -euo pipefail

TYPST_VERSION="v0.14.2"

case "$(uname -m)" in
  x86_64)  TYPST_ARCH="x86_64" ;;
  aarch64) TYPST_ARCH="aarch64" ;;
  *) echo "95-typst: unsupported arch $(uname -m), skipping" >&2; exit 0 ;;
esac

TARBALL="typst-${TYPST_ARCH}-unknown-linux-musl.tar.xz"
URL="https://github.com/typst/typst/releases/download/${TYPST_VERSION}/${TARBALL}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "95-typst: downloading ${TYPST_VERSION} (${TYPST_ARCH}) from GitHub..."
curl --proto '=https' --tlsv1.2 -fsSL "$URL" -o "$tmp/$TARBALL"
tar -xJf "$tmp/$TARBALL" -C "$tmp"

install -m 0755 "$tmp/typst-${TYPST_ARCH}-unknown-linux-musl/typst" /usr/local/bin/typst

echo "95-typst: installed $(/usr/local/bin/typst --version)"
