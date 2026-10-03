#!/usr/bin/env bash
# jailbee on_create: point Claude Code's per-project memory at the versioned
# docs/superpowers/memory (private submodule). No-op for anyone without that submodule.
set -euo pipefail
REPO=${REPO:-$(cd "$(dirname "$0")/.." && pwd)}
PROJECT_SLUG=${PROJECT_SLUG:-$(printf '%s' "$REPO" | sed 's/[^A-Za-z0-9]/-/g')}
src=$REPO/docs/superpowers/memory
dst=$HOME/.claude/projects/$PROJECT_SLUG/memory
if [ ! -d "$src" ]; then
  echo "link-memory: $src not checked out (git submodule update --init --checkout docs/superpowers); skipping" >&2
  exit 1
fi
if [ -L "$dst" ]; then [ "$(readlink "$dst")" = "$src" ] && exit 0; echo "link-memory: $dst links elsewhere; leaving it" >&2; exit 1; fi
if [ -e "$dst" ]; then echo "link-memory: $dst is a real directory; merge it into $src by hand" >&2; exit 1; fi
mkdir -p "$(dirname "$dst")"; ln -s "$src" "$dst"; echo "link-memory: $dst -> $src"
