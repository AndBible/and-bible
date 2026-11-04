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
    xcrun simctl terminate "$id" "$bundle" 2>/dev/null || true
    xcrun simctl uninstall "$id" "$bundle" 2>/dev/null || true
    xcrun simctl install "$id" "$old"
    xcrun simctl launch "$id" "$bundle"
    sleep 10
    xcrun simctl terminate "$id" "$bundle"
    data=$(xcrun simctl get_app_container "$id" "$bundle" data)
    cp "$OUT/fixtures/extended/"*.store* "$data/Library/Application Support/"
    xcrun simctl io "$id" screenshot "$OUT/seeded-old-app.png" >/dev/null 2>&1
    xcrun simctl install "$id" "$new"
    after=$(xcrun simctl get_app_container "$id" "$bundle" data)
    test "$data" = "$after"
    printf 'PASS install-over preserved container %s\n' "$id" | tee "$OUT/seeded-install-over.txt"
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
    find "$data" -name '*.sqlite3' -o -name '*report*' > "$OUT/seeded-data-paths.txt"
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
      -o 'expr -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"org.andbible.ios.remote-sync-refresh"]' \
      -o 'process detach' > "$OUT/background-hook.txt" 2>&1
    sleep 5
    xcrun simctl spawn "$id" log show --style compact --last 2m --predicate 'process == "AndBibleDev"' > "$OUT/background-app.log"
    ;;
  *) printf 'usage: %s prepare|seeded-install-over|background-hook\n' "$0" >&2; exit 2 ;;
esac
