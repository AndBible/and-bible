#!/bin/bash
# 94-android-sdk — a container-owned Android SDK with a pinned emulator.
#
# Why in the image and not mounted from the host: the in-container emulator only works
# reliably with a known emulator build, and a host SDK update could silently change it.
# Emulator 37.2.12 (stable, 2026-10) leaks ~16 host render threads and ~250 MB per guest
# process death and SIGSEGVs in gfxstream (TextureResize/ColorBufferGl::create racing
# FrameBuffer::cleanupProcGLObjects) after an app force-stop + relaunch; 37.3.4 fixes both
# in our tests (docs/emulator-and-webview-debugging.md, "Emulator version and GPU mode").
# A writable SDK also lets a container install a missing system image itself.
#
# Env: CONTAINER_UID, CONTAINER_GID — runs as root in the golden-build container.
# Installs: /opt/android-sdk (owned by the dev user): cmdline-tools, platform-tools,
#   platforms;android-36, build-tools;36.0.0, system-images;android-36;google_apis;x86_64,
#   emulator 37.3.4 (pinned zip; replaces whatever sdkmanager pulled in as a dependency).
set -euo pipefail

SDK=/opt/android-sdk
CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"   # cmdline-tools 23.0
EMULATOR_VERSION="37.3.4"
EMULATOR_BUILD="16555280"
PACKAGES=(
  "platform-tools"
  "platforms;android-36"
  "build-tools;36.0.0"
  "system-images;android-36;google_apis;x86_64"
)

if [ "$(uname -m)" != x86_64 ]; then
  echo "94-android-sdk: the x86_64 emulator/system image needs an x86_64 host, skipping" >&2
  exit 0
fi
if ! command -v java >/dev/null 2>&1; then
  echo "94-android-sdk: java not found (golden.stacks.java missing?), skipping" >&2
  exit 0
fi

repo=https://dl.google.com/android/repository
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$SDK/cmdline-tools"

echo "94-android-sdk: cmdline-tools"
curl --proto '=https' --tlsv1.2 -fsSL "$repo/$CMDLINE_TOOLS_ZIP" -o "$tmp/cmdline-tools.zip"
python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$tmp/cmdline-tools.zip" "$tmp/ct"
rm -rf "$SDK/cmdline-tools/latest"
mv "$tmp/ct/cmdline-tools" "$SDK/cmdline-tools/latest"
chmod +x "$SDK/cmdline-tools/latest/bin/"*
sdkmanager="$SDK/cmdline-tools/latest/bin/sdkmanager"

echo "94-android-sdk: ${PACKAGES[*]}"
# `yes` dies of SIGPIPE when sdkmanager stops reading; that must not fail the pipeline.
{ yes || true; } | "$sdkmanager" --sdk_root="$SDK" --licenses >/dev/null
"$sdkmanager" --sdk_root="$SDK" --install "${PACKAGES[@]}" | grep -v '^\[' || true

# Pinned emulator. The system image's dependency may have pulled the stable emulator; keep
# its package.xml (sdkmanager's record of the package) with the revision rewritten, as the
# emulator archive page describes for a manual install.
echo "94-android-sdk: emulator ${EMULATOR_VERSION} (${EMULATOR_BUILD})"
curl --proto '=https' --tlsv1.2 -fsSL "$repo/emulator-linux_x64-${EMULATOR_BUILD}.zip" -o "$tmp/emulator.zip"
# python3 zipfile (the image has no unzip) drops the executable bits: chmod after.
python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$tmp/emulator.zip" "$tmp/emu"
rm -f "$tmp/emulator.zip"
if [ -f "$SDK/emulator/package.xml" ]; then cp "$SDK/emulator/package.xml" "$tmp/emu-package.xml"; fi
rm -rf "$SDK/emulator"
mv "$tmp/emu/emulator" "$SDK/emulator"
chmod -R a+rx "$SDK/emulator"
if [ -f "$tmp/emu-package.xml" ]; then
  python3 - "$tmp/emu-package.xml" "$SDK/emulator/package.xml" "$EMULATOR_VERSION" <<'PY'
import re, sys
src, dst, version = sys.argv[1:]
major, minor, micro = version.split(".")
xml = open(src).read()
rev = f"<revision><major>{major}</major><minor>{minor}</minor><micro>{micro}</micro></revision>"
open(dst, "w").write(re.sub(r"<revision>.*?</revision>", rev, xml, count=1, flags=re.S))
PY
fi
grep -q "Pkg.Revision=${EMULATOR_VERSION}" "$SDK/emulator/source.properties"

chown -R "${CONTAINER_UID:-1000}:${CONTAINER_GID:-1000}" "$SDK"
echo "94-android-sdk: installed in $SDK ($(du -sh "$SDK" | cut -f1))"
