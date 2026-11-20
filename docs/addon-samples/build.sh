#!/usr/bin/env bash
# Builds the sample add-on modules as installable zips and a SWORD repository layout:
#   repo/mods.d.tar.gz        the conf index
#   repo/packages/*.zip       one zip per module (mods.d/<conf> + modules/...)
# Usage: docs/addon-samples/build.sh [output-dir]   (default: docs/addon-samples/repo)
# Serve the output over HTTPS to use it as an AndBible custom repository (see docs/addon-modules.md).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$(realpath -m "${1:-$here/repo}")"
rm -rf "$out"
mkdir -p "$out/packages" "$out/mods.d"
for module in collection-sample prompt-sample; do
  conf=$(ls "$here/$module/mods.d/"*.conf)
  name=$(basename "$conf" .conf)
  (cd "$here/$module" && zip -qr "$out/packages/$name.zip" mods.d modules)
  cp "$conf" "$out/mods.d/"
done
(cd "$out" && tar czf mods.d.tar.gz mods.d && rm -rf mods.d)
echo "Built $out:"; (cd "$out" && find . -type f | sort)
