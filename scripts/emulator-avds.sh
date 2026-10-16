#!/usr/bin/env bash
# Create an AVD (Pixel 6 Pro, API 36 google_apis x86_64), default name screenshot_phone.
# Idempotent. AVDs go to $ANDROID_AVD_HOME (default ~/.android/avd).
#
#   scripts/emulator-avds.sh [name] [--help]
#
# Needs $ANDROID_HOME with cmdline-tools and the system image
#   system-images;android-36;google_apis;x86_64
# The jailbee golden image has both in /opt/android-sdk. If the image is missing this script
# prints the sdkmanager command instead of installing it.
set -euo pipefail

name=screenshot_phone
case "${1:-}" in
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    "") ;;
    -*) echo "unknown arg $1 (see --help)" >&2; exit 2 ;;
    *) name="$1" ;;
esac

[[ -n "${ANDROID_HOME:-}" ]] || { echo "ERROR: ANDROID_HOME is not set." >&2; exit 1; }
AVDMGR="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
[[ -x "$AVDMGR" ]] || { echo "ERROR: $AVDMGR not found (install Android SDK Command-line Tools)." >&2; exit 1; }

IMG="system-images;android-36;google_apis;x86_64"
if [[ ! -d "$ANDROID_HOME/system-images/android-36/google_apis/x86_64" ]]; then
    echo "ERROR: system image missing. Install it with:" >&2
    echo "  sdkmanager '$IMG'" >&2
    exit 1
fi

# avdmanager falls back to ~/.android/avd when ANDROID_AVD_HOME does not exist yet.
mkdir -p "${ANDROID_AVD_HOME:-$HOME/.android/avd}"
if "$AVDMGR" list avd 2>/dev/null | grep -q "Name: $name"; then
    echo "AVD $name already exists"
    exit 0
fi
echo "Creating AVD $name (pixel_6_pro) ..."
echo "no" | "$AVDMGR" create avd --name "$name" --package "$IMG" --device pixel_6_pro --force

# 6 GB userdata: /sdcard is a FUSE mount over /data/media on API 31+, so external
# storage shares this partition; the default leaves no headroom for APK installs.
config="${ANDROID_AVD_HOME:-$HOME/.android/avd}/${name}.avd/config.ini"
if [[ -f "$config" ]]; then
    if grep -q '^disk.dataPartition.size' "$config"; then
        sed -i 's|^disk.dataPartition.size.*|disk.dataPartition.size = 6442450944|' "$config"
    else
        echo "disk.dataPartition.size = 6442450944" >> "$config"
    fi
fi
echo "AVD $name ready"
