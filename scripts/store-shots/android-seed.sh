#!/usr/bin/env bash
# Puts the demo databases of a demo backup zip into the app's databases/ folder on the container emulator
# (debug build, via run-as), replacing the app's own: the same files the app's restore writes. The app is
# force-stopped first. Modules are installed separately (scripts/andbible-emu.sh push-module).
#   scripts/store-shots/android-seed.sh <demo.abdb.zip> [package]
set -euo pipefail
zip="${1:?demo backup zip}"
pkg="${2:-net.bible.android.activity.debug}"
repo="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
adb() { "$repo/scripts/with-container-adb.sh" adb "$@"; }
tmp="$(mktemp -d)"
trap 'rm -r -- "$tmp"' EXIT
python3 -I -c 'import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
for n in z.namelist():
    if n.startswith("db/") and n.endswith(".sqlite3"):
        open(sys.argv[2] + "/" + n[3:], "wb").write(z.read(n))' "$zip" "$tmp"
stage=/data/local/tmp/store-shots-db
adb shell rm -rf "$stage" </dev/null
adb shell mkdir -p "$stage" </dev/null
for f in "$tmp"/*.sqlite3; do adb push "$f" "$stage/" >/dev/null </dev/null; done
adb shell chmod -R a+rX "$stage" </dev/null
adb shell am force-stop "$pkg" </dev/null
adb shell run-as "$pkg" sh -c "'rm -rf databases && mkdir databases && cp $stage/*.sqlite3 databases/'" </dev/null
adb shell rm -rf "$stage" </dev/null
echo "android-seed: $(ls "$tmp" | wc -l) databases into $pkg"
