#!/usr/bin/env bash
# Runs ON THE MAC inside the synced tree (a `run` step of scripts/mac-remote.sh batch, after `xcode-build`):
# puts the store-shot demo data into the booted simulator's app container and fixes the status bar.
#   bash scripts/store-shots/ios-seed.sh <light|dark>
# Input (written by ios-capture.sh into the synced tmp/store-shots/): db/*.sqlite3 (the demo backup's databases),
# modules/*.zip (Sword modules), plan-light.json / plan-dark.json. The plan of the chosen mode becomes plan.json,
# which StoreScreenshotsUITests reads. Databases go where the app keeps them (Application Support/databases),
# modules into Documents/sword: the same files the app's restore and module install write.
set -euo pipefail
mode="${1:?light|dark}"
app_id="${MAC_APP_ID:-org.andbible.ios.dev}"
src="tmp/store-shots"
udid=$(xcrun simctl list devices booted -j | python3 -c 'import json,sys; d=[x["udid"] for g in json.load(sys.stdin)["devices"].values() for x in g if x["state"]=="Booted"]; print(d[0] if len(d)==1 else "")')
[ -n "$udid" ] || { echo "ios-seed: expected exactly one booted simulator" >&2; exit 1; }
data=$(xcrun simctl get_app_container "$udid" "$app_id" data)
xcrun simctl terminate "$udid" "$app_id" >/dev/null 2>&1 || true
support="$data/Library/Application Support"
rm -rf "$support/databases" "$support/backup"
mkdir -p "$support/databases" "$data/Documents/sword"
cp "$src"/db/*.sqlite3 "$support/databases/"
for z in "$src"/modules/*.zip; do unzip -q -o "$z" -d "$data/Documents/sword"; done
cp "$src/plan-$mode.json" "$src/plan.json"
xcrun simctl ui "$udid" appearance "$mode"
# The status bar formats its time with the simulator's region (a Finnish Mac shows "9.41").
xcrun simctl spawn "$udid" defaults write -g AppleLocale en_US || true
xcrun simctl status_bar "$udid" override --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100
echo "ios-seed: $udid $app_id seeded ($(ls "$support/databases" | wc -l) databases, $(ls "$data/Documents/sword/mods.d" | wc -l) modules), appearance $mode"
