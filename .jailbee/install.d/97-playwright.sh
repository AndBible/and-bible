#!/bin/bash
# 97-playwright — Playwright + headless Chromium for the website browser checks.
#
# website/tests/*.mjs drive a headless browser against the built site (see website/README.md,
# "Browser checks"). Env: (none) — runs as root in the golden-build container.
# Installs: /opt/playwright (npm package), /opt/ms-playwright (browser), /etc/profile.d/playwright.sh
# (exports NODE_PATH, PLAYWRIGHT_BROWSERS_PATH and CHROME for login shells).
set -euo pipefail

PLAYWRIGHT_VERSION="1.60.0"
PREFIX=/opt/playwright
BROWSERS=/opt/ms-playwright

if ! command -v npm >/dev/null 2>&1; then
  echo "97-playwright: npm not found (golden.stacks.node missing?), skipping" >&2
  exit 0
fi

mkdir -p "$PREFIX" "$BROWSERS"
npm install --prefix "$PREFIX" --no-audit --no-fund "playwright@${PLAYWRIGHT_VERSION}"
export PLAYWRIGHT_BROWSERS_PATH="$BROWSERS"
# Shared libraries come from golden.extra_apt_packages (.jailbee/config.yaml), not --with-deps. Playwright
# does not know Ubuntu 26.04 yet and refuses to install; the 24.04 build runs fine on it.
PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=ubuntu24.04-x64 \
  "$PREFIX/node_modules/.bin/playwright" install chromium-headless-shell
chmod -R a+rX "$PREFIX" "$BROWSERS"

cat > /etc/profile.d/playwright.sh <<EOF
export NODE_PATH="$PREFIX/node_modules\${NODE_PATH:+:\$NODE_PATH}"
export PLAYWRIGHT_BROWSERS_PATH="$BROWSERS"
export CHROME="\$(ls -d $BROWSERS/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell 2>/dev/null | tail -1)"
EOF
echo "97-playwright: installed playwright ${PLAYWRIGHT_VERSION}"
