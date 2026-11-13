#!/usr/bin/env bash
# Creates the video media folders under .local/video-media (shared by all containers, never in git).
# Usage: media-init.sh [EPISODE]   Idempotent: only mkdir -p, never touches existing content.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MEDIA="$REPO/.local/video-media"
mkdir -p "$MEDIA/shared/music"
if [ -n "${1:-}" ]; then
  case "$1" in */*|.*) echo "invalid episode name: $1" >&2; exit 2;; esac
  mkdir -p "$MEDIA/$1"/{captures,voice,subs}
fi
echo "$MEDIA"
