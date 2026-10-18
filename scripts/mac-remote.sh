#!/usr/bin/env bash
# Run iOS build/test steps on the remote build Mac from a jailbee container
# (setup: docs/ios-remote-mac.md; agent guide: .claude/skills/ios-mac/SKILL.md).
# The working tree is rsynced to ~/work/<container>/and-bible on the Mac (uncommitted changes
# included, no .git); results land in .local/mac/<container>/ here.
# Usage:
#
#   scripts/mac-remote.sh check                 connection, toolchain versions, simulators, build lock
#   scripts/mac-remote.sh sync                  rsync the working tree to the Mac (incremental)
#   scripts/mac-remote.sh gradle <args...>      sync, then ./gradlew -Dorg.gradle.native=false <args> on the Mac
#   scripts/mac-remote.sh native-tests [mod...] sync, boot the iPhone simulator, run <mod>:iosSimulatorArm64Test
#                                               (default: the CI jsword-ios-tests set: jsword kdom sharedCore
#                                               sharedData sharedLogic), fetch the test reports/XML
#   scripts/mac-remote.sh xcode-test [iphone|ipad] [xcodebuild args...]
#                                               the CI `ios` job: bibleview-js build, xcodegen, xcodebuild test
#                                               with the app log streamed, attachments (screenshots) exported,
#                                               everything fetched; extra args e.g. -only-testing:iosAppUITests/PocUITests
#   scripts/mac-remote.sh xcode-build [iphone|ipad]
#                                               build the app for the simulator, install it, launch it
#   scripts/mac-remote.sh run <shell command>   run a command in the remote tree (under the build lock)
#   (gradle, native-tests, xcode-*, run and the sim subcommands that drive the shared simulator — boot,
#   shot, launch, shutdown — all take the one Mac-wide build lock; sync, fetch, check, sim list/log/
#   sample/crashes do not)
#   scripts/mac-remote.sh fetch <remote-rel-path> [local-dir]
#                                               copy a path from the remote tree to .local/mac/<container>/
#   scripts/mac-remote.sh sim <sub>             simulator helpers, on the booted simulator unless noted:
#       sim list                                  devices and their state
#       sim boot [iphone|ipad]                    create the device if missing, boot it, print its UDID
#       sim shot <name>                           screenshot -> .local/mac/<container>/shots/<name>.png
#       sim log [minutes] [predicate]             app unified log of the last N minutes (default 5)
#       sim launch [bundle-id]                    (re)launch the app (default org.andbible.ios.poc)
#       sim sample [process]                      5 s stack sample of the app (hangs, busy loops)
#       sim crashes                               fetch the app's crash reports (~/Library/Logs/DiagnosticReports)
#       sim shutdown                              shut down all simulators
#   scripts/mac-remote.sh ssh [command]         raw ssh to the Mac (interactive shell without a command)
#   scripts/mac-remote.sh unlock                remove a stale build lock (check `check` output first)
#
# Environment:
#   MAC_SSH_CONFIG   ssh config with the host alias (default ~/.ssh-mac/config, from `jailbee mount mac-ssh`)
#   MAC_HOST         host alias in that config (default andbible-mac)
#   MAC_WORKDIR      remote directory under ~ (default work/<container hostname>)
#   MAC_SIM_IPHONE   iPhone simulator name (default "iPhone 16", as in CI; created if missing)
#   MAC_SIM_IPAD     iPad simulator name (default "iPad Air 11-inch (M4)"; created if missing)
#   MAC_TIMEOUT      seconds before a remote Gradle/xcodebuild step is killed (default 3000)
#   MAC_LOCK_WAIT    seconds to wait for another container's build lock (default 5400; a dead holder's
#                    lock is broken automatically, so this only bounds waiting behind live runs)
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_CONFIG="${MAC_SSH_CONFIG:-$HOME/.ssh-mac/config}"
HOST="${MAC_HOST:-andbible-mac}"
NAME="$(hostname)"
WORKDIR="${MAC_WORKDIR:-work/$NAME}"
TREE="$WORKDIR/and-bible"
OUT="$repo/.local/mac/$NAME"
SIM_IPHONE="${MAC_SIM_IPHONE:-iPhone 16}"
SIM_IPAD="${MAC_SIM_IPAD:-iPad Air 11-inch (M4)}"
TIMEOUT="${MAC_TIMEOUT:-3000}"
LOCK_WAIT="${MAC_LOCK_WAIT:-5400}"
APP_ID=org.andbible.ios.poc
APP_PROCESS=AndBiblePoC

die() { echo "mac-remote: $*" >&2; exit 1; }

[ -f "$SSH_CONFIG" ] || die "no ssh config at $SSH_CONFIG. Ask the user to run on the host: jailbee mount mac-ssh $NAME (see docs/ios-remote-mac.md)"

SSH=(ssh -F "$SSH_CONFIG")
# Remote runs get their own connection, never the shared ControlMaster one: the build-lock watchdog
# watches this run's sshd-session, which only ends with its own connection.
SSH_RUN=(ssh -F "$SSH_CONFIG" -o ControlMaster=no -o ControlPath=none)
RSYNC=(rsync -az -e "ssh -F $SSH_CONFIG" --rsync-path=/opt/homebrew/bin/rsync)

# Shell-quote arguments for the remote command line.
q() { [ $# -eq 0 ] || printf '%q ' "$@"; }

# Run a bash script on the Mac. $1 = script text; the remote bash gets the login PATH from ~/.zshenv
# because ssh starts the command through the user's zsh.
remote() { "${SSH_RUN[@]}" "$HOST" 'bash -s' <<<"$1"; }

# Prologue for remote scripts: cd into the tree, helpers, optional build lock.
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

# The Mac build lock (one heavy step at a time for every container; the Mac has 16 GiB shared with
# its owner's session). A waiter breaks the lock when its holder process is gone (killed, Mac
# rebooted), so a dead run never blocks the others. A holder whose container side disappears (ssh
# connection lost: local job killed, session ended) is stopped by a watchdog, so it does not keep
# the lock or the RAM for a result nobody will read.
lock() {
    cat <<EOF
L=\$HOME/work/.build-lock; got=; t0=\$SECONDS
lock_stale() {   # the holder's pid is no longer a live bash, or the dir has had no owner for 60 s
    local pid
    if [ -f "\$L/owner" ]; then
        pid=\$(sed -n 's/.* pid \\([0-9]*\\) .*/\\1/p' "\$L/owner")
        [ -n "\$pid" ] && ! ps -p "\$pid" -o comm= 2>/dev/null | grep -q bash
    else
        [ \$(( \$(date +%s) - \$(stat -f %m "\$L" 2>/dev/null || date +%s) )) -gt 60 ]
    fi
}
for i in \$(seq 1 $((LOCK_WAIT / 5 + 1))); do
    if mkdir "\$L" 2>/dev/null; then got=1; break; fi
    if lock_stale; then
        echo "mac-remote: breaking a stale build lock (holder gone): \$(cat "\$L/owner" 2>/dev/null)" >&2
        rm -rf "\$L"; continue
    fi
    [ \$(( i % 60 )) = 1 ] && echo "mac-remote: waiting \$(( SECONDS - t0 )) s for the build lock held by: \$(cat "\$L/owner" 2>/dev/null)" >&2
    sleep 5
done
[ -n "\$got" ] || { echo "mac-remote: build lock not free after ${LOCK_WAIT}s (held by: \$(cat "\$L/owner" 2>/dev/null))" >&2; exit 75; }
echo "$NAME pid \$\$ since \$(date '+%F %T')" > "\$L/owner"
# Watchdog: the nearest sshd ancestor is this connection; when it is gone, stop this process group.
# Its fds go to /dev/null, otherwise it would hold the ssh channel open after a normal exit.
sshd_pid=; p=\$\$
while [ -n "\$p" ] && [ "\$p" -gt 1 ]; do
    case "\$(ps -p "\$p" -o command= 2>/dev/null)" in sshd*) sshd_pid=\$p; break ;; esac
    p=\$(ps -p "\$p" -o ppid= 2>/dev/null | tr -d ' ')
done
WD=
if [ -n "\$sshd_pid" ]; then
    ( while ps -p "\$sshd_pid" >/dev/null 2>&1; do sleep 10; done
      echo "\$(date '+%F %T') $NAME: ssh connection gone, stopping run \$\$" >> "\$HOME/work/.mac-remote-watchdog.log"
      kill -TERM 0 ) </dev/null >/dev/null 2>&1 &
    WD=\$!
fi
echo "mac-remote: build lock acquired after \$(( SECONDS - t0 )) s; free memory \$(vm_stat | awk '/page size/{ps=\$8} /Pages free/{f=\$3} /Pages inactive/{n=\$3} END{printf "%.1f", (f+n)*ps/1073741824}') GiB" >&2
trap 'rm -rf "\$L"; [ -n "\$WD" ] && kill \$WD 2>/dev/null' EXIT
trap 'exit 129' HUP INT TERM
EOF
}

sync_tree() {
    "${SSH[@]}" "$HOST" "mkdir -p $(q "$TREE")"
    command -v rsync >/dev/null || die "rsync is not installed in this container (old golden image?)"
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

cmd="${1:-}"; shift || true
case "$cmd" in
check)
    "${SSH[@]}" "$HOST" 'bash -s' <<'EOF'
echo "host: $(hostname)  user: $(whoami)  macOS $(sw_vers -productVersion) $(uname -m)  RAM $(($(sysctl -n hw.memsize)/1073741824)) GiB  cpus $(sysctl -n hw.ncpu)"
echo "disk free: $(df -h ~ | awk 'NR==2{print $4}')"
xcodebuild -version | head -1; java -version 2>&1 | head -1; echo "node $(node --version)"; echo "xcodegen $(xcodegen --version 2>&1 | awk '{print $2}')"
xcrun simctl list runtimes available | grep iOS || echo "NO iOS simulator runtime"
xcrun simctl list devices booted | grep -v '^==' || true
L=$HOME/work/.build-lock; [ -d "$L" ] && echo "BUILD LOCK held by: $(cat "$L/owner" 2>/dev/null)" || echo "build lock: free"
echo "free memory: $(vm_stat | awk '/page size/{ps=$8} /Pages free/{f=$3} /Pages inactive/{n=$3} END{printf "%.1f", (f+n)*ps/1073741824}') GiB; Gradle daemons: $(pgrep -f GradleDaemon | wc -l | tr -d ' '); Kotlin daemons: $(pgrep -f KotlinCompileDaemon | wc -l | tr -d ' ')"
[ -f ~/work/.mac-remote-watchdog.log ] && { echo "watchdog (last 3):"; tail -3 ~/work/.mac-remote-watchdog.log; }
echo "trees: $(ls ~/work 2>/dev/null | tr '\n' ' ')"
EOF
    ;;
sync)
    sync_tree
    ;;
gradle)
    [ $# -gt 0 ] || die "usage: gradle <tasks/args...>"
    sync_tree
    remote "$(prologue)
$(lock)
tmo $TIMEOUT ./gradlew -Dorg.gradle.native=false $(q "$@"); rc=\$?
[ \$rc = 142 ] && { echo 'mac-remote: TIMEOUT after ${TIMEOUT}s, stopping the Gradle daemon' >&2; ./gradlew --stop >/dev/null 2>&1; }
exit \$rc"
    ;;
native-tests)
    mods=("$@"); [ ${#mods[@]} -gt 0 ] || mods=(jsword kdom sharedCore sharedData sharedLogic sharedUi)
    sync_tree
    rc=0
    remote "$(prologue)
$(lock)
# sharedCore's tests run inside a booted simulator (IOS_TEST_SIMULATOR, see sharedCore/build.gradle.kts).
export IOS_TEST_SIMULATOR=\$(sim_boot $(q "$SIM_IPHONE")) || exit 1
fail=0
for m in $(q "${mods[@]}"); do
    echo \"=== :\$m:iosSimulatorArm64Test\"
    tmo $TIMEOUT ./gradlew -Dorg.gradle.native=false :\$m:iosSimulatorArm64Test --continue; r=\$?
    [ \$r = 142 ] && { echo \"mac-remote: TIMEOUT in \$m after ${TIMEOUT}s\" >&2; ./gradlew --stop >/dev/null 2>&1; }
    [ \$r = 0 ] || fail=1
done
exit \$fail" || rc=$?
    for m in "${mods[@]}"; do
        for d in test-results/iosSimulatorArm64Test reports/tests/iosSimulatorArm64Test; do
            fetch "$m/build/$d" "$OUT/native-tests/$m/$(dirname "$d")" 2>/dev/null || true
        done
    done
    echo "mac-remote: native-tests exit $rc; XML under ${OUT#"$repo"/}/native-tests/<module>/test-results"
    exit $rc
    ;;
xcode-test|xcode-build)
    which="${1:-iphone}"; [ $# -gt 0 ] && shift
    case "$which" in iphone) sim="$SIM_IPHONE" ;; ipad) sim="$SIM_IPAD" ;; *) die "device must be iphone or ipad" ;; esac
    sync_tree
    rc=0
    remote "$(prologue)
$(lock)
mkdir -p build && rm -rf build/ios-$which.xcresult build/screenshots-$which
# bibleview-js: npm ci only when package-lock.json changed (macOS-native node_modules live on the Mac)
( cd app/bibleview-js
  stamp=node_modules/.mac-remote-lock-sha; sha=\$(shasum package-lock.json | cut -d' ' -f1)
  [ \"\$(cat \$stamp 2>/dev/null)\" = \"\$sha\" ] || { npm ci --no-audit --no-fund && echo \$sha > \$stamp; } &&
  npm run build-production ) > build/bibleview-$which.log 2>&1 || { tail -40 build/bibleview-$which.log; exit 1; }
( cd iosApp && xcodegen generate --quiet ) || exit 1
UDID=\$(sim_boot $(q "$sim")) || exit 1
echo \"mac-remote: simulator \$UDID\"
if [ $cmd = xcode-test ]; then
    # The app runs as its own process under XCUITest: its NSLog output only reaches the unified log.
    xcrun simctl spawn \$UDID log stream --style compact --predicate 'process == \"$APP_PROCESS\"' > build/app-$which.log 2>&1 &
    LOGPID=\$!; sleep 2
    tmo $TIMEOUT xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \\
        -destination \"platform=iOS Simulator,id=\$UDID\" -resultBundlePath build/ios-$which.xcresult $(q "$@") \\
        > build/xcodebuild-$which.log 2>&1; rc=\$?
    sleep 2; kill \$LOGPID 2>/dev/null
    [ -d build/ios-$which.xcresult ] && xcrun xcresulttool export attachments --path build/ios-$which.xcresult \\
        --output-path build/screenshots-$which >/dev/null 2>&1
    grep -E '^(Test Suite|Test Case .*(passed|failed|skipped))|error:|\\*\\* TEST' build/xcodebuild-$which.log | grep -vE \"Test Suite '(All tests|.*\\.xctest)' started\" | tail -60
    echo \"app log: \$(wc -l < build/app-$which.log) lines, WEBVIEW-TERMINATED: \$(grep -c WEBVIEW-TERMINATED build/app-$which.log), js[ lines: \$(grep -c 'js\\[' build/app-$which.log)\"
    exit \$rc
else
    tmo $TIMEOUT xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \\
        -destination \"platform=iOS Simulator,id=\$UDID\" -derivedDataPath build/dd-$which \\
        > build/xcodebuild-$which.log 2>&1 || { grep -E 'error:|BUILD FAILED' build/xcodebuild-$which.log | tail -40; exit 1; }
    APP=\$(ls -d build/dd-$which/Build/Products/Debug-iphonesimulator/$APP_PROCESS.app | head -1)
    xcrun simctl install \$UDID \"\$APP\" && xcrun simctl terminate \$UDID $APP_ID 2>/dev/null
    xcrun simctl launch \$UDID $APP_ID && sleep 5   # let the first render settle before a sim shot
fi" || rc=$?
    mkdir -p "$OUT/xcode"
    rm -rf "$OUT/xcode/screenshots-$which"
    for f in "build/xcodebuild-$which.log" "build/app-$which.log" "build/screenshots-$which" "build/ios-$which.xcresult"; do
        fetch "$f" "$OUT/xcode" >/dev/null 2>&1 || true
    done
    # xcresulttool names attachments by UUID; manifest.json maps them to the names the tests gave
    # (e.g. split2-dark_0_<uuid>.png): copy each PNG to <test>/<name>.png next to them.
    python3 - "$OUT/xcode/screenshots-$which" <<'EOF' || true
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
    echo "mac-remote: $cmd $which exit $rc; logs, xcresult and screenshots under ${OUT#"$repo"/}/xcode/"
    exit $rc
    ;;
run)
    [ $# -gt 0 ] || die "usage: run <shell command>"
    remote "$(prologue)
$(lock)
$*"
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
          remote "$(prologue)
$(lock)
sim_boot $(q "$s")" ;;
    shot) [ $# -ge 1 ] || die "usage: sim shot <name>"
          remote "$(prologue)
$(lock)
mkdir -p build/shots && xcrun simctl io booted screenshot $(q "build/shots/$1.png")>/dev/null 2>&1"
          fetch "build/shots/$1.png" "$OUT/shots" ;;
    log)  min="${1:-5}"; pred="${2:-process == \"$APP_PROCESS\"}"
          remote "$(prologue)
xcrun simctl spawn booted log show --style compact --last ${min}m --predicate $(q "$pred")" ;;
    launch) remote "$(prologue)
$(lock)
xcrun simctl terminate booted $(q "${1:-$APP_ID}") 2>/dev/null; xcrun simctl launch booted $(q "${1:-$APP_ID}")" ;;
    sample) remote "$(prologue)
p=\$(pgrep -n $(q "${1:-$APP_PROCESS}")) || { echo 'process not running' >&2; exit 1; }
sample \$p 5 -file /tmp/mac-remote-sample.txt >/dev/null 2>&1; cat /tmp/mac-remote-sample.txt" ;;
    crashes) remote "$(prologue)
mkdir -p build/crashes && find ~/Library/Logs/DiagnosticReports -name '$APP_PROCESS*' -mtime -2 -exec cp {} build/crashes/ \\; 2>/dev/null; ls -t build/crashes | head"
          fetch build/crashes "$OUT" ;;
    shutdown) remote "$(prologue)
$(lock)
xcrun simctl shutdown all" ;;
    *) die "unknown sim subcommand: $sub" ;;
    esac
    ;;
ssh)
    if [ $# -gt 0 ]; then "${SSH[@]}" "$HOST" "cd $(q "$TREE") 2>/dev/null; $*"; else "${SSH[@]}" -t "$HOST" "cd $(q "$TREE") 2>/dev/null; exec zsh -l"; fi
    ;;
unlock)
    "${SSH[@]}" "$HOST" 'L=$HOME/work/.build-lock; [ -d "$L" ] && { echo "removing lock held by: $(cat "$L/owner" 2>/dev/null)"; rm -rf "$L"; } || echo "no lock"'
    ;;
*)
    sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'
    [ -z "$cmd" ] || exit 2
    ;;
esac
