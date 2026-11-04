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
#   scripts/mac-remote.sh queue                 list queued tickets (JSON: identity, group, arrival, priority, expiry)
#   scripts/mac-remote.sh queue priority <ticket> <integer>  group orchestrator: higher priority goes first, ties FIFO
#   scripts/mac-remote.sh queue reserve <seconds>           group orchestrator: reserve a FIFO place for 1-3600 seconds
#   scripts/mac-remote.sh queue release <ticket>            group orchestrator: release its group's reservation
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
#                                               extra args e.g. -only-testing:iosAppUITests/PocUITests.
#                                               --release (right after the device): Release configuration (store
#                                               identity org.andbible.ios, generated version), and the Release app is
#                                               uninstalled first, so every run is a fresh install (Release ignores
#                                               -resetData), e.g. xcode-test iphone --release
#                                               -only-testing:iosAppUITests/OnboardingUITests
#   scripts/mac-remote.sh xcode-build [iphone|ipad] [--release]
#                                               build the app for the simulator, install it, launch it
#                                               (--release: the Release configuration, uninstalled first as above)
#   scripts/mac-remote.sh run [--no-sync] <shell command>
#                                               lock, sync, then run a command in the remote tree. --no-sync keeps the
#                                               Mac tree as it is (inspect or rerun what a previous run left there)
#   scripts/mac-remote.sh batch [--no-sync] [--keep-going] <step>...
#                                               several steps under ONE lock acquisition and ONE sync, run in order,
#                                               stopping at the first failing step (--keep-going: run every step, e.g.
#                                               one xcode-test per UI class; exit code and marker name the first
#                                               failure). Each step is one quoted argument:
#                                                 batch link native-tests "xcode-test iphone" "xcode-test ipad"
#                                               steps: link (= gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64),
#                                               gradle <args>, native-tests [--force] [mod...], xcode-test [dev] [args],
#                                               xcode-build [dev], run <command>
#   (gradle, native-tests, xcode-*, run, batch and the sim subcommands that drive the shared simulator — boot,
#   shot, launch, shutdown, crashes — plus sync and fetch take the one Mac-wide build lock and write a run marker
#   under .local/mac/<container>/runs/<run-id>/ (meta, output.log, steps, exit); check, cancel and sim list/log/sample do not)
#   scripts/mac-remote.sh cleanup               lock, then clean up after use: shut down all simulators, kill processes left over
#                                               from THIS container's tree (~/work/<container>/: test.kexe, xcodebuild, gradle
#                                               wrapper/daemons, simctl spawn), report (never kill) such processes of other
#                                               containers' trees with age/RSS/CPU, print free memory before and after.
#                                               Every Mac session ends with it (or `sim shutdown`); `check` lists the leftovers
#       Shared cleanup keeps warm outputs/daemons and only deletes captured scratch from the current source generation;
#       shared-path processes are report-only. Private cleanup removes old release archives/shots and own leftovers.
#       --derived-data explicitly removes own DerivedData (shared: tree-local build/dd-*). Prints disk state before/after.
#   scripts/mac-remote.sh reset-tree            lock, clear selected tree regenerable outputs; never global Xcode caches
#   scripts/mac-remote.sh prune [--trees <name...>] [--older-than <days>] [--sims] [--yes]
#                                               DELETES other containers' ~/work trees (+ their DerivedData) and, with --sims,
#                                               simulator devices other than the script's iPhone/iPad. Dry run (listing with
#                                               sizes) unless --yes; never touches this container's tree, the lock holder or a
#                                               tree with running processes. Only the maintainer knows which containers are
#                                               gone: the orchestrator asks and passes the names. check lists trees and sizes
#   (gradle, native-tests, xcode-*, run and batch shut down all simulators when their locked session ends, success or
#   failure, unless MAC_KEEP_SIM=1 or --keep-sim; the `sim` helpers keep the simulator booted for their call sequence)
#   scripts/mac-remote.sh fetch <remote-rel-path> [local-dir]
#                                               copy a path from the remote tree to .local/mac/<container>/
#   scripts/mac-remote.sh sim <sub>             simulator helpers, on the booted simulator unless noted:
#       sim list                                  devices and their state
#       sim boot [iphone|ipad]                    create the device if missing, boot it, print its UDID
#       sim shot <name>                           screenshot -> .local/mac/<container>/shots/<name>.png
#       sim log [minutes] [predicate]             app unified log of the last N minutes (default 5)
#       sim launch [bundle-id]                    (re)launch the app (default org.andbible.ios.dev, or $MAC_APP_ID)
#       sim sample [process]                      5 s stack sample of the app (hangs, busy loops)
#       sim crashes                               fetch the app's crash reports (~/Library/Logs/DiagnosticReports)
#       sim shutdown                              shut down all simulators
#   scripts/mac-remote.sh ssh [command]         raw ssh to the Mac (interactive shell without a command)
#   scripts/mac-remote.sh unlock                remove a stale build lock (check `check` output first; prefer `cancel`)
#
# Environment:
#   MAC_SSH_CONFIG   ssh config with the host alias (default ~/.ssh-mac/config, from `jailbee mount mac-ssh`)
#   MAC_HOST         host alias in that config (default andbible-mac)
#   MAC_WORKDIR      remote directory under ~ (default work/shared; safe work/<name> only)
#   MAC_OWN_TREE     1: use work/<container> instead of the shared warm tree
#   MAC_TRANSFER_TIMEOUT seconds per sync/evidence transfer (default 1800; bounded lease)
#   MAC_SIM_IPHONE   iPhone simulator name (default "iPhone 16", as in CI; created if missing)
#   MAC_SIM_IPAD     iPad simulator name (default "iPad Air 11-inch (M4)"; created if missing)
#   MAC_TIMEOUT      seconds before one remote step (Gradle/xcodebuild/run) is killed (default 3000; 2400 for xcode-test)
#   MAC_SLOTS        must be 1 (legacy lock compatibility; no parallel execution)
#   JAILBEE_ROLE / JAILBEE_ORCHESTRATOR  children may inspect the queue; only their named parent controls its group
#   MAC_LOCK_WAIT    seconds to wait for another container's build lock (default 5400; a dead holder's
#                    lock is broken automatically, so this only bounds waiting behind live runs)
#   MAC_HEARTBEAT    seconds between "heartbeat" lines a running step prints with the idle time of the newest
#                    remote log (default 120), so a hang shows in output.log and `check`
#   MAC_KEEP_RUNS    run directories (markers, logs, evidence) kept per container (default 20)
#   MAC_KEEP_SIM    1: do not shut down the simulators at the end of a gradle/native-tests/xcode-*/run/batch session
#                    (same as --keep-sim right after the command name)
#   MAC_RELEASE_KEEP_INSTALL  non-empty: `--release` does NOT uninstall org.andbible.ios first (a test over seeded app data)
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
# Cooperative ownership: children belong to their parent, orchestrators to themselves.
QUEUE_ROLE=orchestrator
QUEUE_GROUP=$NAME
if [ "${JAILBEE_ROLE:-}" = child ]; then
    QUEUE_ROLE=child
    QUEUE_GROUP=${JAILBEE_ORCHESTRATOR:-$NAME}
fi
SSH_BIN="${MAC_SSH_BIN:-ssh}"
RSYNC_BIN="${MAC_RSYNC_BIN:-rsync}"
WORKDIR="${MAC_WORKDIR:-work/shared}"
[ "${MAC_OWN_TREE:-0}" != 1 ] || WORKDIR="${MAC_WORKDIR:-work/$NAME}"
SHARED=0; [ "$WORKDIR" != work/shared ] || SHARED=1
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
# The Mac frees the lock when the container side is silent this long between steps (lost network, SIGKILLed
# script): it must cover the longest container-side sync/fetch. STEP_MARGIN: how long after a step's timeout
# the step-level watchdog kills the whole step (npm, xcodegen, simulator boot... run outside the inner timeouts).
IDLE_MAX="${MAC_IDLE_MAX:-1800}"
STEP_MARGIN="${MAC_STEP_MARGIN:-600}"
# Debug identity by default; MAC_APP_ID / MAC_APP_PRODUCT select another configuration (Release: org.andbible.ios / AndBible).
APP_ID="${MAC_APP_ID:-org.andbible.ios.dev}"
APP_PROCESS="${MAC_APP_PRODUCT:-AndBibleDev}"

die() { echo "mac-remote: $*" >&2; exit 1; }

# Legacy clients cannot honor parallel slots. Fail before any SSH or run side effect.
[ "${MAC_SLOTS:-1}" = 1 ] || die "MAC_SLOTS must be 1 while legacy clients use the build lock"
[[ "$NAME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]] && [ "$NAME" != shared ] || die "unsafe MAC_CONTAINER"
[[ "$WORKDIR" =~ ^work/[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]] || die "MAC_WORKDIR must be work/<safe-name> (nested paths/aliases unsupported)"
[ "${MAC_OWN_TREE:-0}" != 1 ] || [ "$SHARED" = 0 ] || die "own tree cannot alias shared"
[ -z "${GRADLE_USER_HOME:-}" ] || die "custom GRADLE_USER_HOME unsupported"
SOURCE_REVISION=unknown SOURCE_DIRTY=unknown

[ -f "$SSH_CONFIG" ] || die "no ssh config at $SSH_CONFIG. Ask the user to run on the host: jailbee mount mac-ssh $NAME (see docs/ios-remote-mac.md)"

SSH=("$SSH_BIN" -F "$SSH_CONFIG")
# Remote runs get their own connection, never the shared ControlMaster one: the build-lock watchdog
# watches this run's sshd-session, which only ends with its own connection.
SSH_RUN=("$SSH_BIN" -F "$SSH_CONFIG" -o ControlMaster=no -o ControlPath=none -o ServerAliveInterval=30 -o ServerAliveCountMax=10)
RSYNC=("$RSYNC_BIN" -az -e "ssh -F $SSH_CONFIG -o ServerAliveInterval=30 -o ServerAliveCountMax=10" --rsync-path=/opt/homebrew/bin/rsync)

# Shell-quote arguments for the remote command line.
q() { [ $# -eq 0 ] || printf '%q ' "$@"; }

# Run a bash script on the Mac. $1 = script text; the remote bash gets the login PATH from ~/.zshenv
# because ssh starts the command through the user's zsh.
remote() { timeout --kill-after=10 "${MAC_TRANSFER_TIMEOUT:-1800}" "${SSH_RUN[@]}" "$HOST" 'bash -s' <<<"$1"; }

# Fail closed on aliases, including symlinked ancestors and nested outputs used for deletion.
path_guard() {
    cat <<EOF
TREE=\$HOME/$(q "$TREE")
python3 -I - \$HOME $(q "$TREE") <<'__MR_PATH__' || exit 73
import os, sys
home, rel = sys.argv[1:]
p = home
owner_dir = os.path.join(home, os.path.dirname(rel))
for name in ('.mac-owner',):
    if os.path.islink(os.path.join(owner_dir, name)):
        raise SystemExit('mac-remote: symlink ownership record refused')
for part in rel.split('/'):
    p = os.path.join(p, part)
    if os.path.islink(p):
        raise SystemExit('mac-remote: symlink tree component refused: ' + p)
if os.path.realpath(p) != os.path.join(os.path.realpath(home), rel):
    raise SystemExit('mac-remote: noncanonical tree refused')
if os.path.isdir(p):
    for root, dirs, files in os.walk(p, followlinks=False):
        if '.git' in dirs: dirs.remove('.git')
        for name in dirs + files:
            f = os.path.join(root, name)
            if os.path.islink(f) and not os.path.realpath(f).startswith(p + os.sep):
                raise SystemExit('mac-remote: escaping symlink refused: ' + f)
__MR_PATH__
EOF
}
locked_guard() {
    path_guard
    printf '[ -n %q ] || exit 73\n' "$TOKEN"
    printf '[ "$(sed -n "s/^token=//p" "$HOME/work/.build-lock/owner" 2>/dev/null)" = %q ] || exit 73\n' "$TOKEN"
}
owner_guard() {
    locked_guard
    [ "$SHARED" = 0 ] || cat <<EOF
[ "\$(sed -n 's/^container=//p' \$HOME/work/shared/.mac-owner 2>/dev/null)" = $(q "$NAME") ] || { echo 'mac-remote: shared tree has no matching completed owner; sync first' >&2; exit 73; }
EOF
}
PROLOGUE_NOTREE=0   # 1 for cleanup/prune: they work without this container's tree
# Prologue for remote scripts: cd into the tree, helpers.
prologue() {
    cat <<EOF
set -uo pipefail
$(path_guard)
[ -z "\${GRADLE_USER_HOME:-}" ] || { echo 'mac-remote: custom remote GRADLE_USER_HOME unsupported' >&2; exit 73; }
for __opts in "\${GRADLE_OPTS:-}" "\${JAVA_OPTS:-}" "\${JAVA_TOOL_OPTIONS:-}" "\${JDK_JAVA_OPTIONS:-}" "\${_JAVA_OPTIONS:-}"; do
    case "\$__opts" in *gradle.user.home*) echo 'mac-remote: JVM Gradle home override unsupported' >&2; exit 73 ;; esac
done
export MAC_REMOTE_BUILD_CACHE=1
export GRADLE_OPTS="\${GRADLE_OPTS:-} -Dorg.gradle.caching=true"
cd "\$TREE" 2>/dev/null || $( [ "${PROLOGUE_NOTREE:-0}" = 1 ] && echo 'cd "$HOME"' || echo '{ echo "mac-remote: no remote tree at $TREE; run sync first" >&2; exit 1; }' )
# timeout without coreutils: perl's alarm survives exec
# tmo <seconds> cmd...: runs cmd in its own process group; on timeout (exit 142) or TERM the whole group is
# stopped (TERM, then KILL after 3 s). Exit code: the command's, 128+signal if it was killed. perl (setpgrp, fork)
# ships with macOS.
tmo() { perl -e 'use POSIX ":sys_wait_h"; my \$t = shift; my \$pid = fork(); die "fork: \$!" unless defined \$pid; if (!\$pid) { setpgrp(0,0); exec @ARGV or exit 127 } setpgrp(\$pid,\$pid); my \$stop = sub { my \$code = shift; kill "TERM", -\$pid; for (1..30) { last if waitpid(\$pid, WNOHANG) == \$pid; select(undef,undef,undef,0.1) } kill "KILL", -\$pid; kill "KILL", \$pid; waitpid(\$pid, 0); exit \$code }; \$SIG{ALRM} = sub { \$stop->(142) }; \$SIG{TERM} = sub { \$stop->(143) }; \$SIG{HUP} = sub { \$stop->(129) }; alarm \$t; waitpid(\$pid, 0); my \$st = \$?; alarm 0; exit((\$st & 127) ? 128 + (\$st & 127) : \$st >> 8)' "\$@"; }
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

# Remote snippets (portable bash 3.2). sims_down: shut down every booted simulator and say which.
sims_down_script() {
    cat <<'EOF'
__booted=$(xcrun simctl list devices booted 2>/dev/null | sed -n 's/^ *\(.*\) ([0-9A-F-]\{36\}) (Booted).*/\1/p' | tr '\n' ',' | sed 's/,$//; s/,/, /g')
xcrun simctl shutdown all >/dev/null 2>&1
if [ -n "$__booted" ]; then echo "mac-remote: cleanup: shut down simulators: $__booted"; else echo "mac-remote: cleanup: no simulator was booted"; fi
EOF
}
# Free memory line (GiB free+inactive, and the system's free percentage).
mem_line_script() {
    cat <<'EOF'
mem_line() { echo "mac-remote: memory $1: $(vm_stat 2>/dev/null | awk '/page size/{ps=$8} /Pages free/{f=$3} /Pages inactive/{n=$3} END{printf "%.1f GiB free+inactive", (f+n)*ps/1073741824}'); $(memory_pressure 2>/dev/null | sed -n 's/^System-wide memory free percentage: *//p' | sed 's/^/system free /')"; }
EOF
}
# Processes left over under ~/work. Prints "<mine|other> <pid> <etime> <rss-MiB> <cpu%> <command>" lines.
# A process belongs to a tree when its command line mentions /work/<tree>/ or its cwd is inside it (gradle daemons).
leftovers_script() {
    cat <<'EOF'
leftovers() {   # $1 = this container's name; prints classified lines
    ps -axww -o pid=,etime=,rss=,pcpu=,command= 2>/dev/null | while read -r pid et rss cpu cmd; do
        [ "$pid" != "$$" ] || continue
        case "$cmd" in
            *test.kexe*|*xcodebuild*|*gradlew*|*GradleWrapperMain*|*GradleDaemon*|*KotlinCompileDaemon*|*"simctl spawn"*|*xcodegen*|*node*"/work/"*|*xctest*) ;;
            *) continue ;;
        esac
        case "$cmd" in *.step-*|*.build-lock*|*mac-remote-watchdog*|*"ps -axww"*|"bash -s"*|*sshd*) continue ;; esac
        where=$(printf '%s\n' "$cmd" | sed -n 's|.*/work/\([^/ ]*\)/.*|\1|p' | sed -n 1p)
        if [ -z "$where" ]; then
            case "$cmd" in *Gradle*|*xcodebuild*|*test.kexe*)
                cwd=$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | sed -n 1p)
                where=$(printf '%s\n' "$cwd" | sed -n 's|.*/work/\([^/ ]*\)\(/.*\)\{0,1\}$|\1|p' | sed -n 1p) ;;
            esac
        fi
        [ -n "$where" ] || continue
        if [ "$where" = "$1" ] && [ "$where" != shared ]; then k=mine; else k=other; fi
        printf '%s %s %s %s %s %s [%s]\n' "$k" "$pid" "$et" "$((rss / 1024))" "$cpu" "$(printf '%s' "$cmd" | cut -c1-110)" "$where"
    done
}
EOF
}

# Disk report (static remote function; no parameters but $1 = this container's name for the "mine" marker).
disk_script() {
    cat <<'EOF'
gib() { awk -v k="$1" 'BEGIN{printf "%.1f GiB", k/1048576}'; }
# newest mtime (epoch) of anything at depth <= 3 in a tree (rsync keeps source mtimes, so the tree dir itself says little)
tree_mtime() { find "$1" -maxdepth 3 -print0 2>/dev/null | xargs -0 stat -f %m 2>/dev/null | sort -n | tail -1; }
disk_report() {   # $1 = this container; $2 = "quiet" to print only the summary line
    df -k ~ | awk 'NR==2{printf "mac-remote: disk (data volume): %.1f GiB free of %.1f GiB, %s used\n", $4/1048576, $2/1048576, $5}'
    pct=$(df -k ~ | awk 'NR==2{gsub("%","",$5); print $5}')
    if [ "${pct:-0}" -ge 85 ] 2>/dev/null; then
        echo "mac-remote: WARNING: the Mac data volume is ${pct}% FULL (limit 85%). Ask the maintainer before 'mac-remote.sh prune' (it deletes other containers' trees)."
    fi
    [ "${2:-}" != quiet ] || return 0
    dd_tmp=$(mktemp -d)
    for t in ~/work/*/; do
        t=${t%/}; ( du -sk "$t" 2>/dev/null | awk '{print $1}' > "$dd_tmp/$(basename "$t")" ) &
    done
    ( du -sk ~/Library/Developer/CoreSimulator/Devices 2>/dev/null | awk '{print $1}' > "$dd_tmp/.sim" ) &
    ( du -sk ~/Library/Developer/Xcode/DerivedData 2>/dev/null | awk '{print $1}' > "$dd_tmp/.dd" ) &
    ( du -sk ~/.konan 2>/dev/null | awk '{print $1}' > "$dd_tmp/.konan" ) &
    ( du -sk ~/.gradle 2>/dev/null | awk '{print $1}' > "$dd_tmp/.gradle" ) &
    wait
    echo "mac-remote: caches: CoreSimulator/Devices $(gib "$(cat "$dd_tmp/.sim")"), DerivedData $(gib "$(cat "$dd_tmp/.dd")") ($(ls ~/Library/Developer/Xcode/DerivedData 2>/dev/null | wc -l | tr -d ' ') folders), ~/.konan $(gib "$(cat "$dd_tmp/.konan")"), ~/.gradle $(gib "$(cat "$dd_tmp/.gradle")")"
    echo "mac-remote: ~/work trees, largest first (size, last modified; * = this container; a tree of a destroyed container is junk, but only the maintainer knows which):"
    for t in ~/work/*/; do
        t=${t%/}; n=$(basename "$t"); m=$(tree_mtime "$t")
        mark=" "; [ "$n" != "$1" ] || mark="*"
        echo "$(cat "$dd_tmp/$n") $mark $n $(date -r "${m:-0}" '+%F %H:%M')"
    done | sort -rn | awk '{k=$1; $1=""; printf "  %7.1f GiB %s\n", k/1048576, substr($0,2)}'
    rm -rf "$dd_tmp"
}
EOF
}
# Remove this container's regenerable junk (arg 1 = container, arg 2 = 1 to also remove its DerivedData).
own_junk_script() {
    cat <<'EOF'
own_junk() {
    local me=$1 dd=$2 tree=$TREE freed=0 f
    if [ "${MR_SHARED:-0}" = 1 ]; then
        echo "mac-remote: cleanup: shared warm outputs and unowned scratch kept"
        if [ "$dd" = 1 ]; then
            for f in "$tree"/build/dd-*; do
                [ ! -L "$f" ] || { echo 'mac-remote: symlink DerivedData refused' >&2; return 73; }
                [ ! -d "$f" ] || rm -rf "$f"
            done
        fi
        return 0
    fi
    if [ -d "$tree/build/release" ]; then
        # keep the newest of each kind (xcarchive, ipa, export dir), delete the rest
        for pat in '*.xcarchive' '*.ipa' 'export*'; do
            for f in $(cd "$tree/build/release" && ls -dt $pat 2>/dev/null | sed 1d); do
                echo "mac-remote: cleanup: removing build/release/$f ($(du -sh "$tree/build/release/$f" | awk '{print $1}'))"
                rm -rf "$tree/build/release/$f"
            done
        done
    fi
    # screenshots were fetched into the container by the step that took them
    if [ -d "$tree/build/shots" ] && [ -n "$(ls -A "$tree/build/shots" 2>/dev/null)" ]; then
        echo "mac-remote: cleanup: removing build/shots ($(du -sh "$tree/build/shots" | awk '{print $1}'))"
        rm -rf "$tree/build/shots"
    fi
    if [ "$dd" = 1 ]; then
        for d in ~/Library/Developer/Xcode/DerivedData/*/; do
            d=${d%/}
            case "${d##*/}" in ModuleCache.noindex|SDKStatCaches.noindex) continue ;; esac
            [ ! -L "$d" ] || continue
            if grep -qs "/work/$me/" "$d/info.plist" "$d/Info.plist" 2>/dev/null; then
                echo "mac-remote: cleanup: removing own DerivedData $(basename "$d") ($(du -sh "$d" | awk '{print $1}'))"
                rm -rf "$d"
            fi
        done
    else
        echo "mac-remote: cleanup: own DerivedData kept (it speeds up builds); '--derived-data' removes it"
    fi
}
EOF
}
# prune: delete OTHER containers' ~/work trees (and their DerivedData) and unneeded simulator devices. Dry run by default.
prune_script() {
    cat <<'EOF'
prune() {   # args: [--trees name...] [--older-than days] [--sims] [--yes]; PRUNE_ME, PRUNE_KEEP_SIMS (names) are preset
    local yes=0 older="" sims=0 mode="" names="" a t n d freed=0 now sz holder todo="" skipped=""
    now=$(date +%s)
    while [ $# -gt 0 ]; do
        case "$1" in
        --yes) yes=1 ;;
        --sims) sims=1 ;;
        --older-than) shift; older=${1:-}; case "$older" in ''|*[!0-9]*) echo "mac-remote: --older-than needs a number of days" >&2; return 2 ;; esac; mode=days ;;
        --trees) mode=trees ;;
        --*) echo "mac-remote: prune: unknown option $1" >&2; return 2 ;;
        *) [ "$mode" = trees ] || { echo "mac-remote: prune: unexpected '$1' (names go after --trees)" >&2; return 2; }; names="$names $1" ;;
        esac
        shift
    done
    if [ -z "$names" ] && [ -z "$older" ] && [ "$sims" = 0 ]; then
        echo "mac-remote: prune: nothing selected. Use --trees <name...>, --older-than <days> and/or --sims (add --yes to delete; default is a dry run)" >&2; return 2
    fi
    holder=$(sed -n 's/^container=//p' ~/work/.build-lock/owner 2>/dev/null | sed -n 1p)
    busy=$(leftovers "$PRUNE_ME" | awk '{print $NF}' | tr -d '[]' | sort -u)
    echo "mac-remote: prune ($([ "$yes" = 1 ] && echo DELETING || echo "dry run, nothing is deleted; add --yes")):"
    for a in $names; do
        case "$a" in */*|.*|'') echo "mac-remote: prune: bad tree name '$a'" >&2; return 2 ;; esac
        [ -d "$HOME/work/$a" ] || { echo "  $a: no such tree"; continue; }
        todo="$todo $a"
    done
    if [ -n "$older" ]; then
        for t in ~/work/*/; do
            n=$(basename "${t%/}")
            m=$(tree_mtime "${t%/}")
            [ $(( now - ${m:-0} )) -ge $(( older * 86400 )) ] || continue
            case " $todo " in *" $n "*) ;; *) todo="$todo $n" ;; esac
        done
    fi
    for n in $todo; do
        if [ "$n" = shared ]; then echo "  shared: SKIPPED, protected warm tree"; continue; fi
        [ ! -L "$HOME/work/$n" ] || { echo "mac-remote: refusing symlink prune target" >&2; return 73; }
        if [ "$n" = "$PRUNE_ME" ] || [ "$n" = "${PRUNE_SELECTED:-}" ]; then echo "  $n: SKIPPED, this container's own tree (use 'cleanup')"; continue; fi
        if [ -n "$holder" ] && [ "$n" = "$holder" ] && [ "$holder" != "$PRUNE_ME" ]; then echo "  $n: SKIPPED, holds the build lock"; continue; fi
        if echo "$busy" | grep -qx "$n"; then echo "  $n: SKIPPED, has running processes"; continue; fi
        sz=$(du -sk "$HOME/work/$n" 2>/dev/null | awk '{print $1}')
        dds=""
        for d in ~/Library/Developer/Xcode/DerivedData/*/; do
            d=${d%/}
            case "${d##*/}" in ModuleCache.noindex|SDKStatCaches.noindex) continue ;; esac
            [ ! -L "$d" ] || continue
            grep -qs "/work/$n/" "$d/info.plist" "$d/Info.plist" 2>/dev/null && dds="$dds $d"
        done
        for d in $dds; do sz=$(( sz + $(du -sk "$d" 2>/dev/null | awk '{print $1}') )); done
        echo "  $n: $(gib "$sz") (last modified $(date -r "$(tree_mtime "$HOME/work/$n")" '+%F %H:%M')); tree + $(echo $dds | wc -w | tr -d ' ') DerivedData folder(s)"
        freed=$(( freed + sz ))
        if [ "$yes" = 1 ]; then rm -rf "$HOME/work/$n" $dds; echo "    deleted"; fi
    done
    if [ "$sims" = 1 ]; then
        echo "  simulators: 'simctl delete unavailable' and every device other than: $PRUNE_KEEP_SIMS"
        xcrun simctl list devices unavailable 2>/dev/null | grep -c '(unavailable' | sed 's/^/    unavailable devices: /'
        xcrun simctl list devices available 2>/dev/null | sed -n 's/^ *\(.*\) (\([0-9A-F-]\{36\}\)) (\(.*\))$/\2|\1|\3/p' | while IFS='|' read -r u nm st; do
            case "|$PRUNE_KEEP_SIMS|" in *"|$nm|"*) continue ;; esac
            echo "    $nm $u ($st)"
            if [ "$yes" = 1 ]; then xcrun simctl shutdown "$u" >/dev/null 2>&1; xcrun simctl delete "$u" && echo "      deleted"; fi
        done
        [ "$yes" != 1 ] || xcrun simctl delete unavailable
    fi
    echo "mac-remote: prune: $([ "$yes" = 1 ] && echo freed || echo would free) about $(gib "$freed") of trees/DerivedData (simulators not counted)"
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
pstart_of() { LC_ALL=C TZ=UTC0 ps -p "$1" -o lstart= 2>/dev/null | tr -s ' ' | sed 's/^ //;s/ $//'; }
# 0 when the holder is gone (or the dir has had no owner record for 60 s); 1 for no lock or a live holder.
# Judged from ONE read of the owner record; STALE_TOKEN is that copy's token (break_lock compares against it).
STALE_TOKEN=
lock_stale() {
    STALE_TOKEN=
    [ -d "$L" ] || return 1
    local snap pid pstart cur
    snap=$(cat "$L/owner" 2>/dev/null)
    if [ -n "$snap" ]; then
        STALE_TOKEN=$(printf '%s\n' "$snap" | sed -n 's/^token=//p' | sed -n 1p)
        pid=$(printf '%s\n' "$snap" | sed -n 's/^pid=//p' | sed -n 1p)
        pstart=$(printf '%s\n' "$snap" | sed -n 's/^pstart=//p' | sed -n 1p)
        if [ -n "$pid" ] && [ -n "$pstart" ]; then
            cur=$(pstart_of "$pid")
            if [ -z "$cur" ]; then ! kill -0 "$pid" 2>/dev/null; else [ "$cur" != "$pstart" ]; fi   # ps blind but pid alive = alive
        else
            pid=$(printf '%s\n' "$snap" | sed -n '1s/.* pid \([0-9]*\) .*/\1/p')
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

# Embed the local helper, not a file in the (not yet synced) source tree. A shell
# function feeds Python via stdin on every call, so different script versions do
# not overwrite a helper being used by an existing waiter.
queue_helper_check() {
    [ -s "$(dirname "${BASH_SOURCE[0]}")/mac-queue.py" ] || die "queue helper mac-queue.py is missing or empty"
}
queue_lib() {
    printf 'QIDENTITY=%q QGROUP=%q QROLE=%q\n' "$NAME" "$QUEUE_GROUP" "$QUEUE_ROLE"
    cat <<'EOF'
mac_queue() {
    python3 -I - --root "$HOME/work/.mac-queue" --identity "$QIDENTITY" --group "$QGROUP" --role "$QROLE" "$@" <<'__MR_QUEUE_PY__'
EOF
    cat "$(dirname "${BASH_SOURCE[0]}")/mac-queue.py"
    printf '\n__MR_QUEUE_PY__\n}\n'
}

# Remote script part 1: wait for the lock, then print @@LOCKED-<run token> on stdout (the caller syncs the tree next).
lock_script() {
    printf 'set -uo pipefail\nMYNAME=%q TOKEN=%q LPID=%q RUNID=%q LOCK_WAIT=%q POLL=%q MSG_EVERY=%q IDLE_MAX=%q\n' \
        "$NAME" "$TOKEN" "$$" "$RUN_ID" "$LOCK_WAIT" "$LOCK_POLL" "$LOCK_MSG_EVERY" "$IDLE_MAX"
    rlib
    queue_lib
    cat <<'EOF'
mkdir -p "$HOME/work"
QTICKET=
try_acquire() {
    local P="$L.pending.$TOKEN"
    rm -rf "$P"; mkdir "$P" || return 1
    printf '%s pid %s since %s\ntoken=%s\ncontainer=%s\npid=%s\npstart=%s\nepoch=%s\nlpid=%s\nrun=%s\n' \
        "$MYNAME" $$ "$(date '+%F %T')" "$TOKEN" "$MYNAME" $$ "$(pstart_of $$)" "$(date +%s)" "$LPID" "$RUNID" > "$P/owner"
    local admission rc
    admission=$(mac_queue admit "$QTICKET" "$P" "$L"); rc=$?
    rm -rf "$P"
    [ "$rc" = 0 ] || return 74
    [ "$admission" = acquired ] && return 0
    [ "$admission" = busy ] && return 1
    return 74
}
break_lock() {
    local B="$L.breaking" seen got moved bpid
    if ! mkdir "$B" 2>/dev/null; then
        # an abandoned breaker mutex: its recorded pid is dead (no pid file: older than 60 s)
        bpid=$(cat "$B/pid" 2>/dev/null)
        if [ -n "$bpid" ]; then kill -0 "$bpid" 2>/dev/null || rm -rf "$B"
        elif [ $(( $(date +%s) - $(mtime "$B") )) -gt 60 ]; then rm -rf "$B"; fi
        return 1
    fi
    echo $$ > "$B/pid"
    lock_stale || { rm -rf "$B"; return 1; }
    seen=$STALE_TOKEN; moved="$L.broken.$TOKEN"
    if rename "$L" "$moved"; then
        got=$(sed -n 's/^token=//p' "$moved/owner" 2>/dev/null | sed -n 1p)
        if [ "$got" = "$seen" ]; then
            echo "mac-remote: breaking a stale build lock (holder gone): $(sed -n 1p "$moved/owner" 2>/dev/null)" >&2
            rm -rf "$moved"
        else   # the lock changed hands between check and rename: put it back
            rename "$moved" "$L" || echo "mac-remote: WARNING: moved a live lock aside and could not restore it: $moved" >&2
        fi
    fi
    rm -rf "$B"; return 0
}
# Idle guard: armed while the container side works (sync, fetches) and between steps. When no step starts
# (or ends the session) within IDLE_MAX seconds the container side is lost (network down: the Mac's sshd
# session can outlive it for hours): release the lock and stop this session. 1 s ticks so a disarm leaves no long sleep.
__idle=
idle_disarm() { [ -z "$__idle" ] || kill "$__idle" 2>/dev/null; __idle=; }
idle_arm() {
    idle_disarm
    ( trap - EXIT HUP INT TERM
      i=0; while [ "$i" -lt "$IDLE_MAX" ]; do sleep 1 >/dev/null 2>&1; i=$((i + 1)); done
      echo "$(date '+%F %T') $MYNAME: no instruction from the container for ${IDLE_MAX}s, releasing the lock of run $RUNID" >> "$HOME/work/.mac-remote-watchdog.log"
      lock_release_if "$TOKEN"; kill -TERM $$; sleep 2; kill -KILL $$ ) </dev/null >/dev/null 2>&1 &
    __idle=$!
}
WD=
# MR_SIMDOWN=1 (set by the container side for gradle/native-tests/xcode-*/run/batch) also covers abnormal ends (watchdog, idle guard)
# A helper can receive TERM before its first trap-reset instruction. Gate the
# owner's EXIT trap itself as well, so that fork/startup window cannot release it.
trap 'if [ "$BASH_SUBSHELL" = 0 ]; then [ "${MR_SIMDOWN:-}" = 1 ] && xcrun simctl shutdown all >/dev/null 2>&1; lock_release_if "$TOKEN"; [ -z "$QTICKET" ] || mac_queue remove "$QTICKET"; rm -rf "$L.pending.$TOKEN"; [ -n "$WD" ] && kill $WD 2>/dev/null; fi' EXIT
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
    ( trap - EXIT HUP INT TERM
      while ps -p "$sshd_pid" >/dev/null 2>&1; do sleep 10; done
      echo "$(date '+%F %T') $MYNAME: ssh connection gone, stopping run $$" >> "$HOME/work/.mac-remote-watchdog.log"
      kill -TERM 0 ) </dev/null >/dev/null 2>&1 &
    WD=$!
fi
QTICKET=$(mac_queue register --pid "$$" --pstart "$(pstart_of $$)" --ticket "$TOKEN") || exit 74
[ "$QTICKET" = "$TOKEN" ] || { echo 'mac-remote: queue registration returned no matching ticket' >&2; exit 74; }
got=; t0=$SECONDS; last=-1000000
while :; do
    # Only the new-client head competes for the unchanged legacy lock. Old
    # clients can still overtake us; FIFO is guaranteed only among new clients.
    QHEAD=$(mac_queue head --ticket "$QTICKET") || exit 74
    if [ "$QHEAD" = "$QTICKET" ]; then
        # admit rechecks and consumes under the same guard as cancel/priority,
        # holding it through legacy rename. The earlier head result is a hint.
        try_acquire; admission_rc=$?
        if [ "$admission_rc" = 0 ]; then QTICKET=; got=1; break; fi
        [ "$admission_rc" = 1 ] || exit 74
        if lock_stale; then break_lock && continue; fi
    fi
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
idle_arm
echo "@@LOCKED-$TOKEN"
EOF
}

cache_install_script() {
    local encoded
    encoded=$(base64 < "$(dirname "${BASH_SOURCE[0]}")/mac-build-cache.init.gradle" | tr -d '\n') || return 1
    cat <<EOF
python3 -I - $(q "$encoded") <<'__MR_INIT__'
import base64, os, sys, tempfile
root = os.path.join(os.environ['HOME'], '.gradle')
d = os.path.join(root, 'init.d')
for p in (root, d):
    if os.path.islink(p): raise SystemExit('mac-remote: symlink Gradle init directory refused')
os.makedirs(d, exist_ok=True)
p = os.path.join(d, 'andbible-mac-build-cache.gradle')
content = base64.b64decode(sys.argv[1])
if os.path.islink(p) or (os.path.exists(p) and open(p, 'rb').read() != content):
    raise SystemExit('mac-remote: unrelated existing cache init refused')
fd, tmp = tempfile.mkstemp(prefix='.andbible-cache-', dir=d)
try:
    with os.fdopen(fd, 'wb') as f: f.write(content)
    os.replace(tmp, p)
finally:
    if os.path.exists(tmp): os.unlink(tmp)
__MR_INIT__
EOF
}

recorded_source_metadata() {
    local record required=${1:-1}
    record=$(remote "$(locked_guard)
cat \$HOME/$(q "$WORKDIR/.mac-owner") 2>/dev/null") || {
        # Older private trees can lack an owner record: never attribute local HEAD.
        [ "$SHARED" = 0 ] || [ "$required" = 0 ] || return 73
        record=
    }
    SOURCE_REVISION=$(sed -n 's/^source_revision=//p' <<<"$record")
    SOURCE_DIRTY=$(sed -n 's/^source_dirty=//p' <<<"$record")
    SOURCE_REVISION=${SOURCE_REVISION:-unknown}; SOURCE_DIRTY=${SOURCE_DIRTY:-unknown}
    [[ "$SOURCE_REVISION" =~ ^[a-zA-Z0-9]+$ && "$SOURCE_DIRTY" =~ ^[a-zA-Z0-9]+$ ]] || return 73
    sed -i "s/^source_revision=.*/source_revision=$SOURCE_REVISION/; s/^source_dirty=.*/source_dirty=$SOURCE_DIRTY/" "$RUNDIR/meta"
}

step_script_sim_crashes() {
    local n=$1
    cat <<EOF
stage=build/.mac-evidence/$TOKEN/crashes/step$n/crashes
mkdir -p "\$stage" || return 74
# A run-specific empty directory cannot contain a previous owner's reports.
if [ -d "\$HOME/Library/Logs/DiagnosticReports" ]; then
    find "\$HOME/Library/Logs/DiagnosticReports" -name $(q "$APP_PROCESS*") -mtime -2 -exec cp {} "\$stage/" \\; || return 74
fi
ls -t "\$stage" | head
EOF
}
step_post_sim_crashes() {
    local n=$1 ev="$RUNDIR/crashes/step$1"
    fetch "build/.mac-evidence/$TOKEN/crashes/step$n/crashes" "$ev" || return $?
    mkdir -p "$OUT"
    [ -L "$OUT/crashes" ] || rm -rf "$OUT/crashes"
    ln -sfn "runs/$RUN_ID/crashes/step$n/crashes" "$OUT/crashes"
    remote "$(locked_guard)
rm -rf \"\$TREE\"/$(q "build/.mac-evidence/$TOKEN/crashes/step$n")"
}

step_script_sync() { echo ':'; }
step_script_fetch() { echo ':'; }
step_post_fetch() { shift 2; fetch "$@"; }

step_script_reset_tree() {
    cat <<'EOF'
python3 -I - "$TREE" <<'__MR_RESET__'
import os, shutil, sys
root = sys.argv[1]
outputs = {'build', '.gradle', '.kotlin', 'node_modules'}
for here, dirs, files in os.walk(root, topdown=True, followlinks=False):
    if '.git' in dirs: dirs.remove('.git')
    for name in list(dirs):
        p = os.path.join(here, name)
        if name in outputs:
            if os.path.islink(p): raise SystemExit('mac-remote: symlink output refused: ' + p)
            shutil.rmtree(p)
            dirs.remove(name)
__MR_RESET__
EOF
}

# Optional absence is not a transport error. Always test and copy under the held lock.
fetch_optional() {
    local src=$1 dest=$2 status
    remote "$(locked_guard)
[ -e \"\$TREE\"/$(q "$src") ] || exit 44" >/dev/null
    status=$?
    [ "$status" != 44 ] || { echo "mac-remote: optional evidence absent: $src" >&2; return 0; }
    [ "$status" = 0 ] || return "$status"
    fetch "$src" "$dest"
}

# Content signatures select only newly created/changed top-level release/shots artifacts.
# The pre/post manifest also detects overwritten archive contents without copying warm builds.
artifact_signature_script() {
    cat <<'__MR_SIGNATURE__'
def signature(p):
    h = hashlib.sha256()
    def add(f):
        if os.path.islink(f): raise RuntimeError('symlink artifact refused: ' + f)
        h.update(os.path.relpath(f, p).encode())
        if os.path.isfile(f):
            with open(f, 'rb') as src:
                for b in iter(lambda: src.read(1024 * 1024), b''): h.update(b)
    if os.path.isdir(p):
        for here, dirs, files in os.walk(p):
            dirs.sort(); files.sort()
            for name in dirs + files: add(os.path.join(here, name))
    else: add(p)
    return h.hexdigest()
__MR_SIGNATURE__
}

artifact_script() {
    local mode=$1 n=$2
    cat <<EOF
python3 -I - "\$TREE" $(q "$TOKEN" "$n" "$mode") <<'__MR_ARTIFACT__'
import hashlib, json, os, shutil, sys
root, token, step, mode = sys.argv[1:]
stage = os.path.join(root, 'build', '.mac-evidence', token, 'run', 'step' + step)
os.makedirs(stage, exist_ok=True)
$(artifact_signature_script)
current = {}
for kind in ('release', 'shots'):
    d = os.path.join(root, 'build', kind)
    if os.path.islink(d): raise RuntimeError('symlink artifact directory refused')
    if os.path.isdir(d):
        for name in sorted(os.listdir(d)):
            current[kind + '/' + name] = signature(os.path.join(d, name))
manifest = os.path.join(stage, 'before.json')
if mode == 'before':
    with open(manifest, 'w') as f: json.dump(current, f, sort_keys=True)
else:
    before = json.load(open(manifest))
    changed = {k: v for k, v in current.items() if before.get(k) != v}
    for rel in changed:
        src, dst = os.path.join(root, 'build', rel), os.path.join(stage, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if os.path.isdir(src): shutil.copytree(src, dst)
        else: shutil.copy2(src, dst)
    with open(os.path.join(stage, 'captured.json'), 'w') as f: json.dump(changed, f, sort_keys=True)
__MR_ARTIFACT__
EOF
}

# Cleanup receipts exist only after the evidence copy succeeds. A later handoff or
# content change revokes deletion authority, even for the same pathname.
scratch_receipt_script() {
    local mode=$1 n=${2:-0}
    cat <<EOF
python3 -I - "\$TREE" $(q "$NAME" "$TOKEN" "$n" "$mode") <<'__MR_RECEIPT__'
import hashlib, json, os, shutil, sys
root, owner, token, step, mode = sys.argv[1:]
$(artifact_signature_script)
receipts = os.path.join(root, 'build', '.mac-receipts')
try:
    fields = dict(line.rstrip().split('=', 1) for line in open(os.path.join(os.path.dirname(root), '.mac-owner')))
except (OSError, ValueError): fields = {}
generation = fields.get('token')
if fields.get('container') != owner or not generation: raise SystemExit(0)
if mode == 'capture':
    stage = os.path.join(root, 'build', '.mac-evidence', token, 'run', 'step' + step)
    changed = json.load(open(os.path.join(stage, 'captured.json')))
    os.makedirs(receipts, exist_ok=True)
    p = os.path.join(receipts, token + '-' + step + '.json')
    with open(p + '.tmp', 'w') as f: json.dump({'owner': owner, 'token': token, 'generation': generation, 'changed': changed}, f)
    os.replace(p + '.tmp', p)
else:
    # Only this completed source generation can grant scratch deletion authority.
    if os.path.isdir(receipts):
        for name in os.listdir(receipts):
            p = os.path.join(receipts, name)
            if not name.endswith('.json') or os.path.islink(p): continue
            receipt = json.load(open(p))
            if receipt.get('owner') != owner or receipt.get('generation') != generation: continue
            for rel, digest in receipt['changed'].items():
                if rel.split('/')[0] not in ('release', 'shots') or '..' in rel.split('/'): raise RuntimeError('invalid cleanup receipt')
                target = os.path.join(root, 'build', rel)
                if os.path.exists(target) and signature(target) == digest:
                    if os.path.isdir(target): shutil.rmtree(target)
                    else: os.unlink(target)
            os.unlink(p)
__MR_RECEIPT__
EOF
}

# A transfer has its own bounded process group instead of the between-instruction
# idle timer. Never let the timer release the lock underneath an active rsync.
transfer_begin() {
    session_send "idle_disarm; echo \"@@STEP-RC-$TOKEN lease\"" && session_read && [ "$SENT" = lease ]
}
transfer_end() { session_send 'idle_arm'; }

step_post_run() {
    local n=$1 rc=$2 ev="$RUNDIR/run/step$1"
    remote "$(locked_guard)
$(artifact_script after "$n")" || return $?
    fetch "build/.mac-evidence/$TOKEN/run/step$n/" "$ev" || return $?
    remote "$(locked_guard)
$(scratch_receipt_script capture "$n")" || return $?
    remote "$(locked_guard)
rm -rf \"\$TREE/build/.mac-evidence/$(q "$TOKEN/run/step$n")\""
}

source_metadata() {
    SOURCE_REVISION=$(git -C "$repo" rev-parse HEAD 2>/dev/null || printf unknown)
    SOURCE_DIRTY=$(python3 -I - "$repo" <<'__MR_SOURCE__'
import hashlib, os, subprocess, sys
root = sys.argv[1]
def git(path, *args):
    return subprocess.check_output(['git', '-C', path, *args], stderr=subprocess.DEVNULL)
def fingerprint(path):
    h = hashlib.sha256()
    try:
        names = git(path, 'ls-files', '-z', '--modified', '--others', '--exclude-standard').split(b'\0')
        names += git(path, 'diff', '--cached', '--name-only', '-z', '--diff-filter=ACDMRTUXB').split(b'\0')
        # Inspect every gitlink, including an unchanged module whose worktree is
        # dirty. Its own index/HEAD comparison must be performed recursively.
        modules = {}
        for entry in git(path, 'ls-files', '--stage', '-z').split(b'\0'):
            if not entry: continue
            info, name = entry.split(b'\t', 1)
            mode, oid, stage = info.split()
            if mode == b'160000': modules[name] = oid
        names += list(modules)
    except subprocess.CalledProcessError:
        return h.hexdigest()
    for raw in sorted(set(names)):
        if not raw: continue
        h.update(raw + b'\0')
        p = os.path.join(path, os.fsdecode(raw))
        if raw in modules:
            h.update(b'<submodule>' + modules[raw] + b'\0')
            # .git is a file for standard submodules, a dir for embedded fixtures.
            if os.path.exists(os.path.join(p, '.git')):
                h.update(git(p, 'rev-parse', 'HEAD').strip() + b'\0')
                h.update(fingerprint(p).encode() + b'\0')
            else: h.update(b'<uninitialized-or-deleted>')
        elif os.path.islink(p): h.update(os.fsencode(os.readlink(p)))
        elif os.path.isfile(p):
            with open(p, 'rb') as f:
                for data in iter(lambda: f.read(1024 * 1024), b''): h.update(data)
        else: h.update(b'<deleted>')
    return h.hexdigest()
print(fingerprint(root))
__MR_SOURCE__
    ) || die 'cannot fingerprint source'
}

sync_tree() {
    remote "$(locked_guard)
rm -f \$HOME/$(q "$WORKDIR/.mac-owner")
mkdir -p \"\$TREE\"" || die "unsafe remote tree or lost lock"
    command -v "$RSYNC_BIN" >/dev/null || die "rsync is not installed in this container (old golden image?)"
    local t0=$SECONDS stats
    # Version.xcconfig is gitignored but generated locally, so it travels with the tree (rsync here
    # does not use .gitignore filters); xcodegen on the Mac then reads it through Base.xcconfig.
    VERSION_XCCONFIG="$repo/iosApp/Config/Version.xcconfig" "$(dirname "${BASH_SOURCE[0]}")/ios-version-xcconfig.sh" \
        || die "cannot generate iosApp/Config/Version.xcconfig"
    # Not synced: .git (the container's objects live in an alternate on the host), container-only data,
    # build outputs (kept on the Mac: excluded paths survive --delete), and Linux-native node_modules.
    stats=$(timeout --kill-after=10 "${MAC_TRANSFER_TIMEOUT:-1800}" "${RSYNC[@]}" --delete --stats \
        --exclude='.git' --exclude='/.local/' --exclude='/docs/' --exclude='/website/' \
        --exclude='/fastlane/' --exclude='/play/' --exclude='/app/src/test/roborazzi/' \
        --exclude='/local.properties' --exclude='build/' --exclude='.gradle/' --exclude='.kotlin/' \
        --exclude='node_modules/' --exclude='/app/bibleview-js/dist/' \
        --exclude='/iosApp/iosApp.xcodeproj/' --exclude='*.xcresult' \
        "$repo/" "$HOST:$TREE/") || die "rsync to $HOST failed"
    source_metadata
    sed -i "s/^source_revision=.*/source_revision=$SOURCE_REVISION/; s/^source_dirty=.*/source_dirty=$SOURCE_DIRTY/" "$RUNDIR/meta"
    grep -E '^(Number of regular files transferred|Total transferred file size)' <<<"$stats" || true
    # :app's Gradle configuration runs `git rev-parse` / `git describe`: give it a one-commit repo.
    # It also needs an Android SDK location; iOS tasks never use the SDK, so an empty stub directory does.
    remote "$(locked_guard)
$(prologue)
set -e
[ -d .git ] || { git init -q && git -c user.name=mac-remote -c user.email=mac-remote@localhost commit -q --allow-empty -m 'mac-remote tree'; }
# Never carry client-local properties across a handoff.
mkdir -p \$HOME/android-sdk-stub
printf 'sdk.dir=%s\\n' \"\${ANDROID_HOME:-\$HOME/android-sdk-stub}\" > local.properties.tmp
mv local.properties.tmp local.properties
$(cache_install_script)
printf 'container=%s\\nsource_revision=%s\\nsource_dirty=%s\\ntoken=%s\\n' $(q "$NAME" "$SOURCE_REVISION" "$SOURCE_DIRTY" "$TOKEN") > \$HOME/$(q "$WORKDIR/.mac-owner.tmp.$TOKEN")
mv \$HOME/$(q "$WORKDIR/.mac-owner.tmp.$TOKEN") \$HOME/$(q "$WORKDIR/.mac-owner")" || die "remote setup failed"
    echo "mac-remote: synced to $HOST:~/$TREE in $((SECONDS - t0)) s"
}

fetch() {
    local src="$1" dest="${2:-$OUT}"
    [[ "$src" =~ ^[a-zA-Z0-9_./-]+$ ]] && [[ "$src" != /* && "$src" != *..* ]] || { echo 'mac-remote: unsafe fetch path' >&2; return 73; }
    remote "$(locked_guard)" || return $?
    mkdir -p "$dest"
    timeout --kill-after=10 "${MAC_TRANSFER_TIMEOUT:-1800}" "${RSYNC[@]}" "$HOST:$TREE/$src" "$dest/" && echo "mac-remote: fetched $src -> ${dest#"$repo"/}"
}

# ---------------------------------------------------------------------------------------------
# Run markers (container side): $OUT/runs/<run-id>/{meta,output.log,steps,exit}
#  meta    key=value: id, container, cmd, pid, pstart (local process start time), start_epoch, token, steps
#  steps   tab separated: <n> start <epoch> <step>   /   <n> end <epoch> <exit code>
#  exit    key=value, written once at the end (also on timeout, failure and SIGTERM/INT/HUP):
#          code, reason, end_epoch, failed_step.  No exit file and a dead pid = the run was killed hard.
lpstart() { LC_ALL=C TZ=UTC0 ps -p "$1" -o lstart= 2>/dev/null | tr -s ' ' | sed 's/^ //;s/ $//'; }
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
        echo "log=$RUNDIR/output.log"; echo "tree=$TREE"; echo "source_revision=$SOURCE_REVISION"; echo "source_dirty=$SOURCE_DIRTY"; echo "requested_source_revision=$SOURCE_REVISION"; echo "requested_source_dirty=$SOURCE_DIRTY"
    } > "$RUNDIR/meta"
    : > "$RUNDIR/steps"
    # prune old run dirs (the newest KEEP_RUNS stay; a running run is always among them)
    ls -1d "$OUT"/runs/*/ 2>/dev/null | sort | head -n "-$KEEP_RUNS" | while read -r old; do
        old=${old%/}
        [ -f "$old/exit" ] || ! run_alive "$old" || continue   # never a live run's directory
        rm -rf "$old"
    done
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
# per step; the step's exit code comes back as a "@@STEP-RC-<token> n" line. The remote bash reads its script from
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
        # sentinels carry the run token: step output cannot forge them
        *"@@LOCKED-$TOKEN") pre=${line%@@LOCKED-"$TOKEN"}; [ -z "$pre" ] || printf '%s\n' "$pre"; SENT=locked; return 0 ;;
        *"@@STEP-RC-$TOKEN "*) pre=${line%%@@STEP-RC-"$TOKEN" *}; [ -z "$pre" ] || printf '%s' "$pre"; SENT=${line##*@@STEP-RC-"$TOKEN" }; return 0 ;;
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
# One step = one script file on the Mac, run by tmo in its own process group with a step-level timeout
# (step timeout + STEP_MARGIN): everything the step starts, also outside the inner tmo calls (npm, xcodegen,
# simulator boot), is killed together. A copy of the script is kept in $RUNDIR/step-<n>.sh.
session_step() {   # n script  -> sets STEP_RC
    local n=$1 t f eofm="__MR_STEP_END_$TOKEN" body
    t=$(( $(step_timeout "${STEP[0]}") + STEP_MARGIN ))
    f="\$HOME/work/.step-$TOKEN-$n.sh"
    body="$(prologue)
__body() {
$2
}
__body
exit \$?"
    printf '%s\n' "$body" > "$RUNDIR/step-$n.sh"
    session_send "idle_disarm
cat > \"$f\" <<'$eofm'
$body
$eofm
( trap - EXIT HUP INT TERM; __i=0; while :; do sleep 1 >/dev/null 2>&1; __i=\$((__i + 1)); [ \$((__i % $HEARTBEAT)) = 0 ] || continue; echo \"mac-remote: heartbeat (step $n): newest build log: \$(f=\$(ls -t build/*.log 2>/dev/null | sed -n 1p); [ -n \"\$f\" ] && echo \"\$f idle \$(( \$(date +%s) - \$(mtime \"\$f\") )) s\" || echo none)\"; done ) </dev/null &
__hb=\$!
tmo $t bash \"$f\" </dev/null; __rc=\$?
kill \$__hb 2>/dev/null; rm -f \"$f\"
[ \$__rc != 142 ] || echo 'mac-remote: step $n stopped by the step timeout' >&2
idle_arm
echo \"@@STEP-RC-$TOKEN \$__rc\"" || true
    if session_read; then STEP_RC=$SENT; else
        STEP_RC=0; wait "$RS_PID_" 2>/dev/null || STEP_RC=$?; RS_PID_=
        [ "$STEP_RC" != 0 ] || STEP_RC=255
        echo "mac-remote: remote session ended without a result for step $n (exit $STEP_RC)" >&2
    fi
}
session_close() {
    [ -n "$RS_PID_" ] || return 0
    session_send "idle_disarm; exit 0" || true
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
        case "$a" in -g|-g?*|--gradle-user-home*|-Dgradle.user.home*|--no-build-cache) die "unsupported Gradle cache/home override: $a" ;; esac
        case "$a" in *iosSimulatorArm64Test*)
            echo "mac-remote: WARNING: '$a' through the plain gradle step runs without a booted simulator (IOS_TEST_SIMULATOR unset): sharedCore/sharedUi tests crash with SIGTRAP. Use: mac-remote.sh native-tests [--force] <modules>" >&2 ;;
        esac
    done
}
step_script_gradle() {
    local n=$1 t; shift; t=$(step_timeout gradle)
    cat <<EOF
tmo $t ./gradlew -Dorg.gradle.native=false $(q "$@"); rc=\$?
[ \$rc = 142 ] && { echo 'mac-remote: TIMEOUT after ${t}s, stopping the owned step group' >&2; }
return \$rc
EOF
}

native_parse() {   # [--force] [mod...] -> NATIVE_FORCE, NATIVE_MODS
    NATIVE_FORCE=; NATIVE_MODS=()
    while [ $# -gt 0 ]; do
        case "$1" in --force) NATIVE_FORCE=1 ;; jsword|kdom|sharedCore|sharedData|sharedLogic|sharedUi) NATIVE_MODS+=("$1") ;; *) die "unsupported Native module: $1" ;; esac
        shift
    done
    [ ${#NATIVE_MODS[@]} -gt 0 ] || NATIVE_MODS=(jsword kdom sharedCore sharedData sharedLogic sharedUi)
}
step_script_native_tests() {
    local n=$1 t; shift; t=$(step_timeout native-tests)
    native_parse "$@"
    cat <<EOF
STAGE="\$TREE/build/.mac-evidence/$TOKEN/native/step$n"
mkdir -p "\$STAGE"
for m in $(q "${NATIVE_MODS[@]}"); do mkdir -p "\$STAGE/\$m"; echo unattempted > "\$STAGE/\$m/status"; done
IOS_TEST_SIMULATOR=\$(sim_boot $(q "$SIM_IPHONE")) || return 1
export IOS_TEST_SIMULATOR
fail=0
for m in $(q "${NATIVE_MODS[@]}"); do
    $([ -z "$NATIVE_FORCE" ] || echo 'rm -rf "$m/build/test-results/iosSimulatorArm64Test" "$m/build/reports/tests/iosSimulatorArm64Test"')
    echo attempted > "\$STAGE/\$m/status"
    echo "=== :\$m:iosSimulatorArm64Test"
    # A failed invocation must not re-present an unchanged previous result as fresh.
    before=\$(find "\$m/build/test-results/iosSimulatorArm64Test" -type f -exec shasum {} \\; 2>/dev/null | shasum)
    tmo $t ./gradlew -Dorg.gradle.native=false :\$m:iosSimulatorArm64Test $([ -z "$NATIVE_FORCE" ] || echo --rerun) --continue; r=\$?
    after=\$(find "\$m/build/test-results/iosSimulatorArm64Test" -type f -exec shasum {} \\; 2>/dev/null | shasum)
    printf 'exit=%s\\n' "\$r" > "\$STAGE/\$m/status"
    if [ "\$r" = 0 ] || [ "\$before" != "\$after" ]; then
        for d in test-results/iosSimulatorArm64Test reports/tests/iosSimulatorArm64Test; do
            if [ -e "\$m/build/\$d" ]; then
                mkdir -p "\$STAGE/\$m/\$(dirname "\$d")"
                cp -R "\$m/build/\$d" "\$STAGE/\$m/\$(dirname "\$d")/" || return 74
            fi
        done
    fi
    [ \$r != 142 ] || echo "mac-remote: TIMEOUT in \$m after ${t}s" >&2
    [ \$r = 0 ] || { [ \$fail != 0 ] || fail=\$r; }
done
return \$fail
EOF
}
step_post_native_tests() {
    local n=$1 rc=$2 m failed=0 ev="$RUNDIR/native-tests/step$1"; shift 2
    native_parse "$@"
    mkdir -p "$ev" "$OUT/native-tests"
    for m in "${NATIVE_MODS[@]}"; do
        if fetch_optional "build/.mac-evidence/$TOKEN/native/step$n/$m" "$ev"; then
            mkdir -p "$ev/$m"
            [ -f "$ev/$m/status" ] || echo unavailable > "$ev/$m/status"
            [ -L "$OUT/native-tests/$m" ] || rm -rf "$OUT/native-tests/$m"
            ln -sfn "../runs/$RUN_ID/native-tests/step$n/$m" "$OUT/native-tests/$m"
        else
            failed=74
        fi
    done
    [ "$failed" = 0 ] || return "$failed"
    remote "$(locked_guard)
rm -rf \"\$TREE/build/.mac-evidence/$(q "$TOKEN/native/step$n")\"" || return $?
    echo "mac-remote: native-tests exit $rc; evidence under ${ev#"$repo"/}"
}

xcode_parse() {   # [iphone|ipad] [--release] [xcodebuild args...] -> XC_WHICH, XC_SIM, XC_ARGS, XC_CONFIG, XC_APP_ID, XC_APP
    XC_WHICH="${1:-iphone}"; [ $# -gt 0 ] && shift
    case "$XC_WHICH" in iphone) XC_SIM="$SIM_IPHONE" ;; ipad) XC_SIM="$SIM_IPAD" ;; *) echo "mac-remote: device must be iphone or ipad" >&2; return 1 ;; esac
    XC_CONFIG=Debug XC_APP_ID=$APP_ID XC_APP=$APP_PROCESS
    if [ "${1:-}" = --release ]; then
        shift; XC_CONFIG=Release XC_APP_ID=org.andbible.ios XC_APP=AndBible
    fi
    XC_ARGS=("$@")
}
step_script_xcode() {   # <kind> <n> [dev] [args...]
    local kind=$1 n=$2 t; shift 2; t=$(step_timeout "$kind")
    xcode_parse "$@" || return 1
    local which=$XC_WHICH
    cat <<EOF
mkdir -p build && rm -rf build/ios-$which.xcresult build/screenshots-$which build/app-$which.log build/xcodebuild-$which.log build/bibleview-$which.log
# bibleview-js: npm ci only when package-lock.json changed (macOS-native node_modules live on the Mac)
( cd app/bibleview-js
  stamp=node_modules/.mac-remote-lock-sha; sha=\$(shasum package-lock.json | cut -d' ' -f1)
  [ "\$(cat \$stamp 2>/dev/null)" = "\$sha" ] || { npm ci --no-audit --no-fund && echo \$sha > \$stamp; } &&
  npm run build-production ) > build/bibleview-$which.log 2>&1 || { tail -40 build/bibleview-$which.log; return 1; }
( cd iosApp && xcodegen generate --quiet ) || return 1
UDID=\$(sim_boot $(q "$XC_SIM")) || return 1
echo "mac-remote: simulator \$UDID"
$([ $XC_CONFIG = Release ] && [ -z "${MAC_RELEASE_KEEP_INSTALL:-}" ] && echo "xcrun simctl uninstall \$UDID $XC_APP_ID 2>/dev/null; echo 'mac-remote: --release: uninstalled $XC_APP_ID (fresh install)'")
if [ $kind = xcode-test ]; then
    # The app runs as its own process under XCUITest: its NSLog output only reaches the unified log.
    xcrun simctl spawn \$UDID log stream --style compact --predicate 'process == "$XC_APP"' > build/app-$which.log 2>&1 &
    LOGPID=\$!; sleep 2
    $(for v in $(compgen -v TEST_RUNNER_ || true); do printf 'export %s=%q\n' "$v" "${!v}"; done)
    tmo $t xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \\
        -destination "platform=iOS Simulator,id=\$UDID" -resultBundlePath build/ios-$which.xcresult -configuration $XC_CONFIG $(q "${XC_ARGS[@]}") \\
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
        -destination "platform=iOS Simulator,id=\$UDID" -derivedDataPath build/dd-$which -configuration $XC_CONFIG \\
        > build/xcodebuild-$which.log 2>&1; rc=\$?
    [ \$rc = 0 ] || { [ \$rc != 142 ] || echo "mac-remote: TIMEOUT: xcodebuild killed after ${t}s" >&2; grep -E 'error:|BUILD FAILED' build/xcodebuild-$which.log | tail -40; [ \$rc = 142 ] && return 142; return 1; }
    APP=\$(ls -d build/dd-$which/Build/Products/$XC_CONFIG-iphonesimulator/$XC_APP.app | sed -n 1p)
    xcrun simctl install \$UDID "\$APP" && xcrun simctl terminate \$UDID $XC_APP_ID 2>/dev/null
    xcrun simctl launch \$UDID $XC_APP_ID && sleep 5   # let the first render settle before a sim shot
fi
EOF
}
step_script_xcode_test() { step_script_xcode xcode-test "$@"; }
step_script_xcode_build() { step_script_xcode xcode-build "$@"; }

# Evidence goes to a per-run directory, $RUNDIR/xcode/step<n>-<device>/, so a later run never overwrites it;
# the old paths .local/mac/<container>/xcode/<name>-<device> become links to the latest run.
step_post_xcode() {   # <kind> <n> <rc> [dev] [args...]
    local kind=$1 n=$2 rc=$3; shift 3
    xcode_parse "$@" || return 0
    local which=$XC_WHICH ev="$RUNDIR/xcode/step$n-$XC_WHICH" f failed=0
    mkdir -p "$ev" "$OUT/xcode"
    for f in "build/xcodebuild-$which.log" "build/app-$which.log" "build/screenshots-$which" "build/ios-$which.xcresult"; do
        fetch_optional "$f" "$ev" >/dev/null || failed=74
    done
    # xcresulttool names attachments by UUID; manifest.json maps them to the names the tests gave
    # (e.g. split2-dark_0_<uuid>.png): copy each PNG to <test>/<name>.png next to them.
    python3 -I - "$ev/screenshots-$which" <<'EOF' || true
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
    return "$failed"
}
step_post_xcode_test() { step_post_xcode xcode-test "$@"; }
step_post_xcode_build() { step_post_xcode xcode-build "$@"; }

step_script_run() {
    local n=$1 t; shift; t=$(step_timeout run)
    cat <<EOF
export TREE
$(artifact_script before "$n")
[ \$? = 0 ] || return 74
tmo $t bash -c $(q "$*"); rc=\$?
[ \$rc = 142 ] && echo "mac-remote: TIMEOUT: command killed after ${t}s" >&2
return \$rc
EOF
}

step_script_sim_boot() { local n=$1; shift; echo "sim_boot $(q "$1")"; }
step_script_sim_shot() { local n=$1; shift; echo "mkdir -p build/shots && xcrun simctl io booted screenshot $(q "build/shots/$1.png") >/dev/null 2>&1"; }
step_post_sim_shot() { local n=$1 rc=$2; shift 2; fetch "build/shots/$1.png" "$OUT/shots"; }
step_script_sim_launch() { local n=$1; shift; echo "xcrun simctl terminate booted $(q "$1") 2>/dev/null; xcrun simctl launch booted $(q "$1")"; }
step_script_sim_shutdown() { sims_down_script; }
# `cleanup`: shut down simulators, kill this container's leftover processes, report other containers' ones.
step_script_cleanup() {
    cat <<EOF
$(mem_line_script)
$(leftovers_script)
$(disk_script)
$(own_junk_script)
ME=$(q "$NAME")
MR_SHARED=$SHARED
$([ "$SHARED" = 0 ] || scratch_receipt_script cleanup)
mem_line before
disk_report "\$ME" quiet
$(sims_down_script)
mine=\$(leftovers "\$ME" | grep '^mine ')
if [ -n "\$mine" ]; then
    echo "mac-remote: cleanup: killing leftover processes of this container (\$ME):"
    echo "\$mine" | sed 's/^mine /  /'
    for p in \$(echo "\$mine" | awk '{print \$2}'); do kill -TERM "\$p" 2>/dev/null; done
    sleep 3
    for p in \$(echo "\$mine" | awk '{print \$2}'); do kill -0 "\$p" 2>/dev/null && { echo "  pid \$p still alive, KILL"; kill -KILL "\$p" 2>/dev/null; }; done
else
    echo "mac-remote: cleanup: no leftover processes of this container (\$ME)"
fi
other=\$(leftovers "\$ME" | grep '^other ')
if [ -n "\$other" ]; then
    echo "mac-remote: cleanup: leftovers of OTHER containers' trees (reported, not killed; pid age rss-MiB cpu% command):"
    echo "\$other" | sed 's/^other /  /'
fi
own_junk "\$ME" ${CLEANUP_DD:-0} || return \$?
mem_line after
disk_report "\$ME"
return 0
EOF
}
# `prune`: other containers' trees and unneeded simulators; dry run unless --yes.
step_script_prune() {
    local n=$1; shift
    cat <<EOF
$(leftovers_script)
$(disk_script)
$(prune_script)
PRUNE_ME=$(q "$NAME")
PRUNE_SELECTED=$(q "${WORKDIR#work/}")
PRUNE_KEEP_SIMS=$(q "$SIM_IPHONE|$SIM_IPAD")
prune $(q "$@")
return \$?
EOF
}

# Run one or more steps under one lock: label, sync (1/0), then one quoted step per argument.
run_steps() {
    local label=$1 sync=$2; shift 2
    local total=$# n=0 step kind fn rc=0 s e failed=0
    local steps=("$@")
    for step in "${steps[@]}"; do
        eval "STEP=($step)"
        [ "${STEP[0]}" != link ] || continue
        kind=${STEP[0]//-/_}
        if [ "$kind" = gradle ]; then step_prep_gradle "${STEP[@]:1}"; fi
        if [ "$kind" = native_tests ]; then ( native_parse "${STEP[@]:1}" ) || exit 1; fi
        case "${STEP[0]}" in xcode-test|xcode-build) ( xcode_parse "${STEP[@]:1}" ) || exit 1 ;; esac
        declare -F "step_script_$kind" >/dev/null || die "unknown step '${STEP[0]}' (steps: link gradle native-tests xcode-test xcode-build run)"
    done
    case "$label" in cleanup|prune|sim-*) PROLOGUE_NOTREE=1 ;; esac
    queue_helper_check
    [ -s "$(dirname "${BASH_SOURCE[0]}")/mac-build-cache.init.gradle" ] || die "missing cache init component"
    source_metadata
    run_begin "$label" "$total" "${steps[@]}"
    RUN_REASON="no lock"
    session_open || exit $?
    RUN_REASON=
    remote "$(locked_guard)" || die "unsafe remote tree or lost lock"
    if [ "$sync" = 1 ]; then
        RUN_REASON="sync failed"
        transfer_begin || die "lost transfer lease"
        sync_tree
        transfer_end || die "lost transfer lease"
        RUN_REASON=
    else
        case "$label" in
            run|batch|fetch|sim-shot|sim-crashes)
                remote "$(owner_guard)" || die "shared ownership check failed"
                recorded_source_metadata 1 || die "cannot attribute selected remote source" ;;
            *) recorded_source_metadata 0 || die "cannot read selected remote provenance" ;;
        esac
        case "$label" in run|batch)
            remote "$(locked_guard)
$(prologue)
set -e
$(cache_install_script)" || die "remote cache setup failed" ;;
        esac
    fi
    session_send "$(prologue)" || true
    # shut the simulators down when the session ends (unless kept; sim helpers and cleanup manage them themselves)
    auto_down=1
    case "$label" in sim-*|cleanup|sync|fetch|reset-tree|prune) auto_down=0 ;; esac
    [ "${MAC_KEEP_SIM:-0}" != 1 ] || auto_down=0
    [ "$auto_down" != 1 ] || session_send "MR_SIMDOWN=1" || true
    for step in "${steps[@]}"; do
        n=$((n + 1))
        eval "STEP=($step)"
        builds=0; case "${STEP[0]}" in link|gradle) builds=1 ;; esac
        [ "${STEP[0]}" != link ] || STEP=(gradle :sharedUi:linkDebugFrameworkIosSimulatorArm64)
        kind=${STEP[0]//-/_}
        printf '%s\tstart\t%s\t%s\n' "$n" "$(date +%s)" "${STEP[*]}" >> "$RUNDIR/steps"
        ! declare -F "step_prep_$kind" >/dev/null || "step_prep_$kind" "${STEP[@]:1}"
        session_step "$n" "$("step_script_$kind" "$n" "${STEP[@]:1}")"
        local post_rc=0
        if declare -F "step_post_$kind" >/dev/null; then
            if transfer_begin; then
                "step_post_$kind" "$n" "$STEP_RC" "${STEP[@]:1}" || post_rc=$?
                transfer_end || { [ "$post_rc" != 0 ] || post_rc=73; }
            else post_rc=73; fi
        fi
        if [ "$post_rc" != 0 ]; then
            echo "mac-remote: evidence capture failed for step $n (exit $post_rc)" >&2
            [ "$STEP_RC" != 0 ] || STEP_RC=$post_rc
        fi
        printf '%s\tend\t%s\t%s\n' "$n" "$(date +%s)" "$STEP_RC" >> "$RUNDIR/steps"
        if [ "$STEP_RC" != 0 ]; then
            failed=$((failed + 1))
            [ "$rc" != 0 ] || { rc=$STEP_RC; RUN_FAILED_STEP=$n; }
            # a lost remote session cannot run the next step, and a failed build step would make the later test steps
            # run (and pass) against the previous build
            [ "${KEEP_GOING:-0}" = 1 ] && [ -n "$RS_PID_" ] && [ "$builds" = 0 ] || break
        fi
    done
    [ "$auto_down" != 1 ] || [ -z "$RS_PID_" ] || session_send "$(sims_down_script)
MR_SIMDOWN=" || true
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
        [ "${KEEP_GOING:-0}" != 1 ] || echo "mac-remote: $failed of $total steps failed"
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
    queue_helper_check
    local want="${1:-}" d id tokens="" found=0 rc=0
    local cancel_dirs=()
    for d in $(ls -1d "$OUT"/runs/*/ 2>/dev/null | sort); do
        d=${d%/}; id=$(basename "$d")
        [ -z "$want" ] || [ "$id" = "$want" ] || continue
        [ ! -f "$d/exit" ] || [ -f "$d/cancel-pending" ] || continue
        found=1
        # Write before stopping the local process: its EXIT trap may write an
        # exit marker, but remote cleanup must stay retryable until confirmed.
        : > "$d/cancel-pending"
        cancel_dirs+=("$d")
        if run_alive "$d"; then
            echo "mac-remote: cancelling $id (local pid $(meta_get "$d" pid))"
            kill_run "$(meta_get "$d" pid)"
        else
            echo "mac-remote: $id has no exit marker and its process is gone (lost run)"
        fi
        tokens="$tokens $(meta_get "$d" token)"
    done
    [ "$found" = 1 ] || echo "mac-remote: no running or lost run of $NAME recorded${want:+ with id $want}"
    [ -n "$tokens" ] || { echo "mac-remote: nothing to release on the Mac; if 'check' still shows a lock of this container, it is from an older script version: use 'unlock' after checking"; return 0; }
    {
        printf 'TOKENS=%q\n' "$tokens"
        rlib
        queue_lib
        cat <<'EOF'
queue_rc=0
for TOK in $TOKENS; do
    mac_queue remove "$TOK" || queue_rc=74
done
for TOK in $TOKENS; do
    [ -d "$L" ] || { echo "mac-remote: remote lock already free"; break; }
    if [ "$(owner_get token)" = "$TOK" ]; then
        pid=$(owner_get pid)
        if [ -n "$pid" ] && [ "$(pstart_of "$pid")" = "$(owner_get pstart)" ]; then
            # the step's tmo (a direct child) stops its whole process group on TERM; then the session itself
            pkill -TERM -P "$pid" 2>/dev/null; kill -TERM "$pid" 2>/dev/null
            i=0; while [ "$i" -lt 50 ] && [ "$(pstart_of "$pid")" = "$(owner_get pstart)" ]; do sleep 0.2; i=$((i + 1)); done
            if [ "$(pstart_of "$pid")" = "$(owner_get pstart)" ]; then pkill -KILL -P "$pid" 2>/dev/null; kill -KILL "$pid" 2>/dev/null; fi
        fi
        lock_release_if "$TOK" && echo "mac-remote: released the remote lock (run $(echo "$TOK" | cut -c1-8)...)"
        break
    fi
done
[ -d "$L" ] && echo "mac-remote: remote lock left alone, held by: $(sed -n 1p "$L/owner" 2>/dev/null) (token is not one of the cancelled runs)"
if [ "$queue_rc" != 0 ]; then
    echo 'mac-remote: queue cleanup failed; matching holder cleanup was attempted independently. Repair queue metadata, then retry cancel.' >&2
fi
exit "$queue_rc"
EOF
    } | "${SSH[@]}" "$HOST" 'bash -s' || rc=$?
    if [ "$rc" = 0 ]; then
        for d in "${cancel_dirs[@]}"; do
            if [ ! -f "$d/exit" ]; then
                printf 'code=143\nreason=cancelled\nend_epoch=%s\nfailed_step=\n' "$(date +%s)" > "$d/exit"
            fi
            rm -f "$d/cancel-pending"
        done
    else
        echo 'mac-remote: cancellation cleanup incomplete; cancel-pending retained for retry with cancel' >&2
    fi
    return "$rc"
}

cmd="${1:-}"; shift || true
if [ "${1:-}" = --keep-sim ]; then MAC_KEEP_SIM=1; shift; fi
case "$cmd" in
queue)
    queue_helper_check
    # Read-only listing is available to every group; controls are checked by the helper.
    [ $# -gt 0 ] || set -- list
    case "$1" in list|priority|reserve|release) ;; *) die "usage: queue [priority <ticket> <integer>|reserve <seconds>|release <ticket>]" ;; esac
    remote "$(queue_lib)
mac_queue $(q "$@")"
    ;;
reset-tree)
    PROLOGUE_NOTREE=1
    run_steps reset-tree 0 "$(q reset-tree)"
    ;;
cleanup)
    CLEANUP_DD=0; [ "${1:-}" != --derived-data ] || { CLEANUP_DD=1; shift; }
    run_steps cleanup 0 "$(q cleanup)"
    ;;
prune)
    # dry run (no --yes) needs no lock: it only lists; deleting runs as a locked step
    case " $* " in
    *" --yes "*) run_steps prune 0 "$(q prune "$@")" ;;
    *) PROLOGUE_NOTREE=1
       remote "$(prologue)
__body() {
$(step_script_prune 0 "$@")
}
__body" ;;
    esac
    ;;
check)
    list_runs
    echo
    {
        printf 'TREE=$HOME/%s\nMYNAME=%q\n' "$(q "$TREE")" "$NAME"
        rlib
        leftovers_script
        disk_script
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
disk_report "$MYNAME"
echo "leftovers (processes in ~/work trees; pid age rss-MiB cpu% command [tree]; 'mac-remote.sh cleanup' kills this container's):"
lo=$(leftovers "$MYNAME"); [ -n "$lo" ] && echo "$lo" | sed 's/^/  /' || echo "  none"
[ -f ~/work/.mac-remote-watchdog.log ] && { echo "watchdog (last 3):"; tail -3 ~/work/.mac-remote-watchdog.log; }
echo "trees: $(ls ~/work 2>/dev/null | tr '\n' ' ')"
EOF
    } | "${SSH[@]}" "$HOST" 'bash -s'
    ;;
cancel)
    cmd_cancel "${1:-}"
    ;;
sync)
    run_steps sync 1 "$(q sync)"
    ;;
gradle)
    [ $# -gt 0 ] || die "usage: gradle <tasks/args...>"
    run_steps gradle 1 "$(q gradle "$@")"
    ;;
native-tests)
    run_steps native-tests 1 "$(q native-tests "$@")"
    ;;
xcode-test|xcode-build)
    xcode_parse "$@" || exit 1   # validates the device before taking the lock
    run_steps "$cmd" 1 "$(q "$cmd" "$@")"
    ;;
run)
    sync=1; [ "${1:-}" != --no-sync ] || { sync=0; shift; }
    [ $# -gt 0 ] || die "usage: run [--no-sync] <shell command>"
    run_steps run "$sync" "$(q run "$@")"
    ;;
batch)
    sync=1; KEEP_GOING=0
    while :; do case "${1:-}" in --no-sync) sync=0; shift ;; --keep-going) KEEP_GOING=1; shift ;; *) break ;; esac; done
    [ $# -gt 0 ] || die "usage: batch [--no-sync] [--keep-going] <step>...   e.g. batch link native-tests \"xcode-test iphone\""
    # a batch step is plain words (split on whitespace, no quoting); the steps travel shell-quoted
    batch_steps=(); for step in "$@"; do read -ra w <<<"$step"; batch_steps+=("$(q "${w[@]}")"); done
    run_steps batch "$sync" "${batch_steps[@]}"
    ;;
fetch)
    [ $# -ge 1 ] || die "usage: fetch <remote-rel-path> [local-dir]"
    run_steps fetch 0 "$(q fetch "$@")"
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
    crashes) run_steps sim-crashes 0 "$(q sim-crashes)" ;;
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
