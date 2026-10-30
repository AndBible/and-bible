#!/bin/sh
# Builds app/bibleview-js (production) and copies dist/ + the PoC fixture into $1 (the bibleview-js resource dir).
# npm ci runs only when node_modules is missing or older than package-lock.json; the build only when dist/ is missing or older than any of src/, public/, package.json, package-lock.json, index.html, vite.config.mts or tsconfig*.json.
set -eu
# Git for Windows' sh (the Windows build, W1): System32 precedes the Unix tools on the PATH there, and Windows' own
# find.exe and sort.exe would silently produce an empty files.txt.
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) PATH="/usr/bin:$PATH" ;; esac
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/app/bibleview-js"
DEST="$1"
needs_build() {
  [ -f "$SRC/dist/index.html" ] || return 0
  for p in src public package.json package-lock.json index.html vite.config.mts tsconfig*.json; do
    [ -e "$SRC/$p" ] || continue
    [ -n "$(find "$SRC/$p" -type f -newer "$SRC/dist/index.html" 2>/dev/null | head -n 1)" ] && return 0
  done
  return 1
}
if needs_build; then
  command -v npm >/dev/null 2>&1 || { echo "error: app/bibleview-js/dist is missing or outdated and npm is not installed; install Node.js 24 (npm)"; exit 1; }
  if [ ! -d "$SRC/node_modules" ] || [ "$SRC/package-lock.json" -nt "$SRC/node_modules" ]; then
    (cd "$SRC" && npm ci)
  fi
  (cd "$SRC" && npm run build-production)
fi
[ -f "$SRC/dist/index.html" ] || { echo "error: npm run build-production did not produce app/bibleview-js/dist/index.html"; exit 1; }
rm -rf "$DEST"; mkdir -p "$DEST"
cp -R "$SRC/dist/." "$DEST/"
cp "$SRC/ios/poc-document.json" "$DEST/"
(cd "$DEST" && find . -type f ! -name files.txt | sed 's|^\./||' | LC_ALL=C sort > files.txt)
grep -qx 'index.html' "$DEST/files.txt" || { echo "error: $DEST/files.txt does not list index.html"; exit 1; }
