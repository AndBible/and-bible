#!/usr/bin/env bash
# Tests for mac-remote.sh without a Mac: ssh and rsync are replaced by local fakes, the "Mac" is a
# temp directory (its $HOME), and `bash -s` runs on this machine. Covers the build lock, the sync
# order, run markers, check/cancel, batch, native-tests --force and the xcode-test evidence dirs.
# Run: scripts/test-mac-remote.sh [name-filter]   (takes about a minute)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/mac-remote.sh"
T="$(mktemp -d)"
FILTER="${1:-}"
bg_pids=()
cleanup() { for p in "${bg_pids[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null; done; rm -rf "$T"; }
trap cleanup EXIT
pass=0; failn=0; cur=
fail() { echo "FAIL [$cur]: $*"; failn=$((failn + 1)); return 1; }

mkdir -p "$T/bin"
# ssh: skip the option arguments, then `host command...`; run the command on this machine with the
# fake Mac's $HOME (stdin passes through, so `bash -s` reads the script as the real one does).
cat > "$T/bin/fake-ssh" <<'EOS'
#!/usr/bin/env bash
while [ $# -gt 0 ]; do
    case "$1" in -F|-o) shift 2 ;; -t|-T) shift ;; *) break ;; esac
done
shift   # host
cd "$FAKE_HOME" && HOME="$FAKE_HOME" exec bash -c "$*"
EOS
# rsync: drop -e/--rsync-path, map host:path onto the fake home, record whether the lock was held at a push.
cat > "$T/bin/fake-rsync" <<'EOS'
#!/usr/bin/env bash
args=(); push=
while [ $# -gt 0 ]; do
    case "$1" in
        -e) shift 2 ;;
        --rsync-path=*) shift ;;
        andbible-mac:*) args+=("$FAKE_HOME/${1#andbible-mac:}"); shift ;;
        *) args+=("$1"); shift ;;
    esac
done
case "${args[${#args[@]}-1]}" in "$FAKE_HOME"/*) push=1 ;; esac
[ -n "$push" ] && echo "rsync-push lock=$([ -d "$FAKE_HOME/work/.build-lock" ] && echo held || echo free)" >> "$EVENTS"
exec rsync "${args[@]}"
EOS
chmod +x "$T/bin/fake-ssh" "$T/bin/fake-rsync"

# Stand-ins for the Mac tools the remote scripts call.
cat > "$T/bin/xcrun" <<'EOS'
#!/usr/bin/env bash
case "$*" in
    "simctl list devices available -j") echo '{"devices":{"rt":[{"name":"iPhone 16","udid":"UDID-IPHONE"},{"name":"iPad Air 11-inch (M4)","udid":"UDID-IPAD"}]}}' ;;
    "simctl bootstatus "*) ;;
    "simctl spawn "*) exec sleep 600 ;;
    "xcresulttool export attachments --path "*)
        out="${*##* }"; mkdir -p "$out"; echo shot > "$out/a.png"
        echo '[{"testIdentifier":"T/t()","attachments":[{"exportedFileName":"a.png","suggestedHumanReadableName":"split_0_11111111-1111-1111-1111-111111111111.png"}]}]' > "$out/manifest.json" ;;
esac
exit 0
EOS
cat > "$T/bin/xcodegen" <<'EOS'
#!/usr/bin/env bash
exit 0
EOS
cat > "$T/bin/npm" <<'EOS'
#!/usr/bin/env bash
exit 0
EOS
cat > "$T/bin/xcodebuild" <<'EOS'
#!/usr/bin/env bash
echo "xcodebuild $*" >> "$EVENTS"
while [ $# -gt 0 ]; do [ "$1" = -resultBundlePath ] && { mkdir -p "$2"; echo "run-${XC_MARK:-x}" > "$2/marker"; }; shift; done
[ -n "${XC_SLEEP:-}" ] && sleep "$XC_SLEEP"
echo "Test Case '-[T t]' passed"
exit "${XC_RC:-0}"
EOS
chmod +x "$T/bin/"*

new_env() {   # fresh fake Mac, fake repo and result dir for one test
    cur="$1"
    rm -rf "$T/mac" "$T/repo" "$T/out" "$T/events"
    mkdir -p "$T/mac/work" "$T/repo/app/bibleview-js" "$T/repo/iosApp" "$T/out"
    : > "$T/events"; : > "$T/repo/app/bibleview-js/package-lock.json"
    cat > "$T/repo/gradlew" <<'EOS'
#!/usr/bin/env bash
echo "gradlew $*" >> "$EVENTS"
[ "${1:-}" != --stop ] && [ -n "${GRADLE_SLEEP:-}" ] && sleep "$GRADLE_SLEEP"
exit "${GRADLE_RC:-0}"
EOS
    chmod +x "$T/repo/gradlew"
    echo ssh-config > "$T/ssh-config"
    export FAKE_HOME="$T/mac" EVENTS="$T/events" MAC_REPO_DIR="$T/repo" MAC_LOCAL_DIR="$T/out" \
        MAC_CONTAINER=testbox MAC_SSH_CONFIG="$T/ssh-config" MAC_SSH_BIN="$T/bin/fake-ssh" \
        MAC_RSYNC_BIN="$T/bin/fake-rsync" MAC_LOCK_POLL=0.2 MAC_LOCK_MSG_EVERY=1 MAC_LOCK_WAIT=30 \
        PATH="$T/bin:$PATH"
    unset MAC_TIMEOUT GRADLE_RC GRADLE_SLEEP XC_RC XC_SLEEP XC_MARK
    LOCK="$FAKE_HOME/work/.build-lock"
}
mr() { "$SCRIPT" "$@"; }
pstart_of() { ps -p "$1" -o lstart= | tr -s ' ' | sed 's/^ //;s/ $//'; }
dead_pid() { sleep 0 & local p=$!; wait "$p"; echo "$p"; }
# mk_owner <container> <pid> [token] [pstart]: a lock record as the script writes it
mk_owner() {
    mkdir -p "$LOCK"
    local ps="${4:-$(pstart_of "$2")}"
    printf '%s pid %s since now\ntoken=%s\ncontainer=%s\npid=%s\npstart=%s\nepoch=%s\nlpid=4242\nrun=r-other\n' \
        "$1" "$2" "${3:-tok-$1}" "$1" "$2" "$ps" "$(date +%s)" > "$LOCK/owner"
}
owner_field() { sed -n "s/^$1=//p" "$LOCK/owner" 2>/dev/null | head -1; }
ev() { cat "$EVENTS"; }
run_test() { [ -z "$FILTER" ] || [[ "$1" == *$FILTER* ]] || return 0; local f=$failn; "$1"; [ "$failn" = "$f" ] && pass=$((pass + 1)); return 0; }

# ---------------------------------------------------------------- build lock, sync order
t_gradle_exit_code_and_lock_released() {
    new_env gradle-exit
    out=$(GRADLE_RC=3 mr gradle :x:y 2>&1); rc=$?
    [ $rc = 3 ] || fail "exit code $rc, want 3: $out"
    grep -q "gradlew -Dorg.gradle.native=false :x:y" "$EVENTS" || fail "gradlew not run: $(ev)"
    [ ! -e "$LOCK" ] || fail "lock left behind"
}
t_sync_happens_after_lock_acquired() {
    new_env sync-after-lock
    mr gradle :a >/dev/null 2>&1
    grep -qx "rsync-push lock=held" "$EVENTS" || fail "sync did not run under the lock: $(ev)"
    grep -q "lock=free" "$EVENTS" && fail "a sync ran before the lock: $(ev)"
    # a waiter edits the tree while it waits: the run must see the edit made before it got the lock
    sleep 30 & local hp=$!; bg_pids+=("$hp")
    mk_owner other "$hp"
    echo v1 > "$T/repo/marker.txt"
    mr run 'cat marker.txt' > "$T/o.txt" 2>/dev/null &
    local w=$!; bg_pids+=("$w"); sleep 1
    echo v2 > "$T/repo/marker.txt"; kill "$hp"
    wait "$w"
    grep -qx v2 "$T/o.txt" || fail "run saw a tree older than its lock acquisition: $(cat "$T/o.txt")"
}
t_owner_record_has_token_pid_start() {
    new_env owner-record
    mr run "cp \$HOME/work/.build-lock/owner \$HOME/owner.copy" >/dev/null 2>&1
    local o="$FAKE_HOME/owner.copy"
    for k in token container pid pstart epoch lpid run; do grep -q "^$k=." "$o" || fail "owner lacks $k: $(cat "$o")"; done
    grep -q '^container=testbox$' "$o" || fail "container: $(cat "$o")"
    [ "$(sed -n 's/^token=//p' "$o" | wc -c)" -ge 16 ] || fail "token too short: $(cat "$o")"
}
t_waiter_times_out_behind_live_foreign_holder() {
    new_env foreign-holder
    sleep 30 & local hp=$!; bg_pids+=("$hp")
    mk_owner other "$hp"
    MAC_LOCK_WAIT=1 out=$(mr gradle :a 2>&1); rc=$?
    [ $rc = 75 ] || fail "exit $rc, want 75: $out"
    grep -q "waiting .*held by: other pid $hp" <<<"$out" || fail "no wait message: $out"
    grep -q "this container" <<<"$out" && fail "foreign holder reported as this container: $out"
    [ -d "$LOCK" ] || fail "foreign lock was removed"
    kill "$hp"
}
t_wait_message_warns_when_holder_is_this_container() {
    new_env own-holder-warning
    sleep 30 & local hp=$!; bg_pids+=("$hp")
    mk_owner testbox "$hp"
    MAC_LOCK_WAIT=1 out=$(mr gradle :a 2>&1); rc=$?
    [ $rc = 75 ] || fail "exit $rc: $out"
    grep -q "held by this container: probably a forgotten run of yours (local pid 4242" <<<"$out" || fail "no own-holder warning: $out"
    kill "$hp"
}
t_stale_lock_dead_pid_is_broken() {
    new_env stale-dead
    mk_owner ghost "$(dead_pid)" tok-ghost "Mon Jan  1 00:00:00 2024"
    out=$(mr gradle :a 2>&1); rc=$?
    [ $rc = 0 ] || fail "exit $rc: $out"
    grep -q "breaking a stale build lock" <<<"$out" || fail "no stale message: $out"
    [ ! -e "$LOCK" ] || fail "lock left behind"
    ls "$FAKE_HOME/work" | grep -q 'build-lock\.' && fail "leftover lock dirs: $(ls -a "$FAKE_HOME/work")"
}
t_stale_lock_pid_reused_by_other_process_is_broken() {
    new_env stale-reuse
    sleep 30 & local hp=$!; bg_pids+=("$hp")   # a live process, but not the one that took the lock
    mk_owner ghost "$hp" tok-ghost "Mon Jan  1 00:00:00 2024"
    out=$(MAC_LOCK_WAIT=10 mr gradle :a 2>&1); rc=$?
    [ $rc = 0 ] || fail "pid reuse kept a dead holder's lock alive (exit $rc): $out"
    kill "$hp"
}
t_live_holder_with_matching_start_is_not_broken() {
    new_env live-not-broken
    sleep 30 & local hp=$!; bg_pids+=("$hp")
    mk_owner other "$hp"
    MAC_LOCK_WAIT=1 mr gradle :a >/dev/null 2>&1
    grep -q "breaking" <<<"$(cat "$EVENTS")" && fail "broke a live lock"
    [ "$(owner_field token)" = tok-other ] || fail "live lock was modified"
    kill "$hp"
}
t_concurrent_stale_breakers_exactly_one_wins() {
    new_env two-breakers
    mk_owner ghost "$(dead_pid)" tok-ghost "Mon Jan  1 00:00:00 2024"
    local cmd='echo start >> $HOME/crit.log; sleep 1; echo end >> $HOME/crit.log'
    mr run "$cmd" > "$T/a.out" 2>&1 & local a=$!
    mr run "$cmd" > "$T/b.out" 2>&1 & local b=$!
    wait "$a"; local ra=$?; wait "$b"; local rb=$?
    [ $ra = 0 ] && [ $rb = 0 ] || fail "exit codes $ra $rb: $(cat "$T/a.out" "$T/b.out")"
    [ "$(tr '\n' ' ' < "$FAKE_HOME/crit.log")" = "start end start end " ] || fail "critical sections overlapped: $(tr '\n' ' ' < "$FAKE_HOME/crit.log")"
    local n; n=$(cat "$T/a.out" "$T/b.out" | grep -c "breaking a stale build lock")
    [ "$n" = 1 ] || fail "$n breakers won, want 1: $(cat "$T/a.out" "$T/b.out")"
}
t_release_only_removes_own_token() {
    new_env release-own-token
    mr run "rm -rf \$HOME/work/.build-lock; mkdir \$HOME/work/.build-lock; echo 'x pid 1 since y' > \$HOME/work/.build-lock/owner; echo token=foreign >> \$HOME/work/.build-lock/owner" >/dev/null 2>&1
    [ "$(owner_field token)" = foreign ] || fail "exit trap removed a lock it does not own"
}

for t in t_gradle_exit_code_and_lock_released t_sync_happens_after_lock_acquired t_owner_record_has_token_pid_start \
    t_waiter_times_out_behind_live_foreign_holder t_wait_message_warns_when_holder_is_this_container \
    t_stale_lock_dead_pid_is_broken t_stale_lock_pid_reused_by_other_process_is_broken \
    t_live_holder_with_matching_start_is_not_broken t_concurrent_stale_breakers_exactly_one_wins \
    t_release_only_removes_own_token; do run_test "$t"; done

echo "mac-remote tests: $pass passed, $failn failed"
[ "$failn" = 0 ] && echo "MAC-REMOTE OK"
exit $((failn > 0))
