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

[ $# -ge 1 ] || die "usage: run-on-mac.sh generate <app> <ios> | oracle | package"
mode="$1"; shift
case "$mode" in
    generate) mode_generate "$@" ;;
    oracle) mode_oracle "$@" ;;
    package) mode_package "$@" ;;
    *) die "unknown mode $mode" ;;
esac
