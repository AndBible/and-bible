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
[ -z "$push" ] && [ -n "${FAKE_RSYNC_PULL_SLEEP:-}" ] && sleep "$FAKE_RSYNC_PULL_SLEEP"
[ -n "$push" ] && echo "rsync-push lock=$([ -d "$FAKE_HOME/work/.build-lock" ] && echo held || echo free)" >> "$EVENTS"
exec rsync "${args[@]}"
EOS
chmod +x "$T/bin/fake-ssh" "$T/bin/fake-rsync"

# Stand-ins for the Mac tools the remote scripts call.
cat > "$T/bin/xcrun" <<'EOS'
#!/usr/bin/env bash
case "$*" in
    "simctl list devices available -j") echo '{"devices":{"rt":[{"name":"iPhone 16","udid":"UDID-IPHONE"},{"name":"iPad Air 11-inch (M4)","udid":"UDID-IPAD"}]}}' ;;
    "simctl bootstatus "*) [ -n "${XC_BOOT_SLEEP:-}" ] && sleep "$XC_BOOT_SLEEP" ;;
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
exit 0
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
chmod +x "$T/bin/"*

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
    unset MAC_TIMEOUT MAC_STEP_MARGIN MAC_IDLE_MAX MAC_KEEP_RUNS FAKE_RSYNC_PULL_SLEEP XC_BOOT_SLEEP GRADLE_RC GRADLE_SLEEP XC_RC XC_SLEEP XC_MARK
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
    grep -q 'gradlew --stop' "$EVENTS" || fail "daemon not stopped after the timeout"
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
    "$SCRIPT" run 'sleep 8' > /dev/null 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$LOCK/owner" ] && grep -q "sleep 8" <(ps -eo args | grep -v grep)' || { fail "run did not start"; return; }
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
    [ $rc != 0 ] && grep -q "step 2: .* -> not run" <<<"$out" || fail "a lost session should stop the batch, exit $rc: $out"
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
    MAC_IDLE_MAX=1 FAKE_RSYNC_PULL_SLEEP=3 "$SCRIPT" native-tests jsword > /dev/null 2>&1 & local p=$!; bg_pids+=("$p")
    wait_for '[ -f "$LOCK/owner" ]' || { fail "run did not start"; return; }
    wait_for '[ ! -e "$LOCK" ]' || fail "lock not released while the container side idles"
    kill -0 "$p" 2>/dev/null || fail "the local run ended before the stall was observed (test not meaningful)"
    wait "$p"
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

for t in t_gradle_exit_code_and_lock_released t_sync_happens_after_lock_acquired t_owner_record_has_token_pid_start \
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
    t_app_id_and_product_follow_the_configuration t_version_xcconfig_is_generated_and_synced; do run_test "$t"; done

echo "mac-remote tests: $pass passed, $failn failed"
[ "$failn" = 0 ] && echo "MAC-REMOTE OK"
exit $((failn > 0))
