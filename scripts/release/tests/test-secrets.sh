#!/usr/bin/env bash
# Tests for the bundle functions of scripts/release/lib-secrets.sh and for scripts/release/secrets-pack.sh. Uses a
# throwaway passphrase-less GPG key in a temp GNUPGHOME (never the maintainer's agent), throwaway PKCS12 keystores,
# RELEASE_RAMDIR_MOUNT=none and a temp TMPDIR/HOME. No network, no YubiKey.
# Run: bash scripts/release/tests/test-secrets.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REAL_REPO="$(cd "$HERE/../../.." && pwd)"
LIB="$REAL_REPO/scripts/release/lib-secrets.sh"
PACK="$REAL_REPO/scripts/release/secrets-pack.sh"
T="$(mktemp -d)"
FILTER="${1:-}"
export GNUPGHOME="$T/gnupg"
cleanup() { gpgconf --kill all >/dev/null 2>&1; rm -rf "$T"; }
trap cleanup EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }
SENTINEL="SECRETS-SENTINEL-$RANDOM$RANDOM$RANDOM"
PW="pw$RANDOM$RANDOM$RANDOM"

REAL_GPG=$(command -v gpg); REAL_KEYTOOL=$(command -v keytool); REAL_JARSIGNER=$(command -v jarsigner)
export REAL_GPG REAL_KEYTOOL REAL_JARSIGNER
mkdir -p "$T/bin" "$GNUPGHOME" "$T/members" "$T/home"
chmod 700 "$GNUPGHOME"
# Wrappers: record every argv line, count decrypts, exec the real tool.
for tool in gpg keytool jarsigner; do
    up=$(echo "$tool" | tr a-z A-Z)
    cat > "$T/bin/$tool" <<EOS
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "\$STUB_DIR/$tool.argv"
for a; do [ "\$a" = --decrypt ] && echo x >> "\$STUB_DIR/decrypts"; done
exec "\$REAL_$up" "\$@"
EOS
done
chmod +x "$T/bin/"*

"$REAL_GPG" --batch --quiet --passphrase '' --quick-gen-key 'Release Test <rt@test.invalid>' default default never 2>/dev/null
"$REAL_GPG" --batch --quiet --passphrase '' --quick-gen-key 'Other Test <ot@test.invalid>' default default never 2>/dev/null
KEYID=$("$REAL_GPG" 2>/dev/null --list-keys --with-colons rt@test.invalid | awk -F: '/^sub/{print $5; exit}' 2>/dev/null)
OTHER=$("$REAL_GPG" 2>/dev/null --list-keys --with-colons ot@test.invalid | awk -F: '/^sub/{print $5; exit}' 2>/dev/null)
[ -n "$KEYID" ] && [ -n "$OTHER" ] || { echo "cannot create test keys" >&2; exit 1; }
enc() { "$REAL_GPG" --batch --yes --trust-model always --encrypt --recipient "$1!" -o "$3" "$2" 2>/dev/null; }   # enc <keyid> <in> <out>

ALL_MEMBERS=(andbible.p12 keystore.properties play-service-account.json AuthKey.p8 asc-api.env)
# mk_bundle <out> [members...]: fake members with the sentinel, MANIFEST format=1 listing the given members.
mk_bundle() {
    local out="$1"; shift
    local d="$T/members/b$RANDOM" m; mkdir -p "$d"
    [ $# -gt 0 ] || set -- "${ALL_MEMBERS[@]}"
    { echo "${MANIFEST_FORMAT:-format=1}"; for m in "${ALL_MEMBERS[@]}"; do echo "member=$m"; done; } > "$d/MANIFEST"
    for m in "$@"; do echo "$SENTINEL $m" > "$d/$m"; done
    tar -C "$d" -cf "$d.tar" MANIFEST "$@"
    enc "${RECIP:-$KEYID}" "$d.tar" "$out"
    rm -rf "$d" "$d.tar"
}
ramdirs() { find "$T/tmp" -mindepth 1 -maxdepth 1 2>/dev/null | wc -l; }
count() { [ -f "$1" ] && wc -l < "$1" || echo 0; }
sentinel_anywhere() { grep -rl "$SENTINEL" "$T" --exclude-dir=gnupg 2>/dev/null | grep -v '/out$' | head -1; }
# lib <shell code>: runs in a subshell with the lib sourced and the test env; output in $T/out, status in $RC.
lib() {
    PATH="$T/bin:$PATH" TMPDIR="$T/tmp" HOME="$T/home" RELEASE_RAMDIR_MOUNT=none RELEASE_SECRETS_SRC="$T/src" \
        bash -c "set -uo pipefail; . '$LIB'; $1" > "$T/out" 2>&1
    RC=$?
}
fresh() { rm -rf "$T/bundle.tar.gpg" "$STUB_DIR" "$T/tmp" "$T/src" "$T/home"; mkdir -p "$STUB_DIR" "$T/tmp" "$T/src" "$T/home"; }

test_open_bundle_decrypts_once_and_exports_dir() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    lib 'secrets_open_bundle || exit 9; [ -n "$RELEASE_SECRETS_DIR" ] || exit 8; p=$(secrets_member AuthKey.p8) || exit 7
         case "$p" in "$RELEASE_SECRETS_DIR"/AuthKey.p8) ;; *) exit 6 ;; esac; grep -q SENTINEL "$p" || exit 5; secrets_close'
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(count "$STUB_DIR/decrypts")" = 1 ] || return "$(fail "decrypts=$(count "$STUB_DIR/decrypts")")"
    grep -qi 'touch the YubiKey' "$T/out" || return "$(fail "no touch prompt: $(cat "$T/out")")"
    grep -q "$SENTINEL" "$T/out" && return "$(fail "content printed")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_members_0600_dir_0700() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    lib 'secrets_open_bundle || exit 9; stat -c "%a %n" "$RELEASE_SECRETS_DIR" "$RELEASE_SECRETS_DIR"/*; secrets_close'
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^700 .*release-secrets\.' "$T/out" || return "$(fail "dir mode: $(cat "$T/out")")"
    [ "$(grep -c '^600 ' "$T/out")" = 6 ] || return "$(fail "member modes: $(cat "$T/out")")"
}
test_missing_member_fails_and_wipes() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg" andbible.p12 keystore.properties play-service-account.json asc-api.env
    lib 'secrets_open_bundle; echo "rc=$?"; echo "dir=${RELEASE_SECRETS_DIR:-}"'
    grep -q '^rc=0' "$T/out" && return "$(fail "missing AuthKey.p8 accepted")"
    grep -q 'AuthKey.p8' "$T/out" || return "$(fail "message does not name the member: $(cat "$T/out")")"
    grep -q '^dir=$' "$T/out" || return "$(fail "RELEASE_SECRETS_DIR still set")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_bad_manifest_format_fails() {
    fresh; MANIFEST_FORMAT=format=2 mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    lib 'secrets_open_bundle'
    [ "$RC" != 0 ] && grep -qi manifest "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_partial_stream_leaves_nothing() {
    fresh; mk_bundle "$T/full.gpg"
    head -c $(( $(stat -c %s "$T/full.gpg") / 2 )) "$T/full.gpg" > "$T/src/andbible-release-secrets.tar.gpg"; rm "$T/full.gpg"
    lib 'secrets_open_bundle'
    [ "$RC" != 0 ] && grep -qi decrypt "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left: $(ls "$T/tmp")")"
    local s; s=$(sentinel_anywhere); [ -z "$s" ] || return "$(fail "sentinel left in $s")"
}
test_wrong_recipient_fails_cleanly() {
    fresh; RECIP=$OTHER mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    "$REAL_GPG" --batch --yes --delete-secret-keys "$("$REAL_GPG" --list-keys --with-colons ot@test.invalid | awk -F: '/^fpr/{print $10; exit}')" 2>/dev/null
    lib 'secrets_open_bundle'
    [ "$RC" != 0 ] && grep -qi decrypt "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_missing_bundle_fails_before_decrypt() {
    fresh
    lib 'secrets_open_bundle'
    [ "$RC" != 0 ] && grep -q 'secrets-pack.sh' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(count "$STUB_DIR/decrypts")" = 0 ] && [ "$(ramdirs)" = 0 ] || return "$(fail "decrypt ran or ramdir left")"
}
test_member_missing_path_fails() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    lib 'secrets_member AuthKey.p8 && exit 5; secrets_open_bundle || exit 9; secrets_member nope.txt && exit 6
         secrets_member ../MANIFEST && exit 7; secrets_close; exit 0'
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
test_close_leaves_no_sentinel() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    lib 'secrets_open_bundle || exit 9; secrets_close'
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local s; s=$(sentinel_anywhere); [ -z "$s" ] || return "$(fail "sentinel left in $s")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_sweep_removes_stale_keeps_current() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    mkdir -m 700 "$T/tmp/release-secrets.STALE1"; echo "$SENTINEL" > "$T/tmp/release-secrets.STALE1/key"
    mkdir -m 700 "$T/tmp/unrelated"
    lib 'secrets_open_bundle || exit 9; secrets_sweep_stale; ls "$RELEASE_SECRETS_DIR" | wc -l; secrets_close'
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ ! -e "$T/tmp/release-secrets.STALE1" ] || return "$(fail "stale dir kept")"
    [ -d "$T/tmp/unrelated" ] || return "$(fail "unrelated dir removed")"
    grep -qx 6 "$T/out" || return "$(fail "current dir damaged: $(cat "$T/out")")"
}

test_sweep_keeps_dir_of_live_owner() {
    fresh
    sleep 30 & local live=$!
    mkdir -m 700 "$T/tmp/release-secrets.LIVE" "$T/tmp/release-secrets.DEAD"
    echo "$live" > "$T/tmp/release-secrets.LIVE/.owner"; echo x > "$T/tmp/release-secrets.LIVE/k"
    echo 999999 > "$T/tmp/release-secrets.DEAD/.owner"
    lib 'secrets_sweep_stale'
    kill "$live" 2>/dev/null
    [ -f "$T/tmp/release-secrets.LIVE/k" ] || return "$(fail "live run's dir wiped")"
    [ ! -e "$T/tmp/release-secrets.DEAD" ] || return "$(fail "dead run's dir kept")"
}
test_open_records_owner() {
    fresh; mk_bundle "$T/src/andbible-release-secrets.tar.gpg"
    lib 'secrets_open_bundle || exit 9; cat "$RELEASE_SECRETS_DIR/.owner"; echo "me=$$"; secrets_close'
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local o m; o=$(grep -x '[0-9]*' "$T/out" | head -1); m=$(sed -n 's/^me=//p' "$T/out")
    [ -n "$o" ] && [ "$o" = "$m" ] || return "$(fail "owner '$o' vs '$m'")"
}

# --- secrets-pack.sh ---
mk_pack_inputs() {   # throwaway inputs in the layout the maintainer has: repo root + mount
    rm -rf "$T/packrepo" "$T/pk"; mkdir -p "$T/packrepo" "$T/pk"
    local k
    for k in andbible other; do
        "$REAL_KEYTOOL" -genkeypair -keystore "$T/pk/src.p12" -storetype PKCS12 -storepass "$PW" -keypass "$PW" -alias "$k" \
            -keyalg RSA -keysize 2048 -validity 30 -dname "CN=$k" >/dev/null 2>&1 || return 1
    done
    printf 'storeFile=release_keys.p12\nstorePassword=%s\nkeyAlias=andbible\nkeyPassword=%s\n' "$PW" "$PW" > "$T/pk/props"
    enc "$KEYID" "$T/pk/props" "$T/packrepo/keystore.properties.gpg"
    enc "$KEYID" "$T/pk/src.p12" "$T/src/release_keys.p12.gpg"
    printf -- '-----BEGIN PRIVATE KEY-----\n%s\n-----END PRIVATE KEY-----\n' "$SENTINEL" > "$T/pk/p8"
    enc "$KEYID" "$T/pk/p8" "$T/src/AuthKey_KID1.p8.gpg"
    printf '{"type": "service_account", "private_key": "%s"}\n' "$SENTINEL" > "$T/pk/json"
    enc "$KEYID" "$T/pk/json" "$T/src/proj-123.json.gpg"
    printf 'ASC_KEY_ID=KID1\nASC_ISSUER_ID=iss-1\n' > "$T/src/asc-api.env"
    FP=$("$REAL_KEYTOOL" -list -v -keystore "$T/pk/src.p12" -storetype PKCS12 -storepass "$PW" -alias andbible 2>/dev/null | awk '/SHA256:/{print $2; exit}')
    rm -f "$T/pk/props" "$T/pk/p8" "$T/pk/json"
}
pack() {
    PATH="$T/bin:$PATH" TMPDIR="$T/tmp" HOME="$T/home" RELEASE_RAMDIR_MOUNT=none RELEASE_SECRETS_SRC="$T/src" \
        RELEASE_LOCK_FILE="$T/release.lock" REPO_ROOT="$T/packrepo" PACK_PAUSE=0 PACK_EXPECT_CERT_PREFIX="${FP:0:11}" PACK_EXPECT_CERT_SUFFIX="${FP: -11}" \
        bash "$PACK" "$@" > "$T/out" 2>&1
    RC=$?
}
test_pack_round_trip() {
    fresh; mk_pack_inputs || return "$(fail "inputs")"
    pack --out "$T/bundle.tar.gpg"
    [ "$RC" = 0 ] || return "$(fail "pack rc=$RC: $(cat "$T/out")")"
    [ "$(stat -c %a "$T/bundle.tar.gpg")" = 600 ] || return "$(fail "bundle mode")"
    grep -q "$KEYID" "$T/out" || return "$(fail "default recipient not reported: $(cat "$T/out")")"
    grep -q 'andbible-release' "$T/out" && grep -q 'rm ' "$T/out" || return "$(fail "host commands missing: $(cat "$T/out")")"
    cp "$T/out" "$T/pack.out"
    # no password and no secret content in any recorded argv or the output
    grep -rq -e "$PW" -e "$SENTINEL" "$STUB_DIR" "$T/pack.out" && return "$(fail "password/sentinel in argv or output")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left after pack")"
    lib "secrets_open_bundle '$T/bundle.tar.gpg' || exit 9; d=\$RELEASE_SECRETS_DIR
         grep -qx 'storeFile=andbible.p12' \$d/keystore.properties || exit 8
         grep -qx 'keyAlias=andbible' \$d/keystore.properties || exit 7
         \"\$REAL_KEYTOOL\" -list -keystore \$d/andbible.p12 -storetype PKCS12 -storepass:file <(sed -n 's/^storePassword=//p' \$d/keystore.properties | tr -d '\n') 2>/dev/null > \$d/../list.txt
         grep -c PrivateKeyEntry \$d/../list.txt; grep -o '^[a-z]*,' \$d/../list.txt; rm -f \$d/../list.txt
         grep -qx 'ASC_TEAM_ID=X6A6CDK8T7' \$d/asc-api.env || exit 6
         grep -q '$SENTINEL' \$d/AuthKey.p8 && grep -q '$SENTINEL' \$d/play-service-account.json || exit 5
         secrets_close"
    [ "$RC" = 0 ] || return "$(fail "open rc=$RC: $(cat "$T/out")")"
    grep -qx 1 "$T/out" && grep -qx 'andbible,' "$T/out" && ! grep -q 'other' "$T/out" || return "$(fail "aliases: $(cat "$T/out")")"
}
test_pack_refuses_foreign_cert() {
    fresh; mk_pack_inputs || return "$(fail "inputs")"
    FP="AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA"
    pack --out "$T/bundle.tar.gpg" --recipient "$KEYID"
    [ "$RC" != 0 ] && grep -qi 'cert' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ ! -e "$T/bundle.tar.gpg" ] && [ "$(ramdirs)" = 0 ] || return "$(fail "bundle written or ramdir left")"
}
test_pack_refuses_two_json() {
    fresh; mk_pack_inputs || return "$(fail "inputs")"
    cp "$T/src/proj-123.json.gpg" "$T/src/proj-456.json.gpg"
    pack --out "$T/bundle.tar.gpg"
    [ "$RC" != 0 ] && grep -q 'json' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(count "$STUB_DIR/decrypts")" = 0 ] || return "$(fail "decrypt before the input check")"
}
test_pack_refuses_existing_out() {
    fresh; mk_pack_inputs || return "$(fail "inputs")"
    echo old > "$T/bundle.tar.gpg"
    pack --out "$T/bundle.tar.gpg"
    [ "$RC" != 0 ] && [ "$(cat "$T/bundle.tar.gpg")" = old ] || return "$(fail "rc=$RC, existing bundle overwritten")"
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
