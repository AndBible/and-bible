#!/usr/bin/env bash
# Create the screenshot_phone AVD (Pixel 6 Pro, API 36 google_apis x86_64). Idempotent.
#
#   scripts/emulator-avds.sh [--help]
#
# Needs $ANDROID_HOME with cmdline-tools and the system image
#   system-images;android-36;google_apis;x86_64
# The SDK may be read-only (it is in the container), so this script never installs
# the image: if it is missing it prints the command to run on the host.
set -euo pipefail

case "${1:-}" in
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    "") ;;
    *) echo "unknown arg $1 (see --help)" >&2; exit 2 ;;
esac

[[ -n "${ANDROID_HOME:-}" ]] || { echo "ERROR: ANDROID_HOME is not set." >&2; exit 1; }
AVDMGR="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
[[ -x "$AVDMGR" ]] || { echo "ERROR: $AVDMGR not found (install Android SDK Command-line Tools)." >&2; exit 1; }

IMG="system-images;android-36;google_apis;x86_64"
if [[ ! -d "$ANDROID_HOME/system-images/android-36/google_apis/x86_64" ]]; then
    echo "ERROR: system image missing. Install it on the HOST (the SDK is read-only here):" >&2
    echo "  sdkmanager '$IMG'" >&2
    exit 1
fi

name=screenshot_phone
if "$AVDMGR" list avd 2>/dev/null | grep -q "Name: $name"; then
    echo "AVD $name already exists"
    exit 0
fi
echo "Creating AVD $name (pixel_6_pro) ..."
echo "no" | "$AVDMGR" create avd --name "$name" --package "$IMG" --device pixel_6_pro --force

# 6 GB userdata: /sdcard is a FUSE mount over /data/media on API 31+, so external
# storage shares this partition; the default leaves no headroom for APK installs.
config="$HOME/.android/avd/${name}.avd/config.ini"
if [[ -f "$config" ]]; then
    if grep -q '^disk.dataPartition.size' "$config"; then
        sed -i 's|^disk.dataPartition.size.*|disk.dataPartition.size = 6442450944|' "$config"
    else
        echo "disk.dataPartition.size = 6442450944" >> "$config"
    fi
fi
echo "AVD $name ready"
