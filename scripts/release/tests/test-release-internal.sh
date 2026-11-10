#!/usr/bin/env bash
# Tests for scripts/release/release-internal.sh: android.sh, ios-build.sh, mac-remote.sh and curl are stubs (ANDROID_SH,
# IOS_BUILD, MAC_REMOTE, CURL); the bundle is real, encrypted to a throwaway key in a temp GNUPGHOME, and a gpg wrapper
# counts decrypts; flock is real; RELEASE_RAMDIR_MOUNT=none. No network, no Mac, no YubiKey.
# Run: bash scripts/release/tests/test-release-internal.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REAL_REPO="$(cd "$HERE/../../.." && pwd)"
SCRIPT="$REAL_REPO/scripts/release/release-internal.sh"
T="$(mktemp -d)"
FILTER="${1:-}"
export GNUPGHOME="$T/gnupg"
cleanup() { pkill -KILL -f "$T/" 2>/dev/null; gpgconf --kill all >/dev/null 2>&1; rm -rf "$T"; }
trap cleanup EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }
SENTINEL="REL-SENTINEL-$RANDOM$RANDOM$RANDOM"

REAL_GPG=$(command -v gpg); export REAL_GPG
mkdir -p "$T/bin" "$GNUPGHOME" "$T/etc-jailbee" "$T/src" "$T/home/.ssh-mac"; chmod 700 "$GNUPGHOME"
printf '{"short_name": "pub"}\n' > "$T/etc-jailbee/container.json"
: > "$T/home/.ssh-mac/config"
cat > "$T/bin/gpg" <<'EOS'
#!/usr/bin/env bash
for a; do [ "$a" = --decrypt ] && echo x >> "$STUB_DIR/decrypts"; done
exec "$REAL_GPG" "$@"
EOS
cat > "$T/bin/fastlane" <<'EOS'
#!/usr/bin/env bash
exit 0
EOS
cat > "$T/curl" <<'EOS'
#!/usr/bin/env bash
url="${*: -1}"; h=${url#https://}; h=${h%/}
case " ${STUB_BLOCKED:-} " in *" $h "*) printf 000; exit 7 ;; esac
printf 404
EOS
cat > "$T/mac-remote" <<'EOS'
#!/usr/bin/env bash
echo "mac-remote $*" >> "$STUB_DIR/calls"
case "$1" in check) echo "${STUB_CHECK:-build lock: free}"; exit "${STUB_CHECK_RC:-0}" ;; esac
EOS
# Branch stubs: log start/end (epoch ms), whether the session is visible, sleep, then exit with the configured code.
cat > "$T/android" <<'EOS'
#!/usr/bin/env bash
now() { date +%s%3N; }
echo "android $* sess=${RELEASE_SECRETS_DIR:+set} start=$(now)" >> "$STUB_DIR/calls"
case "$1" in reusable) exit "${STUB_A_REUSABLE:-1}" ;; esac
[ -z "${RELEASE_SECRETS_DIR:-}" ] || [ "${2:-}" = --dry-run ] || grep -q SENTINEL "$RELEASE_SECRETS_DIR/keystore.properties" || exit 9
echo $$ >> "$STUB_DIR/pids"
[ -z "${STUB_A_READ_STDIN:-}" ] || { read -r -t 5 _x; echo "android read rc=$?" >> "$STUB_DIR/calls"; }
[ -z "${STUB_A_SLEEP:-}" ] || { sleep "$STUB_A_SLEEP" & echo $! >> "$STUB_DIR/sleeppids"; wait $!; }
echo "android: did $1"
echo "android $1 end=$(now)" >> "$STUB_DIR/calls"
v="STUB_A_RC_$1"; exit "${!v:-0}"
EOS
cat > "$T/ios" <<'EOS'
#!/usr/bin/env bash
now() { date +%s%3N; }
if [ "${1:-}" = --detach ]; then echo "ios-detach $2" >> "$STUB_DIR/calls"; exit 0; fi
echo "ios $* sess=${RELEASE_SECRETS_DIR:+set} start=$(now)" >> "$STUB_DIR/calls"
[ -z "${RELEASE_SECRETS_DIR:-}" ] || [[ " $* " == *" --dry-run "* ]] || grep -q SENTINEL "$RELEASE_SECRETS_DIR/AuthKey.p8" || exit 9
echo $$ >> "$STUB_DIR/pids"
[ -z "${IOS_VOL_FILE:-}" ] || echo "asckey-$$" > "$IOS_VOL_FILE"
[ -z "${STUB_I_ESCAPE:-}" ] || { setsid sleep 60 & echo $! >> "$STUB_DIR/escaped"; }
if [ -n "${STUB_I_IGNORE_TERM:-}" ]; then trap '' TERM; else trap 'echo "ios-term-detach" >> "$STUB_DIR/calls"; exit 143' TERM; fi
[ -z "${STUB_I_SLEEP:-}" ] || { sleep "$STUB_I_SLEEP" & echo $! >> "$STUB_DIR/sleeppids"; wait $!; }
echo "build 2026.1110.120000"
echo "build number 2026.1110.120000, version 6.0.1262, commit abc, destination upload"
echo "ios end=$(now)" >> "$STUB_DIR/calls"
case " $* " in *" --reuse "*) exit "${STUB_I_RC_REUSE:-0}" ;; esac
exit "${STUB_I_RC:-0}"
EOS
chmod +x "$T/bin/"* "$T/curl" "$T/mac-remote" "$T/android" "$T/ios"

"$REAL_GPG" --batch --quiet --passphrase '' --quick-gen-key 'Rel Test <rel@test.invalid>' default default never 2>/dev/null
KEYID=$("$REAL_GPG" --list-keys --with-colons rel@test.invalid 2>/dev/null | awk -F: '/^sub/{print $5; exit}')
mkdir -p "$T/m"
{ echo format=1; for m in andbible.p12 keystore.properties play-service-account.json AuthKey.p8 asc-api.env; do echo "member=$m"; done; } > "$T/m/MANIFEST"
for m in andbible.p12 keystore.properties play-service-account.json AuthKey.p8 asc-api.env; do echo "$SENTINEL $m" > "$T/m/$m"; done
tar -C "$T/m" -cf - . | "$REAL_GPG" --batch --yes --trust-model always --encrypt --recipient "$KEYID!" -o "$T/src/andbible-release-secrets.tar.gpg" 2>/dev/null
rm -rf "$T/m"

mk_repo() {
    R="$T/repo"; rm -rf "$R" "$STUB_DIR" "$T/tmp" "$T/xdg"; mkdir -p "$STUB_DIR" "$T/tmp" "$T/xdg"
    git init -q "$R"; cd "$R" || return 1
    git config user.email t@t; git config user.name t
    mkdir -p scripts; cp "$REAL_REPO/scripts/lib-version.sh" scripts/
    printf 'versionMajorMinor=6.0\nversionCode=1261\nversionNameSuffix=-beta\n' > version.properties
    printf '.local/\n' > .gitignore
    git add -A; git commit -q -m init
}
envs() {
    env PATH="$T/bin:$PATH" STUB_DIR="$STUB_DIR" REPO_ROOT="$R" HOME="$T/home" TMPDIR="$T/tmp" XDG_RUNTIME_DIR="$T/xdg" \
        RELEASE_LOCK_FILE="${LOCKF-$T/xdg/release.lock}" RELEASE_RAMDIR_MOUNT=none RELEASE_SECRETS_SRC="${SRC_DIR-$T/src}" JAILBEE_ETC="${JB_ETC-$T/etc-jailbee}" \
        ANDROID_SH="$T/android" IOS_BUILD="$T/ios" MAC_REMOTE="$T/mac-remote" CURL="$T/curl" \
        FASTLANE="${FASTLANE_CMD-fastlane}" RELEASE_KILL_WAIT="${RELEASE_KILL_WAIT:-10}" "$@"
}
run() { envs bash "$SCRIPT" "$@" > "$T/out" 2>&1; RC=$?; }
count() { [ -f "$1" ] && wc -l < "$1" || echo 0; }
ramdirs() { find "$T/tmp" -mindepth 1 -maxdepth 1 -name 'release-secrets.*' | wc -l; }
SUMF() { echo "$R/.local/release/1261/summary.txt"; }
field() { sed -n "s/.*$2=\([0-9]*\).*/\1/p" <<<"$1"; }

test_dry_run_one_decrypt_then_both_branches() {
    mk_repo; run --dry-run
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(count "$STUB_DIR/decrypts")" = 0 ] || return "$(fail "gpg ran in a dry run")"
    local d a i
    d=$(grep -n 'dry-run.*decrypt.*touch' "$T/out" | cut -d: -f1 | head -1)
    a=$(grep -n '^\[android\]' "$T/out" | cut -d: -f1 | head -1); i=$(grep -n '^\[ios\]' "$T/out" | cut -d: -f1 | head -1)
    [ -n "$d" ] && [ -n "$a" ] && [ -n "$i" ] && [ "$d" -lt "$a" ] && [ "$d" -lt "$i" ] || return "$(fail "order: $(cat "$T/out")")"
    grep -q '^android build --dry-run' "$STUB_DIR/calls" && grep -q '^android upload --dry-run' "$STUB_DIR/calls" \
        && grep -q '^ios .*--dry-run' "$STUB_DIR/calls" || return "$(fail "branch dry runs: $(cat "$STUB_DIR/calls")")"
}
test_one_decrypt_both_branches() {
    mk_repo; run
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(count "$STUB_DIR/decrypts")" = 1 ] || return "$(fail "decrypts=$(count "$STUB_DIR/decrypts")")"
    grep -q '^android build sess=set' "$STUB_DIR/calls" && grep -q '^android upload sess=set' "$STUB_DIR/calls" \
        && grep -q '^ios  *sess=set' "$STUB_DIR/calls" || return "$(fail "calls: $(cat "$STUB_DIR/calls")")"
    grep -qi 'touch the YubiKey' "$T/out" || return "$(fail "no touch prompt")"
    grep -q '^android: PASS' "$(SUMF)" && grep -q '^ios: PASS' "$(SUMF)" || return "$(fail "summary: $(cat "$(SUMF)")")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_branches_run_in_parallel() {
    mk_repo; STUB_A_SLEEP=2 STUB_I_SLEEP=2 run
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local as ae is ie
    as=$(field "$(grep '^android build ' "$STUB_DIR/calls")" start); ae=$(field "$(grep '^android upload end' "$STUB_DIR/calls")" end)
    is=$(field "$(grep '^ios ' "$STUB_DIR/calls")" start); ie=$(field "$(grep '^ios end' "$STUB_DIR/calls")" end)
    [ -n "$as" ] && [ -n "$is" ] && [ "$is" -lt "$ae" ] && [ "$as" -lt "$ie" ] || return "$(fail "no overlap: $(cat "$STUB_DIR/calls")")"
}
test_android_fail_ios_continues() {
    mk_repo; STUB_A_RC_build=1 STUB_I_SLEEP=1 run
    [ "$RC" != 0 ] || return "$(fail "exit 0 with a failed branch")"
    grep -q '^android: FAIL' "$(SUMF)" && grep -q '^ios: PASS' "$(SUMF)" || return "$(fail "summary: $(cat "$(SUMF)")")"
    grep -q 'make release-internal PLATFORM=android' "$(SUMF)" || return "$(fail "no retry line")"
    grep -q 'PLATFORM=ios' "$(SUMF)" && return "$(fail "retry line for the passed branch")"
    grep -q '^android upload' "$STUB_DIR/calls" && return "$(fail "uploaded after a failed build")"
    grep -q '^ios end' "$STUB_DIR/calls" || return "$(fail "ios did not finish")"
}
test_wipe_after_both_end() {
    mk_repo; STUB_I_SLEEP=3 envs bash "$SCRIPT" > "$T/out" 2>&1 &
    local pid=$! i seen=0
    for i in $(seq 30); do grep -q '^android upload end' "$STUB_DIR/calls" 2>/dev/null && break; sleep 0.1; done
    [ "$(ramdirs)" = 1 ] && seen=1
    wait "$pid"; RC=$?
    [ "$seen" = 1 ] || return "$(fail "RAM dir gone while ios still ran")"
    [ "$RC" = 0 ] && [ "$(ramdirs)" = 0 ] || return "$(fail "rc=$RC ramdirs=$(ramdirs)")"
}
start_bg() {   # start_bg: run in its own process group (like a terminal job), wait until both branches run
    rm -f "$T/pg"
    envs perl -e '$SIG{INT}="DEFAULT"; setpgrp(0,0); open(my $f, ">", "'"$T/pg"'"); print $f $$; close $f; exec @ARGV' bash "$SCRIPT" > "$T/out" 2>&1 &
    BGJOB=$!
    local i
    for i in $(seq 100); do [ "$(count "$STUB_DIR/pids")" -ge 2 ] && [ -s "$T/pg" ] && break; sleep 0.1; done
    BG=$(cat "$T/pg" 2>/dev/null)
    [ "$(count "$STUB_DIR/pids")" -ge 2 ] && [ -n "$BG" ]
}
test_int_wipes_and_kills_both() {
    mk_repo; export STUB_A_SLEEP=30 STUB_I_SLEEP=30
    start_bg || { unset STUB_A_SLEEP STUB_I_SLEEP; kill -9 -- -"$BG"; return "$(fail "branches never started: $(cat "$T/out")")"; }
    unset STUB_A_SLEEP STUB_I_SLEEP
    local t0=$SECONDS p
    kill -INT -- -"$BG"; wait "$BGJOB"; RC=$?
    [ $((SECONDS - t0)) -lt 15 ] || return "$(fail "took $((SECONDS - t0))s")"
    [ "$RC" = 130 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    sleep 0.3
    for p in $(cat "$STUB_DIR/pids" "$STUB_DIR/sleeppids"); do kill -0 "$p" 2>/dev/null && return "$(fail "pid $p alive")"; done
    grep -q 'ios-term-detach' "$STUB_DIR/calls" || return "$(fail "iOS branch not told to detach")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
    local s; s=$(grep -rl "$SENTINEL" "$T" --exclude-dir=gnupg --exclude-dir=src 2>/dev/null | head -1); [ -z "$s" ] || return "$(fail "sentinel in $s")"
}
test_ios_killed_gets_detach() {
    mk_repo; export STUB_A_SLEEP=30 STUB_I_SLEEP=30 STUB_I_IGNORE_TERM=1 RELEASE_KILL_WAIT=1
    start_bg || { unset STUB_A_SLEEP STUB_I_SLEEP STUB_I_IGNORE_TERM RELEASE_KILL_WAIT; kill -9 -- -"$BG"; return "$(fail "no start")"; }
    unset STUB_A_SLEEP STUB_I_SLEEP STUB_I_IGNORE_TERM RELEASE_KILL_WAIT
    kill -TERM -- -"$BG"; wait "$BGJOB"; RC=$?
    [ "$RC" = 143 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^ios-detach asckey-[0-9]' "$STUB_DIR/calls" || return "$(fail "no detach of the killed branch's volume: $(cat "$STUB_DIR/calls")")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_second_run_refused_and_live_dir_untouched() {
    mk_repo; export STUB_A_SLEEP=4 STUB_I_SLEEP=4
    start_bg || { unset STUB_A_SLEEP STUB_I_SLEEP; kill -9 -- -"$BG"; return "$(fail "no start")"; }
    unset STUB_A_SLEEP STUB_I_SLEEP
    local live; live=$(find "$T/tmp" -mindepth 1 -maxdepth 1 -name 'release-secrets.*')
    envs bash "$SCRIPT" > "$T/out2" 2>&1; local rc2=$?
    [ "$rc2" != 0 ] && grep -q 'another release run is active' "$T/out2" || return "$(fail "second run rc=$rc2: $(cat "$T/out2")")"
    [ "$(count "$STUB_DIR/decrypts")" = 1 ] || return "$(fail "second run decrypted")"
    [ -n "$live" ] && [ "$(ls -A "$live" | wc -l)" = 7 ] || return "$(fail "live dir damaged")"
    wait "$BGJOB"; RC=$?
    [ "$RC" = 0 ] || return "$(fail "first run rc=$RC: $(cat "$T/out")")"
}
test_export_with_reusable_aab_uploads_nothing() {
    mk_repo; STUB_A_REUSABLE=0 run --export-only
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^android upload' "$STUB_DIR/calls" && return "$(fail "uploaded in export mode: $(cat "$STUB_DIR/calls")")"
    grep -q '^android verify sess=set' "$STUB_DIR/calls" || return "$(fail "reused AAB not verified: $(cat "$STUB_DIR/calls")")"
}
test_branches_do_not_read_the_terminal() {
    mk_repo
    STUB_A_READ_STDIN=1 envs timeout 30 script -qec "bash $SCRIPT --platform android" /dev/null > "$T/out" 2>&1; RC=$?
    [ "$RC" = 0 ] || return "$(fail "rc=$RC (124 = hung on a terminal read): $(tail -5 "$T/out")")"
    grep -q 'android read rc=1' "$STUB_DIR/calls" || return "$(fail "stdin not /dev/null: $(cat "$STUB_DIR/calls")")"
}
test_int_kills_escaped_helpers() {
    mk_repo; export STUB_A_SLEEP=30 STUB_I_SLEEP=30 STUB_I_ESCAPE=1
    start_bg || { unset STUB_A_SLEEP STUB_I_SLEEP STUB_I_ESCAPE; kill -9 -- -"$BG"; return "$(fail "no start")"; }
    unset STUB_A_SLEEP STUB_I_SLEEP STUB_I_ESCAPE
    local i; for i in $(seq 50); do [ -s "$STUB_DIR/escaped" ] && break; sleep 0.1; done
    kill -INT -- -"$BG"; wait "$BGJOB"; RC=$?
    sleep 0.3
    local p; p=$(cat "$STUB_DIR/escaped")
    kill -0 "$p" 2>/dev/null && { kill "$p"; return "$(fail "helper in its own session survived")"; }
    [ "$RC" = 130 ] || return "$(fail "rc=$RC")"
}
test_lock_does_not_depend_on_xdg_runtime_dir() {
    mk_repo; export STUB_A_SLEEP=4 STUB_I_SLEEP=4 LOCKF=""
    start_bg || { unset STUB_A_SLEEP STUB_I_SLEEP LOCKF; kill -9 -- -"$BG"; return "$(fail "no start")"; }
    unset STUB_A_SLEEP STUB_I_SLEEP
    mkdir -p "$T/xdg2"; envs env XDG_RUNTIME_DIR="$T/xdg2" bash "$SCRIPT" > "$T/out2" 2>&1; local rc2=$?
    unset LOCKF
    wait "$BGJOB"
    [ "$rc2" != 0 ] && grep -q 'another release run is active' "$T/out2" || return "$(fail "second run with another XDG_RUNTIME_DIR: rc=$rc2 $(cat "$T/out2")")"
}
test_export_needs_no_play_egress() {
    mk_repo; FASTLANE_CMD=/nonexistent/fastlane STUB_BLOCKED="androidpublisher.googleapis.com oauth2.googleapis.com www.googleapis.com" run --export-only
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^android build sess=set' "$STUB_DIR/calls" || return "$(fail "android not built")"
    grep -q 'egress rm' "$(SUMF)" && return "$(fail "egress reminder for an export run")"
    true
}
test_stale_dir_swept() {
    mk_repo; mkdir -m 700 "$T/tmp/release-secrets.OLD1"; echo "$SENTINEL" > "$T/tmp/release-secrets.OLD1/k"
    run
    [ "$RC" = 0 ] && [ ! -e "$T/tmp/release-secrets.OLD1" ] || return "$(fail "rc=$RC, stale dir kept")"
}
test_preflight_lists_all_misses() {
    mk_repo; SRC_DIR="$T/nomount" FASTLANE_CMD=/nonexistent/fastlane STUB_BLOCKED=oauth2.googleapis.com run
    [ "$RC" = 1 ] || return "$(fail "rc=$RC")"
    grep -q 'jailbee mount release-secrets pub --for 2h' "$T/out" || return "$(fail "no mount line: $(cat "$T/out")")"
    grep -q 'fastlane' "$T/out" || return "$(fail "no fastlane line")"
    grep -q 'jailbee net egress add oauth2.googleapis.com pub' "$T/out" || return "$(fail "no egress line")"
    [ "$(count "$STUB_DIR/decrypts")" = 0 ] || return "$(fail "decrypted")"
    ! grep -q '^android build\|^ios ' "$STUB_DIR/calls" 2>/dev/null || return "$(fail "a branch ran")"
}
test_preflight_blocked_host_prints_egress_line() {
    mk_repo; STUB_BLOCKED="androidpublisher.googleapis.com www.googleapis.com" run
    [ "$RC" = 1 ] || return "$(fail "rc=$RC")"
    grep -q 'jailbee net egress add androidpublisher.googleapis.com pub' "$T/out" \
        && grep -q 'jailbee net egress add www.googleapis.com pub' "$T/out" || return "$(fail "$(cat "$T/out")")"
    grep -q 'egress add oauth2' "$T/out" && return "$(fail "reachable host listed")"
    true
}
test_preflight_dirty_tree_and_bundle_missing() {
    mk_repo; echo x > stray.txt; mkdir -p "$T/emptysrc"; SRC_DIR="$T/emptysrc" run
    [ "$RC" = 1 ] && grep -q 'not clean' "$T/out" && grep -q 'secrets-pack.sh' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
test_preflight_not_jailbee() {
    mk_repo; JB_ETC="$T/none" run
    [ "$RC" = 1 ] && grep -qi 'JailBee' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
}
test_preflight_mac_lock_held() {
    mk_repo; STUB_CHECK='BUILD LOCK held by: other-box [alive]' run --platform ios
    [ "$RC" = 1 ] && grep -q 'other-box' "$T/out" || return "$(fail "rc=$RC: $(cat "$T/out")")"
    rm -f "$T/home/.ssh-mac/config"; run --platform ios; : > "$T/home/.ssh-mac/config"
    [ "$RC" = 1 ] && grep -q 'jailbee mount mac-ssh pub' "$T/out" || return "$(fail "no mac-ssh line: $(cat "$T/out")")"
}
test_platform_ios_skips_android_checks() {
    mk_repo; FASTLANE_CMD=/nonexistent/fastlane STUB_BLOCKED="androidpublisher.googleapis.com oauth2.googleapis.com www.googleapis.com" run --platform ios
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^android' "$STUB_DIR/calls" && return "$(fail "android ran")"
    grep -q '^android: SKIPPED' "$(SUMF)" || return "$(fail "summary: $(cat "$(SUMF)")")"
    grep -q 'egress rm' "$(SUMF)" && return "$(fail "egress reminder without android")"
    true
}
test_platform_android_skips_mac() {
    mk_repo; rm -f "$T/home/.ssh-mac/config"; run --platform android; : > "$T/home/.ssh-mac/config"
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^ios\|mac-remote' "$STUB_DIR/calls" && return "$(fail "ios/mac ran: $(cat "$STUB_DIR/calls")")"
    grep -q 'jailbee net egress rm www.googleapis.com pub' "$(SUMF)" || return "$(fail "no egress reminder")"
}
ios_env() {   # ios_env <commit> <uploaded>
    mkdir -p "$R/.local/release/1261/ios"
    printf 'COMMIT=%s\nVERSION_CODE=1261\nVERSION_NAME=6.0.1261\nBUILD=2026.1109.101010\nUPLOADED=%s\n' "$1" "$2" > "$R/.local/release/1261/ios/artifact.env"
}
test_reuse_passed_to_ios() {
    mk_repo; ios_env "$(git rev-parse --short=10 HEAD)" 0; run --platform ios
    [ "$RC" = 0 ] && grep -q '^ios --reuse 2026.1109.101010 ' "$STUB_DIR/calls" || return "$(fail "rc=$RC: $(cat "$STUB_DIR/calls")")"
    mk_repo; ios_env "$(git rev-parse --short=10 HEAD)" 1; run --platform ios
    grep -q -- '--reuse' "$STUB_DIR/calls" && return "$(fail "reused an uploaded build")"
    mk_repo; ios_env 0000000000 0; run --platform ios
    grep -q -- '--reuse' "$STUB_DIR/calls" && return "$(fail "reused another commit's build")"
    true
}
test_ios_reuse_exit3_rebuilds_once() {
    mk_repo; ios_env "$(git rev-parse --short=10 HEAD)" 0; STUB_I_RC_REUSE=3 run --platform ios
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    [ "$(grep -c '^ios .*start=' "$STUB_DIR/calls")" = 2 ] && grep -q '^ios  *sess=set' "$STUB_DIR/calls" || return "$(fail "calls: $(cat "$STUB_DIR/calls")")"
    mk_repo; ios_env "$(git rev-parse --short=10 HEAD)" 0; STUB_I_RC_REUSE=3 STUB_I_RC=3 run --platform ios
    [ "$RC" != 0 ] && [ "$(grep -c '^ios .*start=' "$STUB_DIR/calls")" = 2 ] || return "$(fail "rc=$RC, calls $(grep -c '^ios ' "$STUB_DIR/calls")")"
}
test_android_reuse_skips_build() {
    mk_repo; STUB_A_REUSABLE=0 run --platform android
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^android build' "$STUB_DIR/calls" && return "$(fail "rebuilt")"
    grep -q '^android upload sess=set' "$STUB_DIR/calls" || return "$(fail "no upload")"
}
test_export_mode_uploads_nothing() {
    mk_repo; run --export-only
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q '^android upload' "$STUB_DIR/calls" && return "$(fail "android uploaded")"
    grep -q '^ios --export-only' "$STUB_DIR/calls" || return "$(fail "ios not export-only: $(cat "$STUB_DIR/calls")")"
    grep -q '^android build sess=set' "$STUB_DIR/calls" || return "$(fail "android not built")"
}
test_sentinel_not_in_logs() {
    mk_repo; STUB_A_RC_upload=1 run
    grep -rq "$SENTINEL" "$T/out" "$R/.local" && return "$(fail "sentinel in output or logs")"
    [ -f "$R/.local/release/1261/android/branch.log" ] && [ -f "$R/.local/release/1261/ios/branch.log" ] || return "$(fail "no branch logs")"
    grep -q 'android: did build' "$R/.local/release/1261/android/branch.log" || return "$(fail "branch log content")"
}
test_summary_fields() {
    mk_repo; run
    local s; s=$(cat "$(SUMF)")
    grep -q "commit $(git rev-parse --short=10 HEAD)" <<<"$s" && grep -q 'versionCode 1261' <<<"$s" && grep -q '6.0.1261-beta' <<<"$s" \
        && grep -q 'build 2026.1110.120000 ' <<<"$s" && grep -q 'testflight/2026.1110.120000/$' <<<"$s" && grep -q 'jailbee unmount release-secrets pub' <<<"$s" \
        || return "$(fail "summary: $s")"
}
test_make_testflight_maps_to_ios() {
    local o
    o=$(make -s -n -C "$REAL_REPO" testflight DEST=export 2>&1)
    grep -q 'release-internal.sh --platform ios --export-only' <<<"$o" || return "$(fail "make -n testflight: $o")"
    o=$(make -s -n -C "$REAL_REPO" release-internal PLATFORM=android DRY_RUN=1 2>&1)
    grep -q 'release-internal.sh --platform android .*--dry-run' <<<"$o" || return "$(fail "make -n release-internal: $o")"
    make -s -C "$REAL_REPO" release-internal PLATFORM=bogus >/dev/null 2>&1 && return "$(fail "bogus PLATFORM accepted")"
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
