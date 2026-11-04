#!/bin/bash
# Apply a real per-process Seatbelt network deny before the app executes migration.
# No proxy, reachability flag, host firewall change or production code injection is used.
set -euo pipefail
mkdir -p build/shots
id=$(xcrun simctl list devices available -j | python3 -I -c 'import json,sys; print(next(d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"]=="iPhone 16"))')
bundle=org.andbible.ios.dev
xcrun simctl boot "$id" 2>/dev/null || true
xcrun simctl bootstatus "$id" -b
app="$PWD/build/dd-iphone/Build/Products/Debug-iphonesimulator/AndBibleDev.app"
test -d "$app"
xcrun simctl install "$id" "$app"
xcrun simctl terminate "$id" "$bundle" 2>/dev/null || true
fixture="$PWD/build/device-checks/fixtures/extended"
test -f "$fixture/sidecar.json"
pid=$(xcrun simctl launch --wait-for-debugger "$id" "$bundle" -seedLegacyFixture "$fixture" -task6ResetCrashState | tr -d '\r' | cut -d: -f2 | tr -d ' ')
trap 'xcrun simctl terminate "$id" "$bundle" 2>/dev/null || true' EXIT
# Check the syscall result in the attached process before allowing any migration work.
xcrun lldb --batch -p "$pid" \
  -o 'expr -l objc -- @import Darwin' \
  -o 'expr -l objc -- (int)sandbox_init("(version 1)(allow default)(deny network*)", 0, (char **)0)' \
  -o 'process detach' > build/shots/3959-offline-sandbox.txt 2>&1
if ! grep -Eq '\(int\).* = 0$' build/shots/3959-offline-sandbox.txt; then
  printf 'BLOCKED: process sandbox installation was not proved; no offline acceptance\n'
  exit 2
fi
# A successful sandbox syscall alone is not enough: require explicit network-denial evidence before acceptance.
printf 'PARTIAL: sandbox syscall succeeded, independent outbound denial and relaunch notices still require verification\n'
exit 2
