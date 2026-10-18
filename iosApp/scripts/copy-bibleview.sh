#!/bin/sh
# Copies the bibleview-js production build + the PoC fixture into the app bundle at bibleview-js/.
# Builds the bundle first (npm ci + build-production) when dist/ is missing or older than src/.
set -eu
SRC="$SRCROOT/../app/bibleview-js"
DEST="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/bibleview-js"
needs_build() {
  [ -f "$SRC/dist/index.html" ] || return 0
  [ -n "$(find "$SRC/src" -type f -newer "$SRC/dist/index.html" 2>/dev/null | head -n 1)" ]
}
if needs_build; then
  # Xcode run-script phases get a minimal PATH; add the usual npm homes.
  PATH="/opt/homebrew/bin:/usr/local/bin:$HOME/.nvm/versions/node/$(ls "$HOME/.nvm/versions/node" 2>/dev/null | tail -n 1)/bin:$PATH"
  command -v npm >/dev/null 2>&1 || { echo "error: app/bibleview-js/dist is missing or outdated and npm is not installed; install Node.js 24 (npm) or run npm run build-production in app/bibleview-js"; exit 1; }
  (cd "$SRC" && npm ci && npm run build-production)
fi
[ -f "$SRC/dist/index.html" ] || { echo "error: npm run build-production did not produce app/bibleview-js/dist/index.html"; exit 1; }
rm -rf "$DEST"; mkdir -p "$DEST"
cp -R "$SRC/dist/." "$DEST/"
cp "$SRC/ios/poc-document.json" "$DEST/"
