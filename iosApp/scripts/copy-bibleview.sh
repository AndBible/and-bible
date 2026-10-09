#!/bin/sh
# Copies the bibleview-js production build + the iOS shim + the PoC fixture into the app bundle
# at bibleview-js/. Requires `npm run build-production` in app/bibleview-js beforehand (CI does it).
set -eu
SRC="$SRCROOT/../app/bibleview-js"
DEST="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/bibleview-js"
[ -f "$SRC/dist/index.html" ] || { echo "error: run npm run build-production in app/bibleview-js first"; exit 1; }
rm -rf "$DEST"; mkdir -p "$DEST"
cp -R "$SRC/dist/." "$DEST/"
cp "$SRC/ios/ios-shim.js" "$SRC/ios/poc-document.json" "$DEST/"
