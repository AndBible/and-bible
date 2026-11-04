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
cleanup() { [ -n "${KEEP:-}" ] && { echo "kept $T"; return; }; for p in "${bg_pids[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null; done; rm -rf "$T"; }
trap cleanup EXIT
pass=0; failn=0; cur=
fail() {
    echo "FAIL [$cur]: $*"
    failn=$((failn + 1))
    if [ -n "${KEEP:-}" ]; then
        mkdir -p "$T/failures/$cur"
        cp -R "$T/out/runs" "$T/failures/$cur/" 2>/dev/null || true
    fi
    return 1
}

mkdir -p "$T/bin"
# ssh: skip the option arguments, then `host command...`; run the command on this machine with the
# fake Mac's $HOME (stdin passes through, so `bash -s` reads the script as the real one does).
cat > "$T/bin/fake-ssh" <<'EOS'
#!/usr/bin/env bash
while [ $# -gt 0 ]; do
    case "$1" in -F|-o) shift 2 ;; -t|-T) shift ;; *) break ;; esac
done
shift   # host
cd "$FAKE_HOME" || exit 1
export HOME="$FAKE_HOME"
# Model ssh's exec of the remote interpreter without a redundant bash -c wait
# layer (Bash 5.3 can report "wait_for: No record of process" in that layer).
if [ "$*" = 'bash -s' ]; then exec bash -s; fi
exec bash -c "$*"
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
[ -z "$push" ] && [ -n "${FAKE_RSYNC_PULL_SLEEP:-}" ] && sleep "$FAKE_RSYNC_PULL_SLEEP"
[ -z "$push" ] && [ -n "${FAKE_RSYNC_FAIL_PULL:-}" ] && exit 23
[ -n "$push" ] && echo "rsync-push lock=$([ -d "$FAKE_HOME/work/.build-lock" ] && echo held || echo free)" >> "$EVENTS"
exec rsync "${args[@]}"
EOS
chmod +x "$T/bin/fake-ssh" "$T/bin/fake-rsync"

# Stand-ins for the Mac tools the remote scripts call.
cat > "$T/bin/xcrun" <<'EOS'
#!/usr/bin/env bash
case "$*" in
    "simctl list devices available -j") echo '{"devices":{"rt":[{"name":"iPhone 16","udid":"UDID-IPHONE"},{"name":"iPad Air 11-inch (M4)","udid":"UDID-IPAD"}]}}' ;;
    "simctl bootstatus "*) touch "$FAKE_HOME/booted"; [ -n "${XC_BOOT_SLEEP:-}" ] && sleep "$XC_BOOT_SLEEP" ;;
    "simctl list devices booted") [ -f "$FAKE_HOME/booted" ] && echo "    iPhone 16 (11111111-2222-3333-4444-555555555555) (Booted)" ;;
    "simctl shutdown all") echo "simctl-shutdown-all" >> "$EVENTS"; rm -f "$FAKE_HOME/booted" ;;
    "simctl spawn "*) exec sleep 600 ;;
    "xcresulttool export attachments --path "*)
        out="${@: -1}"; mkdir -p "$out"; echo shot > "$out/a.png"
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
mkdir -p node_modules
exit "${NPM_RC:-0}"
EOS
cat > "$T/bin/xcodebuild" <<'EOS'
#!/usr/bin/env bash
echo "xcodebuild $*" >> "$EVENTS"
env | grep '^TEST_RUNNER_' | sed 's/^/env /' >> "$EVENTS"
while [ $# -gt 0 ]; do [ "$1" = -resultBundlePath ] && { mkdir -p "$2"; echo "run-${XC_MARK:-x}" > "$2/marker"; }; shift; done
[ -n "${XC_SLEEP:-}" ] && sleep "$XC_SLEEP"
echo "Test Case '-[T t]' passed"
exit "${XC_RC:-0}"
EOS
# BSD-flavoured stat/date/vm_stat shims for the fake Mac (the remote code uses `stat -f %m`, `date -r <epoch>`)
cat > "$T/bin/stat" <<'EOS'
#!/usr/bin/env bash
if [ "${1:-}" = -f ] && [ "${2:-}" = "%m" ]; then shift 2; exec /usr/bin/stat -c %Y "$@"; fi
exec /usr/bin/stat "$@"
EOS
cat > "$T/bin/date" <<'EOS'
#!/usr/bin/env bash
if [ "${1:-}" = -r ] && [[ "${2:-}" =~ ^[0-9]+$ ]]; then e=$2; shift 2; exec /usr/bin/date -d "@$e" "$@"; fi
exec /usr/bin/date "$@"
EOS
chmod +x "$T/bin/"*
cp "$T/bin/xcodebuild" "$T/bin/xcodebuild.original"

new_env() {   # fresh fake Mac, fake repo and result dir for one test
    cur="$1"
    rm -rf "$T/mac" "$T/repo" "$T/out" "$T/events"
    mkdir -p "$T/mac/work" "$T/repo/app/bibleview-js" "$T/repo/iosApp" "$T/out"
    : > "$T/events"; : > "$T/repo/app/bibleview-js/package-lock.json"
    cat > "$T/repo/gradlew" <<'EOS'
#!/usr/bin/env bash
echo "gradlew $*" >> "$EVENTS"
for a in "$@"; do case "$a" in :*:iosSimulatorArm64Test) m="${a#:}"; m="${m%%:*}"; echo "results $m present=$([ -d "$m/build/test-results/iosSimulatorArm64Test" ] && echo yes || echo no)" >> "$EVENTS" ;; esac; done
[ "${1:-}" != --stop ] && [ -n "${GRADLE_SLEEP:-}" ] && sleep "$GRADLE_SLEEP"
exit "${GRADLE_RC:-0}"
EOS
    chmod +x "$T/repo/gradlew"
    echo ssh-config > "$T/ssh-config"
    export FAKE_HOME="$T/mac" EVENTS="$T/events" MAC_REPO_DIR="$T/repo" MAC_LOCAL_DIR="$T/out" \
        MAC_CONTAINER=testbox MAC_SSH_CONFIG="$T/ssh-config" MAC_SSH_BIN="$T/bin/fake-ssh" \
        MAC_RSYNC_BIN="$T/bin/fake-rsync" MAC_LOCK_POLL=0.2 MAC_LOCK_MSG_EVERY=1 MAC_LOCK_WAIT=30 \
        PATH="$T/bin:$PATH"
    export MAC_OWN_TREE=1
    unset MAC_WORKDIR MAC_TIMEOUT MAC_STEP_MARGIN MAC_IDLE_MAX MAC_KEEP_RUNS FAKE_RSYNC_PULL_SLEEP FAKE_RSYNC_FAIL_PULL XC_BOOT_SLEEP GRADLE_RC GRADLE_SLEEP XC_RC XC_SLEEP XC_MARK NPM_RC GRADLE_USER_HOME
    unset MAC_REMOTE_GRADLE_OPTS MAC_REMOTE_ANDROID_HOME MAC_REMOTE_LOCAL_PROPERTIES

    LOCK="$FAKE_HOME/work/.build-lock"
}
mr() { "$SCRIPT" "$@"; }
pstart_of() { LC_ALL=C TZ=UTC0 ps -p "$1" -o lstart= | tr -s ' ' | sed 's/^ //;s/ $//'; }
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
    mr run 'cat marker.txt' > "$T/o.txt" 2>"$T/sync-wait.err" &
    local w=$!; bg_pids+=("$w"); sleep 1
    echo v2 > "$T/repo/marker.txt"; kill "$hp"
    wait "$w"; local waiter_rc=$?
    [ "$waiter_rc" = 0 ] || fail "sync waiter exit $waiter_rc: $(cat "$T/sync-wait.err")"
    # run_begin's process-substitution tee can drain after its leader exits.
    wait_for 'grep -qx v2 "$T/o.txt"' || fail "run saw a tree older than its lock acquisition: $(cat "$T/o.txt"); stderr=$(cat "$T/sync-wait.err")"
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
    "$SCRIPT" run "$cmd" > "$T/a.out" 2>&1 & local a=$!
    "$SCRIPT" run "$cmd" > "$T/b.out" 2>&1 & local b=$!
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

# ---------------------------------------------------------------- queue admission (legacy clients ignore queue tickets)
queue_count() { mr queue | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'; }
t_queue_fifo_three_waiters_behind_legacy_holder() {
    new_env queue-fifo
    sleep 60 & local hp=$!; bg_pids+=("$hp")
    # Oldest clients write only this first line; they must still block new clients.
    mkdir "$LOCK"; echo "legacy pid $$ since now" > "$LOCK/owner"
    local a b c
    MAC_CONTAINER=first "$SCRIPT" run 'echo first >> $HOME/order; sleep 1' > "$T/a.out" 2>&1 & a=$!; bg_pids+=("$a")
    wait_for '[ "$(queue_count)" = 1 ]' || { fail "first not queued"; return; }
    MAC_CONTAINER=second "$SCRIPT" run 'echo second >> $HOME/order; sleep 1' > "$T/b.out" 2>&1 & b=$!; bg_pids+=("$b")
    wait_for '[ "$(queue_count)" = 2 ]' || { fail "second not queued"; return; }
    MAC_CONTAINER=third "$SCRIPT" run 'echo third >> $HOME/order' > "$T/c.out" 2>&1 & c=$!; bg_pids+=("$c")
    wait_for '[ "$(queue_count)" = 3 ]' || { fail "third not queued"; return; }
    [ ! -e "$FAKE_HOME/order" ] && [ ! -s "$EVENTS" ] || fail "new client bypassed legacy holder"
    rm -rf "$LOCK"; kill "$hp"
    wait "$a" && wait "$b" && wait "$c" || fail "waiters failed: $(cat "$T/a.out" "$T/b.out" "$T/c.out")"
    [ "$(tr '\n' ' ' < "$FAKE_HOME/order")" = 'first second third ' ] || fail "non-FIFO admission"
    [ "$(queue_count)" = 0 ] || fail "acquired tickets leaked"
}
t_queue_timeout_and_cancel_remove_waiters() {
    new_env queue-cancel
    sleep 60 & local hp=$!; bg_pids+=("$hp")
    mk_owner other "$hp"
    MAC_LOCK_WAIT=1 mr gradle :a >/dev/null 2>&1; local rc=$?
    [ "$rc" = 75 ] && [ "$(queue_count)" = 0 ] || fail "timeout leaked ticket or wrong code $rc"
    "$SCRIPT" run 'echo should-not-run > $HOME/cancelled' > "$T/waiter.out" 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ "$(queue_count)" = 1 ]' || { fail "waiter not queued"; return; }
    mr cancel >/dev/null 2>&1
    wait "$p" 2>/dev/null
    wait_for '[ "$(queue_count)" = 0 ]' || fail "cancelled head leaked ticket"
    [ "$(owner_field token)" = tok-other ] || fail "cancel touched foreign legacy holder"
    kill "$hp"
    mr run 'true' >/dev/null 2>&1 || fail "cancelled head blocked successor"
    [ ! -e "$FAKE_HOME/cancelled" ] || fail "cancelled waiter ran later"
}
t_queue_controls_use_orchestrator_group_not_child_name() {
    new_env queue-group
    local reservation listing
    reservation=$(JAILBEE_ROLE=orchestrator MAC_CONTAINER=parent mr queue reserve 60) || { fail "reserve failed"; return; }
    listing=$(JAILBEE_ROLE=child JAILBEE_ORCHESTRATOR=parent MAC_CONTAINER=kid mr queue)
    grep -q '"group": "parent"' <<<"$listing" || fail "child could not list group"
    JAILBEE_ROLE=child JAILBEE_ORCHESTRATOR=parent MAC_CONTAINER=kid mr queue release "$reservation" >/dev/null 2>&1 && fail "child controlled queue"
    JAILBEE_ROLE=orchestrator MAC_CONTAINER=outsider mr queue release "$reservation" >/dev/null 2>&1 && fail "foreign orchestrator controlled queue"
    JAILBEE_ROLE=orchestrator MAC_CONTAINER=parent mr queue release "$reservation" || fail "group orchestrator could not release"
    [ "$(queue_count)" = 0 ] || fail "reservation leaked"
}
t_queue_legacy_client_respects_new_holder_and_can_overtake_reservation() {
    new_env queue-legacy
    "$SCRIPT" run 'echo start >> $HOME/legacy-order; sleep 2; echo end >> $HOME/legacy-order' > "$T/new.out" 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$FAKE_HOME/legacy-order" ]' || { fail "new holder not running"; return; }
    # Same rename(2) gate used by the old script, without consulting tickets.
    python3 - "$LOCK" <<'PY' || fail "legacy gate ignored new holder"
import os, pathlib, sys, tempfile
lock = pathlib.Path(sys.argv[1])
pending = pathlib.Path(tempfile.mkdtemp(dir=lock.parent))
(pending / 'owner').write_text('legacy owner')
try:
    try:
        os.rename(pending, lock)
    except OSError:
        pass
    else:
        raise SystemExit('legacy client entered a new holder critical section')
finally:
    (pending / 'owner').unlink()
    pending.rmdir()
PY
    wait "$p" || fail "new holder failed"
    local reservation
    reservation=$(JAILBEE_ROLE=orchestrator mr queue reserve 60) || { fail "reserve failed"; return; }
    # Reservations do NOT create placeholder execution locks. Legacy clients
    # are permitted to overtake a reservation, without weakening exclusivity.
    mkdir "$LOCK" || fail "reservation unexpectedly took legacy lock"
    echo "legacy pid $$ since now" > "$LOCK/owner"
    JAILBEE_ROLE=orchestrator mr queue release "$reservation" || fail "release failed"
    [ -f "$LOCK/owner" ] || fail "reservation release touched legacy lock"
    rm -rf "$LOCK"
}
t_queue_child_registration_inherits_group_and_priority_changes_admission() {
    new_env queue-priority
    local reservation a b
    reservation=$(JAILBEE_ROLE=orchestrator MAC_CONTAINER=parent mr queue reserve 60) || { fail "reserve failed"; return; }
    JAILBEE_ROLE=child JAILBEE_ORCHESTRATOR=parent MAC_CONTAINER=kid1 "$SCRIPT" run 'echo kid1 >> $HOME/priority-order' > "$T/a.out" 2>&1 & a=$!; bg_pids+=("$a")
    wait_for '[ "$(queue_count)" = 2 ]' || { fail "first child not queued"; return; }
    JAILBEE_ROLE=child JAILBEE_ORCHESTRATOR=parent MAC_CONTAINER=kid2 "$SCRIPT" run 'echo kid2 >> $HOME/priority-order' > "$T/b.out" 2>&1 & b=$!; bg_pids+=("$b")
    wait_for '[ "$(queue_count)" = 3 ]' || { fail "second child not queued"; return; }
    local ticket listing
    listing=$(mr queue)
    ticket=$(python3 -c 'import json,sys; print(next(r["ticket"] for r in json.load(sys.stdin) if r["identity"] == "kid2" and r["group"] == "parent"))' <<<"$listing")
    [ -n "$ticket" ] || { fail "child group not inherited"; return; }
    JAILBEE_ROLE=orchestrator MAC_CONTAINER=parent mr queue priority "$ticket" 5 || fail "priority rejected"
    wait "$b" || fail "promoted child failed"
    [ "$(cat "$FAKE_HOME/priority-order")" = kid2 ] || fail "reservation/priority admission broken"
    JAILBEE_ROLE=orchestrator MAC_CONTAINER=parent mr queue release "$reservation" || fail "release failed"
    wait "$a" || fail "first child failed"
    [ "$(tr '\n' ' ' < "$FAKE_HOME/priority-order")" = 'kid2 kid1 ' ] || fail "priority not respected"
}
t_queue_cancel_after_head_check_cannot_admit() {
    new_env queue-cancel-race
    # Pause the actual legacy owner-record preparation after eligibility was
    # observed. Cancellation completes before the pending rename can resume.
    cat > "$T/bin/ps" <<'EOS'
#!/usr/bin/env bash
if [[ "$*" == *'lstart='* ]] && [ "$(find "$FAKE_HOME/work" -maxdepth 1 -name '.build-lock.pending.*' | wc -l)" -gt 0 ] && mkdir "$FAKE_HOME/pause-once" 2>/dev/null; then
    touch "$FAKE_HOME/admission-paused"
    while [ ! -f "$FAKE_HOME/admission-resume" ]; do sleep 0.05; done
fi
exec /bin/ps "$@"
EOS
    chmod +x "$T/bin/ps"
    "$SCRIPT" run 'touch $HOME/ran-after-cancel' > "$T/race.out" 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$FAKE_HOME/admission-paused" ]' || { fail "admission did not pause"; rm -f "$T/bin/ps"; return; }
    local d token
    d=$(last_run); token=$(kv "$d/meta" token)
    # Model a lost local client: remote waiter survives; cancellation can only
    # coordinate through its token, not by killing the lost local PID.
    sed -i 's/^pstart=.*/pstart=lost-client/' "$d/meta"
    mr cancel >/dev/null 2>&1 || fail "cancel failed"
    touch "$FAKE_HOME/admission-resume"
    wait "$p" 2>/dev/null
    [ ! -e "$FAKE_HOME/ran-after-cancel" ] || fail "cancelled ticket admitted from stale eligibility"
    [ ! -e "$LOCK" ] || fail "cancelled waiter acquired legacy gate"
    rm -f "$T/bin/ps"
}
t_cancel_corrupt_queue_releases_matching_holder_and_remains_retryable() {
    new_env cancel-corrupt
    sleep 60 & local hp=$!; bg_pids+=("$hp")
    mk_owner testbox "$hp" tok-corrupt
    local d="$T/out/runs/19990101-000000-run-1" out rc
    mkdir -p "$d" "$FAKE_HOME/work/.mac-queue"
    printf 'id=lost\npid=99999999\npstart=gone\nstart_epoch=1\ncmd=run lost\ntoken=tok-corrupt\n' > "$d/meta"
    echo '{broken' > "$FAKE_HOME/work/.mac-queue/state.json"
    out=$(mr cancel 2>&1); rc=$?
    [ "$rc" = 74 ] || fail "queue cleanup failure hidden: exit $rc $out"
    [ ! -e "$LOCK" ] || fail "corrupt queue prevented matching holder release: $out"
    wait_for '! kill -0 "$hp" 2>/dev/null' || fail "matching holder survived queue failure"
    [ -f "$d/cancel-pending" ] || fail "failed cancellation is not recoverable"
    grep -q 'queue cleanup failed' <<<"$out" || fail "failure/retry guidance missing: $out"
    # Explicit operator recovery of bad metadata, then retry the SAME run.
    rm "$FAKE_HOME/work/.mac-queue/state.json"
    out=$(mr cancel 2>&1); rc=$?
    [ "$rc" = 0 ] && [ ! -f "$d/cancel-pending" ] || fail "cancellation retry failed: $out"
    [ "$(kv "$d/exit" reason)" = cancelled ] || fail "recovered cancellation not recorded"
}
t_queue_missing_embedded_helper_fails_closed() {
    new_env queue-missing-helper
    mkdir -p "$T/standalone"
    cp "$SCRIPT" "$T/standalone/mac-remote.sh"
    local out rc
    out=$(bash "$T/standalone/mac-remote.sh" run true 2>&1); rc=$?
    [ "$rc" != 0 ] && grep -q 'queue helper' <<<"$out" || fail "missing helper did not fail closed: $out"
    [ ! -e "$T/out/runs" ] && [ ! -s "$EVENTS" ] || fail "missing helper started work"
}
t_queue_rejects_parallel_slots_before_connecting() {
    new_env queue-slots
    local out rc
    out=$(MAC_SLOTS=2 mr run true 2>&1); rc=$?
    [ "$rc" != 0 ] && grep -q 'MAC_SLOTS.*1' <<<"$out" || fail "unsafe slots accepted: $out"
    [ ! -e "$T/out/runs" ] && [ ! -s "$EVENTS" ] || fail "unsafe slots started work"
}

# ---------------------------------------------------------------- run markers
last_run() { ls -1d "$T/out/runs"/*/ 2>/dev/null | sort | tail -1 | sed 's:/$::'; }
kv() { sed -n "s/^$2=//p" "$1" | head -1; }
# stop what a killed/cancelled fake run left on the "Mac" (the real Mac's watchdog does this)
reap_remote() {
    local pid; pid=$(owner_field pid)
    [ -z "$pid" ] || { pkill -TERM -P "$pid" 2>/dev/null; kill -TERM "$pid" 2>/dev/null; }
    sleep 0.3; rm -rf "$LOCK"
}
wait_for() { local i; for i in $(seq 1 100); do eval "$1" && return 0; sleep 0.1; done; return 1; }

t_marker_written_on_success() {
    new_env marker-success
    mr gradle :a >/dev/null 2>&1
    local d; d=$(last_run); [ -n "$d" ] || { fail "no run dir"; return; }
    for k in id container cmd pid pstart start_epoch token log; do [ -n "$(kv "$d/meta" $k)" ] || fail "meta lacks $k: $(cat "$d/meta")"; done
    grep -q 'gradle :a' "$d/meta" || fail "meta cmd: $(cat "$d/meta")"
    [ "$(kv "$d/exit" code)" = 0 ] && [ "$(kv "$d/exit" reason)" = ok ] || fail "exit marker: $(cat "$d/exit" 2>&1)"
    [ -n "$(kv "$d/exit" end_epoch)" ] || fail "no end time"
    grep -q 'synced to' "$d/output.log" || fail "output.log lacks the run output"
    [ "$(cut -f2 "$d/steps" | tr '\n' ' ')" = "start end " ] || fail "steps: $(cat "$d/steps")"
}
t_marker_records_failure_code() {
    new_env marker-failure
    GRADLE_RC=3 mr gradle :a >/dev/null 2>&1
    local d; d=$(last_run)
    [ "$(kv "$d/exit" code)" = 3 ] && [ "$(kv "$d/exit" reason)" = failed ] && [ "$(kv "$d/exit" failed_step)" = 1 ] || fail "exit marker: $(cat "$d/exit")"
}
t_marker_records_timeout() {
    new_env marker-timeout
    out=$(MAC_TIMEOUT=1 GRADLE_SLEEP=5 mr gradle :a 2>&1); rc=$?
    [ $rc = 142 ] || fail "exit $rc, want 142: $out"
    local d; d=$(last_run)
    [ "$(kv "$d/exit" code)" = 142 ] && [ "$(kv "$d/exit" reason)" = timeout ] || fail "exit marker: $(cat "$d/exit")"
    grep -q "TIMEOUT after 1s" <<<"$out" || fail "no timeout message: $out"
    ! grep -q 'gradlew --stop' "$EVENTS" || fail "timeout stopped another client's daemon"
    [ ! -e "$LOCK" ] || fail "lock left behind after a timeout"
}
t_marker_records_killed_run() {
    new_env marker-killed
    "$SCRIPT" run 'sleep 20' > /dev/null 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -n "$(last_run)" ] && [ -f "$LOCK/owner" ] && grep -q sleep <(ps -eo args)' || { fail "run did not start"; return; }
    kill -TERM "$p"; wait "$p" 2>/dev/null
    local d; d=$(last_run)
    [ "$(kv "$d/exit" code)" = 143 ] && [ "$(kv "$d/exit" reason)" = "signal TERM" ] || fail "exit marker after kill: $(cat "$d/exit" 2>&1)"
    reap_remote
}

# ---------------------------------------------------------------- check
t_check_shows_running_run_and_own_lock() {
    new_env check-running
    "$SCRIPT" run 'sleep 6' > /dev/null 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$LOCK/owner" ] && grep -q "sleep 6" <(ps -eo args)' || { fail "run did not start"; return; }
    sleep 1.2
    out=$(mr check 2>&1)
    grep -q "RUNNING since .*local pid $p" <<<"$out" || fail "no RUNNING line: $out"
    grep -q "output.log idle" <<<"$out" || fail "no log idle time: $out"
    grep -q "BUILD LOCK held by: testbox pid" <<<"$out" || fail "no lock line: $out"
    grep -q "holder is THIS container (testbox): local pid $p" <<<"$out" || fail "own holder not flagged: $out"
    grep -q "held for [0-9]* min" <<<"$out" || fail "no holder age: $out"
    wait "$p"
    out=$(mr check 2>&1)
    grep -q "exited 0 (ok) after" <<<"$out" || fail "no exited line: $out"
    grep -q "build lock: free" <<<"$out" || fail "lock not free: $out"
}
t_check_other_holder_and_timeout_and_lost() {
    new_env check-other
    sleep 30 & local hp=$!; bg_pids+=("$hp")
    mk_owner someone-else "$hp"
    MAC_TIMEOUT=1 GRADLE_SLEEP=3 MAC_LOCK_WAIT=0 mr gradle :a >/dev/null 2>&1
    # a lost run: marker without exit and a dead process
    local d="$T/out/runs/19990101-000000-run-1"; mkdir -p "$d"
    printf 'id=x\npid=%s\npstart=Mon Jan  1 00:00:00 2024\nstart_epoch=%s\ncmd=run old\ntoken=t\n' "$(dead_pid)" "$(date +%s)" > "$d/meta"
    out=$(mr check 2>&1)
    grep -q "holder is another container (someone-else)" <<<"$out" || fail "other holder: $out"
    grep -q "holder is THIS container" <<<"$out" && fail "foreign holder flagged as own: $out"
    grep -q "LOST: process gone without an exit marker" <<<"$out" || fail "lost run not shown: $out"
    kill "$hp"
    out=$(mr check 2>&1)
    grep -q "holder GONE: stale" <<<"$out" || fail "dead holder not shown stale: $out"
}

# ---------------------------------------------------------------- cancel
t_cancel_kills_only_own_pids_and_releases_matching_lock() {
    new_env cancel-own
    sleep 40 & local by=$!; bg_pids+=("$by")      # bystander
    # a finished run of this container whose pid now belongs to the bystander: must be left alone
    local old="$T/out/runs/19990101-000000-run-1"; mkdir -p "$old"
    printf 'id=old\npid=%s\npstart=%s\nstart_epoch=1\ncmd=run old\ntoken=oldtok\n' "$by" "$(pstart_of "$by")" > "$old/meta"; echo code=0 > "$old/exit"
    # a running-looking record whose pid was reused by another process (start time differs): not ours to kill
    local reuse="$T/out/runs/19990101-000001-run-2"; mkdir -p "$reuse"
    printf 'id=reuse\npid=%s\npstart=Mon Jan  1 00:00:00 2024\nstart_epoch=1\ncmd=run reused\ntoken=reusetok\n' "$by" > "$reuse/meta"
    "$SCRIPT" run 'sleep 40' > "$T/run.out" 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$LOCK/owner" ] && grep -q "sleep 40" <(ps -eo args | grep -v grep)' || { fail "run did not start"; return; }
    local d tok; d=$(ls -1d "$T/out/runs"/*/ | sort | tail -1 | sed 's:/$::'); tok=$(kv "$d/meta" token)
    [ "$(owner_field token)" = "$tok" ] || fail "lock token $(owner_field token) is not the run token $tok"
    out=$(mr cancel 2>&1); rc=$?
    [ $rc = 0 ] || fail "cancel exit $rc: $out"
    kill -0 "$p" 2>/dev/null && fail "own run still alive after cancel: $out"
    kill -0 "$by" 2>/dev/null || fail "cancel killed a bystander process: $out"
    [ "$(kv "$d/exit" reason)" = "signal TERM" ] || [ "$(kv "$d/exit" reason)" = cancelled ] || fail "no exit marker for the cancelled run: $(cat "$d/exit" 2>&1)"
    [ ! -e "$LOCK" ] || fail "matching lock not released: $out"
    [ "$(kv "$old/exit" code)" = 0 ] && ! grep -q cancelled "$old/exit" || fail "finished run record was modified"
    kill "$by"
}
t_cancel_releases_lock_of_a_lost_run_and_stops_its_holder() {
    new_env cancel-lost
    # a run whose container side is gone (killed hard) while the Mac side still holds the lock under its token
    sleep 40 & local hp=$!; bg_pids+=("$hp")
    mk_owner testbox "$hp" tok-lost
    local d="$T/out/runs/19990101-000000-run-1"; mkdir -p "$d"
    printf 'id=lostrun\npid=%s\npstart=Mon Jan  1 00:00:00 2024\nstart_epoch=1\ncmd=run lost\ntoken=tok-lost\n' "$(dead_pid)" > "$d/meta"
    out=$(mr cancel 2>&1)
    grep -q "lost run" <<<"$out" || fail "lost run not reported: $out"
    grep -q "released the remote lock" <<<"$out" || fail "no release message: $out"
    [ ! -e "$LOCK" ] || fail "lock of the lost run not released"
    wait_for '! kill -0 "$hp" 2>/dev/null' || fail "the Mac-side holder process was not stopped"
    [ "$(kv "$d/exit" reason)" = cancelled ] || fail "lost run not closed: $(cat "$d/exit" 2>&1)"
}
t_cancel_leaves_foreign_lock_alone() {
    new_env cancel-foreign
    "$SCRIPT" run 'touch "$HOME/cancel-ready"; sleep 8' > /dev/null 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$FAKE_HOME/cancel-ready" ]' || { fail "run did not start"; return; }
    # the Mac lock changed hands (e.g. the old one was broken as stale): the owner token is another run's
    local mine; mine=$(owner_field token)
    sed -i 's/^token=.*/token=someone-elses-token/' "$LOCK/owner"
    out=$(mr cancel 2>&1)
    kill -0 "$p" 2>/dev/null && fail "own run still alive: $out"
    [ "$(owner_field token)" = someone-elses-token ] || fail "cancel released a lock it does not own: $out"
    grep -q "left alone" <<<"$out" || fail "no left-alone message: $out"
    sleep 8.5; rm -rf "$LOCK"
}
t_cancel_with_nothing_to_cancel() {
    new_env cancel-none
    out=$(mr cancel 2>&1); rc=$?
    [ $rc = 0 ] && grep -q "no running or lost run" <<<"$out" || fail "exit $rc: $out"
}

# ---------------------------------------------------------------- batch
t_batch_stops_at_first_failing_step() {
    new_env batch-stop
    out=$(mr batch "run echo step-one" "run echo step-two; exit 3" "run echo step-three" 2>&1); rc=$?
    [ $rc = 3 ] || fail "exit $rc, want 3: $out"
    grep -q step-one <<<"$out" || fail "step 1 did not run: $out"
    grep -q '^step-three' <<<"$out" && fail "step 3 ran after a failure: $out"
    grep -q "step 1: .* -> exit 0" <<<"$out" && grep -q "step 2: .* -> exit 3" <<<"$out" && grep -q "step 3: .* -> not run" <<<"$out" || fail "per-step results: $out"
    [ "$(grep -c 'build lock acquired' <<<"$out")" = 1 ] || fail "lock taken more than once: $out"
    [ "$(grep -c '^rsync-push' "$EVENTS")" = 1 ] || fail "synced more than once: $(ev)"
    local d; d=$(last_run)
    [ "$(kv "$d/exit" code)" = 3 ] && [ "$(kv "$d/exit" failed_step)" = 2 ] || fail "one batch exit marker expected: $(cat "$d/exit")"
    [ "$(ls "$T/out/runs" | wc -l)" = 1 ] || fail "batch wrote several run dirs"
    [ ! -e "$LOCK" ] || fail "lock left behind"
}
t_batch_keep_going_runs_every_step() {
    new_env batch-keep
    out=$(mr batch --keep-going "run echo step-one; exit 4" "run echo step-two" "run echo step-three; exit 6" 2>&1); rc=$?
    [ $rc = 4 ] || fail "exit $rc, want the first failing step's 4: $out"
    grep -q '^step-two' <<<"$out" && grep -q '^step-three' <<<"$out" || fail "steps after a failure did not run: $out"
    grep -q "step 1: .* -> exit 4" <<<"$out" && grep -q "step 2: .* -> exit 0" <<<"$out" && grep -q "step 3: .* -> exit 6" <<<"$out" || fail "per-step results: $out"
    grep -q "2 of 3 steps failed" <<<"$out" || fail "no failure count: $out"
    [ "$(grep -c 'build lock acquired' <<<"$out")" = 1 ] || fail "lock taken more than once: $out"
    local d; d=$(last_run)
    [ "$(kv "$d/exit" code)" = 4 ] && [ "$(kv "$d/exit" failed_step)" = 1 ] || fail "exit marker should name the first failure: $(cat "$d/exit")"
    [ ! -e "$LOCK" ] || fail "lock left behind"
}
t_batch_keep_going_stops_after_a_failed_build_step() {
    new_env batch-keep-build
    # a failed link/gradle step must not let later test steps run (and pass) against the previous build
    out=$(GRADLE_RC=3 mr batch --keep-going "gradle :x:y" "run echo after-build" 2>&1); rc=$?
    [ $rc != 0 ] && grep -q "step 2: .* -> not run" <<<"$out" && ! grep -q '^after-build' <<<"$out" || fail "a failed build step should stop the batch, exit $rc: $out"
}
t_batch_keep_going_with_no_sync() {
    new_env batch-keep-nosync
    # no remote tree yet: the session is lost at step 1, so --keep-going must not try step 2
    out=$(mr batch --no-sync --keep-going "run exit 2" "run echo after" 2>&1); rc=$?
    [ $rc != 0 ] && ! grep -qx after <<<"$out" || fail "missing-tree preflight should stop the batch, exit $rc: $out"
    mr sync >/dev/null 2>&1 || fail "sync failed"
    local pushes; pushes=$(grep -c '^rsync-push' "$EVENTS")
    out=$(mr batch --keep-going --no-sync "run exit 2" "run echo after" 2>&1); rc=$?
    [ $rc = 2 ] && grep -q '^after' <<<"$out" || fail "exit $rc: $out"
    [ "$(grep -c '^rsync-push' "$EVENTS")" = "$pushes" ] || fail "synced despite --no-sync: $(ev)"
}
t_batch_holds_lock_across_steps() {
    new_env batch-lock
    "$SCRIPT" batch 'run echo a1 >> $HOME/o.log; sleep 1' 'run echo a2 >> $HOME/o.log; sleep 1' > "$T/a.out" 2>&1 & local a=$!; bg_pids+=("$a")
    wait_for '[ -f "$LOCK/owner" ]' || { fail "batch did not start"; return; }
    mr run 'echo b >> $HOME/o.log' > "$T/b.out" 2>&1 &
    local b=$!; bg_pids+=("$b")
    wait "$a"; wait "$b"
    [ "$(tr '\n' ' ' < "$FAKE_HOME/o.log")" = "a1 a2 b " ] || fail "a waiter got in between the batch steps: $(tr '\n' ' ' < "$FAKE_HOME/o.log")"
}
t_batch_link_and_unknown_step() {
    new_env batch-link
    mr batch link >/dev/null 2>&1 || fail "batch link failed"
    grep -q 'gradlew -Dorg.gradle.native=false :sharedUi:linkDebugFrameworkIosSimulatorArm64' "$EVENTS" || fail "link step: $(ev)"
    : > "$EVENTS"
    out=$(mr batch link bogus 2>&1); rc=$?
    [ $rc != 0 ] && grep -q "unknown step 'bogus'" <<<"$out" || fail "unknown step accepted (exit $rc): $out"
    [ ! -s "$EVENTS" ] || fail "something ran before the steps were validated: $(ev)"
}
t_batch_native_then_xcode_sequence() {
    new_env batch-seq
    out=$(mr batch "native-tests jsword" "xcode-test iphone -only-testing:iosAppUITests/PocUITests" "xcode-test ipad" 2>&1); rc=$?
    [ $rc = 0 ] || fail "exit $rc: $out"
    [ "$(grep -c 'build lock acquired' <<<"$out")" = 1 ] || fail "lock more than once"
    grep -q 'xcodebuild test .*-only-testing:iosAppUITests/PocUITests' "$EVENTS" || fail "-only-testing not passed: $(ev)"
    local d; d=$(last_run)
    [ -d "$d/xcode/step2-iphone/ios-iphone.xcresult" ] && [ -d "$d/xcode/step3-ipad/ios-ipad.xcresult" ] || fail "per-step evidence: $(ls -R "$d/xcode" | head -20)"
}

# ---------------------------------------------------------------- native-tests --force, gradle warning
t_native_tests_force_deletes_results() {
    new_env native-force
    mr native-tests jsword >/dev/null 2>&1 || fail "first native-tests failed"
    local tree="$FAKE_HOME/work/testbox/and-bible" m
    for m in jsword kdom sharedLogic; do mkdir -p "$tree/$m/build/test-results/iosSimulatorArm64Test"; echo old > "$tree/$m/build/test-results/iosSimulatorArm64Test/TEST-x.xml"; done
    : > "$EVENTS"
    mr native-tests jsword kdom >/dev/null 2>&1 || fail "plain rerun failed"
    grep -qx "results jsword present=yes" "$EVENTS" && grep -qx "results kdom present=yes" "$EVENTS" || fail "a plain rerun must keep the results (UP-TO-DATE): $(ev)"
    : > "$EVENTS"
    mr native-tests --force jsword kdom >/dev/null 2>&1 || fail "forced rerun failed"
    grep -qx "results jsword present=no" "$EVENTS" && grep -qx "results kdom present=no" "$EVENTS" || fail "--force did not delete the results before running: $(ev)"
    [ -d "$tree/sharedLogic/build/test-results/iosSimulatorArm64Test" ] || fail "--force deleted a module that was not requested"
}
t_native_tests_boots_simulator_and_fetches() {
    new_env native-fetch
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"
    # the gradle stub writes a result XML like the real task
    cat > "$T/repo/gradlew" <<'EOS'
#!/usr/bin/env bash
echo "gradlew $* sim=${IOS_TEST_SIMULATOR:-unset}" >> "$EVENTS"
for a in "$@"; do case "$a" in :*:iosSimulatorArm64Test) m="${a#:}"; m="${m%%:*}"; mkdir -p "$m/build/test-results/iosSimulatorArm64Test"; echo "<testsuite/>" > "$m/build/test-results/iosSimulatorArm64Test/TEST-$m.xml" ;; esac; done
exit 0
EOS
    out=$(mr native-tests sharedCore 2>&1); rc=$?
    [ $rc = 0 ] || fail "exit $rc: $out"
    grep -q 'sim=UDID-IPHONE' "$EVENTS" || fail "IOS_TEST_SIMULATOR not set to the booted simulator: $(ev)"
    [ -f "$T/out/native-tests/sharedCore/test-results/iosSimulatorArm64Test/TEST-sharedCore.xml" ] || fail "XML not fetched: $(find "$T/out/native-tests" | head)"
}
t_gradle_warns_for_native_test_task() {
    new_env gradle-warn
    out=$(mr gradle :sharedUi:iosSimulatorArm64Test 2>&1)
    grep -q "WARNING.*iosSimulatorArm64Test.*without a booted simulator.*native-tests" <<<"$out" || fail "no warning: $out"
    grep -q 'gradlew .*:sharedUi:iosSimulatorArm64Test' "$EVENTS" || fail "the task was not run (it should still run)"
    out=$(mr gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64 2>&1)
    grep -q WARNING <<<"$out" && fail "warned for an unrelated task: $out"
    out=$(mr native-tests jsword 2>&1)
    grep -q WARNING <<<"$out" && fail "native-tests warned: $out"
}

# ---------------------------------------------------------------- xcode-test evidence, timeout, env
t_xcode_test_evidence_per_run_with_latest_links() {
    new_env xcode-evidence
    mkdir -p "$T/out/xcode/screenshots-iphone"; echo old > "$T/out/xcode/screenshots-iphone/old.txt"   # old layout: a real dir
    XC_MARK=A mr xcode-test iphone >/dev/null 2>&1 || fail "run A failed"
    local a; a=$(last_run); sleep 1.1
    TEST_RUNNER_FOO=bar XC_MARK=B mr xcode-test iphone -only-testing:iosAppUITests/X >/dev/null 2>&1 || fail "run B failed"
    local b; b=$(last_run)
    [ "$a" != "$b" ] || { fail "both runs share one run dir"; return; }
    [ "$(cat "$a/xcode/step1-iphone/ios-iphone.xcresult/marker")" = run-A ] || fail "run A evidence missing or overwritten: $(ls -R "$a/xcode" | head)"
    [ "$(cat "$b/xcode/step1-iphone/ios-iphone.xcresult/marker")" = run-B ] || fail "run B evidence missing"
    [ -f "$a/xcode/step1-iphone/screenshots-iphone/T_t/split.png" ] || fail "screenshot not renamed from the manifest: $(find "$a/xcode" -name '*.png')"
    [ -f "$a/xcode/step1-iphone/xcodebuild-iphone.log" ] && [ -f "$a/xcode/step1-iphone/app-iphone.log" ] || fail "logs not fetched"
    [ -L "$T/out/xcode/ios-iphone.xcresult" ] || fail "compat path is not a link"
    [ "$(cat "$T/out/xcode/ios-iphone.xcresult/marker")" = run-B ] || fail "compat path does not point at the latest run"
    [ -L "$T/out/xcode/screenshots-iphone" ] && [ -f "$T/out/xcode/screenshots-iphone/T_t/split.png" ] || fail "screenshots compat link (replacing the old real dir)"
    [ -f "$T/out/xcode/xcodebuild-iphone.log" ] || fail "log compat path"
    grep -q '^env TEST_RUNNER_FOO=bar' "$EVENTS" || fail "TEST_RUNNER_* not forwarded: $(ev)"
}
t_xcode_test_default_timeout_and_override() {
    new_env xcode-timeout
    out=$(MAC_TIMEOUT=1 XC_SLEEP=5 mr xcode-test iphone 2>&1); rc=$?
    [ $rc = 142 ] || fail "exit $rc, want 142: $out"
    grep -q "TIMEOUT: xcodebuild killed after 1s" <<<"$out" || fail "no timeout message: $out"
    [ "$(kv "$(last_run)/exit" reason)" = timeout ] || fail "marker: $(cat "$(last_run)/exit")"
    # the defaults: 2400 s for xcode-test, 3000 s for the others (the generated step script is kept in the run dir)
    mr xcode-test iphone >/dev/null 2>&1
    grep -q 'tmo 2400 xcodebuild test' "$(last_run)/step-1.sh" || fail "xcode-test default is not 2400 s: $(grep -n tmo "$(last_run)/step-1.sh")"
    mr gradle :a >/dev/null 2>&1
    grep -q 'tmo 3000 ./gradlew' "$(last_run)/step-1.sh" || fail "gradle default is not 3000 s"
    MAC_TIMEOUT=77 mr xcode-test iphone >/dev/null 2>&1
    grep -q 'tmo 77 xcodebuild test' "$(last_run)/step-1.sh" || fail "MAC_TIMEOUT override ignored for xcode-test"
    out=$(mr xcode-test jphone 2>&1); [ $? != 0 ] && grep -q "device must be iphone or ipad" <<<"$out" || fail "bad device accepted: $out"
}
t_heartbeat_shows_in_output_log() {
    new_env heartbeat
    MAC_HEARTBEAT=1 mr run 'sleep 3' >/dev/null 2>&1
    grep -q "heartbeat (step 1): newest build log:" "$(last_run)/output.log" || fail "no heartbeat: $(cat "$(last_run)/output.log")"
}
t_sim_boot_goes_through_lock_and_marker() {
    new_env sim-boot
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"   # sim steps do not sync: the tree exists from earlier runs
    out=$(mr sim boot 2>&1); rc=$?
    [ $rc = 0 ] && grep -qx "UDID-IPHONE" <<<"$out" || fail "exit $rc: $out"
    grep -q lock=free "$EVENTS" && fail "sim boot synced"
    [ "$(kv "$(last_run)/exit" code)" = 0 ] || fail "no marker for sim boot"
}

# ---------------------------------------------------------------- review fixes
t_step_output_cannot_spoof_the_result() {
    new_env spoof
    out=$(mr batch "run echo x@@STEP-RC 0; exit 5" "run echo second" 2>&1); rc=$?
    [ $rc = 5 ] || fail "exit $rc, want 5 (output spoofed the sentinel): $out"
    grep -qx second <<<"$out" && fail "step 2 ran after a failed step 1: $out"
    grep -q "step 2: .* -> not run" <<<"$out" || fail "no per-step result: $out"
    out=$(mr run 'echo @@LOCKED; echo @@STEP-RC-nope 0; exit 4' 2>&1); rc=$?
    [ $rc = 4 ] || fail "run exit $rc, want 4: $out"
}
t_bad_device_in_batch_is_rejected_before_locking() {
    new_env batch-bad-device
    out=$(mr batch "run echo one" "xcode-test jphone" 2>&1); rc=$?
    [ $rc != 0 ] && grep -q "device must be iphone or ipad" <<<"$out" || fail "bad device accepted (exit $rc): $out"
    [ ! -s "$EVENTS" ] && [ ! -e "$T/out/runs" ] || fail "a run started before validation: $(ev) $(ls "$T/out" 2>&1)"
}
t_idle_guard_releases_lock_when_container_side_stalls() {
    new_env idle-guard
    # the container side stalls in fetches after the step (a dropped network would look the same): the guard frees the lock
    MAC_TRANSFER_TIMEOUT=1 FAKE_RSYNC_PULL_SLEEP=3 "$SCRIPT" native-tests jsword > /dev/null 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$LOCK/owner" ]' || { fail "run did not start"; return; }
    wait "$p"; local rc=$?
    [ "$rc" != 0 ] || fail 'stalled transfer reported success'
    [ ! -e "$LOCK" ] || fail 'bounded transfer left lock'
}
t_timeout_kills_the_whole_process_group() {
    new_env timeout-group
    rm -f "$FAKE_HOME/survivor"
    MAC_TIMEOUT=1 mr run '(sleep 3; touch $HOME/survivor) & sleep 30' >/dev/null 2>&1; rc=$?
    [ $rc = 142 ] || fail "exit $rc, want 142"
    sleep 3.5
    [ ! -e "$FAKE_HOME/survivor" ] || fail "a child of the timed-out command survived the timeout"
}
t_step_watchdog_bounds_work_outside_the_inner_timeouts() {
    new_env step-watchdog
    local t0=$SECONDS
    out=$(MAC_TIMEOUT=1 MAC_STEP_MARGIN=1 XC_BOOT_SLEEP=30 mr native-tests jsword 2>&1); rc=$?
    [ $rc = 142 ] || fail "exit $rc, want 142 (simulator boot hang not bounded): $out"
    [ $((SECONDS - t0)) -lt 15 ] || fail "took $((SECONDS - t0)) s"
    [ "$(kv "$(last_run)/exit" reason)" = timeout ] || fail "marker: $(cat "$(last_run)/exit")"
    [ ! -e "$LOCK" ] || fail "lock left behind"
}
t_timeouts_are_142_everywhere() {
    new_env timeout-codes
    MAC_TIMEOUT=1 GRADLE_SLEEP=5 mr native-tests jsword >/dev/null 2>&1; rc=$?
    [ $rc = 142 ] || fail "native-tests timeout exit $rc, want 142"
    MAC_TIMEOUT=1 XC_SLEEP=5 mr xcode-build iphone >/dev/null 2>&1; rc=$?
    [ $rc = 142 ] || fail "xcode-build timeout exit $rc, want 142"
}
t_live_holder_is_not_broken_under_another_timezone() {
    new_env tz-liveness
    sleep 30 & local hp=$!; bg_pids+=("$hp")
    mk_owner other "$hp"
    out=$(TZ=Pacific/Auckland LC_ALL=C MAC_LOCK_WAIT=1 mr gradle :a 2>&1)
    grep -q "breaking" <<<"$out" && fail "a live holder was judged dead under another TZ: $out"
    [ "$(owner_field token)" = tok-other ] || fail "live lock modified"
    kill "$hp"
}
t_pruning_keeps_a_live_runs_directory() {
    new_env prune-live
    "$SCRIPT" run 'sleep 3' >/dev/null 2>&1 & local a=$!; bg_pids+=("$a")
    wait_for '[ -f "$LOCK/owner" ]' || { fail "run did not start"; return; }
    local live; live=$(last_run); sleep 1.1
    MAC_KEEP_RUNS=1 mr run 'true' >/dev/null 2>&1
    [ -d "$live" ] || fail "the live run's directory was pruned"
    wait "$a"
}
t_abandoned_breaker_mutex_is_judged_by_its_pid() {
    new_env breaker-mutex
    mk_owner ghost "$(dead_pid)" tok-ghost "Mon Jan  1 00:00:00 2024"
    mkdir "$LOCK.breaking"; dead_pid > "$LOCK.breaking/pid"      # breaker died: fresh dir, dead pid
    out=$(MAC_LOCK_WAIT=5 mr gradle :a 2>&1); rc=$?
    [ $rc = 0 ] || fail "dead breaker's mutex blocked the next waiter (exit $rc): $out"
    new_env breaker-live
    mk_owner ghost "$(dead_pid)" tok-ghost "Mon Jan  1 00:00:00 2024"
    sleep 30 & local bp=$!; bg_pids+=("$bp")
    mkdir "$LOCK.breaking"; echo "$bp" > "$LOCK.breaking/pid"
    MAC_LOCK_WAIT=2 mr gradle :a >/dev/null 2>&1; rc=$?
    [ $rc = 75 ] || fail "a live breaker's mutex was removed (exit $rc)"
    [ -d "$LOCK.breaking" ] || fail "live breaker mutex gone"
    kill "$bp"
}
t_step_script_is_kept_in_run_dir() {
    new_env step-script
    mr gradle :zzz >/dev/null 2>&1
    grep -q ':zzz' "$(last_run)/step-1.sh" || fail "step script not kept"
}

t_app_id_and_product_follow_the_configuration() {
    new_env app-id
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"
    mr sim launch >/dev/null 2>&1
    grep -q 'org.andbible.ios.dev' "$(last_run)/step-1.sh" || fail "default app id changed: $(cat "$(last_run)/step-1.sh")"
    MAC_APP_ID=org.andbible.ios MAC_APP_PRODUCT=AndBible mr sim launch >/dev/null 2>&1
    grep -Eq 'booted org\.andbible\.ios( |$)' "$(last_run)/step-1.sh" && ! grep -q 'ios\.dev' "$(last_run)/step-1.sh" || fail "MAC_APP_ID ignored: $(cat "$(last_run)/step-1.sh")"
    mr xcode-build iphone >/dev/null 2>&1
    grep -q 'Debug-iphonesimulator/AndBibleDev.app' "$(last_run)/step-1.sh" && grep -q 'simctl launch \$UDID org.andbible.ios.dev' "$(last_run)/step-1.sh" || fail "default build step changed"
}
t_version_xcconfig_is_generated_and_synced() {
    new_env xcconfig
    rm -f "$T/repo/iosApp/Config/Version.xcconfig"
    mr gradle :zzz >/dev/null 2>&1
    grep -q '^MARKETING_VERSION = ' "$FAKE_HOME/work/testbox/and-bible/iosApp/Config/Version.xcconfig" || fail "Version.xcconfig missing from the synced tree"
}

# ---------------------------------------------------------------- cleanup after use
t_cleanup_shuts_down_simulators() {
    new_env cleanup
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"
    mr sim boot >/dev/null 2>&1
    [ -f "$FAKE_HOME/booted" ] || fail "sim boot did not leave a booted simulator"
    out=$(mr cleanup 2>&1); rc=$?
    [ $rc = 0 ] || fail "exit $rc: $out"
    [ -f "$FAKE_HOME/booted" ] && fail "simulator still booted after cleanup"
    grep -q "shut down simulators: iPhone 16" <<<"$out" || fail "no shutdown line: $out"
    grep -q "memory before" <<<"$out" && grep -q "memory after" <<<"$out" || fail "no memory lines: $out"
    [ -d "$LOCK" ] && fail "cleanup left the lock"
}
t_cleanup_kills_own_tree_and_reports_foreign() {
    new_env cleanup-procs
    mkdir -p "$FAKE_HOME/work/testbox/and-bible" "$FAKE_HOME/work/otherbox/and-bible"
    printf '#!/bin/bash\nsleep 300\n' | tee "$FAKE_HOME/work/testbox/and-bible/test.kexe" > "$FAKE_HOME/work/otherbox/and-bible/test.kexe"
    chmod +x "$FAKE_HOME"/work/*/and-bible/test.kexe
    "$FAKE_HOME/work/testbox/and-bible/test.kexe" & mine=$!
    "$FAKE_HOME/work/otherbox/and-bible/test.kexe" & other=$!
    bg_pids+=("$mine" "$other")
    sleep 0.3
    out=$(mr cleanup 2>&1); rc=$?
    sleep 0.3
    kill -0 "$mine" 2>/dev/null && fail "own leftover test.kexe survived: $out"
    kill -0 "$other" 2>/dev/null || fail "another container's process was killed"
    grep -q "OTHER containers" <<<"$out" && grep -q "otherbox" <<<"$out" || fail "foreign leftover not reported: $out"
    kill "$other" 2>/dev/null; [ $rc = 0 ] || fail "exit $rc"
}
t_gradle_shuts_down_simulators_unless_kept() {
    new_env gradle-sim
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"
    mr sim boot >/dev/null 2>&1
    out=$(mr gradle help 2>&1)
    [ -f "$FAKE_HOME/booted" ] && fail "gradle left the simulator booted"
    grep -q "shut down simulators" <<<"$out" || fail "no shutdown line: $out"
    mr sim boot >/dev/null 2>&1
    MAC_KEEP_SIM=1 mr gradle help >/dev/null 2>&1
    [ -f "$FAKE_HOME/booted" ] || fail "MAC_KEEP_SIM=1 still shut it down"
    mr gradle --keep-sim help >/dev/null 2>&1 || true
}
t_failed_batch_still_shuts_down_simulators() {
    new_env batch-sim
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"
    mr sim boot >/dev/null 2>&1
    GRADLE_RC=3 mr batch "gradle help" >/dev/null 2>&1
    [ -f "$FAKE_HOME/booted" ] && fail "failed batch left the simulator booted"
}
t_sim_helpers_keep_simulator_booted() {
    new_env sim-keeps
    mkdir -p "$FAKE_HOME/work/testbox/and-bible"
    mr sim boot >/dev/null 2>&1; mr sim launch >/dev/null 2>&1
    [ -f "$FAKE_HOME/booted" ] || fail "sim launch shut the simulator down"
}

t_cleanup_removes_own_old_archives_and_shots() {
    new_env cleanup-junk
    r="$FAKE_HOME/work/testbox/and-bible/build/release"
    mkdir -p "$r/old.xcarchive" "$r/new.xcarchive" "$r/export1" "$FAKE_HOME/work/testbox/and-bible/build/shots"
    touch -d '2 days ago' "$r/old.xcarchive"; echo x > "$FAKE_HOME/work/testbox/and-bible/build/shots/a.png"
    mkdir -p "$FAKE_HOME/work/otherbox/and-bible/build/release/old.xcarchive"
    out=$(mr cleanup 2>&1) || fail "exit: $out"
    [ -d "$r/new.xcarchive" ] && [ ! -d "$r/old.xcarchive" ] || fail "archive pruning wrong: $(ls "$r")"
    [ -d "$r/export1" ] || fail "the only export dir must stay"
    [ -e "$FAKE_HOME/work/testbox/and-bible/build/shots" ] && fail "shots kept"
    [ -d "$FAKE_HOME/work/otherbox/and-bible/build/release/old.xcarchive" ] || fail "another container's tree was touched"
    grep -q "disk (data volume)" <<<"$out" || fail "no disk line: $out"
}
t_prune_is_a_dry_run_unless_yes() {
    new_env prune-dry
    mkdir -p "$FAKE_HOME/work/testbox/and-bible" "$FAKE_HOME/work/gone1/and-bible" "$FAKE_HOME/work/keep/and-bible"
    head -c 100000 /dev/zero > "$FAKE_HOME/work/gone1/and-bible/big"
    out=$(mr prune --trees gone1 testbox nosuch 2>&1) || fail "exit: $out"
    [ -d "$FAKE_HOME/work/gone1" ] || fail "dry run deleted the tree"
    grep -q "gone1:" <<<"$out" && grep -q "testbox: SKIPPED, this container's own tree" <<<"$out" && grep -q "nosuch: no such tree" <<<"$out" || fail "listing wrong: $out"
    [ -d "$LOCK" ] && fail "dry run left a lock"
    out=$(mr prune --trees gone1 --yes 2>&1) || fail "exit --yes: $out"
    [ -e "$FAKE_HOME/work/gone1" ] && fail "gone1 not deleted"
    [ -d "$FAKE_HOME/work/keep" ] || fail "unnamed tree deleted"
    [ -d "$FAKE_HOME/work/testbox" ] || fail "own tree deleted"
}
t_prune_older_than_and_refusals() {
    new_env prune-old
    mkdir -p "$FAKE_HOME/work/testbox/and-bible" "$FAKE_HOME/work/stale/and-bible" "$FAKE_HOME/work/fresh/and-bible"
    find "$FAKE_HOME/work/stale" -exec touch -d '40 days ago' {} +
    out=$(mr prune --older-than 14 2>&1)
    grep -q "stale:" <<<"$out" && ! grep -q "fresh:" <<<"$out" || fail "age selection wrong: $out"
    out=$(mr prune 2>&1); [ $? = 2 ] || fail "prune without selection must fail: $out"
    out=$(mr prune --trees ../x 2>&1); [ $? = 2 ] || fail "bad name accepted: $out"
}

t_warm_shared_sync_and_owner() {
    new_env warm-shared; unset MAC_OWN_TREE
    touch "$FAKE_HOME/booted"
    mr sync >/dev/null 2>&1 || fail 'sync failed'
    local tree="$FAKE_HOME/work/shared/and-bible"
    [ -f "$tree/gradlew" ] || { fail 'shared default missing'; return; }
    grep -qx 'rsync-push lock=held' "$EVENTS" || fail 'standalone sync unlocked'
    [ -f "$FAKE_HOME/booted" ] || fail 'sync shut down simulator'
    mkdir -p "$tree/sharedCore/build" "$tree/.gradle" "$tree/.kotlin" "$tree/app/bibleview-js/node_modules"
    local p; for p in sharedCore/build .gradle .kotlin app/bibleview-js/node_modules; do touch "$tree/$p/warm"; done
    echo foreign.setting=bad > "$tree/local.properties"
    MAC_CONTAINER=next mr sync >/dev/null 2>&1 || fail 'second sync failed'
    for p in sharedCore/build .gradle .kotlin app/bibleview-js/node_modules; do [ -f "$tree/$p/warm" ] || fail "lost warm $p"; done
    ! grep -q foreign.setting "$tree/local.properties" || fail 'foreign settings survived'
    mr run --no-sync 'touch forbidden' >/dev/null 2>&1 && fail 'foreign owner accepted'
    mr fetch gradlew >/dev/null 2>&1 && fail 'foreign fetch accepted'
    MAC_CONTAINER=next mr fetch gradlew "$T/copy" >/dev/null 2>&1 || fail 'owner fetch refused'
    [ -f "$T/copy/gradlew" ] || fail 'owner fetch lost file'
    grep -q '^tree=work/shared/and-bible$' "$(last_run)/meta" || fail 'tree metadata missing'
    grep -q '^source_revision=.' "$(last_run)/meta" || fail 'revision missing'
}
t_warm_setup_failure_invalidates_owner() {
    new_env warm-setup; unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1 || fail 'initial sync'
    mkdir -p "$FAKE_HOME/.gradle/init.d"
    echo 'unrelated init' > "$FAKE_HOME/.gradle/init.d/andbible-mac-build-cache.gradle"
    out=$(mr sync 2>&1); rc=$?
    [ "$rc" != 0 ] && ! grep -q 'synced to' <<<"$out" || fail "setup reported success: $out"
    grep -qx 'unrelated init' "$FAKE_HOME/.gradle/init.d/andbible-mac-build-cache.gradle" || fail 'unrelated init overwritten'
    mr run --no-sync true >/dev/null 2>&1 && fail 'partial sync authorized'
}
t_warm_paths_fail_closed() {
    new_env warm-paths
    MAC_CONTAINER=../escape mr sync >/dev/null 2>&1 && fail 'container traversal accepted'
    local p; for p in work work/../outside /tmp/outside work/shared/../testbox; do MAC_WORKDIR="$p" mr sync >/dev/null 2>&1 && fail "unsafe path $p"; done
    mkdir -p "$FAKE_HOME/outside"; ln -s "$FAKE_HOME/outside" "$FAKE_HOME/work/testbox"
    mr sync >/dev/null 2>&1 && fail 'symlink escape accepted'
    [ ! -e "$FAKE_HOME/outside/and-bible" ] || fail 'escape mutated outside'
}
t_warm_cache_environment_and_force() {
    new_env warm-cache
    mr run 'printf "%s\n" "$MAC_REMOTE_BUILD_CACHE" "$GRADLE_OPTS" > build-env' >/dev/null 2>&1 || fail 'cache run failed'
    local tree="$FAKE_HOME/work/testbox/and-bible"
    grep -qx 1 "$tree/build-env" && grep -q -- '-Dorg.gradle.caching=true' "$tree/build-env" || fail 'cache env absent'
    cmp "$HERE/mac-build-cache.init.gradle" "$FAKE_HOME/.gradle/init.d/andbible-mac-build-cache.gradle" || fail 'init absent'
    mr native-tests --force jsword >/dev/null 2>&1 || fail 'forced Native run failed'
    grep -q ':jsword:iosSimulatorArm64Test --rerun' "$EVENTS" || fail 'no task-scoped rerun'
    GRADLE_USER_HOME="$FAKE_HOME/custom" mr run true >/dev/null 2>&1 && fail 'custom Gradle home accepted'
    mr gradle -g /tmp/custom help >/dev/null 2>&1 && fail 'custom Gradle home argument accepted'
}
t_warm_native_immutable_and_transfer_failure() {
    new_env warm-native
    cat > "$T/repo/gradlew" <<'EOS'
#!/bin/bash
mkdir -p jsword/build/test-results/iosSimulatorArm64Test
echo "${XC_MARK:-A}" > jsword/build/test-results/iosSimulatorArm64Test/TEST-x.xml
EOS
    mr native-tests jsword >/dev/null 2>&1 || fail 'first Native run'
    local a; a=$(last_run)
    XC_MARK=B mr native-tests jsword >/dev/null 2>&1 || fail 'second Native run'
    [ "$(cat "$a/native-tests/step1/jsword/test-results/iosSimulatorArm64Test/TEST-x.xml" 2>/dev/null)" = A ] || fail 'immutable Native evidence missing'
    [ -L "$T/out/native-tests/jsword" ] || fail 'Native latest not linked'
    FAKE_RSYNC_FAIL_PULL=1 mr native-tests jsword >/dev/null 2>&1; rc=$?
    [ "$rc" != 0 ] || fail 'Native transport failure ignored'
    FAKE_RSYNC_FAIL_PULL=1 mr xcode-test iphone >/dev/null 2>&1; rc=$?
    [ "$rc" != 0 ] || fail 'Xcode transport failure ignored'
}
t_warm_xcode_early_failure_clears_stale() {
    new_env warm-xcode
    mr xcode-test iphone >/dev/null 2>&1 || fail 'initial Xcode run'
    NPM_RC=9 mr xcode-test iphone >/dev/null 2>&1; rc=$?
    [ "$rc" != 0 ] || fail 'npm failure ignored'
    [ ! -e "$(last_run)/xcode/step1-iphone/app-iphone.log" ] && [ ! -e "$(last_run)/xcode/step1-iphone/xcodebuild-iphone.log" ] || fail 'stale Xcode logs recaptured'
}
t_warm_run_snapshots_only_current_outputs() {
    new_env warm-run; unset MAC_OWN_TREE
    MAC_CONTAINER=foreign mr run 'mkdir -p build/release/old.xcarchive build/shots; echo old > build/shots/old.png' >/dev/null 2>&1 || fail 'initial artifacts'
    mr run 'mkdir -p build/release/new.xcarchive; echo new > build/release/new.xcarchive/data; echo new > build/shots/new.png; exit 6' >/dev/null 2>&1; rc=$?
    [ "$rc" = 6 ] || fail "original failure lost: $rc"
    local ev="$(last_run)/run/step1"
    [ -f "$ev/release/new.xcarchive/data" ] && [ -f "$ev/shots/new.png" ] || fail 'new artifacts not snapshotted'
    [ ! -e "$ev/release/old.xcarchive" ] && [ ! -e "$ev/shots/old.png" ] || fail 'old artifacts snapshotted'
    mr cleanup >/dev/null 2>&1 || fail 'cleanup failed'
    [ -d "$FAKE_HOME/work/shared/and-bible/build/release/old.xcarchive" ] || fail 'cleanup removed foreign scratch'
}
t_warm_reset_cleanup_prune_protect_caches() {
    new_env warm-reset; unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1 || fail 'sync failed'
    local tree="$FAKE_HOME/work/shared/and-bible"
    mkdir -p "$tree/sharedCore/build" "$tree/.gradle" "$tree/.kotlin" "$tree/build/dd-iphone" "$tree/app/bibleview-js/node_modules" "$FAKE_HOME/Library/Developer/Xcode/DerivedData/ModuleCache.noindex"
    touch "$tree/sharedCore/build/warm" "$tree/.gradle/warm" "$tree/app/bibleview-js/node_modules/warm"
    mr cleanup >/dev/null 2>&1 || fail 'cleanup failed'
    [ -f "$tree/sharedCore/build/warm" ] && [ -f "$tree/.gradle/warm" ] && [ -f "$tree/app/bibleview-js/node_modules/warm" ] || fail 'cleanup destroyed caches'
    mr prune --trees shared --yes >/dev/null 2>&1 || fail 'prune failed'
    [ -d "$tree" ] || fail 'prune selected shared'
    mr reset-tree >/dev/null 2>&1 || fail 'reset failed'
    [ ! -e "$tree/sharedCore/build" ] && [ ! -e "$tree/.gradle" ] && [ ! -e "$tree/build" ] || fail 'reset kept outputs'
    [ -f "$tree/gradlew" ] && [ -d "$FAKE_HOME/Library/Developer/Xcode/DerivedData/ModuleCache.noindex" ] || fail 'reset deleted sources/external cache'
}

t_warm_nested_xcode_env_preserves_options() {
    new_env warm-nested
    # The fake build phase invokes Gradle just as project.yml does.
    cat > "$T/bin/xcodebuild" <<'EOS'
#!/bin/bash
bash -c 'printf "%s\n" "$MAC_REMOTE_BUILD_CACHE" "$GRADLE_OPTS" > "$HOME/nested-env"; ./gradlew nested'
EOS
    GRADLE_OPTS='-Dexisting.setting=retained' mr xcode-build iphone --release >/dev/null 2>&1
    grep -qx 1 "$FAKE_HOME/nested-env" && grep -q -- '-Dexisting.setting=retained' "$FAKE_HOME/nested-env" && grep -q -- '-Dorg.gradle.caching=true' "$FAKE_HOME/nested-env" || fail 'nested build phase lost cache env/options'
    grep -q 'gradlew nested' "$EVENTS" || fail 'nested Gradle not invoked'
    # Restore the global tool fixture for subsequent tests.
    cp "$T/bin/xcodebuild.original" "$T/bin/xcodebuild"
}
t_warm_derived_data_flag_and_shared_process_safety() {
    new_env warm-dd
    local dd="$FAKE_HOME/Library/Developer/Xcode/DerivedData"
    mkdir -p "$dd/own" "$dd/other" "$dd/ModuleCache.noindex" "$FAKE_HOME/work/testbox/and-bible"
    echo /work/testbox/and-bible > "$dd/own/info.plist"
    echo /work/other/and-bible > "$dd/other/info.plist"
    mr cleanup --derived-data >/dev/null 2>&1 || fail 'own derived cleanup failed'
    [ ! -e "$dd/own" ] && [ -d "$dd/other" ] && [ -d "$dd/ModuleCache.noindex" ] || fail 'derived flag propagation or boundary wrong'
    unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1 || fail 'shared sync failed'
    mkdir -p "$FAKE_HOME/work/shared/and-bible/build/dd-iphone"
    mr cleanup --derived-data >/dev/null 2>&1 || fail 'shared derived cleanup failed'
    [ ! -e "$FAKE_HOME/work/shared/and-bible/build/dd-iphone" ] || fail 'explicit shared derived cleanup ignored'
    mkdir -p "$FAKE_HOME/work/shared/and-bible"
    printf '#!/bin/bash\nsleep 60\n' > "$FAKE_HOME/work/shared/and-bible/test.kexe"
    chmod +x "$FAKE_HOME/work/shared/and-bible/test.kexe"
    "$FAKE_HOME/work/shared/and-bible/test.kexe" & local p=$!; bg_pids+=("$p")
    mr cleanup >/dev/null 2>&1 || fail 'shared process cleanup failed'
    kill -0 "$p" 2>/dev/null || fail 'shared path process killed without token ownership'
    kill "$p" 2>/dev/null
}
t_warm_failed_and_unattempted_native_not_fresh() {
    new_env warm-native-stale
    mr sync >/dev/null 2>&1 || fail 'sync failed'
    local tree="$FAKE_HOME/work/testbox/and-bible"
    mkdir -p "$tree/jsword/build/test-results/iosSimulatorArm64Test" "$tree/kdom/build/test-results/iosSimulatorArm64Test"
    echo old > "$tree/jsword/build/test-results/iosSimulatorArm64Test/TEST-old.xml"
    echo old > "$tree/kdom/build/test-results/iosSimulatorArm64Test/TEST-old.xml"
    GRADLE_RC=3 mr native-tests jsword >/dev/null 2>&1
    [ ! -e "$(last_run)/native-tests/step1/jsword/test-results/iosSimulatorArm64Test/TEST-old.xml" ] || fail 'failed invocation reported unchanged XML as fresh'
    MAC_TIMEOUT=1 MAC_STEP_MARGIN=1 XC_BOOT_SLEEP=5 mr native-tests jsword kdom >/dev/null 2>&1
    [ "$(cat "$(last_run)/native-tests/step1/kdom/status")" = unattempted ] && [ ! -e "$(last_run)/native-tests/step1/kdom/test-results" ] || fail 'unattempted module reported stale results'
}

t_warm_transfer_lease_and_failure_precedence() {
    new_env warm-lease
    # A successful transfer may outlast the between-instruction idle guard.
    MAC_IDLE_MAX=1 FAKE_RSYNC_PULL_SLEEP=2 mr run 'mkdir -p build/shots; echo current > build/shots/current.png' >/dev/null 2>&1; rc=$?
    [ "$rc" = 0 ] || fail "active evidence copy lost lock: $rc"
    [ -f "$(last_run)/run/step1/shots/current.png" ] || fail 'evidence not captured across lease'
    FAKE_RSYNC_FAIL_PULL=1 mr run 'mkdir -p build/shots; echo failed > build/shots/failed.png; exit 7' >/dev/null 2>&1; rc=$?
    [ "$rc" = 7 ] || fail "transport replaced original step failure: $rc"
}
t_warm_shared_scratch_cleanup_is_content_owned() {
    new_env warm-scratch; unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1
    local tree="$FAKE_HOME/work/shared/and-bible"
    mkdir -p "$tree/build/release/foreign.xcarchive" "$tree/build/shots"
    echo foreign > "$tree/build/shots/foreign.png"
    mr run 'echo mine > build/shots/mine.png; mkdir -p build/release/mine.xcarchive; echo mine > build/release/mine.xcarchive/data' >/dev/null 2>&1 || fail 'artifact run'
    mr cleanup >/dev/null 2>&1 || fail 'cleanup'
    [ ! -e "$tree/build/shots/mine.png" ] && [ ! -e "$tree/build/release/mine.xcarchive" ] || fail 'captured own scratch not cleaned'
    [ -f "$tree/build/shots/foreign.png" ] && [ -d "$tree/build/release/foreign.xcarchive" ] || fail 'foreign scratch cleaned'
    mr run 'echo mine > build/shots/reused.png' >/dev/null 2>&1
    echo foreign-overwrite > "$tree/build/shots/reused.png"
    MAC_CONTAINER=other mr sync >/dev/null 2>&1
    mr cleanup >/dev/null 2>&1 || fail 'cleanup after foreign handoff'
    [ -f "$tree/build/shots/reused.png" ] || fail 'foreign overwrite cleaned'
}

t_warm_nosync_installs_cache_under_lock() {
    new_env warm-nosync-cache
    mr sync >/dev/null 2>&1 || fail 'sync'
    rm "$FAKE_HOME/.gradle/init.d/andbible-mac-build-cache.gradle"
    local result
    result=$(mr run --no-sync 'test -f "$HOME/.gradle/init.d/andbible-mac-build-cache.gradle"' 2>&1) || fail "no-sync cache setup failed: $result"
}
t_warm_reset_refuses_nested_external_symlink() {
    new_env warm-reset-escape; unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1
    local tree="$FAKE_HOME/work/shared/and-bible"
    mkdir -p "$FAKE_HOME/external" "$tree/build"
    touch "$FAKE_HOME/external/keep"
    ln -s "$FAKE_HOME/external" "$tree/build/escape"
    mr reset-tree >/dev/null 2>&1 && fail 'reset accepted escaping output symlink'
    [ -f "$FAKE_HOME/external/keep" ] || fail 'reset escaped tree'
    mr prune --older-than 0 --yes >/dev/null 2>&1
    [ -d "$tree" ] || fail 'age prune selected shared'
}

t_warm_prune_preserves_external_xcode_caches() {
    new_env warm-prune-cache
    local dd="$FAKE_HOME/Library/Developer/Xcode/DerivedData"
    mkdir -p "$FAKE_HOME/work/gone/and-bible" "$dd/ModuleCache.noindex" "$dd/SDKStatCaches.noindex"
    echo /work/gone/and-bible > "$dd/ModuleCache.noindex/info.plist"
    echo /work/gone/and-bible > "$dd/SDKStatCaches.noindex/info.plist"
    mr prune --trees gone --yes >/dev/null 2>&1 || fail 'prune failed'
    [ -d "$dd/ModuleCache.noindex" ] && [ -d "$dd/SDKStatCaches.noindex" ] || fail 'prune deleted global Xcode caches'
}

t_warm_dirty_source_fingerprint_tracks_content() {
    new_env warm-dirty
    git -C "$T/repo" init -q
    git -C "$T/repo" -c user.name=test -c user.email=test@localhost commit -q --allow-empty -m fixture
    echo one > "$T/repo/dirty.txt"
    mr sync >/dev/null 2>&1 || fail 'initial generated files sync'
    mr sync >/dev/null 2>&1 || fail 'first sync'
    local first; first=$(kv "$(last_run)/meta" source_dirty)
    echo two > "$T/repo/dirty.txt"
    mr sync >/dev/null 2>&1 || fail 'second sync'
    [ "$first" != "$(kv "$(last_run)/meta" source_dirty)" ] || fail 'dirty fingerprint ignored content change'
}

t_review_receipt_generation_revoked_on_handoff() {
    new_env review-generation; unset MAC_OWN_TREE
    mr run 'mkdir -p build/shots; echo same > build/shots/same.png' >/dev/null 2>&1 || fail 'A run'
    MAC_CONTAINER=other mr run 'echo same > build/shots/same.png' >/dev/null 2>&1 || fail 'B run'
    mr sync >/dev/null 2>&1 || fail 'A reacquire'
    mr cleanup >/dev/null 2>&1 || fail 'A cleanup'
    [ -f "$FAKE_HOME/work/shared/and-bible/build/shots/same.png" ] || fail 'old A receipt deleted same-byte B artifact'
}
t_review_crashes_locked_and_fetchable() {
    new_env review-crashes; unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1 || fail 'sync'
    mkdir -p "$FAKE_HOME/Library/Logs/DiagnosticReports"
    echo crash > "$FAKE_HOME/Library/Logs/DiagnosticReports/AndBibleDev-crash.ips"
    sleep 30 & local hp=$!; bg_pids+=("$hp"); mk_owner other "$hp"
    MAC_LOCK_WAIT=1 mr sim crashes >/dev/null 2>&1; local rc=$?
    [ "$rc" = 75 ] || fail "crashes bypassed holder: $rc"
    [ ! -e "$FAKE_HOME/work/shared/and-bible/build/crashes" ] || fail 'crashes mutated before lock'
    kill "$hp"; wait "$hp" 2>/dev/null; rm -rf "$LOCK"
    mr sim crashes >/dev/null 2>&1 || fail 'crashes fetch failed'
    [ -f "$T/out/crashes/AndBibleDev-crash.ips" ] || fail 'crash evidence absent'
    MAC_CONTAINER=other mr sim crashes >/dev/null 2>&1 && fail 'foreign crash collection accepted'
}
t_review_jvm_home_overrides_fail_closed() {
    new_env review-jvm-home
    local key out rc
    for key in GRADLE_OPTS JAVA_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
        out=$(env "$key=-Dgradle.user.home=$FAKE_HOME/custom" "$SCRIPT" run 'touch forbidden' 2>&1); rc=$?
        [ "$rc" != 0 ] || fail "$key override accepted"
    done
    [ ! -e "$FAKE_HOME/work/testbox/and-bible/forbidden" ] || fail 'override ran build body'
    # Simulate a remote-only login environment, not the local shell.
    cp "$T/bin/fake-ssh" "$T/bin/fake-ssh.original"
    python3 -I - "$T/bin/fake-ssh" <<'EOS'
import sys
p=sys.argv[1]
s=open(p).read().replace('cd "$FAKE_HOME"', 'export JAVA_TOOL_OPTIONS="-Dgradle.user.home=$FAKE_HOME/remote-custom"\ncd "$FAKE_HOME"')
open(p,'w').write(s)
EOS
    mr xcode-test iphone >/dev/null 2>&1 && fail 'remote login JVM home override accepted'
    cp "$T/bin/fake-ssh.original" "$T/bin/fake-ssh"
}
t_review_nosync_metadata_uses_executed_source() {
    new_env review-nosync; unset MAC_OWN_TREE
    git -C "$T/repo" init -q
    git -C "$T/repo" -c user.name=test -c user.email=test@localhost commit -q --allow-empty -m A
    mr sync >/dev/null 2>&1 || fail 'sync A'
    local revision dirty; revision=$(kv "$(last_run)/meta" source_revision); dirty=$(kv "$(last_run)/meta" source_dirty)
    echo B > "$T/repo/change"
    git -C "$T/repo" add change
    git -C "$T/repo" -c user.name=test -c user.email=test@localhost commit -qm B
    echo local-only > "$T/repo/dirty"
    mr run --no-sync 'test ! -e change' >/dev/null 2>&1 || fail 'no-sync execution'
    [ "$(kv "$(last_run)/meta" source_revision)" = "$revision" ] && [ "$(kv "$(last_run)/meta" source_dirty)" = "$dirty" ] || fail 'no-sync source attributed to local checkout'
    mr fetch gradlew >/dev/null 2>&1 || fail 'fetch'
    [ "$(kv "$(last_run)/meta" source_revision)" = "$revision" ] || fail 'fetch source attributed to local checkout'
}
t_review_staged_fingerprint_tracks_all_states() {
    new_env review-staged
    git -C "$T/repo" init -q
    echo clean > "$T/repo/tracked"
    git -C "$T/repo" add tracked
    git -C "$T/repo" -c user.name=test -c user.email=test@localhost commit -qm initial
    mr sync >/dev/null 2>&1; mr sync >/dev/null 2>&1
    local previous current
    previous=$(kv "$(last_run)/meta" source_dirty)
    local action
    for action in modifyA modifyB addition deletion; do
        case "$action" in modifyA) echo A > "$T/repo/tracked" ;; modifyB) echo B > "$T/repo/tracked" ;; addition) echo new > "$T/repo/added" ;; deletion) rm "$T/repo/tracked" ;; esac
        git -C "$T/repo" add -A
        mr sync >/dev/null 2>&1 || fail "sync $action"
        current=$(kv "$(last_run)/meta" source_dirty)
        [ "$previous" != "$current" ] || fail "fingerprint omitted staged $action"
        previous=$current
    done
}
t_review_selected_custom_tree_prune_protected() {
    new_env review-custom
    MAC_WORKDIR=work/custom mr sync >/dev/null 2>&1 || fail 'custom sync'
    MAC_WORKDIR=work/custom mr prune --trees custom --yes >/dev/null 2>&1 || fail 'explicit prune'
    [ -f "$FAKE_HOME/work/custom/and-bible/gradlew" ] || fail 'explicit prune removed selected tree'
    MAC_WORKDIR=work/custom mr sync >/dev/null 2>&1
    MAC_WORKDIR=work/custom mr prune --older-than 0 --yes >/dev/null 2>&1 || fail 'age prune'
    [ -f "$FAKE_HOME/work/custom/and-bible/gradlew" ] || fail 'age prune removed selected tree'
}

t_review_idle_guard_disarm_never_releases_parent_lock() {
    new_env review-guard-disarm
    local steps=() i out rc
    for i in $(seq 1 25); do steps+=("run test -d \$HOME/work/.build-lock"); done
    out=$(mr batch "${steps[@]}" 2>&1); rc=$?
    [ "$rc" = 0 ] || fail "disarmed guard released parent lock or corrupted process state: $out"
    [ "$(grep -c 'end' "$(last_run)/steps")" = 25 ] || fail 'guard disarm prevented later steps'
}

t_review_maintenance_without_completed_source_owner() {
    new_env review-ownerless; unset MAC_OWN_TREE
    mr sim boot >/dev/null 2>&1 || fail 'fresh shared boot refused'
    mr sim shutdown >/dev/null 2>&1 || fail 'fresh shared shutdown refused'
    mr cleanup >/dev/null 2>&1 || fail 'fresh shared cleanup refused'
    mr reset-tree >/dev/null 2>&1 || fail 'fresh shared reset refused'
    mr sync >/dev/null 2>&1 || fail 'initial sync'
    mkdir -p "$FAKE_HOME/work/shared/and-bible/sharedCore/build"
    touch "$FAKE_HOME/work/shared/and-bible/sharedCore/build/partial"
    echo unrelated > "$FAKE_HOME/.gradle/init.d/andbible-mac-build-cache.gradle"
    mr sync >/dev/null 2>&1 && fail 'setup failure not triggered'
    [ ! -f "$FAKE_HOME/work/shared/.mac-owner" ] || fail 'failed sync owner not invalidated'
    mr sim boot >/dev/null 2>&1 || fail 'invalidated shared boot refused'
    mr sim shutdown >/dev/null 2>&1 || fail 'invalidated shared shutdown refused'
    mr cleanup >/dev/null 2>&1 || fail 'invalidated shared cleanup refused'
    mr reset-tree >/dev/null 2>&1 || fail 'invalidated shared reset refused'
    [ ! -e "$FAKE_HOME/work/shared/and-bible/sharedCore/build" ] || fail 'recovery reset did not execute'
    [ "$(kv "$(last_run)/meta" source_revision)" = unknown ] || fail 'ownerless recovery misattributed source'
    mr run --no-sync true >/dev/null 2>&1 && fail 'ownerless shared build accepted'
    mr fetch gradlew >/dev/null 2>&1 && fail 'ownerless shared fetch accepted'
    mr sim crashes >/dev/null 2>&1 && fail 'ownerless shared crash fetch accepted'
}

t_review_owner_exit_trap_is_parent_only_before_helper_reset() {
    new_env review-owner-trap
    # Execute the production owner trap in an inherited subshell before any
    # helper reset instruction. Assert every owner-only side effect is skipped.
    local body
    body=$(python3 -I - "$SCRIPT" <<'EOS'
import pathlib, re, sys
s=pathlib.Path(sys.argv[1]).read_text()
m=re.search(r"^trap '(.*)' EXIT$", s, re.M)
print(m.group(1))
EOS
    )
    mkdir -p "$T/trap-lock.pending.token"
    local marker="$T/trap-actions"
    (
        export MR_SIMDOWN=1 TOKEN=token QTICKET=ticket L="$T/trap-lock" WD=
        lock_release_if() { echo release >> "$marker"; }
        mac_queue() { echo queue >> "$marker"; }
        xcrun() { echo sim >> "$marker"; }
        # Nested eval models the parent's inherited trap in a child.
        ( eval "$body" )
    )
    [ ! -e "$marker" ] && [ -d "$T/trap-lock.pending.token" ] || fail 'child trap performed parent cleanup before helper reset'
    # Normal parent exit, not merely syntax inspection, still releases the lock.
    mr run true >/dev/null 2>&1 || fail 'normal parent run'
    [ ! -e "$LOCK" ] || fail 'parent exit cleanup disabled'
}

t_review_genuine_idle_timeout_releases_parent_lock() {
    new_env review-idle-timeout
    # Load function definitions without CLI dispatch, then run the real lock
    # session with no next instruction while its stdin remains open.
    python3 -I - "$SCRIPT" "$T/library.sh" <<'EOS'
import pathlib, sys
s=pathlib.Path(sys.argv[1]).read_text().split('cmd="${1:-}"; shift || true')[0]
pathlib.Path(sys.argv[2]).write_text(s)
EOS
    cp "$HERE/mac-queue.py" "$T/mac-queue.py"
    MAC_IDLE_MAX=1 bash -c 'source "$1"; TOKEN=idle-test-token; RUN_ID=idle-test; lock_script' bash "$T/library.sh" > "$T/idle-session.sh"
    ( /bin/cat "$T/idle-session.sh"; sleep 5 ) | HOME="$FAKE_HOME" bash -s > "$T/idle-session.log" 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for 'grep -q "@@LOCKED-idle-test-token" "$T/idle-session.log"' || fail 'idle session did not acquire'
    wait_for '[ ! -e "$LOCK" ]' || fail 'genuine idle timeout failed to release parent lock'
    wait "$p" 2>/dev/null
    grep -q 'no instruction' "$FAKE_HOME/work/.mac-remote-watchdog.log" || fail 'idle timeout did not execute watchdog'
}

t_final_crashes_collection_is_current_run_only() {
    new_env final-crashes; unset MAC_OWN_TREE
    mr sync >/dev/null 2>&1 || fail 'A sync'
    local report="$FAKE_HOME/Library/Logs/DiagnosticReports/AndBibleDev-old.ips"
    mkdir -p "$(dirname "$report")"; echo old > "$report"
    mr sim crashes >/dev/null 2>&1 || fail 'A collection'
    local a; a="$T/out/$(readlink "$T/out/crashes")"; a=${a%/crashes/step1/crashes}
    [ -f "$a/crashes/step1/crashes/AndBibleDev-old.ips" ] || fail 'A immutable report missing'
    rm "$report"
    MAC_CONTAINER=other mr sync >/dev/null 2>&1 || fail 'B sync'
    MAC_CONTAINER=other mr sim crashes >/dev/null 2>&1 || fail 'B empty collection'
    [ ! -e "$(last_run)/crashes/step1/crashes/AndBibleDev-old.ips" ] || fail 'B collection recaptured A stale crash'
    [ -d "$T/out/crashes" ] && [ -z "$(ls -A "$T/out/crashes")" ] || fail 'empty latest crash view retained stale evidence'
    [ -f "$a/crashes/step1/crashes/AndBibleDev-old.ips" ] || fail 'A immutable crash evidence changed'
}
t_final_submodule_source_fingerprint_tracks_revision_and_contents() {
    new_env final-submodule
    git -C "$T/repo" init -q
    mkdir -p "$T/repo/jsword"
    git -C "$T/repo/jsword" init -q
    echo base > "$T/repo/jsword/tracked"
    git -C "$T/repo/jsword" add tracked
    git -C "$T/repo/jsword" -c user.name=test -c user.email=test@localhost commit -qm base
    git -C "$T/repo" add jsword
    git -C "$T/repo" -c user.name=test -c user.email=test@localhost commit -qm parent
    mr sync >/dev/null 2>&1; mr sync >/dev/null 2>&1
    local previous current parent
    parent=$(kv "$(last_run)/meta" source_revision)
    previous=$(kv "$(last_run)/meta" source_dirty)
    local action
    for action in headA headB stagedA stagedB untrackedA untrackedB; do
        case "$action" in
            headA|headB) echo "$action" > "$T/repo/jsword/tracked"; git -C "$T/repo/jsword" add tracked; git -C "$T/repo/jsword" -c user.name=test -c user.email=test@localhost commit -qm "$action" ;;
            stagedA|stagedB) echo "$action" > "$T/repo/jsword/tracked"; git -C "$T/repo/jsword" add tracked ;;
            *) echo "$action" > "$T/repo/jsword/untracked" ;;
        esac
        mr sync >/dev/null 2>&1 || fail "sync $action"
        current=$(kv "$(last_run)/meta" source_dirty)
        [ "$current" != "$previous" ] || fail "submodule fingerprint omitted $action"
        [ "$(kv "$(last_run)/meta" source_revision)" = "$parent" ] || fail 'parent HEAD changed in fixture'
        previous=$current
    done
}

for t in t_final_crashes_collection_is_current_run_only t_final_submodule_source_fingerprint_tracks_revision_and_contents \
    t_review_genuine_idle_timeout_releases_parent_lock \
    t_review_owner_exit_trap_is_parent_only_before_helper_reset \
    t_review_maintenance_without_completed_source_owner \
    t_review_idle_guard_disarm_never_releases_parent_lock \
    t_review_receipt_generation_revoked_on_handoff t_review_crashes_locked_and_fetchable t_review_jvm_home_overrides_fail_closed t_review_nosync_metadata_uses_executed_source t_review_staged_fingerprint_tracks_all_states t_review_selected_custom_tree_prune_protected \
    t_warm_dirty_source_fingerprint_tracks_content \
    t_warm_prune_preserves_external_xcode_caches \
    t_warm_nosync_installs_cache_under_lock t_warm_reset_refuses_nested_external_symlink \
    t_warm_transfer_lease_and_failure_precedence t_warm_shared_scratch_cleanup_is_content_owned \
    t_warm_nested_xcode_env_preserves_options t_warm_derived_data_flag_and_shared_process_safety t_warm_failed_and_unattempted_native_not_fresh \
    t_warm_shared_sync_and_owner t_warm_setup_failure_invalidates_owner t_warm_paths_fail_closed t_warm_cache_environment_and_force t_warm_native_immutable_and_transfer_failure t_warm_xcode_early_failure_clears_stale t_warm_run_snapshots_only_current_outputs t_warm_reset_cleanup_prune_protect_caches \
    t_queue_fifo_three_waiters_behind_legacy_holder t_queue_timeout_and_cancel_remove_waiters \
    t_queue_controls_use_orchestrator_group_not_child_name t_queue_rejects_parallel_slots_before_connecting \
    t_queue_legacy_client_respects_new_holder_and_can_overtake_reservation \
    t_queue_child_registration_inherits_group_and_priority_changes_admission t_queue_missing_embedded_helper_fails_closed \
    t_queue_cancel_after_head_check_cannot_admit t_cancel_corrupt_queue_releases_matching_holder_and_remains_retryable \
    t_gradle_exit_code_and_lock_released t_sync_happens_after_lock_acquired t_owner_record_has_token_pid_start \
    t_waiter_times_out_behind_live_foreign_holder t_wait_message_warns_when_holder_is_this_container \
    t_stale_lock_dead_pid_is_broken t_stale_lock_pid_reused_by_other_process_is_broken \
    t_live_holder_with_matching_start_is_not_broken t_concurrent_stale_breakers_exactly_one_wins \
    t_release_only_removes_own_token \
    t_marker_written_on_success t_marker_records_failure_code t_marker_records_timeout t_marker_records_killed_run \
    t_check_shows_running_run_and_own_lock t_check_other_holder_and_timeout_and_lost \
    t_cancel_kills_only_own_pids_and_releases_matching_lock t_cancel_releases_lock_of_a_lost_run_and_stops_its_holder t_cancel_leaves_foreign_lock_alone t_cancel_with_nothing_to_cancel \
    t_batch_stops_at_first_failing_step t_batch_keep_going_runs_every_step t_batch_keep_going_stops_after_a_failed_build_step t_batch_keep_going_with_no_sync t_batch_holds_lock_across_steps t_batch_link_and_unknown_step t_batch_native_then_xcode_sequence \
    t_native_tests_force_deletes_results t_native_tests_boots_simulator_and_fetches t_gradle_warns_for_native_test_task \
    t_xcode_test_evidence_per_run_with_latest_links t_xcode_test_default_timeout_and_override \
    t_heartbeat_shows_in_output_log t_sim_boot_goes_through_lock_and_marker \
    t_step_output_cannot_spoof_the_result t_bad_device_in_batch_is_rejected_before_locking \
    t_idle_guard_releases_lock_when_container_side_stalls t_timeout_kills_the_whole_process_group \
    t_step_watchdog_bounds_work_outside_the_inner_timeouts t_timeouts_are_142_everywhere \
    t_live_holder_is_not_broken_under_another_timezone t_pruning_keeps_a_live_runs_directory \
    t_abandoned_breaker_mutex_is_judged_by_its_pid t_step_script_is_kept_in_run_dir \
    t_app_id_and_product_follow_the_configuration t_version_xcconfig_is_generated_and_synced \
    t_cleanup_shuts_down_simulators t_cleanup_kills_own_tree_and_reports_foreign t_gradle_shuts_down_simulators_unless_kept t_failed_batch_still_shuts_down_simulators t_sim_helpers_keep_simulator_booted \
    t_cleanup_removes_own_old_archives_and_shots t_prune_is_a_dry_run_unless_yes t_prune_older_than_and_refusals; do run_test "$t"; done

echo "mac-remote tests: $pass passed, $failn failed"
[ "$failn" = 0 ] && echo "MAC-REMOTE OK"
exit $((failn > 0))
