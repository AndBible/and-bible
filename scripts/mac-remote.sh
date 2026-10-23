#!/usr/bin/env bash
# Run iOS build/test steps on the remote build Mac from a jailbee container
# (setup: docs/ios-remote-mac.md; agent guide: .claude/skills/ios-mac/SKILL.md).
# The working tree is rsynced to ~/work/<container>/and-bible on the Mac (uncommitted changes
# included, no .git); results land in .local/mac/<container>/ here.
# Usage:
#
#   scripts/mac-remote.sh check                 this container's recent runs (state, elapsed, log idle time),
#                                               then the Mac: toolchain, simulators, build lock (and whether
#                                               the holder is THIS container), remote log idle times
#   scripts/mac-remote.sh cancel [run-id]       stop this container's running mac-remote runs (process trees,
#                                               by pid) and release the Mac lock if its owner token is theirs
#   scripts/mac-remote.sh sync                  rsync the working tree to the Mac (incremental)
#   scripts/mac-remote.sh gradle <args...>      lock, sync, then ./gradlew -Dorg.gradle.native=false <args> on the Mac
#   scripts/mac-remote.sh native-tests [--force] [mod...]
#                                               lock, sync, boot the iPhone simulator, run <mod>:iosSimulatorArm64Test
#                                               (default: the CI jsword-ios-tests set: jsword kdom sharedCore
#                                               sharedData sharedLogic sharedUi), fetch the test reports/XML.
#                                               --force first deletes the modules' remote test-results, so a rerun on
#                                               an unchanged tree really runs (otherwise: UP-TO-DATE, nothing runs)
#   scripts/mac-remote.sh xcode-test [iphone|ipad] [xcodebuild args...]
#                                               the CI `ios` job: bibleview-js build, xcodegen, xcodebuild test
#                                               with the app log streamed, attachments (screenshots) exported,
#                                               everything fetched into a per-run evidence directory (the old
#                                               xcode/<name>-<device> paths are links to the latest run);
#                                               extra args e.g. -only-testing:iosAppUITests/PocUITests
#   scripts/mac-remote.sh xcode-build [iphone|ipad]
#                                               build the app for the simulator, install it, launch it
#   scripts/mac-remote.sh run [--no-sync] <shell command>
#                                               lock, sync, then run a command in the remote tree. --no-sync keeps the
#                                               Mac tree as it is (inspect or rerun what a previous run left there)
#   scripts/mac-remote.sh batch [--no-sync] <step>...
#                                               several steps under ONE lock acquisition and ONE sync, run in order,
#                                               stopping at the first failing step. Each step is one quoted argument:
#                                                 batch link native-tests "xcode-test iphone" "xcode-test ipad"
#                                               steps: link (= gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64),
#                                               gradle <args>, native-tests [--force] [mod...], xcode-test [dev] [args],
#                                               xcode-build [dev], run <command>
#   (gradle, native-tests, xcode-*, run, batch and the sim subcommands that drive the shared simulator — boot,
#   shot, launch, shutdown — all take the one Mac-wide build lock and write a run marker under
#   .local/mac/<container>/runs/<run-id>/ (meta, output.log, steps, exit); sync, fetch, check, cancel,
#   sim list/log/sample/crashes do not)
#   scripts/mac-remote.sh fetch <remote-rel-path> [local-dir]
#                                               copy a path from the remote tree to .local/mac/<container>/
#   scripts/mac-remote.sh sim <sub>             simulator helpers, on the booted simulator unless noted:
#       sim list                                  devices and their state
#       sim boot [iphone|ipad]                    create the device if missing, boot it, print its UDID
#       sim shot <name>                           screenshot -> .local/mac/<container>/shots/<name>.png
#       sim log [minutes] [predicate]             app unified log of the last N minutes (default 5)
#       sim launch [bundle-id]                    (re)launch the app (default org.andbible.ios.dev)
#       sim sample [process]                      5 s stack sample of the app (hangs, busy loops)
#       sim crashes                               fetch the app's crash reports (~/Library/Logs/DiagnosticReports)
#       sim shutdown                              shut down all simulators
#   scripts/mac-remote.sh ssh [command]         raw ssh to the Mac (interactive shell without a command)
#   scripts/mac-remote.sh unlock                remove a stale build lock (check `check` output first; prefer `cancel`)
#
# Environment:
#   MAC_SSH_CONFIG   ssh config with the host alias (default ~/.ssh-mac/config, from `jailbee mount mac-ssh`)
#   MAC_HOST         host alias in that config (default andbible-mac)
#   MAC_WORKDIR      remote directory under ~ (default work/<container hostname>)
#   MAC_SIM_IPHONE   iPhone simulator name (default "iPhone 16", as in CI; created if missing)
#   MAC_SIM_IPAD     iPad simulator name (default "iPad Air 11-inch (M4)"; created if missing)
#   MAC_TIMEOUT      seconds before one remote step (Gradle/xcodebuild/run) is killed (default 3000; 2400 for xcode-test)
#   MAC_LOCK_WAIT    seconds to wait for another container's build lock (default 5400; a dead holder's
#                    lock is broken automatically, so this only bounds waiting behind live runs)
#   MAC_HEARTBEAT    seconds between "heartbeat" lines a running step prints with the idle time of the newest
#                    remote log (default 120), so a hang shows in output.log and `check`
#   MAC_KEEP_RUNS    run directories (markers, logs, evidence) kept per container (default 20)
#   TEST_RUNNER_*    forwarded to `xcode-test`; xcodebuild hands them to the UI tests without the prefix
#                    (e.g. TEST_RUNNER_FILES_TEST_PHOTO_LABEL for FilesUITests, see docs/ios-remote-mac.md)
#   MAC_REPO_DIR, MAC_CONTAINER, MAC_LOCAL_DIR, MAC_SSH_BIN, MAC_RSYNC_BIN, MAC_LOCK_POLL, MAC_LOCK_MSG_EVERY
#                    test seams (scripts/test-mac-remote.sh): tree to sync, container name, result dir,
#                    ssh/rsync binaries, lock poll and wait-message intervals in seconds
#
# Remote side: the Mac runs ONE `bash -s` session per call; this script feeds it the lock code, then (after the
# lock is held) rsyncs the tree, then one script per step, reading the step's result from a sentinel line. The
# remote code must stay portable to macOS (bash 3.2, BSD tools): no mapfile, no associative arrays, no GNU flags.
set -uo pipefail

repo="${MAC_REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SSH_CONFIG="${MAC_SSH_CONFIG:-$HOME/.ssh-mac/config}"
HOST="${MAC_HOST:-andbible-mac}"
NAME="${MAC_CONTAINER:-$(hostname)}"
SSH_BIN="${MAC_SSH_BIN:-ssh}"
RSYNC_BIN="${MAC_RSYNC_BIN:-rsync}"
WORKDIR="${MAC_WORKDIR:-work/$NAME}"
TREE="$WORKDIR/and-bible"
OUT="${MAC_LOCAL_DIR:-$repo/.local/mac/$NAME}"
SIM_IPHONE="${MAC_SIM_IPHONE:-iPhone 16}"
SIM_IPAD="${MAC_SIM_IPAD:-iPad Air 11-inch (M4)}"
# One XCUITest class takes at most ~25 min (BackupUITests is the longest); a stuck xcodebuild must end in minutes, not hours (see the
# ios-mac-runs-must-be-bounded-and-observable memory). Gradle/native steps keep the long default.
step_timeout() { case "$1" in xcode-test) echo "${MAC_TIMEOUT:-2400}" ;; *) echo "${MAC_TIMEOUT:-3000}" ;; esac; }
LOCK_WAIT="${MAC_LOCK_WAIT:-5400}"
LOCK_POLL="${MAC_LOCK_POLL:-5}"
LOCK_MSG_EVERY="${MAC_LOCK_MSG_EVERY:-300}"
HEARTBEAT="${MAC_HEARTBEAT:-120}"
KEEP_RUNS="${MAC_KEEP_RUNS:-20}"
APP_ID=org.andbible.ios.dev
APP_PROCESS=AndBibleDev

die() { echo "mac-remote: $*" >&2; exit 1; }

[ -f "$SSH_CONFIG" ] || die "no ssh config at $SSH_CONFIG. Ask the user to run on the host: jailbee mount mac-ssh $NAME (see docs/ios-remote-mac.md)"

SSH=("$SSH_BIN" -F "$SSH_CONFIG")
# Remote runs get their own connection, never the shared ControlMaster one: the build-lock watchdog
# watches this run's sshd-session, which only ends with its own connection.
SSH_RUN=("$SSH_BIN" -F "$SSH_CONFIG" -o ControlMaster=no -o ControlPath=none -o ServerAliveInterval=30 -o ServerAliveCountMax=10)
RSYNC=("$RSYNC_BIN" -az -e "ssh -F $SSH_CONFIG" --rsync-path=/opt/homebrew/bin/rsync)

# Shell-quote arguments for the remote command line.
q() { [ $# -eq 0 ] || printf '%q ' "$@"; }

# Run a bash script on the Mac. $1 = script text; the remote bash gets the login PATH from ~/.zshenv
# because ssh starts the command through the user's zsh.
remote() { "${SSH_RUN[@]}" "$HOST" 'bash -s' <<<"$1"; }

# Prologue for remote scripts: cd into the tree, helpers.
prologue() {
    cat <<EOF
set -uo pipefail
TREE=\$HOME/$(q "$TREE")
cd "\$TREE" 2>/dev/null || { echo "mac-remote: no remote tree at \$TREE; run sync first" >&2; exit 1; }
# timeout without coreutils: perl's alarm survives exec
tmo() { perl -e 'alarm shift; exec @ARGV' "\$@"; }
sim_udid() { xcrun simctl list devices available -j | python3 -c 'import json,sys; n=sys.argv[1]; print(next((d["udid"] for rt in json.load(sys.stdin)["devices"].values() for d in rt if d["name"]==n), ""))' "\$1"; }
# Create the simulator if missing (device type = name, newest iOS runtime), boot it, print its UDID.
sim_boot() {
    local name="\$1" udid
    udid=\$(sim_udid "\$name")
    if [ -z "\$udid" ]; then
        local rt type
        rt=\$(xcrun simctl list runtimes available -j | python3 -c 'import json,sys; r=[x for x in json.load(sys.stdin)["runtimes"] if x["platform"]=="iOS"]; print(r[-1]["identifier"] if r else "")')
        type=\$(xcrun simctl list devicetypes -j | python3 -c 'import json,sys; n=sys.argv[1]; print(next((d["identifier"] for d in json.load(sys.stdin)["devicetypes"] if d["name"]==n), ""))' "\$name")
        [ -n "\$rt" ] && [ -n "\$type" ] || { echo "mac-remote: cannot create simulator '\$name' (runtime '\$rt', type '\$type')" >&2; return 1; }
        udid=\$(xcrun simctl create "\$name" "\$type" "\$rt") || return 1
        echo "mac-remote: created simulator '\$name' \$udid" >&2
    fi
    xcrun simctl bootstatus "\$udid" -b >/dev/null 2>&1 || xcrun simctl boot "\$udid" 2>/dev/null || true
    echo "\$udid"
}
EOF
}

# ---------------------------------------------------------------------------------------------
# The Mac build lock: directory ~/work/.build-lock, one heavy step at a time for every container (the
# Mac has 16 GiB shared with its owner's session).
#  - The lock dir is created under a pending name WITH its owner record (token, container, pid, process
#    start time, local pid, run id) and renamed into place with rename(2), which fails when the lock exists:
#    acquisition is atomic and a lock never exists without an owner.
#  - A holder is alive when its pid exists AND the process start time equals the recorded one (pid reuse by
#    another process no longer keeps a dead holder's lock alive). Records of the older script (first line
#    "<container> pid N since ...", no token) are judged by "pid is a live bash".
#  - Breaking a stale lock is serialised by a mutex dir, re-checks staleness under it, moves the lock away
#    with an atomic rename to a unique name and re-checks the token before deleting; only one breaker wins.
#  - Only the owner token releases a lock (exit trap, cancel).
# Portable remote code (macOS bash 3.2).
rlib() {
    cat <<'EOF'
L=$HOME/work/.build-lock
mtime() { perl -e 'print((stat $ARGV[0])[9] // 0)' "$1"; }
rename() { perl -e 'rename($ARGV[0],$ARGV[1]) or exit 1' "$1" "$2" 2>/dev/null; }
owner_get() { sed -n "s/^$1=//p" "$L/owner" 2>/dev/null | sed -n 1p; }
pstart_of() { ps -p "$1" -o lstart= 2>/dev/null | tr -s ' ' | sed 's/^ //;s/ $//'; }
# 0 when the holder is gone (or the dir has had no owner record for 60 s); 1 for no lock or a live holder
lock_stale() {
    [ -d "$L" ] || return 1
    if [ -f "$L/owner" ]; then
        local pid pstart
        pid=$(owner_get pid); pstart=$(owner_get pstart)
        if [ -n "$pid" ] && [ -n "$pstart" ]; then
            [ "$(pstart_of "$pid")" != "$pstart" ]
        else
            pid=$(sed -n '1s/.* pid \([0-9]*\) .*/\1/p' "$L/owner" 2>/dev/null)
            [ -n "$pid" ] && ! ps -p "$pid" -o comm= 2>/dev/null | grep -q bash
        fi
    else
        [ $(( $(date +%s) - $(mtime "$L") )) -gt 60 ]
    fi
}
# remove the lock only when its owner token is $1
lock_release_if() {
    [ -d "$L" ] && [ -n "$1" ] && [ "$(owner_get token)" = "$1" ] && rm -rf "$L"
}
EOF
}

# Remote script part 1: wait for the lock, then print @@LOCKED on stdout (the caller syncs the tree next).
lock_script() {
    printf 'set -uo pipefail\nMYNAME=%q TOKEN=%q LPID=%q RUNID=%q LOCK_WAIT=%q POLL=%q MSG_EVERY=%q\n' \
        "$NAME" "$TOKEN" "$$" "$RUN_ID" "$LOCK_WAIT" "$LOCK_POLL" "$LOCK_MSG_EVERY"
    rlib
    cat <<'EOF'
mkdir -p "$HOME/work"
try_acquire() {
    local P="$L.pending.$TOKEN"
    rm -rf "$P"; mkdir "$P" || return 1
    printf '%s pid %s since %s\ntoken=%s\ncontainer=%s\npid=%s\npstart=%s\nepoch=%s\nlpid=%s\nrun=%s\n' \
        "$MYNAME" $$ "$(date '+%F %T')" "$TOKEN" "$MYNAME" $$ "$(pstart_of $$)" "$(date +%s)" "$LPID" "$RUNID" > "$P/owner"
    rename "$P" "$L" && return 0
    rm -rf "$P"; return 1
}
break_lock() {
    local B="$L.breaking" seen got moved
    if ! mkdir "$B" 2>/dev/null; then
        [ $(( $(date +%s) - $(mtime "$B") )) -gt 60 ] && rm -rf "$B"   # an abandoned breaker mutex
        return 1
    fi
    lock_stale || { rmdir "$B"; return 1; }
    seen=$(owner_get token); moved="$L.broken.$TOKEN"
    if rename "$L" "$moved"; then
        got=$(sed -n 's/^token=//p' "$moved/owner" 2>/dev/null | sed -n 1p)
        if [ "$got" = "$seen" ]; then
            echo "mac-remote: breaking a stale build lock (holder gone): $(sed -n 1p "$moved/owner" 2>/dev/null)" >&2
            rm -rf "$moved"
        else   # the lock changed hands between check and rename: put it back
            rename "$moved" "$L" || echo "mac-remote: WARNING: moved a live lock aside and could not restore it: $moved" >&2
        fi
    fi
    rmdir "$B" 2>/dev/null; return 0
}
WD=
trap 'lock_release_if "$TOKEN"; rm -rf "$L.pending.$TOKEN"; [ -n "$WD" ] && kill $WD 2>/dev/null' EXIT
trap 'exit 129' HUP INT TERM
# Watchdog: the nearest sshd ancestor is this connection; when it is gone, stop this process group (also
# a waiter, so a cancelled or killed local run never takes the lock later). Its fds go to /dev/null,
# otherwise it would hold the ssh channel open after a normal exit.
sshd_pid=; p=$$
while [ -n "$p" ] && [ "$p" -gt 1 ]; do
    case "$(ps -p "$p" -o command= 2>/dev/null)" in sshd*) sshd_pid=$p; break ;; esac
    p=$(ps -p "$p" -o ppid= 2>/dev/null | tr -d ' ')
done
if [ -n "$sshd_pid" ]; then
    ( while ps -p "$sshd_pid" >/dev/null 2>&1; do sleep 10; done
      echo "$(date '+%F %T') $MYNAME: ssh connection gone, stopping run $$" >> "$HOME/work/.mac-remote-watchdog.log"
      kill -TERM 0 ) </dev/null >/dev/null 2>&1 &
    WD=$!
fi
got=; t0=$SECONDS; last=-1000000
while :; do
    if try_acquire; then got=1; break; fi
    if lock_stale; then break_lock && continue; fi
    if [ $(( SECONDS - last )) -ge "$MSG_EVERY" ]; then
        last=$SECONDS
        echo "mac-remote: waiting $(( SECONDS - t0 )) s for the build lock held by: $(sed -n 1p "$L/owner" 2>/dev/null)" >&2
        if [ "$(owner_get container)" = "$MYNAME" ]; then
            echo "mac-remote: held by this container: probably a forgotten run of yours (local pid $(owner_get lpid), run $(owner_get run)); 'mac-remote.sh check' shows it, 'mac-remote.sh cancel' stops it" >&2
        fi
    fi
    if [ $(( SECONDS - t0 )) -ge "$LOCK_WAIT" ]; then
        echo "mac-remote: build lock not free after ${LOCK_WAIT}s (held by: $(sed -n 1p "$L/owner" 2>/dev/null))" >&2
        exit 75
    fi
    sleep "$POLL"
done
mem=?; command -v vm_stat >/dev/null 2>&1 && mem=$(vm_stat | awk '/page size/{ps=$8} /Pages free/{f=$3} /Pages inactive/{n=$3} END{printf "%.1f", (f+n)*ps/1073741824}')
echo "mac-remote: build lock acquired after $(( SECONDS - t0 )) s; free memory $mem GiB" >&2
echo @@LOCKED
EOF
}

sync_tree() {
    "${SSH[@]}" "$HOST" "mkdir -p $(q "$TREE")" || die "cannot reach $HOST"
    command -v "$RSYNC_BIN" >/dev/null || die "rsync is not installed in this container (old golden image?)"
    local t0=$SECONDS stats
    # Not synced: .git (the container's objects live in an alternate on the host), container-only data,
    # build outputs (kept on the Mac: excluded paths survive --delete), and Linux-native node_modules.
    stats=$("${RSYNC[@]}" --delete --stats \
        --exclude='.git' --exclude='/.local/' --exclude='/docs/' --exclude='/website/' \
        --exclude='/fastlane/' --exclude='/play/' --exclude='/app/src/test/roborazzi/' \
        --exclude='/local.properties' --exclude='build/' --exclude='.gradle/' --exclude='.kotlin/' \
        --exclude='node_modules/' --exclude='/app/bibleview-js/dist/' \
        --exclude='/iosApp/iosApp.xcodeproj/' --exclude='*.xcresult' \
        "$repo/" "$HOST:$TREE/") || die "rsync to $HOST failed"
    grep -E '^(Number of regular files transferred|Total transferred file size)' <<<"$stats" || true
    # :app's Gradle configuration runs `git rev-parse` / `git describe`: give it a one-commit repo.
    # It also needs an Android SDK location; iOS tasks never use the SDK, so an empty stub directory does.
    remote "$(prologue)
[ -d .git ] || { git init -q && git -c user.name=mac-remote -c user.email=mac-remote@localhost commit -q --allow-empty -m 'mac-remote tree'; }
[ -f local.properties ] || [ -n \"\${ANDROID_HOME:-}\" ] || { mkdir -p \$HOME/android-sdk-stub && echo \"sdk.dir=\$HOME/android-sdk-stub\" > local.properties; }"
    echo "mac-remote: synced to $HOST:~/$TREE in $((SECONDS - t0)) s"
}

fetch() {
    local src="$1" dest="${2:-$OUT}"
    mkdir -p "$dest"
    "${RSYNC[@]}" "$HOST:$TREE/$src" "$dest/" && echo "mac-remote: fetched $src -> ${dest#"$repo"/}"
}

# ---------------------------------------------------------------------------------------------
# Run markers (container side): $OUT/runs/<run-id>/{meta,output.log,steps,exit}
#  meta    key=value: id, container, cmd, pid, pstart (local process start time), start_epoch, token, steps
#  steps   tab separated: <n> start <epoch> <step>   /   <n> end <epoch> <exit code>
#  exit    key=value, written once at the end (also on timeout, failure and SIGTERM/INT/HUP):
#          code, reason, end_epoch, failed_step.  No exit file and a dead pid = the run was killed hard.
lpstart() { ps -p "$1" -o lstart= 2>/dev/null | tr -s ' ' | sed 's/^ //;s/ $//'; }
meta_get() { sed -n "s/^$2=//p" "$1/meta" 2>/dev/null | sed -n 1p; }
exit_get() { sed -n "s/^$2=//p" "$1/exit" 2>/dev/null | sed -n 1p; }
run_alive() {   # the recorded local process still exists (pid and start time match)
    local pid ps
    pid=$(meta_get "$1" pid); ps=$(meta_get "$1" pstart)
    [ -n "$pid" ] && [ -n "$ps" ] && [ "$(lpstart "$pid")" = "$ps" ]
}
fmt_secs() {
    local s=$1
    if [ "$s" -lt 120 ]; then echo "${s}s"
    elif [ "$s" -lt 7200 ]; then echo "$((s / 60))m$((s % 60))s"
    else echo "$((s / 3600))h$((s % 3600 / 60))m"; fi
}
file_age() { echo $(( $(date +%s) - $(stat -c %Y "$1" 2>/dev/null || date +%s) )); }

RUN_ID= RUNDIR= TOKEN= RUN_SIGNAL= RUN_FAILED_STEP= RUN_REASON=
RS_PID_= RS_IN= RS_OUT=
run_begin() {   # label total-steps cmdline...
    local label=$1 total=$2; shift 2
    local cmdline="$*"; cmdline="${cmdline//$'\n'/ }"
    TOKEN=$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')
    RUN_ID="$(date +%Y%m%d-%H%M%S)-$label-$$"
    RUNDIR="$OUT/runs/$RUN_ID"
    mkdir -p "$RUNDIR"
    {
        echo "id=$RUN_ID"; echo "container=$NAME"; echo "cmd=${cmdline:0:300}"; echo "pid=$$"
        echo "pstart=$(lpstart $$)"; echo "start_epoch=$(date +%s)"; echo "token=$TOKEN"; echo "steps=$total"
        echo "log=$RUNDIR/output.log"
    } > "$RUNDIR/meta"
    : > "$RUNDIR/steps"
    # prune old run dirs (the newest KEEP_RUNS stay; a running run is always among them)
    ls -1d "$OUT"/runs/*/ 2>/dev/null | sort | head -n "-$KEEP_RUNS" | while read -r old; do rm -rf "$old"; done
    exec > >(tee -a "$RUNDIR/output.log") 2> >(tee -a "$RUNDIR/output.log" >&2)
    trap 'RUN_SIGNAL=HUP; exit 129' HUP
    trap 'RUN_SIGNAL=INT; exit 130' INT
    trap 'RUN_SIGNAL=TERM; exit 143' TERM
    trap run_exit EXIT
    echo "mac-remote: run $RUN_ID ($label); state in ${RUNDIR#"$repo"/}" >&2
}
run_exit() {
    local rc=$?
    trap - EXIT
    session_abort
    if [ ! -e "$RUNDIR/exit" ]; then
        local reason
        if [ -n "$RUN_SIGNAL" ]; then reason="signal $RUN_SIGNAL"
        elif [ -n "$RUN_REASON" ] && [ "$rc" != 0 ]; then reason="$RUN_REASON"
        elif [ "$rc" = 142 ]; then reason=timeout
        elif [ "$rc" != 0 ]; then reason=failed
        else reason=ok; fi
        printf 'code=%s\nreason=%s\nend_epoch=%s\nfailed_step=%s\n' "$rc" "$reason" "$(date +%s)" "$RUN_FAILED_STEP" > "$RUNDIR/exit.tmp"
        mv "$RUNDIR/exit.tmp" "$RUNDIR/exit"
    fi
    exit "$rc"
}

# ---------------------------------------------------------------------------------------------
# One remote bash session per run (a coprocess): lock code first, @@LOCKED back, local sync, then one script
# per step; the step's exit code comes back as a "@@STEP-RC n" line. The remote bash reads its script from
# stdin as it goes, so the lock (and its watchdog) stays held across all steps.
session_send() {   # text
    trap '' PIPE
    printf '%s\n' "$1" >&"$RS_IN" 2>/dev/null
    local rc=$?
    trap - PIPE
    return $rc
}
# Pass remote stdout through until a sentinel arrives. Returns 0 and sets SENT (the sentinel's argument), or 1 on EOF.
session_read() {
    local line pre
    SENT=
    while IFS= read -r -u "$RS_OUT" line; do
        case "$line" in
        *@@LOCKED) pre=${line%@@LOCKED}; [ -z "$pre" ] || printf '%s\n' "$pre"; SENT=locked; return 0 ;;
        *"@@STEP-RC "*) pre=${line%%@@STEP-RC *}; [ -z "$pre" ] || printf '%s' "$pre"; SENT=${line##*@@STEP-RC }; return 0 ;;
        *) printf '%s\n' "$line" ;;
        esac
    done
    return 1
}
session_open() {
    coproc RS { exec "${SSH_RUN[@]}" "$HOST" 'bash -s'; }
    RS_OUT=${RS[0]}; RS_IN=${RS[1]}; RS_PID_=$RS_PID
    session_send "$(lock_script)"
    if ! session_read; then
        local rc=0; wait "$RS_PID_" 2>/dev/null || rc=$?; RS_PID_=
        [ "$rc" != 0 ] || rc=255
        return "$rc"
    fi
}
session_step() {   # n script  -> sets STEP_RC
    local n=$1
    session_send "__step_$n() {
$2
}
( while :; do sleep $HEARTBEAT >/dev/null 2>&1; echo \"mac-remote: heartbeat (step $n): newest build log: \$(f=\$(ls -t build/*.log 2>/dev/null | sed -n 1p); [ -n \"\$f\" ] && echo \"\$f idle \$(( \$(date +%s) - \$(mtime \"\$f\") )) s\" || echo none)\"; done ) </dev/null &
__hb=\$!
__step_$n </dev/null; __rc=\$?
kill \$__hb 2>/dev/null
echo \"@@STEP-RC \$__rc\"" || true
    if session_read; then STEP_RC=$SENT; else
        STEP_RC=0; wait "$RS_PID_" 2>/dev/null || STEP_RC=$?; RS_PID_=
        [ "$STEP_RC" != 0 ] || STEP_RC=255
        echo "mac-remote: remote session ended without a result for step $n (exit $STEP_RC)" >&2
    fi
}
session_close() {
    [ -n "$RS_PID_" ] || return 0
    session_send "exit 0" || true
    eval "exec $RS_IN>&-"
    # bounded: a stray remote background process holding the channel open must not hang the run
    while IFS= read -r -t 60 -u "$RS_OUT" line; do printf '%s\n' "$line"; done
    kill "$RS_PID_" 2>/dev/null; wait "$RS_PID_" 2>/dev/null; RS_PID_=
}
session_abort() { [ -z "${RS_PID_:-}" ] || kill "$RS_PID_" 2>/dev/null; RS_PID_=; }

# ---------------------------------------------------------------------------------------------
# Steps. step_script_<kind> <n> <args...> prints the body of a remote function (use `return`, not `exit`);
# step_prep_<kind> runs locally before it (warnings); step_post_<kind> <n> <rc> <args...> runs locally after
# it, while the lock is still held (fetching results).
step_prep_gradle() {
    local a
    for a in "$@"; do
        case "$a" in *iosSimulatorArm64Test*)
            echo "mac-remote: WARNING: '$a' through the plain gradle step runs without a booted simulator (IOS_TEST_SIMULATOR unset): sharedCore/sharedUi tests crash with SIGTRAP. Use: mac-remote.sh native-tests [--force] <modules>" >&2 ;;
        esac
    done
}
step_script_gradle() {
    local n=$1 t; shift; t=$(step_timeout gradle)
    cat <<EOF
tmo $t ./gradlew -Dorg.gradle.native=false $(q "$@"); rc=\$?
[ \$rc = 142 ] && { echo 'mac-remote: TIMEOUT after ${t}s, stopping the Gradle daemon' >&2; ./gradlew --stop >/dev/null 2>&1; }
return \$rc
EOF
}

native_parse() {   # [--force] [mod...] -> NATIVE_FORCE, NATIVE_MODS
    NATIVE_FORCE=; NATIVE_MODS=()
    while [ $# -gt 0 ]; do
        case "$1" in --force) NATIVE_FORCE=1 ;; *) NATIVE_MODS+=("$1") ;; esac
        shift
    done
    [ ${#NATIVE_MODS[@]} -gt 0 ] || NATIVE_MODS=(jsword kdom sharedCore sharedData sharedLogic sharedUi)
}
step_script_native_tests() {
    local n=$1 t; shift; t=$(step_timeout native-tests)
    native_parse "$@"
    cat <<EOF
# sharedCore's tests run inside a booted simulator (IOS_TEST_SIMULATOR, see sharedCore/build.gradle.kts).
IOS_TEST_SIMULATOR=\$(sim_boot $(q "$SIM_IPHONE")) || return 1
export IOS_TEST_SIMULATOR
fail=0
for m in $(q "${NATIVE_MODS[@]}"); do
    $([ -z "$NATIVE_FORCE" ] || echo 'rm -rf "$m/build/test-results/iosSimulatorArm64Test"   # --force: a real rerun, not UP-TO-DATE')
    echo "=== :\$m:iosSimulatorArm64Test"
    tmo $t ./gradlew -Dorg.gradle.native=false :\$m:iosSimulatorArm64Test --continue; r=\$?
    [ \$r = 142 ] && { echo "mac-remote: TIMEOUT in \$m after ${t}s" >&2; ./gradlew --stop >/dev/null 2>&1; }
    [ \$r = 0 ] || fail=1
done
return \$fail
EOF
}
step_post_native_tests() {
    local n=$1 rc=$2 m d; shift 2
    native_parse "$@"
    for m in "${NATIVE_MODS[@]}"; do
        rm -rf "$OUT/native-tests/$m/test-results/iosSimulatorArm64Test" "$OUT/native-tests/$m/reports/tests/iosSimulatorArm64Test"
        for d in test-results/iosSimulatorArm64Test reports/tests/iosSimulatorArm64Test; do
            fetch "$m/build/$d" "$OUT/native-tests/$m/$(dirname "$d")" 2>/dev/null || true
        done
    done
    echo "mac-remote: native-tests exit $rc; XML under ${OUT#"$repo"/}/native-tests/<module>/test-results"
}

xcode_parse() {   # [iphone|ipad] [xcodebuild args...] -> XC_WHICH, XC_SIM, XC_ARGS
    XC_WHICH="${1:-iphone}"; [ $# -gt 0 ] && shift
    case "$XC_WHICH" in iphone) XC_SIM="$SIM_IPHONE" ;; ipad) XC_SIM="$SIM_IPAD" ;; *) die "device must be iphone or ipad" ;; esac
    XC_ARGS=("$@")
}
step_script_xcode() {   # <kind> <n> [dev] [args...]
    local kind=$1 n=$2 t; shift 2; t=$(step_timeout "$kind")
    xcode_parse "$@"
    local which=$XC_WHICH
    cat <<EOF
mkdir -p build && rm -rf build/ios-$which.xcresult build/screenshots-$which
# bibleview-js: npm ci only when package-lock.json changed (macOS-native node_modules live on the Mac)
( cd app/bibleview-js
  stamp=node_modules/.mac-remote-lock-sha; sha=\$(shasum package-lock.json | cut -d' ' -f1)
  [ "\$(cat \$stamp 2>/dev/null)" = "\$sha" ] || { npm ci --no-audit --no-fund && echo \$sha > \$stamp; } &&
  npm run build-production ) > build/bibleview-$which.log 2>&1 || { tail -40 build/bibleview-$which.log; return 1; }
( cd iosApp && xcodegen generate --quiet ) || return 1
UDID=\$(sim_boot $(q "$XC_SIM")) || return 1
echo "mac-remote: simulator \$UDID"
if [ $kind = xcode-test ]; then
    # The app runs as its own process under XCUITest: its NSLog output only reaches the unified log.
    xcrun simctl spawn \$UDID log stream --style compact --predicate 'process == "$APP_PROCESS"' > build/app-$which.log 2>&1 &
    LOGPID=\$!; sleep 2
    $(for v in $(compgen -v TEST_RUNNER_ || true); do printf 'export %s=%q\n' "$v" "${!v}"; done)
    tmo $t xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \\
        -destination "platform=iOS Simulator,id=\$UDID" -resultBundlePath build/ios-$which.xcresult $(q "${XC_ARGS[@]}") \\
        > build/xcodebuild-$which.log 2>&1; rc=\$?
    [ \$rc = 142 ] && echo "mac-remote: TIMEOUT: xcodebuild killed after ${t}s" >&2
    sleep 2; kill \$LOGPID 2>/dev/null
    [ -d build/ios-$which.xcresult ] && xcrun xcresulttool export attachments --path build/ios-$which.xcresult \\
        --output-path build/screenshots-$which >/dev/null 2>&1
    grep -E '^(Test Suite|Test Case .*(passed|failed|skipped))|error:|\\*\\* TEST' build/xcodebuild-$which.log | grep -vE "Test Suite '(All tests|.*\\.xctest)' started" | tail -60
    echo "app log: \$(wc -l < build/app-$which.log) lines, WEBVIEW-TERMINATED: \$(grep -c WEBVIEW-TERMINATED build/app-$which.log), js[ lines: \$(grep -c 'js\\[' build/app-$which.log)"
    return \$rc
else
    tmo $t xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \\
        -destination "platform=iOS Simulator,id=\$UDID" -derivedDataPath build/dd-$which \\
        > build/xcodebuild-$which.log 2>&1 || { grep -E 'error:|BUILD FAILED' build/xcodebuild-$which.log | tail -40; return 1; }
    APP=\$(ls -d build/dd-$which/Build/Products/Debug-iphonesimulator/$APP_PROCESS.app | sed -n 1p)
    xcrun simctl install \$UDID "\$APP" && xcrun simctl terminate \$UDID $APP_ID 2>/dev/null
    xcrun simctl launch \$UDID $APP_ID && sleep 5   # let the first render settle before a sim shot
fi
EOF
}
step_script_xcode_test() { step_script_xcode xcode-test "$@"; }
step_script_xcode_build() { step_script_xcode xcode-build "$@"; }

# Evidence goes to a per-run directory, $RUNDIR/xcode/step<n>-<device>/, so a later run never overwrites it;
# the old paths .local/mac/<container>/xcode/<name>-<device> become links to the latest run.
step_post_xcode() {   # <kind> <n> <rc> [dev] [args...]
    local kind=$1 n=$2 rc=$3; shift 3
    xcode_parse "$@"
    local which=$XC_WHICH ev="$RUNDIR/xcode/step$n-$XC_WHICH" f
    mkdir -p "$ev" "$OUT/xcode"
    for f in "build/xcodebuild-$which.log" "build/app-$which.log" "build/screenshots-$which" "build/ios-$which.xcresult"; do
        fetch "$f" "$ev" >/dev/null 2>&1 || true
    done
    # xcresulttool names attachments by UUID; manifest.json maps them to the names the tests gave
    # (e.g. split2-dark_0_<uuid>.png): copy each PNG to <test>/<name>.png next to them.
    python3 - "$ev/screenshots-$which" <<'EOF' || true
import json, os, re, shutil, sys
d = sys.argv[1]
m = os.path.join(d, "manifest.json")
if os.path.exists(m):
    for t in json.load(open(m)):
        test = re.sub(r"\W+", "_", t.get("testIdentifier", "test")).strip("_")
        for a in t.get("attachments", []):
            f = a.get("exportedFileName", "")
            if f.endswith(".png") and os.path.exists(os.path.join(d, f)):
                name = re.sub(r"_\d+_[0-9A-F-]{36}(?=\.png$)", "", a.get("suggestedHumanReadableName", f))
                os.makedirs(os.path.join(d, test), exist_ok=True)
                shutil.copy(os.path.join(d, f), os.path.join(d, test, name))
EOF
    for f in "xcodebuild-$which.log" "app-$which.log" "screenshots-$which" "ios-$which.xcresult"; do
        if [ -e "$ev/$f" ]; then
            [ -L "$OUT/xcode/$f" ] || rm -rf "${OUT:?}/xcode/$f"   # a real file/dir from the old layout
            ln -sfn "../runs/$RUN_ID/xcode/step$n-$which/$f" "$OUT/xcode/$f"
        else
            rm -rf "${OUT:?}/xcode/$f"
        fi
    done
    echo "mac-remote: $kind $which exit $rc; logs, xcresult and screenshots under ${ev#"$repo"/}/ (latest: ${OUT#"$repo"/}/xcode/)"
}
step_post_xcode_test() { step_post_xcode xcode-test "$@"; }
step_post_xcode_build() { step_post_xcode xcode-build "$@"; }

step_script_run() {
    local n=$1 t; shift; t=$(step_timeout run)
    cat <<EOF
export TREE
tmo $t bash -c $(q "$*"); rc=\$?
[ \$rc = 142 ] && echo "mac-remote: TIMEOUT: command killed after ${t}s" >&2
return \$rc
EOF
}

step_script_sim_boot() { local n=$1; shift; echo "sim_boot $(q "$1")"; }
step_script_sim_shot() { local n=$1; shift; echo "mkdir -p build/shots && xcrun simctl io booted screenshot $(q "build/shots/$1.png") >/dev/null 2>&1"; }
step_post_sim_shot() { local n=$1 rc=$2; shift 2; fetch "build/shots/$1.png" "$OUT/shots"; }
step_script_sim_launch() { local n=$1; shift; echo "xcrun simctl terminate booted $(q "$1") 2>/dev/null; xcrun simctl launch booted $(q "$1")"; }
step_script_sim_shutdown() { echo "xcrun simctl shutdown all"; }

# Run one or more steps under one lock: label, sync (1/0), then one quoted step per argument.
run_steps() {
    local label=$1 sync=$2; shift 2
    local total=$# n=0 step kind fn rc=0 s e
    local steps=("$@")
    for step in "${steps[@]}"; do
        eval "STEP=($step)"
        [ "${STEP[0]}" != link ] || continue
        kind=${STEP[0]//-/_}
        declare -F "step_script_$kind" >/dev/null || die "unknown step '${STEP[0]}' (steps: link gradle native-tests xcode-test xcode-build run)"
    done
    run_begin "$label" "$total" "${steps[@]}"
    session_open || { rc=$?; RUN_REASON="no lock"; exit "$rc"; }
    if [ "$sync" = 1 ]; then sync_tree || { RUN_REASON="sync failed"; exit 1; }; fi
    session_send "$(prologue)" || true
    for step in "${steps[@]}"; do
        n=$((n + 1))
        eval "STEP=($step)"
        [ "${STEP[0]}" != link ] || STEP=(gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64)
        kind=${STEP[0]//-/_}
        printf '%s\tstart\t%s\t%s\n' "$n" "$(date +%s)" "${STEP[*]}" >> "$RUNDIR/steps"
        ! declare -F "step_prep_$kind" >/dev/null || "step_prep_$kind" "${STEP[@]:1}"
        session_step "$n" "$("step_script_$kind" "$n" "${STEP[@]:1}")"
        ! declare -F "step_post_$kind" >/dev/null || "step_post_$kind" "$n" "$STEP_RC" "${STEP[@]:1}" || true
        printf '%s\tend\t%s\t%s\n' "$n" "$(date +%s)" "$STEP_RC" >> "$RUNDIR/steps"
        if [ "$STEP_RC" != 0 ]; then rc=$STEP_RC; RUN_FAILED_STEP=$n; break; fi
    done
    session_close
    if [ "$total" -gt 1 ]; then
        echo "mac-remote: batch result:"
        n=0
        for step in "${steps[@]}"; do
            n=$((n + 1))
            s=$(awk -F'\t' -v n="$n" '$1==n && $2=="start"{print $3}' "$RUNDIR/steps")
            e=$(awk -F'\t' -v n="$n" '$1==n && $2=="end"{print $3 " " $4}' "$RUNDIR/steps")
            if [ -z "$s" ]; then echo "  step $n: $step -> not run"
            else set -- $e; echo "  step $n: $step -> exit ${2:-?} ($(fmt_secs $(( ${1:-$s} - s ))))"; fi
        done
    fi
    exit "$rc"
}

# ---------------------------------------------------------------------------------------------
# check / cancel
list_runs() {
    local d id state ci age line n=0
    echo "runs of this container ($NAME), newest last (state kept in ${OUT#"$repo"/}/runs):"
    for d in $(ls -1d "$OUT"/runs/*/ 2>/dev/null | sort | tail -5); do
        d=${d%/}; id=$(basename "$d"); n=$((n + 1))
        age=$(( $(date +%s) - $(meta_get "$d" start_epoch) ))
        if [ -f "$d/exit" ]; then
            ci=$(( $(exit_get "$d" end_epoch) - $(meta_get "$d" start_epoch) ))
            state="exited $(exit_get "$d" code) ($(exit_get "$d" reason)) after $(fmt_secs "$ci"), $(fmt_secs "$age") ago"
        elif run_alive "$d"; then
            state="RUNNING since $(fmt_secs "$age"), local pid $(meta_get "$d" pid), output.log idle $(fmt_secs "$(file_age "$d/output.log")")"
            line=$(tail -1 "$d/steps" 2>/dev/null | awk -F'\t' '{print "step " $1 " " $2}')
            [ -z "$line" ] || state="$state, last step line: $line"
            line=$(grep 'heartbeat' "$d/output.log" 2>/dev/null | tail -1)
            [ -z "$line" ] || state="$state; last $line"
        else
            state="LOST: process gone without an exit marker (killed hard?); 'cancel' cleans it up"
        fi
        echo "  $id  [$(meta_get "$d" cmd)]  $state"
    done
    [ "$n" -gt 0 ] || echo "  none recorded"
}

descendants() { local c; for c in $(pgrep -P "$1" 2>/dev/null); do echo "$c"; descendants "$c"; done; }
kill_run() {   # pid: TERM the leader (its traps write the marker and close the session), then its tree, then KILL
    local pid=$1 tree p i
    tree=$(descendants "$pid")
    kill -TERM "$pid" 2>/dev/null
    for i in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    for p in $tree; do [ "$p" = "$$" ] || kill -TERM "$p" 2>/dev/null; done
    sleep 0.5
    for p in $pid $tree; do [ "$p" = "$$" ] || ! kill -0 "$p" 2>/dev/null || kill -KILL "$p" 2>/dev/null; done
    return 0
}
cmd_cancel() {
    local want="${1:-}" d id tokens="" found=0
    for d in $(ls -1d "$OUT"/runs/*/ 2>/dev/null | sort); do
        d=${d%/}; id=$(basename "$d")
        [ -z "$want" ] || [ "$id" = "$want" ] || continue
        [ ! -f "$d/exit" ] || continue
        found=1
        if run_alive "$d"; then
            echo "mac-remote: cancelling $id (local pid $(meta_get "$d" pid))"
            kill_run "$(meta_get "$d" pid)"
        else
            echo "mac-remote: $id has no exit marker and its process is gone (lost run)"
        fi
        if [ ! -f "$d/exit" ]; then
            printf 'code=143\nreason=cancelled\nend_epoch=%s\nfailed_step=\n' "$(date +%s)" > "$d/exit"
        fi
        tokens="$tokens $(meta_get "$d" token)"
    done
    [ "$found" = 1 ] || echo "mac-remote: no running or lost run of $NAME recorded${want:+ with id $want}"
    [ -n "$tokens" ] || { echo "mac-remote: nothing to release on the Mac; if 'check' still shows a lock of this container, it is from an older script version: use 'unlock' after checking"; return 0; }
    {
        printf 'TOKENS=%q\n' "$tokens"
        rlib
        cat <<'EOF'
for TOK in $TOKENS; do
    [ -d "$L" ] || { echo "mac-remote: remote lock already free"; break; }
    if [ "$(owner_get token)" = "$TOK" ]; then
        pid=$(owner_get pid)
        if [ -n "$pid" ] && [ "$(pstart_of "$pid")" = "$(owner_get pstart)" ]; then
            pkill -TERM -P "$pid" 2>/dev/null; kill -TERM "$pid" 2>/dev/null; sleep 1
        fi
        lock_release_if "$TOK" && echo "mac-remote: released the remote lock (run $(echo "$TOK" | cut -c1-8)...)"
        break
    fi
done
[ -d "$L" ] && echo "mac-remote: remote lock left alone, held by: $(sed -n 1p "$L/owner" 2>/dev/null) (token is not one of the cancelled runs)"
true
EOF
    } | "${SSH[@]}" "$HOST" 'bash -s'
}

cmd="${1:-}"; shift || true
case "$cmd" in
check)
    list_runs
    echo
    {
        printf 'TREE=$HOME/%s\nMYNAME=%q\n' "$(q "$TREE")" "$NAME"
        rlib
        cat <<'EOF'
ram=$(sysctl -n hw.memsize 2>/dev/null || echo 0); cpus=$(sysctl -n hw.ncpu 2>/dev/null || echo ?)
echo "host: $(hostname)  user: $(whoami)  macOS $(sw_vers -productVersion 2>/dev/null) $(uname -m)  RAM $((ram/1073741824)) GiB  cpus $cpus"
echo "disk free: $(df -h ~ | awk 'NR==2{print $4}')"
xcodebuild -version 2>/dev/null | sed -n 1p; java -version 2>&1 | sed -n 1p; echo "node $(node --version 2>/dev/null)"; echo "xcodegen $(xcodegen --version 2>&1 | awk '{print $2}')"
xcrun simctl list runtimes available 2>/dev/null | grep iOS || echo "NO iOS simulator runtime"
xcrun simctl list devices booted 2>/dev/null | grep -v '^==' || true
if [ -d "$L" ]; then
    c=$(owner_get container); ep=$(owner_get epoch); [ -n "$ep" ] || ep=$(mtime "$L")
    if lock_stale; then st="holder GONE: stale, the next waiter breaks it"; else st="holder alive"; fi
    echo "BUILD LOCK held by: $(sed -n 1p "$L/owner" 2>/dev/null) [$st; held for $(( ($(date +%s) - ep) / 60 )) min]"
    if [ "$c" = "$MYNAME" ]; then
        echo "  holder is THIS container ($MYNAME): local pid $(owner_get lpid), run $(owner_get run); 'mac-remote.sh cancel' stops it and releases the lock"
    else
        echo "  holder is another container (${c:-unknown}); waiting behind it is normal, do not unlock a live holder"
    fi
else
    echo "build lock: free"
fi
now=$(date +%s)
for f in "$TREE"/build/xcodebuild-*.log "$TREE"/build/app-*.log "$TREE"/build/bibleview-*.log; do
    [ -f "$f" ] && echo "remote log ${f#$TREE/}: last written $(( (now - $(mtime "$f")) / 60 )) min ago"
done
echo "free memory: $(vm_stat 2>/dev/null | awk '/page size/{ps=$8} /Pages free/{f=$3} /Pages inactive/{n=$3} END{printf "%.1f", (f+n)*ps/1073741824}') GiB; Gradle daemons: $(pgrep -f GradleDaemon | wc -l | tr -d ' '); Kotlin daemons: $(pgrep -f KotlinCompileDaemon | wc -l | tr -d ' ')"
[ -f ~/work/.mac-remote-watchdog.log ] && { echo "watchdog (last 3):"; tail -3 ~/work/.mac-remote-watchdog.log; }
echo "trees: $(ls ~/work 2>/dev/null | tr '\n' ' ')"
EOF
    } | "${SSH[@]}" "$HOST" 'bash -s'
    ;;
cancel)
    cmd_cancel "${1:-}"
    ;;
sync)
    sync_tree
    ;;
gradle)
    [ $# -gt 0 ] || die "usage: gradle <tasks/args...>"
    run_steps gradle 1 "$(q gradle "$@")"
    ;;
native-tests)
    run_steps native-tests 1 "$(q native-tests "$@")"
    ;;
xcode-test|xcode-build)
    xcode_parse "$@"   # validates the device before taking the lock
    run_steps "$cmd" 1 "$(q "$cmd" "$@")"
    ;;
run)
    sync=1; [ "${1:-}" != --no-sync ] || { sync=0; shift; }
    [ $# -gt 0 ] || die "usage: run [--no-sync] <shell command>"
    run_steps run "$sync" "$(q run "$@")"
    ;;
batch)
    sync=1; [ "${1:-}" != --no-sync ] || { sync=0; shift; }
    [ $# -gt 0 ] || die "usage: batch [--no-sync] <step>...   e.g. batch link native-tests \"xcode-test iphone\""
    run_steps batch "$sync" "$@"
    ;;
fetch)
    [ $# -ge 1 ] || die "usage: fetch <remote-rel-path> [local-dir]"
    fetch "$@"
    ;;
sim)
    sub="${1:-list}"; [ $# -gt 0 ] && shift
    case "$sub" in
    list) remote "$(prologue)
xcrun simctl list devices available | grep -v unavailable" ;;
    boot) case "${1:-iphone}" in iphone) s="$SIM_IPHONE" ;; ipad) s="$SIM_IPAD" ;; *) s="$1" ;; esac
          run_steps sim-boot 0 "$(q sim-boot "$s")" ;;
    shot) [ $# -ge 1 ] || die "usage: sim shot <name>"
          run_steps sim-shot 0 "$(q sim-shot "$1")" ;;
    log)  min="${1:-5}"; pred="${2:-process == \"$APP_PROCESS\"}"
          remote "$(prologue)
xcrun simctl spawn booted log show --style compact --last ${min}m --predicate $(q "$pred")" ;;
    launch) run_steps sim-launch 0 "$(q sim-launch "${1:-$APP_ID}")" ;;
    sample) remote "$(prologue)
p=\$(pgrep -n $(q "${1:-$APP_PROCESS}")) || { echo 'process not running' >&2; exit 1; }
sample \$p 5 -file /tmp/mac-remote-sample.txt >/dev/null 2>&1; cat /tmp/mac-remote-sample.txt" ;;
    crashes) remote "$(prologue)
mkdir -p build/crashes && find ~/Library/Logs/DiagnosticReports -name '$APP_PROCESS*' -mtime -2 -exec cp {} build/crashes/ \\; 2>/dev/null; ls -t build/crashes | head"
          fetch build/crashes "$OUT" ;;
    shutdown) run_steps sim-shutdown 0 "$(q sim-shutdown)" ;;
    *) die "unknown sim subcommand: $sub" ;;
    esac
    ;;
ssh)
    if [ $# -gt 0 ]; then "${SSH[@]}" "$HOST" "cd $(q "$TREE") 2>/dev/null; $*"; else "${SSH[@]}" -t "$HOST" "cd $(q "$TREE") 2>/dev/null; exec zsh -l"; fi
    ;;
unlock)
    "${SSH[@]}" "$HOST" 'L=$HOME/work/.build-lock; [ -d "$L" ] && { echo "removing lock held by: $(sed -n 1p "$L/owner" 2>/dev/null)"; rm -rf "$L" "$L.breaking"; } || echo "no lock"'
    ;;
*)
    sed -n '2,/^set -uo/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'
    [ -z "$cmd" ] || exit 2
    ;;
esac
