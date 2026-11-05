#!/usr/bin/env bash
# Tests for scripts/release/mac/archive.sh without a Mac: xcodebuild, xcodegen, codesign, plutil, hdiutil, diskutil, npm,
# shasum and unzip are stubs on PATH that record argv and produce the minimal files the next step reads.
# Run: bash scripts/release/tests/test-mac-archive.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REAL_REPO="$(cd "$HERE/../../.." && pwd)"
SCRIPT="$REAL_REPO/scripts/release/mac/archive.sh"
T="$(mktemp -d)"
FILTER="${1:-}"
trap 'rm -rf "$T"' EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }

BUILD=2026.1105.120000 VERSION=6.0.1250 COMMIT=abcdef0123
FULL=abcdef0123456789abcdef0123456789abcdef01
KEYID=KEYID12345

mkdir -p "$T/bin"
cat > "$T/bin/xcodebuild" <<'EOS'
#!/usr/bin/env bash
echo "xcodebuild $*" >> "$STUB_DIR/calls"
echo "$*" >> "$STUB_DIR/xcodebuild.argv"
case "$*" in
*" archive"*)
    [ "${STUB_ARCHIVE_RC:-0}" = 0 ] || { echo "error: archive broke"; exit "$STUB_ARCHIVE_RC"; }
    ap=$(echo "$*" | sed -n 's/.*-archivePath \([^ ]*\).*/\1/p')
    mkdir -p "$ap/Products/Applications/AndBible.app"; echo "x=1" > "$ap/Products/Applications/AndBible.app/Info.plist" ;;
*-exportArchive*)
    ep=$(echo "$*" | sed -n 's/.*-exportPath \([^ ]*\).*/\1/p')
    mkdir -p "$ep"
    case "$ep" in *export) : > "$ep/AndBible.ipa" ;; *upload) echo upload-called > "$STUB_DIR/upload-called" ;; esac ;;
esac
exit 0
EOS
cat > "$T/bin/xcodegen" <<'EOS'
#!/usr/bin/env bash
echo "xcodegen $*" >> "$STUB_DIR/calls"
EOS
cat > "$T/bin/codesign" <<'EOS'
#!/usr/bin/env bash
echo "codesign $*" >> "$STUB_DIR/calls"
case "$*" in
*"--entitlements"*"--force"*|*"--force"*"--entitlements"*)
    f=$(echo "$*" | sed -n 's/.*--entitlements \([^ ]*\).*/\1/p'); cp "$f" "$STUB_DIR/signed.entitlements" ;;
*"-dv"*)
    echo "Identifier=${STUB_IDENT:-org.andbible.ios}" >&2
    echo "Authority=${STUB_AUTH:-Apple Distribution: Tuomas (X6A6CDK8T7)}" >&2
    echo "TeamIdentifier=${STUB_TEAM:-X6A6CDK8T7}" >&2 ;;
*"-d --entitlements"*)
    [ "${STUB_NO_ICLOUD:-0}" = 1 ] || echo '<string>iCloud.org.andbible.ios</string>'
    [ "${STUB_GTA:-false}" = absent ] || echo "get-task-allow=${STUB_GTA:-false}" ;;
esac
exit 0
EOS
# plutil: -p <file|-> prints stdin/file; -extract KEY raw -o - FILE reads "KEY=value" lines from FILE
cat > "$T/bin/plutil" <<'EOS'
#!/usr/bin/env bash
case "$1" in
-p) if [ "$2" = - ]; then sed 's/get-task-allow=/"get-task-allow" => /'; else cat "$2"; fi ;;
-extract) grep "^$2=" "${@: -1}" | head -1 | cut -d= -f2- ;;
esac
EOS
cat > "$T/bin/unzip" <<'EOS'
#!/usr/bin/env bash
mkdir -p Payload/AndBible.app
printf 'CFBundleShortVersionString=%s\nCFBundleVersion=%s\n' "${STUB_IPA_VERSION:-6.0.1250}" "${STUB_IPA_BUILD:-2026.1105.120000}" > Payload/AndBible.app/Info.plist
EOS
cat > "$T/bin/hdiutil" <<'EOS'
#!/usr/bin/env bash
echo "hdiutil $*" >> "$STUB_DIR/detach.calls"; [ "$1" = info ] && echo 0; exit 0
EOS
cat > "$T/bin/diskutil" <<'EOS'
#!/usr/bin/env bash
echo "diskutil $*" >> "$STUB_DIR/detach.calls"; exit 0
EOS
cat > "$T/bin/npm" <<'EOS'
#!/usr/bin/env bash
echo "npm $*" >> "$STUB_DIR/calls"; echo "npm ran"; exit 0
EOS
cat > "$T/bin/shasum" <<'EOS'
#!/usr/bin/env bash
echo "0123456789abcdef  $1"
EOS
chmod +x "$T/bin/"*

mk_tree() {   # fresh Mac-like tree with synced files, key volume and matching xcconfig
    W="$T/work"; rm -rf "$W"; STUB_DIR="$T/stub"; rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    mkdir -p "$W/app/bibleview-js/node_modules" "$W/iosApp/Config" "$W/iosApp/iosApp" "$W/vol"
    : > "$W/app/bibleview-js/package-lock.json"
    cp "$REAL_REPO/iosApp/iosApp/iosApp.entitlements" "$W/iosApp/iosApp/"
    printf 'MARKETING_VERSION = %s\nAND_BIBLE_COMMIT_HASH = %s\nAND_BIBLE_COMMIT_OVERRIDE = \n' "$VERSION" "$FULL" > "$W/iosApp/Config/Version.xcconfig"
    echo secret > "$W/vol/AuthKey_$KEYID.p8"
    export STUB_DIR
    unset STUB_ARCHIVE_RC STUB_IDENT STUB_AUTH STUB_TEAM STUB_NO_ICLOUD STUB_GTA STUB_IPA_VERSION STUB_IPA_BUILD
}
run_args() {   # run_args <args...> verbatim
    cd "$W" || return 1
    ANDBIBLE_TEST_EXPORT_PATH="$T/bin:/usr/bin:/bin" PATH="$T/bin:$PATH" bash "$SCRIPT" "$@" > "$T/out.txt" 2>&1
    RC=$?
}
run() {   # run [dest] -> rc in $RC, output in $OUT_TXT
    cd "$W" || return 1
    ANDBIBLE_TEST_EXPORT_PATH="$T/bin:/usr/bin:/bin" PATH="$T/bin:$PATH" bash "$SCRIPT" "$W/vol" "$KEYID" issuer-0000 X6A6CDK8T7 "$BUILD" "$VERSION" "$COMMIT" "${1:-upload}" > "$T/out.txt" 2>&1
    RC=$?
}
SUM() { echo "$W/build/release/testflight-$BUILD/summary.txt"; }
called() { grep -q "$1" "$STUB_DIR/calls" 2>/dev/null; }

test_xcconfig_mismatch_refuses() {
    mk_tree; sed -i 's/^AND_BIBLE_COMMIT_HASH = .*/AND_BIBLE_COMMIT_HASH = 1111111111222222222233333333334444444444/' "$W/iosApp/Config/Version.xcconfig"
    run; [ "$RC" != 0 ] || return "$(fail "commit mismatch accepted")"
    called xcodebuild && return "$(fail "xcodebuild ran after commit mismatch")"
    mk_tree; sed -i 's/^MARKETING_VERSION = .*/MARKETING_VERSION = 9.9.9/' "$W/iosApp/Config/Version.xcconfig"
    run; [ "$RC" != 0 ] || return "$(fail "version mismatch accepted")"
    called xcodebuild && return "$(fail "xcodebuild ran after version mismatch")"
    mk_tree; sed -i 's/^AND_BIBLE_COMMIT_HASH = .*/AND_BIBLE_COMMIT_HASH = /' "$W/iosApp/Config/Version.xcconfig"
    run; [ "$RC" != 0 ] || return "$(fail "empty commit hash accepted")"
    tail -1 "$(SUM)" | grep -q '^result=FAIL' || return "$(fail "no FAIL result: $(cat "$(SUM)")")"
}
test_xcconfig_prefix_commit_accepted() {
    mk_tree; run export; [ "$RC" = 0 ] || return "$(fail "matching full-hash xcconfig refused: $(cat "$T/out.txt")")"
    mk_tree; sed -i "s/^AND_BIBLE_COMMIT_HASH = .*/AND_BIBLE_COMMIT_HASH = $COMMIT/" "$W/iosApp/Config/Version.xcconfig"
    run export; [ "$RC" = 0 ] || return "$(fail "10-char xcconfig hash refused")"
}
test_missing_key_refuses() {
    mk_tree; rm "$W/vol/AuthKey_$KEYID.p8"; run
    [ "$RC" != 0 ] || return "$(fail "missing key accepted")"
    called xcodebuild && return "$(fail "xcodebuild ran without key")"
    called npm && return "$(fail "npm ran without key")"
    [ -s "$STUB_DIR/detach.calls" ] || return "$(fail "volume not detached")"
}
test_entitlements_expanded() {
    mk_tree; run; [ "$RC" = 0 ] || return "$(fail "run failed: $(cat "$T/out.txt")")"
    [ -f "$STUB_DIR/signed.entitlements" ] || return "$(fail "codesign --entitlements not called")"
    grep -q 'iCloud.org.andbible.ios' "$STUB_DIR/signed.entitlements" || return "$(fail "no iCloud container")"
    grep -q '\$(' "$STUB_DIR/signed.entitlements" && return "$(fail "unexpanded variable")"
    return 0
}
test_ipa_check_failure_blocks_upload() {
    local v
    for v in "STUB_NO_ICLOUD=1" "STUB_GTA=true" "STUB_IDENT=org.other.app" "STUB_TEAM=ZZZZZZZZZZ" \
             "STUB_AUTH=Apple Development: x" "STUB_IPA_VERSION=1.0.0" "STUB_IPA_BUILD=1"; do
        mk_tree; export "$v"; run
        [ "$RC" != 0 ] || return "$(fail "$v accepted")"
        [ ! -e "$STUB_DIR/upload-called" ] || return "$(fail "$v: upload called")"
        grep -q 'exportPath [^ ]*upload' "$STUB_DIR/xcodebuild.argv" && return "$(fail "$v: upload export called")"
        tail -1 "$(SUM)" | grep -q '^result=FAIL' || return "$(fail "$v: no FAIL result")"
        unset "${v%%=*}"
    done
    return 0
}
test_upload_happy_path() {
    mk_tree; run; [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out.txt")")"
    [ -e "$STUB_DIR/upload-called" ] || return "$(fail "no upload")"
    [ "$(tail -1 "$(SUM)")" = "result=PASS upload" ] || return "$(fail "summary: $(cat "$(SUM)")")"
    local k; for k in version=$VERSION build=$BUILD commit=$COMMIT archive_s= export_s= upload_s= checks=; do
        grep -q "^$k" "$(SUM)" || return "$(fail "summary lacks $k")"
    done
    [ -d "$W/build/release/AndBible-$BUILD.xcarchive" ] || return "$(fail "archive path")"
    local f; for f in bibleview.log archive.log export.log upload.log; do
        [ -f "$W/build/release/testflight-$BUILD/$f" ] || return "$(fail "no $f")"
    done
    grep -q 'CURRENT_PROJECT_VERSION='"$BUILD" "$STUB_DIR/xcodebuild.argv" || return "$(fail "build number not passed")"
}
test_export_mode_never_uploads() {
    mk_tree; run export; [ "$RC" = 0 ] || return "$(fail "rc=$RC")"
    [ ! -e "$STUB_DIR/upload-called" ] || return "$(fail "upload called")"
    grep -q 'exportPath [^ ]*upload' "$STUB_DIR/xcodebuild.argv" && return "$(fail "upload export called")"
    [ "$(tail -1 "$(SUM)")" = "result=PASS export" ] || return "$(fail "summary: $(tail -1 "$(SUM)")")"
    [ ! -e "$W/build/release/testflight-$BUILD/upload.log" ] || return "$(fail "upload.log in export mode")"
}
test_volume_detached_on_failure() {
    mk_tree; STUB_ARCHIVE_RC=1 run; [ "$RC" != 0 ] || return "$(fail "archive failure accepted")"
    grep -q 'detach\|eject' "$STUB_DIR/detach.calls" || return "$(fail "no detach after failure")"
    mk_tree; run; grep -q 'detach\|eject' "$STUB_DIR/detach.calls" || return "$(fail "no detach after success")"
}
test_summary_written_on_failure() {
    mk_tree; STUB_ARCHIVE_RC=1 run
    [ -f "$(SUM)" ] || return "$(fail "no summary")"
    [ "$(tail -1 "$(SUM)")" = "result=FAIL archive" ] || return "$(fail "last line: $(tail -1 "$(SUM)")")"
    grep -q "^build=$BUILD" "$(SUM)" || return "$(fail "no build line")"
}
test_get_task_allow_absent_passes() {
    mk_tree; STUB_GTA=absent run export; [ "$RC" = 0 ] || return "$(fail "absent get-task-allow refused: $(cat "$T/out.txt")")"
    mk_tree; STUB_GTA=false run export; [ "$RC" = 0 ] || return "$(fail "false get-task-allow refused")"
}
test_empty_args_refuse() {
    local i args
    for i in 1 2 3 4 5 6 7; do
        mk_tree; args=("$W/vol" "$KEYID" issuer-0000 X6A6CDK8T7 "$BUILD" "$VERSION" "$COMMIT"); args[$((i-1))]=""
        run_args "${args[@]}" export
        [ "$RC" != 0 ] || return "$(fail "empty arg $i accepted")"
        called xcodebuild && return "$(fail "xcodebuild ran with empty arg $i")"
    done
    return 0
}
test_inexact_identifier_refused() {
    mk_tree; STUB_IDENT=org.andbible.ios.extra run; [ "$RC" != 0 ] || return "$(fail "identifier suffix accepted")"
    [ ! -e "$STUB_DIR/upload-called" ] || return "$(fail "upload called")"
}
test_test_seam_warns() {
    mk_tree; run export; grep -q 'WARNING: ANDBIBLE_TEST_EXPORT_PATH' "$T/out.txt" || return "$(fail "no warning")"
}
test_bad_destination_refuses() {
    mk_tree; run bogus; [ "$RC" != 0 ] || return "$(fail "bogus destination accepted")"
    called xcodebuild && return "$(fail "xcodebuild ran")"
    return 0
}

for t in $(declare -F | awk '{print $3}' | grep '^test_'); do
    [ -z "$FILTER" ] || [[ "$t" == *"$FILTER"* ]] || continue
    cur=$t; rm -f "$T/failflag"
    "$t"; rc=$?
    if [ "$rc" = 0 ] && [ ! -e "$T/failflag" ]; then pass=$((pass+1)); echo "PASS $t"; else failn=$((failn+1)); echo "FAILED $t"; fi
done
echo "passed=$pass failed=$failn"
[ "$failn" = 0 ]
