#!/bin/bash
# Run through mac-remote.sh batch "xcode-build iphone" "run bash scripts/measure-host-workspace-switch-mac.sh".
# Produces build/3955-switch.mp4 and build/3955-measure.log; fetch them before another run.
set -eu
UDID=$(xcrun simctl list devices available -j | python3 -c 'import sys,json; print(next(d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"] == "iPhone 16"))')
xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b
mkdir -p build
rm -rf build/3955-measure.xcresult
xcrun simctl io "$UDID" recordVideo --codec=h264 --force build/3955-switch.mp4 > build/3955-record.log 2>&1 &
rec=$!
trap 'kill -INT "$rec" 2>/dev/null || true; wait "$rec" 2>/dev/null || true' EXIT
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath build/dd-iphone -only-testing:iosAppUITests/ReadingUITests/testWorkspaceSwitchMeasurement -resultBundlePath build/3955-measure.xcresult > build/3955-measure.log 2>&1
