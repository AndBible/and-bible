#!/usr/bin/env bash
# Tests for scripts/release/android.sh without Gradle, Play or a YubiKey: gradle-release.sh, bundletool and fastlane are
# stubs (GRADLE_RELEASE, BUNDLETOOL_DUMP, FASTLANE); keytool and jarsigner are real, on throwaway keystores, so the
# certificate comparison is exercised for real. The repo is a temp git repo with version.properties.
# Run: bash scripts/release/tests/test-android.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REAL_REPO="$(cd "$HERE/../../.." && pwd)"
SCRIPT="$REAL_REPO/scripts/release/android.sh"
T="$(mktemp -d)"
FILTER="${1:-}"
trap 'rm -rf "$T"' EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }
SENTINEL="AND-SENTINEL-$RANDOM$RANDOM$RANDOM"
PW="pw$RANDOM$RANDOM"

mkdir -p "$T/sess" "$T/foreign" "$T/jar"
genkey() { keytool -genkeypair -keystore "$1" -storetype PKCS12 -storepass "$PW" -keypass "$PW" -alias andbible \
    -keyalg RSA -keysize 2048 -validity 30 -dname "CN=$2" >/dev/null 2>&1; }
genkey "$T/sess/andbible.p12" release && genkey "$T/foreign/andbible.p12" foreign || { echo "keytool failed" >&2; exit 1; }
printf 'storeFile=andbible.p12\nstorePassword=%s\nkeyAlias=andbible\nkeyPassword=%s\n' "$PW" "$PW" > "$T/sess/keystore.properties"
printf '{"type": "service_account", "private_key": "%s"}\n' "$SENTINEL" > "$T/sess/play-service-account.json"
echo "$PW" > "$T/pwfile"
# Two fake "AABs": jars signed with the release key and with a foreign key.
echo x > "$T/jar/BundleConfig.pb"; (cd "$T/jar" && jar cf ../good.aab BundleConfig.pb)
cp "$T/good.aab" "$T/foreign.aab"
jarsigner -keystore "$T/sess/andbible.p12" -storepass:file "$T/pwfile" "$T/good.aab" andbible >/dev/null 2>&1
jarsigner -keystore "$T/foreign/andbible.p12" -storepass:file "$T/pwfile" "$T/foreign.aab" andbible >/dev/null 2>&1
rm "$T/pwfile"

cat > "$T/gradle-release" <<'EOS'
#!/usr/bin/env bash
printf 'gradle-release %s props=%s\n' "$*" "$ANDBIBLE_SIGNING_PROPERTIES" >> "$STUB_DIR/argv"
d="$REPO_ROOT/app/build/outputs/bundle/standardGoogleplayRelease"; mkdir -p "$d"
cp "${STUB_AAB:?}" "$d/app-standard-googleplay-release.aab"
[ -z "${STUB_GRADLE_COMMIT:-}" ] || git -C "$REPO_ROOT" -c user.email=t@t -c user.name=t commit -q --allow-empty -m during
exit "${STUB_GRADLE_RC:-0}"
EOS
cat > "$T/bundletool" <<'EOS'
#!/usr/bin/env bash
printf 'bundletool %s\n' "$*" >> "$STUB_DIR/argv"
case "$2" in *versionCode) echo "${STUB_VC:-1261}" ;; *versionName) echo "${STUB_VN:-6.0.1261-beta}" ;; *) exit 1 ;; esac
EOS
cat > "$T/fastlane" <<'EOS'
#!/usr/bin/env bash
printf 'fastlane %s\n' "$*" >> "$STUB_DIR/argv"
[ -z "${STUB_FASTLANE_OUT:-}" ] || echo "$STUB_FASTLANE_OUT"
exit "${STUB_FASTLANE_RC:-0}"
EOS
chmod +x "$T/gradle-release" "$T/bundletool" "$T/fastlane"

mk_repo() {
    R="$T/repo"; rm -rf "$R" "$STUB_DIR"; mkdir -p "$STUB_DIR"
    git init -q "$R"; cd "$R" || return 1
    git config user.email t@t; git config user.name t
    mkdir -p scripts; cp "$REAL_REPO/scripts/lib-version.sh" scripts/
    printf 'versionMajorMinor=6.0\nversionCode=1261\nversionNameSuffix=-beta\n' > version.properties
    printf '.local/\napp/build/\n' > .gitignore
    git add -A; git commit -q -m init
}
run() {   # run <args>: output in $T/out, status in $RC
    REPO_ROOT="$R" RELEASE_SECRETS_DIR="${SESS-$T/sess}" GRADLE_RELEASE="$T/gradle-release" BUNDLETOOL_DUMP="$T/bundletool" \
        FASTLANE="$T/fastlane" STUB_AAB="${STUB_AAB:-$T/good.aab}" bash "$SCRIPT" "$@" > "$T/out" 2>&1
    RC=$?
}
OUT() { echo "$R/.local/release/1261/android"; }

test_build_dry_run_lines() {
    mk_repo; SESS="" run build --dry-run
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q "ANDBIBLE_SIGNING_PROPERTIES=<session>/keystore.properties .*gradle-release bundleStandardGoogleplayRelease" "$T/out" \
        || return "$(fail "gradle line: $(cat "$T/out")")"
    grep -q "andbible-6.0.1261-beta.aab" "$T/out" || return "$(fail "copy line: $(cat "$T/out")")"
    [ ! -e "$STUB_DIR/argv" ] || return "$(fail "something ran: $(cat "$STUB_DIR/argv")")"
}
test_build_writes_artifact_env() {
    mk_repo; run build
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local o; o=$(OUT)
    [ -f "$o/andbible-6.0.1261-beta.aab" ] || return "$(fail "no aab")"
    grep -qx "COMMIT=$(git rev-parse --short=10 HEAD)" "$o/artifact.env" && grep -qx VERSION_CODE=1261 "$o/artifact.env" \
        && grep -qx VERSION_NAME=6.0.1261-beta "$o/artifact.env" \
        && grep -qx "SHA256=$(sha256sum "$o/andbible-6.0.1261-beta.aab" | cut -d' ' -f1)" "$o/artifact.env" \
        || return "$(fail "artifact.env: $(cat "$o/artifact.env")")"
    grep -q "props=$T/sess/keystore.properties" "$STUB_DIR/argv" || return "$(fail "props path not passed by env")"
    grep -q "gradle-release bundleStandardGoogleplayRelease props" "$STUB_DIR/argv" || return "$(fail "gradle argv")"
}
test_upload_targets_internal_with_all_skips() {
    mk_repo; run build; run upload
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local f; f=$(grep '^fastlane ' "$STUB_DIR/argv")
    [ "$(grep -c '^fastlane ' "$STUB_DIR/argv")" = 1 ] || return "$(fail "fastlane calls: $f")"
    local want
    for want in "supply" "--package_name net.bible.android.activity" "--track internal" "--release_status completed" \
        "--aab $(OUT)/andbible-6.0.1261-beta.aab" "--json_key $T/sess/play-service-account.json" \
        "--skip_upload_metadata true" "--skip_upload_changelogs true" "--skip_upload_images true" \
        "--skip_upload_screenshots true" "--skip_upload_apk true"; do
        [[ " $f " == *" $want "* ]] || return "$(fail "missing '$want' in: $f")"
    done
    [ "$(grep -o -- '--track [a-z]*' <<<"$f" | sort -u)" = "--track internal" ] || return "$(fail "other track: $f")"
    [ -f "$(OUT)/upload.log" ] || return "$(fail "no upload.log")"
}
test_upload_argv_has_no_secret_content() {
    mk_repo; run build; run upload
    [ "$RC" = 0 ] || return "$(fail "rc=$RC")"
    grep -rq -e "$SENTINEL" -e "$PW" "$STUB_DIR" "$T/out" "$R/.local" && return "$(fail "secret content leaked")"
    true
}
test_build_fails_on_foreign_cert() {
    mk_repo; STUB_AAB="$T/foreign.aab" run build
    [ "$RC" != 0 ] && grep -qi 'certificate' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ ! -e "$(OUT)/artifact.env" ] || return "$(fail "artifact.env written for a foreign cert")"
}
test_build_fails_on_unsigned() {
    mk_repo; (cd "$T/jar" && jar cf ../unsigned.aab BundleConfig.pb); STUB_AAB="$T/unsigned.aab" run build
    [ "$RC" != 0 ] && grep -qi 'signed\|certificate' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
test_build_fails_on_version_mismatch() {
    mk_repo; STUB_VC=1260 run build
    [ "$RC" != 0 ] && grep -q 'versionCode' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    STUB_VN=6.0.1261 run build
    [ "$RC" != 0 ] && grep -q 'versionName' "$T/out" || return "$(fail "name mismatch rc=$RC: $(cat "$T/out")")"
}
test_build_refuses_head_moved() {
    mk_repo; STUB_GRADLE_COMMIT=1 run build
    [ "$RC" != 0 ] && grep -qi 'HEAD' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ ! -e "$(OUT)/artifact.env" ] || return "$(fail "artifact.env written")"
}
test_build_refuses_dirty_tree() {
    mk_repo; echo x > stray.txt; run build
    [ "$RC" != 0 ] && grep -q 'not clean' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ ! -e "$STUB_DIR/argv" ] || return "$(fail "gradle ran")"
}
test_build_needs_session() {
    mk_repo; SESS="" run build
    [ "$RC" != 0 ] || return "$(fail "built without a secrets session")"
    [ ! -e "$STUB_DIR/argv" ] || return "$(fail "gradle ran")"
}
test_gradle_failure_fails() {
    mk_repo; STUB_GRADLE_RC=1 run build
    [ "$RC" != 0 ] && grep -q 'gradle.log' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
test_reuse_ok_same_commit() {
    mk_repo; run build; run reusable
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
test_reuse_refused_on_commit_change() {
    mk_repo; run build; git commit -q --allow-empty -m next; run reusable
    [ "$RC" != 0 ] || return "$(fail "reused across commits")"
}
test_reuse_refused_on_version_change() {
    mk_repo; run build; sed -i 's/versionCode=1261/versionCode=1262/' version.properties; git commit -qam bump; run reusable
    [ "$RC" != 0 ] || return "$(fail "reused across a version bump")"
}
test_reuse_refused_on_sha_mismatch() {
    mk_repo; run build; echo extra >> "$(OUT)/andbible-6.0.1261-beta.aab"; run reusable
    [ "$RC" != 0 ] || return "$(fail "reused a changed AAB")"
    run upload
    [ "$RC" != 0 ] && grep -q 'SHA-256' "$T/out" || return "$(fail "uploaded a changed AAB: rc=$RC")"
    ! grep -q '^fastlane' "$STUB_DIR/argv" || return "$(fail "fastlane ran")"
}
test_reuse_refused_without_artifact() {
    mk_repo; run reusable
    [ "$RC" != 0 ] || return "$(fail "reusable without a build")"
}
test_play_duplicate_version_message() {
    mk_repo; run build
    STUB_FASTLANE_RC=1 STUB_FASTLANE_OUT="Google Api Error: Invalid request - Version code 1261 has already been used." run upload
    [ "$RC" != 0 ] && grep -q 'Play already has versionCode 1261' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
# A minimal proto-format AndroidManifest (aapt2 Resources.proto XmlNode) inside a zip, read by aab-manifest.py.
test_aab_manifest_reads_proto_manifest() {
    python3 -I - "$T/proto.aab" <<'EOF2'
import sys, zipfile
def varint(n):
    out = b""
    while True:
        b = n & 0x7F; n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n: return out
def fld(num, payload): return varint(num << 3 | 2) + varint(len(payload)) + payload
NS = b"http://schemas.android.com/apk/res/android"
def attr(name, value): return fld(4, fld(1, NS) + fld(2, name) + fld(3, value))
elem = fld(3, b"manifest") + attr(b"versionCode", b"1261") + attr(b"versionName", b"6.0.1261-beta") + attr(b"package", b"x")
elem += fld(5, fld(1, fld(3, b"application") + attr(b"versionCode", b"999")))   # a child element must not win
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("base/manifest/AndroidManifest.xml", fld(1, elem))
EOF2
    local py="$REAL_REPO/scripts/release/aab-manifest.py" c n
    c=$(python3 -I "$py" "$T/proto.aab" versionCode) && n=$(python3 -I "$py" "$T/proto.aab" versionName) \
        || return "$(fail "reader failed")"
    [ "$c" = 1261 ] && [ "$n" = 6.0.1261-beta ] || return "$(fail "got '$c' '$n'")"
    python3 -I "$py" "$T/proto.aab" versionFoo >/dev/null 2>&1 && return "$(fail "missing attribute accepted")"
    true
}

export STUB_DIR="$T/stub"
for t in $(declare -F | awk '{print $3}' | grep '^test_'); do
    [[ -z "$FILTER" || "$t" == *"$FILTER"* ]] || continue
    cur=$t; rm -f "$T/failflag"
    "$t" >/dev/null 2>"$T/err" || : > "$T/failflag"
    [ -s "$T/err" ] && cat "$T/err"
    if [ -e "$T/failflag" ]; then failn=$((failn + 1)); echo "not ok $t"; else pass=$((pass + 1)); echo "ok   $t"; fi
done
echo "$pass passed, $failn failed"
[ "$failn" = 0 ]
