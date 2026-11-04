#!/bin/bash
# Probe only: a loaded checker and a real background transition are supplemental, not long-sync acceptance.
set -euo pipefail
mkdir -p build/shots
id=$(xcrun simctl create "3959 Checker disposable" com.apple.CoreSimulator.SimDeviceType.iPhone-16 com.apple.CoreSimulator.SimRuntime.iOS-18-6)
trap 'xcrun simctl shutdown "$id" 2>/dev/null || true; xcrun simctl delete "$id"' EXIT
xcrun simctl boot "$id"
xcrun simctl bootstatus "$id" -b
xcrun simctl install "$id" build/dd-iphone/Build/Products/Debug-iphonesimulator/AndBibleDev.app
checker="$(xcode-select -p)/usr/lib/libMainThreadChecker.dylib"
test -f "$checker"
pid=$(SIMCTL_CHILD_DYLD_INSERT_LIBRARIES="$checker" xcrun simctl launch "$id" org.andbible.ios.dev -task6ResetCrashState -standardTestFixture | tr -d '\r' | cut -d: -f2 | tr -d ' ')
xcrun lldb --batch -p "$pid" -o 'image list -b' -o 'process detach' > build/shots/3959-checker-images.txt 2>&1
grep 'libMainThreadChecker' build/shots/3959-checker-images.txt
xcrun simctl launch "$id" com.apple.Preferences
xcrun simctl spawn "$id" log show --style compact --last 1m --predicate 'process == "AndBibleDev"' > build/shots/3959-checker-background.log
printf 'PARTIAL: Main Thread Checker image loaded and app backgrounded; no real long-sync/allowance-count evidence\n'
