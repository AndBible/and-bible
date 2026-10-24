#!/usr/bin/env bash
# Generate the legacy Swift app's SwiftData fixtures and oracle exports on the remote Mac.
# Mirrors .github/workflows/ios-i3-legacy-fixtures.yml. Run from the container:
#
#   scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh generate 1.0.2 26
#   scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh generate 1.0.1 26
#   scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh oracle
#   scripts/mac-remote.sh run bash iosApp/legacy-fixtures/run-on-mac.sh package
#   scripts/mac-remote.sh fetch build/i3-fixtures
#
# `mac-remote.sh run` syncs the tree, takes the Mac-wide build lock and runs in the tree root, so
# this script works relative to the current directory. Outputs and logs go to build/i3-fixtures/
# (build/ is excluded from the sync). Legacy checkouts, libsword builds and DerivedData live in
# ~/work/i3-legacy (outside the synced tree, so they survive --delete syncs and other containers).
#
#   generate <app> <ios>   app = 1.0.1 | 1.0.2, ios = major version of the simulator runtime (17, 18, 26)
#   oracle                 pinned app on an iOS 26 simulator, I3OracleExportTests over build/i3-fixtures/gen
#   package                build/i3-fixtures/{gen,oracle} -> build/i3-fixtures/package (zips + manifest.json)
#   install-over <app>     best effort (I3b Task 15): build the pinned Swift app with the dev bundle id, run it once, then
#                          install the new app (last `scripts/mac-remote.sh xcode-build iphone`) over it; results in
#                          build/i3-fixtures/install-over/<app>/ (see README, "Install-over smoke")
# The Python test runs from its own directory: cd iosApp/legacy-fixtures && python3 -m unittest test_package_fixtures
set -euo pipefail

# Pinned Swift app commit and the App Store 1.0.1 release (see the workflow).
LEGACY_REPO_URL="https://github.com/AndBible/and-bible-ios"
LEGACY_PIN="5b14d179e4bc6f8b40751d5593e9b73eb21bc211"
LEGACY_101="9ca04b8a9a2921fd1fb1b1f7653b377482d6854a"

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

TREE="$(pwd)"
FIX="$TREE/build/i3-fixtures"
STATE="$HOME/work/i3-legacy"
LOGS="$FIX/logs"
HERE="iosApp/legacy-fixtures"
mkdir -p "$LOGS" "$STATE"

die() { echo "run-on-mac: $*" >&2; exit 1; }

SIM_UDID=""
cleanup() {
    if [ -n "$SIM_UDID" ]; then
        xcrun simctl shutdown "$SIM_UDID" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

record_toolchain() {
    { date; sw_vers; xcodebuild -version; swift --version; uname -m; xcrun simctl list runtimes; } \
        >"$LOGS/toolchain-$1.txt" 2>&1 || true
}

# Check out <ref> of the legacy app into $STATE/<name>, keeping the libsword build products.
checkout_legacy() {
    local name="$1" ref="$2" dir="$STATE/$1"
    if [ ! -d "$dir/.git" ]; then
        git clone "$LEGACY_REPO_URL" "$dir" >&2
    else
        git -C "$dir" fetch --tags origin >&2
    fi
    git -C "$dir" checkout -f "$ref" >&2
    git -C "$dir" clean -fdx -e libsword/ >&2
    echo "$dir"
}

# Build the iOS-only libsword XCFramework once; reuse keyed on the shasum of build-ios.sh.
build_libsword() {
    local dir="$1/libsword" key stamp
    key="$(shasum "$dir/build-ios.sh" | cut -d' ' -f1)"
    stamp="$dir/.i3-build-key"
    if [ -f "$dir/libsword.xcframework/ios-arm64/libsword.a" ] \
        && [ -f "$dir/libsword.xcframework/ios-arm64_x86_64-simulator/libsword.a" ] \
        && [ "$(cat "$stamp" 2>/dev/null || true)" = "$key" ]; then
        echo "Using the existing libsword.xcframework ($key)"
        return
    fi
    command -v cmake >/dev/null 2>&1 || die "cmake missing on the Mac (brew install cmake, as the Homebrew admin)"
    command -v svn >/dev/null 2>&1 || die "svn missing on the Mac (brew install subversion, as the Homebrew admin)"
    ( cd "$dir" && INCLUDE_MACOS_SLICE=0 ./build-ios.sh )
    echo "$key" >"$stamp"
}

# Create (or reuse) our own simulator "<name>" on the newest available runtime of iOS major <ios>, boot it
# and set SIM_UDID. Never touches the shared "iPhone 16" that other containers drive.
make_simulator() {
    local name="$1" ios="$2" picked runtime devicetype
    picked="$(IOS="$ios" python3 - <<'PY'
import json, os, subprocess
ios = os.environ["IOS"]
data = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "runtimes", "-j"]))
def version_key(rt):
    return tuple(int(p) for p in rt["version"].split(".") if p.isdigit())
candidates = [r for r in data["runtimes"] if r.get("isAvailable") and r.get("platform", "iOS") == "iOS"
              and r["version"].split(".")[0] == ios]
if not candidates:
    raise SystemExit("NONE")
rt = max(candidates, key=version_key)
phones = [d for d in rt["supportedDeviceTypes"] if d["name"].startswith("iPhone")]
wanted = ["iPhone 15", "iPhone 16", "iPhone 17", "iPhone 14"]
pick = next((d for w in wanted for d in phones if d["name"] == w), phones[0])
print(rt["identifier"], pick["identifier"])
PY
)" || true
    [ -n "$picked" ] && [ "$picked" != "NONE" ] || die "no iOS $ios simulator runtime installed (xcrun simctl list runtimes)"
    read -r runtime devicetype <<<"$picked"
    SIM_UDID="$(xcrun simctl list devices available -j | NAME="$name" RT="$runtime" python3 -c '
import json, os, sys
devs = json.load(sys.stdin)["devices"].get(os.environ["RT"], [])
print(next((d["udid"] for d in devs if d["name"] == os.environ["NAME"]), ""))')"
    if [ -z "$SIM_UDID" ]; then
        SIM_UDID="$(xcrun simctl create "$name" "$devicetype" "$runtime")"
    fi
    xcrun simctl boot "$SIM_UDID" 2>/dev/null || true
    xcrun simctl bootstatus "$SIM_UDID" -b
    echo "simulator: $name runtime=$runtime devicetype=$devicetype udid=$SIM_UDID" | tee -a "$LOGS/toolchain-$3.txt"
}

# Copy a fixture-tool test into the legacy checkout.
install_test() {
    cp "$TREE/$HERE/$2" "$1/Sources/BibleCore/Tests/BibleCoreTests/"
}

# Fall back to the simulator's own data directory when the host path stayed empty.
sim_fallback() {
    local out="$1" udid="$2" src
    mkdir -p "$out"
    if ls "$out"/*/AndBible.store >/dev/null 2>&1; then
        echo "Fixtures were written to the host path"
        return
    fi
    echo "Host path empty; searching the simulator container"
    src="$(find "$HOME/Library/Developer/CoreSimulator/Devices/$udid/data" -type d -name i3-fixtures 2>/dev/null | head -n1 || true)"
    if [ -n "$src" ]; then cp -R "$src"/. "$out"/; else echo "No i3-fixtures directory found"; fi
}

# Scenarios every `generate` must produce (one directory with AndBible.store each) and the XCTest case for each.
EXPECTED_SCENARIOS="fresh rich restored-android preserved-ai extended"

# Fail loudly unless every expected scenario was produced and its test case ran. Prints what was produced.
verify_generated() {
    local out="$1" log="$2" installed="$3" missing=0 scenario
    echo "== generated scenarios in $out:"
    for scenario in $EXPECTED_SCENARIOS; do
        if [ -f "$out/$scenario/AndBible.store" ]; then echo "   ok      $scenario"; else echo "   MISSING $scenario" >&2; missing=1; fi
    done
    echo "== XCTest cases that ran (from $log):"
    grep -E "Test Case .*(testScenario[A-Za-z]*) (started|passed|failed)" "$log" | sed 's/^/   /' || true
    grep -E "error:|\*\* TEST FAILED|\*\* BUILD FAILED" "$log" | head -n 20 | sed 's/^/   LOG: /' || true
    if [ "$missing" -ne 0 ]; then
        echo "   installed test file defines: $(grep -c 'func testScenario' "$installed") scenarios, extended present: $(grep -c 'func testScenarioExtended' "$installed")" >&2
        die "expected scenarios are missing in $out (see the lines above and $log)"
    fi
}

mode_generate() {
    [ $# -eq 2 ] || die "usage: generate <app 1.0.1|1.0.2> <ios major>"
    local app="$1" ios="$2" ref dir out log bundle
    case "$app" in
        1.0.1) ref="$LEGACY_101" ;;
        1.0.2) ref="$LEGACY_PIN" ;;
        *) die "unknown app version $app (1.0.1 or 1.0.2)" ;;
    esac
    out="$FIX/gen/$app/$ios"
    log="$LOGS/generate-$app-$ios.log"
    record_toolchain "generate-$app-$ios"
    dir="$(checkout_legacy "app-$app" "$ref")"
    build_libsword "$dir"
    make_simulator "I3 fixtures iOS $ios" "$ios" "generate-$app-$ios"
    install_test "$dir" I3FixtureGeneratorTests.swift
    grep -q 'func testScenarioExtended' "$dir/Sources/BibleCore/Tests/BibleCoreTests/I3FixtureGeneratorTests.swift" \
        || die "the synced $HERE/I3FixtureGeneratorTests.swift has no testScenarioExtended (stale tree sync?)"
    bundle="$LOGS/generate-$app-$ios.xcresult"
    rm -rf "$out" "$bundle"; mkdir -p "$out"
    local rc=0
    ( cd "$dir" && TEST_RUNNER_I3_FIXTURE_OUT="$out" xcodebuild test \
        -project AndBible.xcodeproj \
        -scheme BibleCoreTests \
        -configuration Debug \
        -destination "id=$SIM_UDID" \
        -derivedDataPath "$STATE/derived-$app" \
        -resultBundlePath "$bundle" \
        -only-testing:BibleCoreTests/I3FixtureGeneratorTests \
        CODE_SIGNING_ALLOWED=NO ) 2>&1 | tee "$log" || rc=$?
    sim_fallback "$out" "$SIM_UDID"
    find "$out" -type f | sort | tee "$LOGS/generated-files-$app-$ios.txt"
    verify_generated "$out" "$log" "$dir/Sources/BibleCore/Tests/BibleCoreTests/I3FixtureGeneratorTests.swift"
    return "$rc"
}

mode_oracle() {
    local dir out log bundle rc=0
    out="$FIX/oracle"; log="$LOGS/oracle.log"
    [ -d "$FIX/gen" ] || die "no $FIX/gen; run generate first"
    record_toolchain oracle
    dir="$(checkout_legacy "app-1.0.2" "$LEGACY_PIN")"
    build_libsword "$dir"
    make_simulator "I3 oracle iOS 26" 26 oracle
    install_test "$dir" I3OracleExportTests.swift
    bundle="$LOGS/oracle.xcresult"
    rm -rf "$out" "$bundle"; mkdir -p "$out"
    ( cd "$dir" && TEST_RUNNER_I3_FIXTURE_IN="$FIX/gen" TEST_RUNNER_I3_FIXTURE_OUT="$out" xcodebuild test \
        -project AndBible.xcodeproj \
        -scheme BibleCoreTests \
        -configuration Debug \
        -destination "id=$SIM_UDID" \
        -derivedDataPath "$STATE/derived-1.0.2" \
        -resultBundlePath "$bundle" \
        -only-testing:BibleCoreTests/I3OracleExportTests \
        CODE_SIGNING_ALLOWED=NO ) 2>&1 | tee "$log" || rc=$?
    find "$out" -type f | sort | tee "$LOGS/oracle-files.txt"
    return "$rc"
}

mode_package() {
    [ -d "$FIX/gen" ] || die "no $FIX/gen; run generate first"
    record_toolchain package
    rm -rf "$FIX/package"
    python3 "$TREE/$HERE/package_fixtures.py" "$FIX/gen" "$FIX/oracle" "$FIX/package" 2>&1 | tee "$LOGS/package.log"
    du -sh "$FIX/package"
}

# Build the legacy app target with the dev bundle id; $1 = checkout dir, $2 = log. Returns xcodebuild's status.
build_swift_app() {
    local dir="$1" log="$2" scheme="${INSTALL_OVER_SCHEME:-AndBible}"
    ( cd "$dir" && xcodebuild build \
        -project AndBible.xcodeproj \
        -scheme "$scheme" \
        -configuration Debug \
        -destination "id=$SIM_UDID" \
        -derivedDataPath "$STATE/derived-install-over" \
        PRODUCT_BUNDLE_IDENTIFIER=org.andbible.ios.dev \
        CODE_SIGNING_ALLOWED=NO ) >"$log" 2>&1
}

mode_install_over() {
    [ $# -eq 1 ] || die "usage: install-over <app 1.0.1|1.0.2>"
    local app="$1" ref dir out dev_id=org.andbible.ios.dev attempt built_ok=0 swift_app new_app
    local logpid="" marker data log
    case "$app" in
        1.0.1) ref="$LEGACY_101" ;;
        1.0.2) ref="$LEGACY_PIN" ;;
        *) die "unknown app version $app (1.0.1 or 1.0.2)" ;;
    esac
    out="$FIX/install-over/$app"
    rm -rf "$out"; mkdir -p "$out"
    new_app="${INSTALL_OVER_NEW_APP:-$TREE/build/dd-iphone/Build/Products/Debug-iphonesimulator/AndBibleDev.app}"
    [ -d "$new_app" ] || die "the new app is missing at $new_app; run scripts/mac-remote.sh xcode-build iphone first"
    record_toolchain "install-over-$app"

    echo "== step 1: build the Swift app $app ($ref) with PRODUCT_BUNDLE_IDENTIFIER=$dev_id"
    dir="$(checkout_legacy "app-$app" "$ref")"
    build_libsword "$dir"
    make_simulator "I3 install-over iOS 26" 26 "install-over-$app"
    for attempt in 1 2; do
        log="$out/swift-build-$attempt.log"
        echo "   attempt $attempt (log: $log)"
        if build_swift_app "$dir" "$log"; then built_ok=1; break; fi
        grep -E "error:|\*\* BUILD FAILED" "$log" | head -n 20 | sed 's/^/   LOG: /' || true
    done
    [ "$built_ok" -eq 1 ] || die "install-over smoke not possible on the simulator: the Swift app does not build with $dev_id in two attempts (see $out/swift-build-*.log; scheme AndBible, override with INSTALL_OVER_SCHEME)"
    swift_app="$(find "$STATE/derived-install-over/Build/Products/Debug-iphonesimulator" -maxdepth 1 -name '*.app' ! -name '*Tests*' | head -n1)"
    [ -n "$swift_app" ] || die "the Swift app build produced no .app under $STATE/derived-install-over (see $out/swift-build-*.log)"
    echo "   built: $swift_app, bundle id: $(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$swift_app/Info.plist" 2>&1)"

    echo "== step 2: run the Swift app once (uninstall $dev_id, install, launch, 20 s, screenshot, terminate)"
    xcrun simctl terminate "$SIM_UDID" "$dev_id" >/dev/null 2>&1 || true
    xcrun simctl uninstall "$SIM_UDID" "$dev_id" >/dev/null 2>&1 || true
    xcrun simctl install "$SIM_UDID" "$swift_app" \
        || die "install-over smoke not possible on the simulator: simctl install of the Swift app failed"
    marker="$out/.start-marker"; touch "$marker"
    xcrun simctl launch "$SIM_UDID" "$dev_id" \
        || die "install-over smoke not possible on the simulator: the Swift app does not launch with $dev_id (see the simctl error above; check the CloudKit entitlement / signing)"
    sleep 20
    xcrun simctl io "$SIM_UDID" screenshot "$out/1-swift-app.png" >/dev/null 2>&1 || echo "   (screenshot failed)" >&2
    xcrun simctl terminate "$SIM_UDID" "$dev_id" >/dev/null 2>&1 || true
    data="$(xcrun simctl get_app_container "$SIM_UDID" "$dev_id" data 2>&1 || true)"
    echo "   Swift app data container: $data"
    { echo "$data"; find "$data" -maxdepth 5 \( -name '*.store*' -o -name '*.json' \) 2>/dev/null | sort; } >"$out/swift-data-files.txt" || true
    if ! find "$data" -name 'AndBible.store' 2>/dev/null | grep -q .; then
        echo "   WARNING: the Swift app wrote no AndBible.store; the new app will have nothing to migrate (see $out/1-swift-app.png)" >&2
    fi

    echo "== step 3: install the new app over it ($new_app), launch, 60 s, screenshot"
    xcrun simctl spawn "$SIM_UDID" log stream --style compact --predicate 'process == "AndBibleDev"' >"$out/new-app-log-stream.txt" 2>&1 &
    logpid=$!
    sleep 2
    xcrun simctl install "$SIM_UDID" "$new_app" || { kill "$logpid" 2>/dev/null || true; die "simctl install of the new app over the Swift app failed"; }
    xcrun simctl launch "$SIM_UDID" "$dev_id" || { kill "$logpid" 2>/dev/null || true; die "the new app does not launch over the Swift app"; }
    sleep 60
    xcrun simctl io "$SIM_UDID" screenshot "$out/2-new-app.png" >/dev/null 2>&1 || echo "   (screenshot failed)" >&2
    kill "$logpid" 2>/dev/null || true
    grep -E "LegacyMigration|I2P2" "$out/new-app-log-stream.txt" >"$out/new-app-filtered.log" || true
    xcrun simctl spawn "$SIM_UDID" log show --style compact --last 10m --predicate 'process == "AndBibleDev"' 2>/dev/null \
        | grep -E "LegacyMigration|I2P2" >"$out/new-app-log-show-filtered.log" || true
    echo "   filtered log lines: stream $(wc -l <"$out/new-app-filtered.log"), log show $(wc -l <"$out/new-app-log-show-filtered.log")"
    head -n 40 "$out/new-app-filtered.log" | sed 's/^/   | /'

    echo "== results: done.json and crashes"
    data="$(xcrun simctl get_app_container "$SIM_UDID" "$dev_id" data 2>&1 || true)"
    echo "   new app data container: $data"
    find "$data" -name 'done.json' 2>/dev/null | tee "$out/done-json-path.txt" | sed 's/^/   done.json: /'
    if [ -s "$out/done-json-path.txt" ]; then
        cp "$(head -n1 "$out/done-json-path.txt")" "$out/done.json"
        cat "$out/done.json"; echo
        if grep -Eq '"nothingToMigrate"[[:space:]]*:[[:space:]]*false' "$out/done.json"; then
            echo "   PASS: done.json exists and nothingToMigrate is false"
        else
            echo "   FAIL: nothingToMigrate is not false in done.json" >&2
        fi
    else
        echo "   FAIL: no done.json under the new app's data container" >&2
    fi
    find "$data" -maxdepth 4 2>/dev/null | sort >"$out/new-data-files.txt" || true
    find "$HOME/Library/Logs/DiagnosticReports" \( -name 'AndBible*' -o -name 'iosApp*' \) -newer "$marker" 2>/dev/null \
        | tee "$out/crashes.txt" | sed 's/^/   crash report: /' || true
    if [ -s "$out/crashes.txt" ]; then echo "   FAIL: crash reports since the start of the run (copy: scripts/mac-remote.sh sim crashes)" >&2; fi
    echo "install-over: done; results in $out (fetch with scripts/mac-remote.sh fetch build/i3-fixtures/install-over)"
}

[ $# -ge 1 ] || die "usage: run-on-mac.sh generate <app> <ios> | oracle | package | install-over <app>"
mode="$1"; shift
case "$mode" in
    generate) mode_generate "$@" ;;
    oracle) mode_oracle "$@" ;;
    package) mode_package "$@" ;;
    install-over) mode_install_over "$@" ;;
    *) die "unknown mode $mode" ;;
esac
