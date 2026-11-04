#!/usr/bin/env bash
# Cheap staleness guard for packaging/flatpak/gradle-sources.json (no sha256 download, no node sources).
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
projects=(. app desktopApp desktopWebkitGtk desktopWebView2 sharedUi sharedLogic sharedData sharedCore jsword strings-gen kdom)
outputs=()
for project in "${projects[@]}"; do
    rm -f "$project/build/flatpak/gradle-sources.json"
    outputs+=("$project/build/flatpak/gradle-sources.json")
done
./gradlew --no-configuration-cache -I packaging/flatpak/generate-sources.init.gradle flatpakGradleGenerator :kdom:flatpakGradleGenerator
for output in "${outputs[@]}"; do
    test -f "$output" || { printf 'Missing generator output: %s\n' "$output" >&2; exit 1; }
done
python3 -I packaging/flatpak/merge-gradle-sources.py "$tmp/gradle-sources.json" "${outputs[@]}"
python3 -I packaging/flatpak/check-sources.py packaging/flatpak/gradle-sources.json "$tmp/gradle-sources.json"
