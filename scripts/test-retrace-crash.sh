#!/usr/bin/env bash
# Tests for scripts/retrace-crash.sh on a real obfuscated stack trace: the fixture log is logcat from an R8-obfuscated
# standardGithubRelease build (#4160) on the emulator, and the fixture mapping is the part of that build's mapping.txt
# the trace needs (retracing it gives the same output as the full 136 MB mapping). gh is a stub; retrace is the real
# Android SDK cmdline-tools one.
# Run: bash scripts/test-retrace-crash.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/retrace-crash.sh"
FIX="$HERE/tests/fixtures/retrace"
MAP_ID=a2ce5146482e6c1fea67ebc0f86b412fce8873df34e9ae05d65183f4bdb639a9
T="$(mktemp -d)"
FILTER="${1:-}"
trap 'rm -rf "$T"' EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }

# A decoy mapping of another build: same classes, another map id. Picking it would retrace to the wrong names.
zcat "$FIX/mapping-subset.txt.gz" | sed "s/^# pg_map_id: .*/# pg_map_id: 00decoy/; s/net\.bible\.service\.sword\.DocumentNotFound/WRONG.Build/" \
    > "$T/decoy.txt"

cat > "$T/gh" <<'EOS'
#!/usr/bin/env bash
printf 'gh %s\n' "$*" >> "$STUB_DIR/argv"
case "$1 $2" in
"release list") printf 'beta-1278\nbeta-1277\nbeta-12770\n' ;;
"release download")
    dir=; while [ $# -gt 0 ]; do [ "$1" = -D ] && dir=$2; shift; done
    mkdir -p "$dir"; cp "$STUB_FIX/mapping-subset.txt.gz" "$dir/mapping-andbible-beta-1277.txt.gz" ;;
*) exit 1 ;;
esac
EOS
chmod +x "$T/gh"
export STUB_DIR="$T/stub" STUB_FIX="$FIX"

run() {   # run <args>: output in $T/out, stderr in $T/err, status in $RC
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    GH="$T/gh" ANDBIBLE_MAPPINGS_DIR="${MAPDIR-$T/none}" bash "$SCRIPT" "$@" > "$T/out" 2> "$T/err"
    RC=$?
}
expect_retraced() {
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/err")")"
    local want
    for want in "net.bible.service.sword.DocumentNotFound: John 3:16 was not found in document Kretzmann." \
        "at net.bible.android.control.page.CurrentPageBase.getPageContent\$suspendImpl(CurrentPageBase.kt:199)" \
        "at net.bible.android.control.page.window.Window.fetchDocument(Window.kt:468)" \
        "E CurrentPage: Error getting bible text"; do
        grep -qF -- "$want" "$T/out" || return "$(fail "missing '$want' in: $(head -5 "$T/out")")"
    done
    ! grep -q 'r8-map-id\|WRONG' "$T/out" || return "$(fail "obfuscated or wrong frames left: $(grep 'r8-map-id\|WRONG' "$T/out" | head -2)")"
}

test_picks_the_mapping_by_map_id() {
    MAPDIR="$T/arch"; mkdir -p "$MAPDIR/1276/standardGithub" "$MAPDIR/1277/standardGithub"
    cp "$T/decoy.txt" "$MAPDIR/1276/standardGithub/mapping.txt"
    cp "$FIX/mapping-subset.txt.gz" "$MAPDIR/1277/standardGithub/mapping.txt.gz"
    run "$FIX/obfuscated-logcat.txt"
    expect_retraced || return 1
    grep -q "$MAP_ID" "$T/err" || return "$(fail "the chosen mapping is not named: $(cat "$T/err")")"
}
test_explicit_mapping_gz_log_and_stdin() {
    gzip -c "$FIX/obfuscated-logcat.txt" > "$T/logcat.txt.gz"
    run --mapping "$FIX/mapping-subset.txt.gz" "$T/logcat.txt.gz"
    expect_retraced || return 1
    GH="$T/gh" ANDBIBLE_MAPPINGS_DIR="$T/none" bash "$SCRIPT" --mapping "$FIX/mapping-subset.txt.gz" - \
        < "$FIX/obfuscated-logcat.txt" > "$T/out" 2> "$T/err"; RC=$?
    expect_retraced
}
test_explicit_mapping_of_another_build_is_refused() {
    run --mapping "$T/decoy.txt" "$FIX/obfuscated-logcat.txt"
    [ "$RC" != 0 ] && grep -q "$MAP_ID" "$T/err" || return "$(fail "rc=$RC: $(cat "$T/err")")"
}
test_no_matching_mapping_names_the_map_id() {
    MAPDIR="$T/arch2"; mkdir -p "$MAPDIR"; cp "$T/decoy.txt" "$MAPDIR/mapping.txt"
    run "$FIX/obfuscated-logcat.txt"
    [ "$RC" != 0 ] && grep -q "$MAP_ID" "$T/err" && grep -q -- '--version-code' "$T/err" \
        || return "$(fail "rc=$RC: $(cat "$T/err")")"
}
test_version_code_fetches_the_release_mappings() {
    MAPDIR="$T/arch3"; mkdir -p "$MAPDIR"
    run --version-code 1277 "$FIX/obfuscated-logcat.txt"
    expect_retraced || return 1
    grep -q 'gh release download beta-1277 ' "$STUB_DIR/argv" || return "$(fail "argv: $(cat "$STUB_DIR/argv")")"
    [ -f "$MAPDIR/1277/mapping-andbible-beta-1277.txt.gz" ] || return "$(fail "not archived: $(find "$MAPDIR")")"
    run --version-code 1277 "$FIX/obfuscated-logcat.txt"   # second time: from the archive, no download
    expect_retraced || return 1
    ! grep -qs 'release download' "$STUB_DIR/argv" || return "$(fail "downloaded again")"
}
test_version_code_without_release_fails() {
    MAPDIR="$T/arch4"; mkdir -p "$MAPDIR"
    run --version-code 999 "$FIX/obfuscated-logcat.txt"
    [ "$RC" != 0 ] && grep -q 'no GitHub release' "$T/err" || return "$(fail "rc=$RC: $(cat "$T/err")")"
}
test_log_without_map_id_needs_a_mapping() {
    sed 's/r8-map-id-[0-9a-f]*/SourceFile/' "$FIX/obfuscated-logcat.txt" > "$T/plain.txt"
    MAPDIR="$T/arch5"; mkdir -p "$MAPDIR"
    run "$T/plain.txt"
    [ "$RC" != 0 ] && grep -q -- '--mapping' "$T/err" || return "$(fail "rc=$RC: $(cat "$T/err")")"
    run --mapping "$FIX/mapping-subset.txt.gz" "$T/plain.txt"
    [ "$RC" = 0 ] && grep -qF 'CurrentPageBase.kt:199' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/err")")"
}

test_archived_mapping_is_found() {
    local b="$T/appbuild"; mkdir -p "$b/outputs/mapping/standardSamsungRelease" "$b/outputs/mapping/standardHuaweiRelease"
    zcat "$FIX/mapping-subset.txt.gz" > "$b/outputs/mapping/standardSamsungRelease/mapping.txt"
    cp "$T/decoy.txt" "$b/outputs/mapping/standardHuaweiRelease/mapping.txt"
    MAPDIR="$T/arch6"
    APP_BUILD_DIR="$b" ANDBIBLE_MAPPINGS_DIR="$MAPDIR" bash "$HERE/archive-r8-mappings.sh" > "$T/archived" 2> "$T/err" \
        || return "$(fail "archive failed: $(cat "$T/err")")"
    local vc commit; vc=$(bash -c ". '$HERE/lib-version.sh'; version_code"); commit=$(git -C "$HERE/.." rev-parse --short=10 HEAD)
    grep -qx "$MAPDIR/$vc/standardSamsungRelease/mapping-$commit.txt.gz" "$T/archived" \
        && grep -qx "$MAPDIR/$vc/standardHuaweiRelease/mapping-$commit.txt.gz" "$T/archived" \
        || return "$(fail "archived: $(cat "$T/archived")")"
    run "$FIX/obfuscated-logcat.txt"
    expect_retraced || return 1
    grep -q standardSamsungRelease "$T/err" || return "$(fail "picked: $(cat "$T/err")")"
}
test_archive_without_mappings_fails() {
    mkdir -p "$T/emptybuild"
    APP_BUILD_DIR="$T/emptybuild" ANDBIBLE_MAPPINGS_DIR="$T/arch7" bash "$HERE/archive-r8-mappings.sh" > /dev/null 2> "$T/err" \
        && return "$(fail "archived nothing but succeeded")"
    grep -q 'no mapping.txt' "$T/err" || return "$(fail "$(cat "$T/err")")"
}

for t in $(declare -F | awk '{print $3}' | grep '^test_'); do
    [[ -z "$FILTER" || "$t" == *"$FILTER"* ]] || continue
    cur=$t; rm -f "$T/failflag"
    if "$t" && [ ! -e "$T/failflag" ]; then echo "ok   $t"; pass=$((pass + 1)); else echo "FAIL $t"; failn=$((failn + 1)); fi
done
echo "$pass passed, $failn failed"
[ "$failn" = 0 ]
