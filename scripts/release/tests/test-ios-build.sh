#!/usr/bin/env bash
# Tests for scripts/release/ios-build.sh and lib-secrets.sh without a Mac, key or YubiKey: ssh, gpg and mac-remote.sh
# are stubs on PATH / via MAC_REMOTE, the repo is a temp git repo with real (local) submodules.
# Run: bash scripts/release/tests/test-ios-build.sh [name-filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REAL_REPO="$(cd "$HERE/../../.." && pwd)"
SCRIPT="$REAL_REPO/scripts/release/ios-build.sh"
T="$(mktemp -d)"
FILTER="${1:-}"
trap 'rm -rf "$T"' EXIT
pass=0; failn=0; cur=
# fail <msg>: used as `return "$(fail msg)"`; the message goes to stderr, stdout gives the nonzero return code
fail() { echo "FAIL [$cur]: $*" >&2; : > "$T/failflag"; echo 1; }
SENTINEL="TF-SENTINEL-$RANDOM$RANDOM$RANDOM"

mkdir -p "$T/bin" "$T/tmp" "$T/secrets"
# ssh stub: records argv (one line per call) and stdin length; the attach command prints a fake ls line.
cat > "$T/bin/ssh" <<'EOS'
#!/usr/bin/env bash
echo "ssh $*" >> "$STUB_DIR/calls"
printf 'ssh %s\n' "$*" >> "$STUB_DIR/argv"
if [ ! -t 0 ]; then n=$(wc -c); echo "stdin $n" >> "$STUB_DIR/stdin"; fi
case "$*" in *"hdiutil detach"*) [ -z "${STUB_DETACH_SLEEP:-}" ] || { echo detach-start > "$STUB_DIR/detach-started"; trap '' TERM; if [ -n "${STUB_DETACH_EXEC:-}" ]; then exec sleep "$STUB_DETACH_SLEEP"; fi; sleep "$STUB_DETACH_SLEEP"; echo detach-done > "$STUB_DIR/detach-done"; } ;; esac
case "$*" in *"hdiutil info"*) echo 0 ;; *"ram://"*) echo "-rw------- 40" ;; esac
case "$*" in *"hdiutil detach"*) [ -z "${STUB_DETACH_RC:-}" ] || exit "$STUB_DETACH_RC" ;; esac
exit "${STUB_SSH_RC:-0}"
EOS
cat > "$T/bin/gpg" <<'EOS'
#!/usr/bin/env bash
echo gpg >> "$STUB_DIR/gpgcalls"
[ -z "${STUB_GPG_DIRTY:-}" ] || echo edit > "$REPO_ROOT/edited-during-wait.txt"
[ "${STUB_GPG_RC:-0}" = 0 ] || { echo "gpg: Bad PIN" >&2; exit "$STUB_GPG_RC"; }
echo "$STUB_SENTINEL"
EOS
cat > "$T/mac-remote" <<'EOS'
#!/usr/bin/env bash
echo "mac-remote $*" >> "$STUB_DIR/argv"
echo "$1" >> "$STUB_DIR/mrcalls"
case "$1" in
check) cat "$STUB_DIR/check.out" ;;
run) b=$(echo "$2" | awk '{print $7}')
    if [ "${STUB_CAPTURE:-1}" = 1 ] && [ -z "${STUB_RUN_SLEEP:-}" ]; then
        d="$MAC_LOCAL_DIR/runs/r1/run/step1/release/testflight-$b"; mkdir -p "$d/export" "$d/ipa" "$d/upload"
        echo "build log" > "$d/archive.log"; echo "result=PASS upload" > "$d/summary.txt"; echo ents > "$d/release.entitlements"
        echo ipaents > "$d/ipa-entitlements.txt"; echo big > "$d/export/AndBible.ipa"; echo big > "$d/ipa/x"; echo big > "$d/upload/y"
        mkdir -p "$MAC_LOCAL_DIR/runs/r1/run/step1/release/testflight-OTHER"; echo other > "$MAC_LOCAL_DIR/runs/r1/run/step1/release/testflight-OTHER/summary.txt"
    fi
    [ -z "${STUB_RUN_MARK:-}" ] || echo started > "$STUB_RUN_MARK"; [ -z "${STUB_RUN_SLEEP:-}" ] || sleep "$STUB_RUN_SLEEP"; exit "${STUB_RUN_RC:-0}" ;;
fetch) exit "${STUB_FETCH_RC:-0}" ;;
esac
EOS
chmod +x "$T/bin/"*  "$T/mac-remote"
printf 'ASC_KEY_ID=KEYID12345\nASC_ISSUER_ID=issuer-0000\n' > "$T/secrets/asc-api.env"
: > "$T/secrets/AuthKey_KEYID12345.p8.gpg"

mk_repo() {   # fresh temp repo with jsword, kdom, docs/superpowers submodules
    R="$T/repo"; rm -rf "$R" "$T/sub" "$STUB_DIR" "$T/maclocal"; mkdir -p "$STUB_DIR" "$T/sub"
    local s
    for s in jsword kdom superpowers; do
        git init -q "$T/sub/$s"; git -C "$T/sub/$s" -c user.email=t@t -c user.name=t commit -q --allow-empty -m init
    done
    git init -q "$R"; cd "$R" || return 1
    git config user.email t@t; git config user.name t
    mkdir -p scripts app/src/test
    cp "$REAL_REPO/scripts/lib-version.sh" scripts/
    printf 'versionMajorMinor=6.0\nversionCode=1250\nversionNameSuffix=\n' > version.properties
    git -c protocol.file.allow=always submodule add -q "$T/sub/jsword" jsword
    git -c protocol.file.allow=always submodule add -q "$T/sub/kdom" kdom
    git -c protocol.file.allow=always submodule add -q "$T/sub/superpowers" docs/superpowers
    git -c protocol.file.allow=always submodule add -q "$T/sub/superpowers" app/src/test/roborazzi
    git add -A; git commit -q -m init
    echo "build lock: free" > "$STUB_DIR/check.out"
}
build() {   # build [args]: run ios-build with the stubs; output in $T/out, status in $RC
    PATH="$T/bin:$PATH" STUB_DIR="$STUB_DIR" STUB_SENTINEL="$SENTINEL" REPO_ROOT="$R" MAC_REMOTE="$T/mac-remote" MAC_LOCAL_DIR="$T/maclocal" \
        RELEASE_SECRETS_SRC="$T/secrets" RELEASE_RAMDIR_MOUNT=none TMPDIR="$T/tmp" HOME="$T/home" \
        MAC_SSH_CONFIG=/dev/null MAC_HOST=testmac bash "$SCRIPT" "$@" > "$T/out" 2>&1
    RC=$?
}
ramdirs() { find "$T/tmp" -mindepth 1 -maxdepth 1 | wc -l; }
count() { [ -f "$1" ] && wc -l < "$1" || echo 0; }

test_dry_run_order() {
    mk_repo; build --dry-run || true
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local o; o=$(grep -n '' "$T/out")
    local a b c d e f
    a=$(grep -n '^build ' "$T/out" | cut -d: -f1); b=$(grep -n 'gpg --decrypt' "$T/out" | cut -d: -f1)
    c=$(grep -n 'ram://40960' "$T/out" | cut -d: -f1); d=$(grep -n 'archive.sh' "$T/out" | cut -d: -f1)
    e=$(grep -n ' fetch ' "$T/out" | cut -d: -f1); f=$(grep -n 'EXIT trap' "$T/out" | cut -d: -f1)
    [ -n "$a" ] && [ "$a" -lt "$b" ] && [ "$b" -lt "$c" ] && [ "$c" -lt "$d" ] && [ "$d" -lt "$e" ] && [ "$e" -lt "$f" ] \
        || return "$(fail "order wrong: $o")"
    grep -q '<ramdir>/key.p8' "$T/out" || return "$(fail "no <ramdir>/key.p8")"
    grep -q '^version 6.0.1250$' "$T/out" && grep -q "^commit $(git rev-parse --short=10 HEAD)$" "$T/out" || return "$(fail "version/commit lines: $(cat "$T/out")")"
    grep -q 'X6A6CDK8T7 .* 6.0.1250 .* upload"' "$T/out" || return "$(fail "archive argv: $(grep archive.sh "$T/out")")"
    [ "$(count "$STUB_DIR/mrcalls")" = 1 ] && [ "$(cat "$STUB_DIR/mrcalls")" = check ] || return "$(fail "mac-remote calls: $(cat "$STUB_DIR/mrcalls")")"
    [ ! -e "$STUB_DIR/calls" ] && [ ! -e "$STUB_DIR/gpgcalls" ] || return "$(fail "ssh/gpg ran in dry run")"
}
test_key_never_in_argv_or_output() {
    mk_repo; build
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -q 'ram://40960' "$STUB_DIR/argv" || return "$(fail "stream step did not run")"
    grep -rq "$SENTINEL" "$STUB_DIR/argv" "$T/out" && return "$(fail "sentinel leaked into argv/output")"
    grep -q 'release-secrets\.' "$STUB_DIR/argv" "$T/out" && return "$(fail "RAM dir path leaked")"
    grep -q "^stdin $((${#SENTINEL} + 1))$" "$STUB_DIR/stdin" || return "$(fail "key not streamed on stdin: $(cat "$STUB_DIR/stdin")")"
    grep -q 'testflight rc=0 in [0-9]*s (destination upload)' "$T/out" || return "$(fail "final line: $(tail -3 "$T/out")")"
    [ ! -e "$STUB_DIR/mrcalls" ] || ! grep -qx fetch "$STUB_DIR/mrcalls" || return "$(fail "fetch used although the run capture exists")"
    true
}
evdir() { echo "$R/.local/release/testflight/$(sed -n 's/^build //p' "$T/out")"; }
test_evidence_from_run_capture() {
    mk_repo; build
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    local e; e=$(evdir)
    for f in archive.log summary.txt release.entitlements ipa-entitlements.txt; do [ -f "$e/$f" ] || return "$(fail "missing $f in $e")"; done
    [ ! -e "$e/export" ] && [ ! -e "$e/ipa" ] && [ ! -e "$e/upload" ] || return "$(fail "big dirs copied: $(ls "$e")")"
    [ "$(cat "$e/summary.txt")" = "result=PASS upload" ] || return "$(fail "wrong summary (other build picked?)")"
    # detach exactly once, before the evidence step, and no fetch
    [ "$(grep -c 'hdiutil detach' "$STUB_DIR/argv")" = 1 ] || return "$(fail "detach count: $(grep -c 'hdiutil detach' "$STUB_DIR/argv")")"
    ! grep -qx fetch "$STUB_DIR/mrcalls" || return "$(fail "fetch called")"
}
test_fetch_fallback_when_capture_missing() {
    mk_repo; export STUB_CAPTURE=0; build; unset STUB_CAPTURE
    [ "$RC" = 0 ] || return "$(fail "rc=$RC: $(cat "$T/out")")"
    grep -qx fetch "$STUB_DIR/mrcalls" || return "$(fail "no fetch fallback")"
    grep -q 'mac-remote fetch build/release/testflight-.*/ .*/.local/release/testflight/' "$STUB_DIR/argv" || return "$(fail "fetch argv")"
    export STUB_CAPTURE=0 STUB_FETCH_RC=1; build; unset STUB_CAPTURE STUB_FETCH_RC
    [ "$RC" = 0 ] || return "$(fail "missing evidence turned a good run into rc=$RC")"
    grep -q 'WARNING' "$T/out" || return "$(fail "no warning: $(cat "$T/out")")"
    export STUB_CAPTURE=0 STUB_FETCH_RC=1 STUB_RUN_RC=4; build; unset STUB_CAPTURE STUB_FETCH_RC STUB_RUN_RC
    [ "$RC" = 4 ] || return "$(fail "run rc lost: $RC")"
}
test_tree_edited_during_decrypt_refuses() {
    mk_repo; export STUB_GPG_DIRTY=1; build; unset STUB_GPG_DIRTY
    [ "$RC" != 0 ] && grep -q 'not clean' "$T/out" || return "$(fail "rc=$RC $(cat "$T/out")")"
    ! grep -qx run "$STUB_DIR/mrcalls" || return "$(fail "run called on an edited tree")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_detach_ssh_is_bounded() {
    mk_repo; export STUB_DETACH_SLEEP=30 STUB_DETACH_EXEC=1 MAC_DETACH_TIMEOUT=1 MAC_DETACH_KILL_AFTER=1
    local t0=$SECONDS; build; unset STUB_DETACH_SLEEP STUB_DETACH_EXEC MAC_DETACH_TIMEOUT MAC_DETACH_KILL_AFTER
    [ $((SECONDS - t0)) -lt 15 ] || return "$(fail "stalled detach not bounded: $((SECONDS - t0))s")"
    grep -q 'WARNING: could not detach' "$T/out" || return "$(fail "no warning: $(cat "$T/out")")"
    grep -q 'ServerAliveInterval=10' "$STUB_DIR/argv" && grep -q 'ServerAliveCountMax=3' "$STUB_DIR/argv" || return "$(fail "no keepalive options")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_dirty_parent_refuses() {
    mk_repo; echo x > untracked.txt; build
    [ "$RC" != 0 ] && grep -q 'not clean' "$T/out" || return "$(fail "rc=$RC $(cat "$T/out")")"
    [ ! -e "$STUB_DIR/mrcalls" ] || return "$(fail "mac-remote called")"
    rm -f untracked.txt
    echo more >> version.properties; build
    [ "$RC" != 0 ] || return "$(fail "tracked change accepted")"
}
test_dirty_gitlink_refuses() {
    mk_repo; echo x > jsword/dirty.txt; build
    [ "$RC" != 0 ] || return "$(fail "untracked file in jsword accepted")"
    rm jsword/dirty.txt
    git -C kdom -c user.email=t@t -c user.name=t commit -q --allow-empty -m moved; build
    [ "$RC" != 0 ] && grep -q kdom "$T/out" || return "$(fail "moved kdom gitlink accepted: rc=$RC")"
}
test_dirty_superpowers_allowed() {
    mk_repo; echo x > docs/superpowers/new.txt
    git -C docs/superpowers -c user.email=t@t -c user.name=t commit -q --allow-empty -m moved
    echo y > app/src/test/roborazzi/new.png
    build --dry-run
    [ "$RC" = 0 ] || return "$(fail "rc=$RC $(cat "$T/out")")"
}
test_lock_held_stops_before_decrypt() {
    mk_repo
    printf 'BUILD LOCK held by: other-box [holder alive; held for 3 min]\n  holder is another container (other-box); waiting\n' > "$STUB_DIR/check.out"
    build
    [ "$RC" != 0 ] && grep -q 'other-box' "$T/out" || return "$(fail "rc=$RC $(cat "$T/out")")"
    [ "$(count "$STUB_DIR/gpgcalls")" = 0 ] && [ "$(count "$STUB_DIR/calls")" = 0 ] || return "$(fail "gpg/ssh ran")"
    # a lock held by this very container is fine
    printf 'BUILD LOCK held by: me [x]\n  holder is THIS container (me): local pid 1\n' > "$STUB_DIR/check.out"
    build --dry-run
    [ "$RC" = 0 ] || return "$(fail "own lock refused: $(cat "$T/out")")"
}
test_detach_on_run_failure() {
    mk_repo
    export STUB_RUN_RC=3; build; unset STUB_RUN_RC
    [ "$RC" = 3 ] || return "$(fail "rc=$RC, want 3")"
    grep -q 'hdiutil detach' "$STUB_DIR/argv" || return "$(fail "no detach")"
    grep -q 'testflight rc=3' "$T/out" || return "$(fail "final line")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
    # stream failure: detach too
    mk_repo; export STUB_SSH_RC=1; build; unset STUB_SSH_RC
    [ "$RC" != 0 ] && [ "$(grep -c 'hdiutil detach' "$STUB_DIR/argv")" -ge 1 ] || return "$(fail "no detach after stream failure rc=$RC")"
    [ "$(count "$STUB_DIR/mrcalls")" = 1 ] || return "$(fail "run called after failed stream")"
}
test_detach_on_interrupt() {
    mk_repo; export STUB_RUN_SLEEP=20 STUB_RUN_MARK="$T/runmark"; rm -f "$T/runmark"
    PATH="$T/bin:$PATH" STUB_DIR="$STUB_DIR" STUB_SENTINEL="$SENTINEL" REPO_ROOT="$R" MAC_REMOTE="$T/mac-remote" MAC_LOCAL_DIR="$T/maclocal" \
        RELEASE_SECRETS_SRC="$T/secrets" RELEASE_RAMDIR_MOUNT=none TMPDIR="$T/tmp" HOME="$T/home" \
        MAC_SSH_CONFIG=/dev/null MAC_HOST=testmac \
        perl -e '$SIG{INT}="DEFAULT"; setpgrp(0,0); exec @ARGV' bash "$SCRIPT" > "$T/out" 2>&1 &
    local pid=$! i
    unset STUB_RUN_SLEEP STUB_RUN_MARK
    for i in $(seq 100); do [ -e "$T/runmark" ] && break; sleep 0.1; done
    [ -e "$T/runmark" ] || { kill -9 -- -"$pid" 2>/dev/null; return "$(fail "run never started: $(cat "$T/out")")"; }
    kill -INT -- -"$pid"
    wait "$pid"; RC=$?
    [ "$RC" = 130 ] || return "$(fail "rc=$RC, want 130: $(cat "$T/out")")"
    grep -q 'hdiutil detach' "$STUB_DIR/argv" || return "$(fail "no detach after SIGINT")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_second_interrupt_during_detach() {
    mk_repo; export STUB_RUN_SLEEP=20 STUB_RUN_MARK="$T/runmark" STUB_DETACH_SLEEP=2; rm -f "$T/runmark"
    PATH="$T/bin:$PATH" STUB_DIR="$STUB_DIR" STUB_SENTINEL="$SENTINEL" REPO_ROOT="$R" MAC_REMOTE="$T/mac-remote" MAC_LOCAL_DIR="$T/maclocal" \
        RELEASE_SECRETS_SRC="$T/secrets" RELEASE_RAMDIR_MOUNT=none TMPDIR="$T/tmp" HOME="$T/home" \
        MAC_SSH_CONFIG=/dev/null MAC_HOST=testmac \
        perl -e '$SIG{INT}="DEFAULT"; setpgrp(0,0); exec @ARGV' bash "$SCRIPT" > "$T/out" 2>&1 &
    local pid=$! i
    unset STUB_RUN_SLEEP STUB_RUN_MARK STUB_DETACH_SLEEP
    for i in $(seq 100); do [ -e "$T/runmark" ] && break; sleep 0.1; done
    [ -e "$T/runmark" ] || { kill -9 -- -"$pid" 2>/dev/null; return "$(fail "run never started")"; }
    kill -INT -- -"$pid"
    for i in $(seq 100); do [ -e "$STUB_DIR/detach-started" ] && break; sleep 0.1; done
    [ -e "$STUB_DIR/detach-started" ] || { kill -9 -- -"$pid" 2>/dev/null; return "$(fail "detach never started")"; }
    kill -INT -- -"$pid"   # second Ctrl-C while the detach ssh runs
    wait "$pid"; RC=$?
    [ -e "$STUB_DIR/detach-done" ] || return "$(fail "detach interrupted by the second SIGINT")"
    [ "$RC" = 130 ] || return "$(fail "rc=$RC, want 130")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left")"
}
test_failed_detach_warns() {
    mk_repo
    export STUB_SSH_RC=1; build; unset STUB_SSH_RC
    grep -q 'WARNING: could not detach /Volumes/asckey-' "$T/out" || return "$(fail "no detach warning: $(cat "$T/out")")"
    grep -q "scripts/mac-remote.sh ssh 'hdiutil detach -force /Volumes/asckey-" "$T/out" || return "$(fail "no manual command: $(cat "$T/out")")"
    # the detach itself failing (remote command nonzero because the volume is still there) also warns
    mk_repo; export STUB_DETACH_RC=1; build; unset STUB_DETACH_RC
    grep -q 'WARNING: could not detach /Volumes/asckey-' "$T/out" || return "$(fail "no warning on failed detach: $(cat "$T/out")")"
    grep -q '! -e /Volumes/asckey-' "$STUB_DIR/argv" || return "$(fail "remote command does not verify the volume is gone")"
}
test_ramdir_gone_after_success() {
    mk_repo; build
    [ "$RC" = 0 ] && [ "$(ramdirs)" = 0 ] || return "$(fail "rc=$RC ramdirs=$(ramdirs)")"
}
test_ramdir_gone_after_failed_decrypt() {
    mk_repo; export STUB_GPG_RC=2; build; unset STUB_GPG_RC
    [ "$RC" != 0 ] || return "$(fail "success despite failed decrypt")"
    [ "$(ramdirs)" = 0 ] || return "$(fail "ramdir left: $(ls "$T/tmp")")"
    [ ! -e "$STUB_DIR/calls" ] || return "$(fail "ssh ran after failed decrypt")"
}
test_build_number_utc_format() {
    mk_repo; build --dry-run
    grep -Eq '^build 20[0-9]{2}\.[01][0-9][0-3][0-9]\.[0-2][0-9][0-5][0-9][0-5][0-9]$' "$T/out" || return "$(fail "$(grep '^build' "$T/out")")"
}
test_export_only_passes_export() {
    mk_repo; build --export-only
    [ "$RC" = 0 ] || return "$(fail "rc=$RC")"
    grep -q '^mac-remote run .* export$' "$STUB_DIR/argv" || return "$(fail "$(grep 'mac-remote run' "$STUB_DIR/argv")")"
    grep -q '(destination export)' "$T/out" || return "$(fail "final line")"
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
