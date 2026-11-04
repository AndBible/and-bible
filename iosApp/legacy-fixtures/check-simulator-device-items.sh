#!/bin/bash
# Supplemental #3945 evidence. Run through mac-remote.sh run while its build lock is held.
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
OUT="$PWD/build/device-checks"
mkdir -p "$OUT"
case "${1:-}" in
  prepare)
    for scenario in rich extended; do
      mkdir -p "$OUT/fixtures/$scenario"
      unzip -q -o "sharedLogic/src/jvmTest/resources/legacymigration/fixtures/1.0.2/26/$scenario.zip" -d "$OUT/fixtures/$scenario"
    done
    xcrun simctl list runtimes > "$OUT/runtimes.txt"
    for version in 17-5 18-6 26-5; do
      name="Checklist-3945-iOS-$version"
      id=$(xcrun simctl list devices available -j | python3 -I -c 'import json,sys; name=sys.argv[1]; print(next((d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"]==name), ""))' "$name")
      if [ -z "$id" ]; then
        xcrun simctl create "$name" com.apple.CoreSimulator.SimDeviceType.iPhone-15 "com.apple.CoreSimulator.SimRuntime.iOS-$version"
      fi
    done
    printf 'I3B_FIXTURE_DIR=%s/fixtures\n' "$OUT"
    ;;
  seeded-install-over)
    # The normal install-over tool builds both apps first. Use only synthetic fixture secrets/data.
    id=$(xcrun simctl list devices available -j | python3 -I -c 'import json,sys; print(next(d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"]=="I3 install-over iOS 26"))')
    xcrun simctl boot "$id" 2>/dev/null || true
    xcrun simctl bootstatus "$id" -b
    old="$HOME/work/i3-legacy/derived-install-over/Build/Products/Debug-iphonesimulator/AndBible.app"
    new="$PWD/build/dd-iphone/Build/Products/Debug-iphonesimulator/AndBibleDev.app"
    test -d "$old" && test -d "$new"
    bundle=org.andbible.ios.dev
    # The preceding install-over smoke created this disposable fixture simulator.
    xcrun simctl terminate "$id" "$bundle" 2>/dev/null || true
    xcrun simctl uninstall "$id" "$bundle" 2>/dev/null || true
    rm -f "$OUT/seeded-done.json"
    xcrun simctl install "$id" "$old"
    xcrun simctl launch "$id" "$bundle"
    sleep 10
    xcrun simctl terminate "$id" "$bundle"
    data=$(xcrun simctl get_app_container "$id" "$bundle" data)
    cp "$OUT/fixtures/extended/"*.store* "$data/Library/Application Support/"
    xcrun simctl io "$id" screenshot "$OUT/seeded-old-app.png" >/dev/null 2>&1
    xcrun simctl install "$id" "$new"
    after=$(xcrun simctl get_app_container "$id" "$bundle" data)
    printf 'install-over data before=%s after=%s\n' "$data" "$after" | tee "$OUT/seeded-install-over.txt"
    # CoreSimulator may relocate the sandbox on install; require the actual legacy payload, not its UUID.
    test -f "$after/Library/Application Support/AndBible.store"
    cmp "$OUT/fixtures/extended/AndBible.store" "$after/Library/Application Support/AndBible.store"
    data="$after"
    printf 'PASS install-over preserved legacy store bytes %s\n' "$id" | tee -a "$OUT/seeded-install-over.txt"
    xcrun simctl launch "$id" "$bundle"
    sleep 60
    find "$data" -name done.json -exec cp '{}' "$OUT/seeded-done.json" \;
    test -f "$OUT/seeded-done.json"
    python3 -I - "$OUT/seeded-done.json" <<'PY'
import json, sys
v = json.load(open(sys.argv[1]))
assert v.get('nothingToMigrate') is False, v
print('PASS seeded migration committed; nothingToMigrate=false')
PY
    xcrun simctl io "$id" screenshot "$OUT/seeded-new-app.png" >/dev/null 2>&1
    xcrun simctl terminate "$id" "$bundle"
    python3 -I iosApp/legacy-fixtures/checklist-migration-evidence.py "$data" \
      sharedLogic/src/jvmTest/resources/legacymigration/fixtures/1.0.2/26/extended.zip | tee "$OUT/seeded-oracle.txt"
    find "$data" -name '*.sqlite3' -o -name '*report*' > "$OUT/seeded-data-paths.txt"
    ;;
  tls-native)
    # Keep the server and its marker inside one bounded, lock-held remote step.
    marker=/tmp/andbible-checklist-3945-tls-port
    test ! -e "$marker"
    scratch=$(mktemp -d /tmp/checklist-3945-tls.XXXXXX)
    server_pid=""
    trap 'test -z "$server_pid" || kill "$server_pid" 2>/dev/null || true; rm -f "$marker"; rm -rf "$scratch"' EXIT
    openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost \
      -keyout "$scratch/key.pem" -out "$scratch/cert.pem" > "$OUT/tls-certificate.log" 2>&1
    python3 -I iosApp/legacy-fixtures/checklist-tls-server.py "$scratch/cert.pem" "$scratch/key.pem" "$marker" > "$OUT/tls-server.log" 2>&1 &
    server_pid=$!
    for _ in {1..50}; do test ! -s "$marker" || break; sleep 0.1; done
    test -s "$marker"
    export IOS_TEST_SIMULATOR
    IOS_TEST_SIMULATOR=$(xcrun simctl list devices available -j | python3 -I -c 'import json,sys; print(next(d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"]=="Checklist-3945-iOS-26-5"))')
    xcrun simctl boot "$IOS_TEST_SIMULATOR" 2>/dev/null || true
    xcrun simctl bootstatus "$IOS_TEST_SIMULATOR" -b
    rm -rf sharedLogic/build/test-results/iosSimulatorArm64Test
    ./gradlew -Dorg.gradle.native=false :sharedLogic:iosSimulatorArm64Test --tests '*IosWebDavTlsChecklistTest' > "$OUT/tls-native.log" 2>&1
    grep -E 'CHECKLIST-TLS|BUILD SUCCESSFUL|FAILED' "$OUT/tls-native.log"
    python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
files = list(pathlib.Path('sharedLogic/build/test-results/iosSimulatorArm64Test').glob('*.xml'))
cases = [c for f in files for c in ET.parse(f).iter('testcase') if 'IosWebDavTlsChecklistTest' in c.get('classname', '')]
assert len(cases) == 1 and not list(cases[0]), 'TLS checklist did not execute one successful test'
output = '\n'.join(ET.parse(f).getroot().findtext('system-out', '') for f in files)
assert output.count('CHECKLIST-TLS PASS:') == 3 and 'CHECKLIST-TLS NOT RUN' not in output, output
print('PASS TLS evidence: one executed test, three live challenge assertions')
PY
    ;;
  oldest-runtime-smoke)
    for version in 17-5 18-6; do
      id=$(xcrun simctl list devices available -j | python3 -I -c 'import json,sys; name=sys.argv[1]; print(next(d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"]==name))' "Checklist-3945-iOS-$version")
      xcrun simctl boot "$id" 2>/dev/null || true
      xcrun simctl bootstatus "$id" -b
      result="$OUT/runtime-$version.xcresult"
      test ! -e "$result"
      xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
        -destination "platform=iOS Simulator,id=$id" -resultBundlePath "$result" \
        -only-testing:iosAppUITests/AppUITests/testFreshInstallEasyStartReadsJohn3 \
        -only-testing:iosAppUITests/AppUITests/testRelaunchReopensWorkspace \
        > "$OUT/runtime-$version.log" 2>&1
      grep -E 'Test Case .* (passed|failed)|Executed |TEST SUCCEEDED' "$OUT/runtime-$version.log"
      xcrun simctl shutdown "$id"
    done
    ;;
  background-hook)
    id=$(xcrun simctl list devices available -j | python3 -I -c 'import json,sys; print(next(d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["name"]=="Checklist-3945-iOS-26-5"))')
    xcrun simctl boot "$id" 2>/dev/null || true
    xcrun simctl bootstatus "$id" -b
    bundle=org.andbible.ios.dev
    xcrun simctl install "$id" build/dd-iphone/Build/Products/Debug-iphonesimulator/AndBibleDev.app
    xcrun simctl terminate "$id" "$bundle" 2>/dev/null || true
    pid=$(xcrun simctl launch "$id" "$bundle" -task6ResetCrashState -standardTestFixture | tr -d '\r' | cut -d: -f2 | tr -d ' ')
    sleep 10
    # This private Apple debug hook is diagnostic only, never linked into the product.
    xcrun lldb --batch -p "$pid" \
      -o 'expr -l objc -- (id)[[BGTaskScheduler sharedScheduler] valueForKey:@"_registrations"]' \
      -o 'expr -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"org.andbible.ios.remote-sync-refresh"]' \
      -o 'process detach' > "$OUT/background-hook.txt" 2>&1
    sleep 5
    xcrun simctl spawn "$id" log show --style compact --last 2m --predicate 'process == "AndBibleDev"' > "$OUT/background-app.log"
    ;;
  *) printf 'usage: %s prepare|seeded-install-over|background-hook\n' "$0" >&2; exit 2 ;;
esac
