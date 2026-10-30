#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cp app/bibleview-js/package-lock.json "$tmp/package-lock.json"
flatpak-node-generator --no-playwright-browsers npm "$tmp/package-lock.json" -o "$tmp/node-sources.json"
python3 -I packaging/flatpak/add-sha256.py "$tmp/node-sources.json"
projects=(. app desktopApp desktopWebkitGtk desktopWebView2 sharedUi sharedLogic sharedData sharedCore jsword strings-gen kdom)
outputs=()
for project in "${projects[@]}"; do
    output="$project/build/flatpak/gradle-sources.json"
    rm -f "$output"
    outputs+=("$output")
done
./gradlew --no-configuration-cache -I packaging/flatpak/generate-sources.init.gradle flatpakGradleGenerator :kdom:flatpakGradleGenerator
for output in "${outputs[@]}"; do
    test -f "$output" || { printf 'Missing generator output: %s\n' "$output" >&2; exit 1; }
done
python3 -I packaging/flatpak/merge-gradle-sources.py "$tmp/gradle-sources.json" "${outputs[@]}"
python3 -I packaging/flatpak/add-sha256.py "$tmp/gradle-sources.json"
cp "$tmp/node-sources.json" packaging/flatpak/node-sources.json
cp "$tmp/gradle-sources.json" packaging/flatpak/gradle-sources.json
