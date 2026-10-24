#!/usr/bin/env bash
# Create an AVD by name. Idempotent. AVDs go to $ANDROID_AVD_HOME (default ~/.android/avd).
#
#   scripts/emulator-avds.sh [name] [--help | --list]
#
# Named profiles (system image google_apis x86_64, device, extras):
#   screenshot_phone   API 36, pixel_6_pro (default; also used for any unknown name)
#   t9_api28           API 28, pixel_3  - stock WebView 69 (below the app's 83 floor: the
#                      "old WebView" box). Sideload WebView 91 for a working BibleView, see
#                      docs/emulator-and-webview-debugging.md.
#   t9_api29           API 29, pixel_3  - stock WebView 74; sideload WebView 91 the same way
#   t9_api30           API 30, pixel_4  - API 30-34 cutout/insets path
#   t9_api34           API 34, pixel_6  - API 30-34 cutout/insets path
#   emu_api30_tablet   API 30, 10.1in WXGA tablet, 1280x800 @213
#   tablet_api36       API 36, pixel_tablet
#
# Needs $ANDROID_HOME with cmdline-tools and the profile's system image. The jailbee golden
# image installs all of them in /opt/android-sdk (.jailbee/install.d/94-android-sdk.sh). If the
# image is missing this script prints the sdkmanager command instead of installing it.
set -euo pipefail

# name -> "api|device|lcd density (empty = device default)"
declare -A PROFILES=(
    [screenshot_phone]="36|pixel_6_pro|"
    [t9_api28]="28|pixel_3|"
    [t9_api29]="29|pixel_3|"
    [t9_api30]="30|pixel_4|"
    [t9_api34]="34|pixel_6|"
    [emu_api30_tablet]="30|10.1in WXGA (Tablet)|213"
    [tablet_api36]="36|pixel_tablet|"
)

name=screenshot_phone
case "${1:-}" in
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    --list) printf '%s\n' "${!PROFILES[@]}" | sort; exit 0 ;;
    "") ;;
    -*) echo "unknown arg $1 (see --help)" >&2; exit 2 ;;
    *) name="$1" ;;
esac

IFS='|' read -r api device density <<<"${PROFILES[$name]:-${PROFILES[screenshot_phone]}}"

[[ -n "${ANDROID_HOME:-}" ]] || { echo "ERROR: ANDROID_HOME is not set." >&2; exit 1; }
AVDMGR="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
[[ -x "$AVDMGR" ]] || { echo "ERROR: $AVDMGR not found (install Android SDK Command-line Tools)." >&2; exit 1; }

IMG="system-images;android-$api;google_apis;x86_64"
if [[ ! -d "$ANDROID_HOME/system-images/android-$api/google_apis/x86_64" ]]; then
    echo "ERROR: system image missing. Install it with:" >&2
    echo "  sdkmanager '$IMG'" >&2
    exit 1
fi

# avdmanager falls back to ~/.android/avd when ANDROID_AVD_HOME does not exist yet.
mkdir -p "${ANDROID_AVD_HOME:-$HOME/.android/avd}"
if "$AVDMGR" list avd 2>/dev/null | grep -q "Name: $name\$"; then
    echo "AVD $name already exists"
    exit 0
fi
echo "Creating AVD $name (API $api, $device) ..."
echo "no" | "$AVDMGR" create avd --name "$name" --package "$IMG" --device "$device" --force

# 6 GB userdata: /sdcard is a FUSE mount over /data/media on API 31+, so external
# storage shares this partition; the default leaves no headroom for APK installs.
config="${ANDROID_AVD_HOME:-$HOME/.android/avd}/${name}.avd/config.ini"
set_config() {
    if grep -q "^$1" "$config"; then
        sed -i "s|^$1.*|$1 = $2|" "$config"
    else
        echo "$1 = $2" >> "$config"
    fi
}
if [[ -f "$config" ]]; then
    set_config disk.dataPartition.size 6442450944
    [[ -n "$density" ]] && set_config hw.lcd.density "$density"
fi
echo "AVD $name ready"
