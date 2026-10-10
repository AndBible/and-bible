#!/bin/bash
# 98-ostickethelper — the ostickethelper CLI for reading/resolving support.andbible.org tickets.
#
# Installed with uv (96-uv) from a pinned commit of Sykero-Software/ostickethelper. It drives the
# container's Google Chrome (config `browser_channel: chrome`), so no Playwright browser download.
# Its config, password file and ticket inbox live in .local/osticket/ (gitignored, host-shared).
# Env: (none) — runs as root in the golden-build container.
# Installs: /opt/uv-tools/ostickethelper (venv), /usr/local/bin/ostickethelper
set -euo pipefail

OSTICKETHELPER_REV="645aff224c7083fa1d52448953fb6fb225280d50"

if ! command -v uv >/dev/null 2>&1; then
  echo "98-ostickethelper: uv not found (96-uv missing?), skipping" >&2
  exit 0
fi

UV_TOOL_DIR=/opt/uv-tools UV_TOOL_BIN_DIR=/usr/local/bin UV_PYTHON_PREFERENCE=only-system \
  uv tool install --link-mode=copy \
  "git+https://github.com/Sykero-Software/ostickethelper@${OSTICKETHELPER_REV}"
chmod -R a+rX /opt/uv-tools
ostickethelper --help >/dev/null
echo "98-ostickethelper: installed ostickethelper @ ${OSTICKETHELPER_REV}"
