#!/usr/bin/env bash
# Run ostickethelper with machine-local paths from ai-local/osticket.env.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
ENV_FILE="$REPO_ROOT/ai-local/osticket.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "osticket: env file missing: $ENV_FILE" >&2
  echo "Copy .agents/skills/osticket/env.example to ai-local/osticket.env and set OSTICKETHELPER_DIR and OSTICKET_CONFIG." >&2
  echo "Until that file exists, use /support with a pasted ticket. Do not guess credentials or ticket contents." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ -z "${OSTICKETHELPER_DIR:-}" || -z "${OSTICKET_CONFIG:-}" ]]; then
  echo "osticket: $ENV_FILE must set OSTICKETHELPER_DIR and OSTICKET_CONFIG" >&2
  exit 2
fi

if [[ ! -d "$OSTICKETHELPER_DIR" ]]; then
  echo "osticket: OSTICKETHELPER_DIR is not a directory: $OSTICKETHELPER_DIR" >&2
  exit 2
fi

if [[ ! -f "$OSTICKET_CONFIG" ]]; then
  echo "osticket: OSTICKET_CONFIG not found: $OSTICKET_CONFIG" >&2
  exit 2
fi

export PATH="${HOME}/.local/bin:${PATH}"

cd "$OSTICKETHELPER_DIR"
exec poetry run ostickethelper --config "$OSTICKET_CONFIG" "$@"
