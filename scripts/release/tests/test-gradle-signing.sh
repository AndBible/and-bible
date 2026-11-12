#!/usr/bin/env bash
# Tests for explicit signing opt-in in app/build.gradle.kts and for scripts/release/gradle-release.sh.
# Each Gradle case configures the real project with --no-daemon (minutes each): the controller runs this, not a
# subagent. Throwaway PKCS12 + properties in a temp dir; a gpg stub on PATH logs calls and returns test credentials.
# Run: bash scripts/release/tests/test-gradle-signing.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
T="$(mktemp -d)"
FILTER="${1:-}"
trap 'rm -rf "$T"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }
PW="pw$RANDOM$RANDOM"

mkdir -p "$T/bin" "$T/sess"
cat > "$T/bin/gpg" <<'EOS'
#!/usr/bin/env bash
printf 'gpg %s\n' "$*" >> "$STUB_DIR/gpg.log"
cat "$GPG_TEST_PROPERTIES"
EOS
cat > "$T/gradlew-stub" <<'EOS'
#!/usr/bin/env bash
printf 'gradlew %s\n' "$*" >> "$STUB_DIR/gradlew.log"
EOS
chmod +x "$T/bin/gpg" "$T/gradlew-stub"
keytool -genkeypair -keystore "$T/sess/andbible.p12" -storetype PKCS12 -storepass "$PW" -keypass "$PW" -alias andbible \
    -keyalg RSA -keysize 2048 -validity 30 -dname "CN=test" >/dev/null 2>&1 || { echo "keytool failed" >&2; exit 1; }
printf 'storeFile=andbible.p12\nstorePassword=%s\nkeyAlias=andbible\nkeyPassword=%s\n' "$PW" "$PW" > "$T/sess/keystore.properties"
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=andbible\nkeyPassword=%s\n' "$T/sess/andbible.p12" "$PW" "$PW" > "$T/gpg.properties"
export GPG_TEST_PROPERTIES="$T/gpg.properties"
cat > "$T/signing-probe.gradle" <<'EOS'
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.tasks.register('signingReleaseProbe') { dependsOn 'signingReport' }
    }
}
EOS

gradle() {   # gradle <properties path> <args...>: output in $T/out, status in $RC
    local props="$1"; shift
    (cd "${GRADLE_TEST_REPO:-$REPO}" && GIT_DIR="$REPO/.git" PATH="$T/bin:$PATH" ANDBIBLE_SIGNING_PROPERTIES="$props" ./gradlew --no-daemon "$@") > "$T/out" 2>&1
    RC=$?
}

test_release_task_without_opt_in_never_calls_gpg() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    [ -f "$REPO/keystore.properties.gpg" ] || return "$(fail "encrypted fixture is missing")"
    gradle "" -q -I "$T/signing-probe.gradle" :app:signingReleaseProbe
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(tail -30 "$T/out")")"
    [ ! -s "$STUB_DIR/gpg.log" ] || return "$(fail "gpg called without opt-in: $(cat "$STUB_DIR/gpg.log")")"
    local block
    block=$(awk '/^Variant: standardGithubRelease$/{f=1} f&&/^-+$/{exit} f' "$T/out")
    printf '%s\n' "$block" | grep -qx 'Config: null' || return "$(fail "release was not unsigned: $block")"
}
test_explicit_opt_in_decrypts_once() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    gradle "" -q -Pandbible.sign=true :app:signingReport
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(tail -30 "$T/out")")"
    [ "$(wc -l < "$STUB_DIR/gpg.log")" = 1 ] || return "$(fail "expected exactly one gpg call")"
    grep -qx "gpg --decrypt --quiet $REPO/keystore.properties.gpg" "$STUB_DIR/gpg.log" || return "$(fail "wrong decrypt arguments")"
    grep -qx "Store: $T/sess/andbible.p12" "$T/out" || return "$(fail "decrypted store not loaded")"
}
test_false_opt_in_never_calls_gpg() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    gradle "" -q -Pandbible.sign=false -I "$T/signing-probe.gradle" :app:signingReleaseProbe
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(tail -30 "$T/out")")"
    [ ! -s "$STUB_DIR/gpg.log" ] || return "$(fail "gpg called with sign=false")"
}
test_missing_encrypted_properties_fails_only_with_opt_in() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    # Copy tracked sources, not build outputs or secrets; never move the real encrypted file.
    local copy="$T/repo"
    mkdir -p "$copy"
    (cd "$REPO" && git ls-files --recurse-submodules -z |
        tar --exclude=keystore.properties.gpg --null -T - -cf -) |
        tar -xf - -C "$copy" || return "$(fail "cannot create isolated project")"
    if [ -f "$REPO/local.properties" ]; then cp "$REPO/local.properties" "$copy/"; fi
    GRADLE_TEST_REPO="$copy" gradle "" -q -I "$T/signing-probe.gradle" :app:signingReleaseProbe
    local unsigned_rc="$RC"
    GRADLE_TEST_REPO="$copy" gradle "" -q -Pandbible.sign=true :app:signingReport
    [ "$unsigned_rc" = 0 ] || return "$(fail "missing encrypted file rejected without opt-in")"
    [ "$RC" != 0 ] || return "$(fail "missing encrypted file accepted with opt-in")"
    grep -q 'andbible.sign=true requires keystore.properties.gpg' "$T/out" || return "$(fail "message: $(tail -30 "$T/out")")"
    [ ! -s "$STUB_DIR/gpg.log" ] || return "$(fail "gpg called for missing encrypted file")"
}
test_signing_report_uses_session_store_and_no_gpg() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    gradle "$T/sess/keystore.properties" -q :app:signingReport
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(tail -30 "$T/out")")"
    local block
    block=$(awk '/^Variant: standardGoogleplayRelease$/{f=1} f&&/^-+$/{exit} f' "$T/out")
    printf '%s\n' "$block" | grep -qx "Store: $T/sess/andbible.p12" || return "$(fail "store: $block")"
    printf '%s\n' "$block" | grep -qx "Alias: andbible" || return "$(fail "alias: $block")"
    [ ! -s "$STUB_DIR/gpg.log" ] || return "$(fail "gpg called: $(cat "$STUB_DIR/gpg.log")")"
    grep -q "$PW" "$T/out" && return "$(fail "password printed")"
    true
}
test_session_properties_override_explicit_decrypt() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    gradle "$T/sess/keystore.properties" -q -Pandbible.sign=true :app:signingReport
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(tail -30 "$T/out")")"
    [ ! -s "$STUB_DIR/gpg.log" ] || return "$(fail "gpg called despite session properties")"
    grep -qx "Store: $T/sess/andbible.p12" "$T/out" || return "$(fail "session store not loaded")"
}
test_missing_properties_file_fails() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    gradle "$T/sess/nope.properties" -q :app:signingReport
    [ "$RC" != 0 ] || return "$(fail "missing file accepted")"
    grep -q 'ANDBIBLE_SIGNING_PROPERTIES points to a missing or unreadable file' "$T/out" || return "$(fail "message: $(tail -30 "$T/out")")"
    [ ! -s "$STUB_DIR/gpg.log" ] || return "$(fail "gpg called")"
}
test_wrapper_refuses_without_env() {
    rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR"
    env -u ANDBIBLE_SIGNING_PROPERTIES GRADLEW="$T/gradlew-stub" bash "$REPO/scripts/release/gradle-release.sh" help > "$T/out" 2>&1
    [ $? != 0 ] || return "$(fail "ran without the env")"
    [ ! -s "$STUB_DIR/gradlew.log" ] || return "$(fail "gradle started")"
    ANDBIBLE_SIGNING_PROPERTIES="$T/sess/nope" GRADLEW="$T/gradlew-stub" bash "$REPO/scripts/release/gradle-release.sh" help > "$T/out" 2>&1
    [ $? != 0 ] && [ ! -s "$STUB_DIR/gradlew.log" ] || return "$(fail "ran with a missing file")"
    ANDBIBLE_SIGNING_PROPERTIES="$T/sess/keystore.properties" GRADLEW="$T/gradlew-stub" bash "$REPO/scripts/release/gradle-release.sh" \
        bundleStandardGoogleplayRelease > "$T/out" 2>&1 || return "$(fail "wrapper failed: $(cat "$T/out")")"
    grep -qx 'gradlew --no-daemon bundleStandardGoogleplayRelease' "$STUB_DIR/gradlew.log" || return "$(fail "argv: $(cat "$STUB_DIR/gradlew.log")")"
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
