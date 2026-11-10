#!/usr/bin/env bash
# One-touch internal release: the clean HEAD to Google Play `internal` and TestFlight internal, in parallel.
# Spec docs/superpowers/specs/2026-11-10-release-internal-one-touch-design.md.
#
# Usage: scripts/release/release-internal.sh [--platform android|ios|both] [--export-only] [--dry-run]
#   (make release-internal [PLATFORM=android|ios|both] [DEST=upload|export] [DRY_RUN=1]; make testflight = PLATFORM=ios)
#   --export-only  build and check both artifacts, upload nothing
#   --dry-run      run the read-only preflight, print the plan and the branches' dry runs; no gpg, store or Mac write
# Order: release lock (one run per container) -> preflight (no secrets; every miss listed with its fix) -> stale RAM dir
# sweep -> reuse decisions -> ONE gpg decrypt of the bundle into RAM (the YubiKey touch) -> Android branch
# (android.sh build/upload) and iOS branch (ios-build.sh) in the background, each in its own process group -> wait for
# both -> wipe the RAM dir -> summary (.local/release/<versionCode>/summary.txt). On INT/TERM/HUP both branches get TERM
# (KILL after RELEASE_KILL_WAIT s), a killed iOS branch's Mac key volume is detached, and the RAM dir is wiped.
# Exit 0 iff every requested branch passed.
# Env (tests): REPO_ROOT, ANDROID_SH, IOS_BUILD, MAC_REMOTE, CURL, FASTLANE, JAILBEE_ETC (/etc/jailbee),
#      MAC_SSH_CONFIG (~/.ssh-mac/config), RELEASE_SECRETS_SRC (~/.release-secrets), RELEASE_KILL_WAIT (120),
#      RELEASE_RAMDIR_MOUNT=none (tests only).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO_ROOT:-$(cd "$HERE/../.." && pwd)}"
ANDROID_SH="${ANDROID_SH:-$HERE/android.sh}"
IOS_BUILD="${IOS_BUILD:-$HERE/ios-build.sh}"
MAC_REMOTE="${MAC_REMOTE:-$REPO/scripts/mac-remote.sh}"
CURL="${CURL:-curl}"
FASTLANE="${FASTLANE:-fastlane}"
JAILBEE_ETC="${JAILBEE_ETC:-/etc/jailbee}"
MAC_SSH_CONFIG="${MAC_SSH_CONFIG:-$HOME/.ssh-mac/config}"
SRC="${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}"
KILL_WAIT="${RELEASE_KILL_WAIT:-120}"
PLAY_HOSTS=(androidpublisher.googleapis.com oauth2.googleapis.com www.googleapis.com)

PLATFORM=both
DEST=upload
DRY=0
usage() { echo "usage: release-internal.sh [--platform android|ios|both] [--export-only] [--dry-run]" >&2; exit 2; }
while [ $# -gt 0 ]; do
    case "$1" in
    --platform) [ $# -ge 2 ] || usage; PLATFORM="$2"; shift 2 ;;
    --export-only) DEST=export; shift ;;
    --dry-run) DRY=1; shift ;;
    *) usage ;;
    esac
done
case "$PLATFORM" in android|ios|both) ;; *) usage ;; esac
WANT_A=0; WANT_I=0
[ "$PLATFORM" = ios ] || WANT_A=1
[ "$PLATFORM" = android ] || WANT_I=1
die() { echo "release: $*" >&2; exit 1; }

# shellcheck source=lib-secrets.sh
. "$HERE/lib-secrets.sh"
VERSION_FILE="$REPO/version.properties"
# shellcheck source=../lib-version.sh
. "$REPO/scripts/lib-version.sh"

container_name() {
    local n=""
    [ -f "$JAILBEE_ETC/container.json" ] && n=$(sed -n 's/.*"short_name": *"\([^"]*\)".*/\1/p' "$JAILBEE_ETC/container.json" | head -n 1)
    echo "${n:-$(uname -n)}"
}
CONTAINER=$(container_name)

# 1. One release run per container: the stale-dir sweep below must never meet a live run's RAM dir.
exec 9> "${XDG_RUNTIME_DIR:-/tmp}/andbible-release.lock"
flock -n 9 || die "another release run is active in this container (or secrets-pack.sh is running)"

VC=$(version_code) || die "no versionCode in version.properties"
VN_A=$(version_name) || die "no versionName"
VN_I=$(version_name "") || die "no versionName"
COMMIT=$(git -C "$REPO" rev-parse --short=10 HEAD) || die "no HEAD"
RUN_DIR="$REPO/.local/release/$VC"
SUMMARY="$RUN_DIR/summary.txt"
IOS_ENV="$RUN_DIR/ios/artifact.env"

# 2. Preflight: no secrets, nothing written; collect every miss with its fix.
MISSES=()
miss() { MISSES+=("$1"); }
[ -d "$JAILBEE_ETC" ] || miss "not in a JailBee container (the release runs only in one)"
dirty=$(git -C "$REPO" status --porcelain --ignore-submodules=none -- . ':(exclude)docs/superpowers' ':(exclude)app/src/test/roborazzi' 2>&1)
[ -z "$dirty" ] || miss "the tree is not clean (commit or stash; docs/superpowers is ignored): $(printf '%s' "$dirty" | head -n 5 | tr '\n' ' ')"
if [ ! -d "$SRC" ]; then
    miss "the release-secrets mount is missing; on the host: jailbee mount release-secrets $CONTAINER --for 2h"
elif [ ! -f "$SRC/$SECRETS_BUNDLE_NAME" ]; then
    miss "no secrets bundle $SRC/$SECRETS_BUNDLE_NAME; pack it once: scripts/release/secrets-pack.sh"
fi
if [ "$WANT_A" = 1 ]; then
    command -v "$FASTLANE" >/dev/null 2>&1 || miss "fastlane is not on PATH (it comes with the JailBee image)"
    for h in "${PLAY_HOSTS[@]}"; do
        code=$("$CURL" -sS -m 10 -o /dev/null -w '%{http_code}' "https://$h/" 2>/dev/null)
        [ -n "$code" ] && [ "$code" != 000 ] || miss "Play host $h is blocked; on the host: jailbee net egress add $h $CONTAINER"
    done
fi
if [ "$WANT_I" = 1 ]; then
    if [ ! -f "$MAC_SSH_CONFIG" ]; then
        miss "the mac-ssh mount is missing; on the host: jailbee mount mac-ssh $CONTAINER"
    elif ! check_out=$("$MAC_REMOTE" check 2>&1); then
        miss "the Mac does not answer (scripts/mac-remote.sh check): $(printf '%s' "$check_out" | tail -n 2 | tr '\n' ' ')"
    elif holder=$(printf '%s\n' "$check_out" | grep -m1 '^BUILD LOCK held by:') \
        && ! printf '%s\n' "$check_out" | grep -q 'holder is THIS container'; then
        miss "the Mac build lock is held by another container: ${holder#BUILD LOCK held by: }"
    fi
fi
if [ ${#MISSES[@]} -gt 0 ]; then
    echo "release: preflight failed, nothing was decrypted:" >&2
    printf '  - %s\n' "${MISSES[@]}" >&2
    exit 1
fi
[ "$DRY" = 1 ] || secrets_sweep_stale

# 3. Reuse decisions (no secrets).
A_REUSE=0; I_REUSE=""
if [ "$WANT_A" = 1 ] && "$ANDROID_SH" reusable >/dev/null 2>&1; then A_REUSE=1; fi
envget() { sed -n "s/^$1=//p" "$IOS_ENV" 2>/dev/null | head -n 1; }
if [ "$WANT_I" = 1 ] && [ -f "$IOS_ENV" ] && [ "$(envget COMMIT)" = "$COMMIT" ] && [ "$(envget VERSION_CODE)" = "$VC" ] \
    && [ "$(envget VERSION_NAME)" = "$VN_I" ] && [ "$(envget UPLOADED)" = 0 ]; then
    I_REUSE=$(envget BUILD)
fi

mkdir -p "$RUN_DIR/android" "$RUN_DIR/ios"
exec 3>&1    # branches print their one-line progress here; their full output goes to branch.log
APID=""; IPID=""
VOLF="$RUN_DIR/ios/.volume"
rm -f "$VOLF" "$RUN_DIR"/android/.status "$RUN_DIR"/ios/.status

android_branch() {
    local rc
    say() { echo "[android] $*" >&3; }
    if [ "$A_REUSE" = 1 ]; then
        say "reusing the AAB of this commit; uploading to Play internal"
        "$ANDROID_SH" upload ${DRYARG:+"$DRYARG"}; return
    fi
    say "building the signed AAB (Gradle, log $RUN_DIR/android/branch.log)"
    "$ANDROID_SH" build ${DRYARG:+"$DRYARG"} || return
    [ "$DEST" = upload ] || { say "export only: not uploading"; return 0; }
    say "uploading to Play internal"
    "$ANDROID_SH" upload ${DRYARG:+"$DRYARG"}; rc=$?
    return "$rc"
}
ios_branch() {
    local rc args=()
    say() { echo "[ios] $*" >&3; }
    [ "$DEST" = upload ] || args+=(--export-only)
    [ -z "$DRYARG" ] || args+=("$DRYARG")
    if [ -n "$I_REUSE" ]; then
        say "reusing the Mac archive of build $I_REUSE"
        IOS_VOL_FILE="$VOLF" "$IOS_BUILD" --reuse "$I_REUSE" "${args[@]}"; rc=$?
        [ "$rc" = 3 ] || return "$rc"
        say "no archive to reuse; archiving afresh"
    fi
    say "archive on the Mac, export, checks${args[*]:+ (${args[*]})} (log $RUN_DIR/ios/branch.log)"
    IOS_VOL_FILE="$VOLF" "$IOS_BUILD" "${args[@]}"
}
# run_branch <name> <function>: background job (own process group via set -m); records "rc seconds" in .status.
run_branch() {
    local name="$1" fn="$2" t0 rc
    t0=$(date +%s)
    "$fn" > "$RUN_DIR/$name/branch.log" 2>&1
    rc=$?
    echo "$rc $(( $(date +%s) - t0 ))" > "$RUN_DIR/$name/.status"
    if [ "$rc" = 0 ]; then echo "[$name] PASS" >&3; else echo "[$name] FAIL (rc $rc); see $RUN_DIR/$name/branch.log" >&3; fi
    return "$rc"
}

# alive <pid>: is anything left in that branch's process group (a child can outlive the branch's own shell)?
alive() { [ -n "$1" ] && kill -0 -- -"$1" 2>/dev/null; }
# stop_branches: TERM both process groups, wait up to KILL_WAIT s, then KILL; detach the Mac volume of a killed iOS run.
stop_branches() {
    local i ios_killed=0
    for p in $APID $IPID; do kill -TERM -- -"$p" 2>/dev/null; done
    for ((i = 0; i < KILL_WAIT * 10; i++)); do alive "$APID" || alive "$IPID" || break; sleep 0.1; done
    if alive "$APID"; then kill -KILL -- -"$APID" 2>/dev/null; fi
    if alive "$IPID"; then kill -KILL -- -"$IPID" 2>/dev/null; ios_killed=1; fi
    wait 2>/dev/null
    if [ "$ios_killed" = 1 ] && [ -s "$VOLF" ]; then
        echo "release: the iOS branch had to be killed; detaching its Mac key volume" >&2
        "$IOS_BUILD" --detach "$(cat "$VOLF")" >&2
    fi
}
on_exit() {
    local rc=$?
    trap '' INT TERM HUP
    trap - EXIT
    if alive "$APID" || alive "$IPID"; then stop_branches; fi
    secrets_close
    rm -f "$VOLF"
    exit "$rc"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# 4. The one decrypt (the YubiKey touch).
echo "release: $([ "$PLATFORM" = both ] && echo "Play internal + TestFlight" || echo "$PLATFORM"), destination $DEST, commit $COMMIT, versionCode $VC"
[ "$A_REUSE" = 0 ] || echo "release: android: reusing $RUN_DIR/android (same commit and version)"
[ -z "$I_REUSE" ] || echo "release: ios: reusing the Mac archive of build $I_REUSE"
DRYARG=""
if [ "$DRY" = 1 ]; then
    DRYARG=--dry-run
    echo "[dry-run] gpg --decrypt $SRC/$SECRETS_BUNDLE_NAME | tar -x into a RAM dir (the one YubiKey touch)"
else
    secrets_open_bundle || exit 1
fi

# 5. Both branches in parallel, each in its own process group.
T0=$(date +%s)
set -m
[ "$WANT_A" = 0 ] || { run_branch android android_branch & APID=$!; }
[ "$WANT_I" = 0 ] || { run_branch ios ios_branch & IPID=$!; }
set +m
[ -z "$APID" ] || wait "$APID"
[ -z "$IPID" ] || wait "$IPID"
APID=""; IPID=""
secrets_close    # both branches have ended

# 6. Summary.
status_of() {   # status_of <name> <wanted>: PASS|FAIL|SKIPPED and seconds
    local s
    [ "$2" = 1 ] || { echo "SKIPPED -"; return; }
    s=$(cat "$RUN_DIR/$1/.status" 2>/dev/null) || { echo "FAIL -"; return; }
    if [ "${s%% *}" = 0 ]; then echo "PASS ${s#* }"; else echo "FAIL ${s#* }"; fi
}
read -r A_ST A_S <<<"$(status_of android "$WANT_A")"
read -r I_ST I_S <<<"$(status_of ios "$WANT_I")"
I_BUILD=$(sed -n 's/^build //p' "$RUN_DIR/ios/branch.log" 2>/dev/null | tail -n 1)
{
    echo "release-internal $(date -u +%Y-%m-%dT%H:%M:%SZ) commit $COMMIT versionCode $VC destination $DEST$([ "$DRY" = 1 ] && echo ' (dry run)') total $(( $(date +%s) - T0 ))s"
    echo "android: $A_ST $VN_A ${A_S}s$([ "$A_REUSE" = 1 ] && echo ' reused')  $RUN_DIR/android/ (andbible-$VN_A.aab, branch.log)"
    echo "ios: $I_ST $VN_I build ${I_BUILD:--} ${I_S}s$([ -n "$I_REUSE" ] && echo ' reused')  $RUN_DIR/ios/branch.log${I_BUILD:+ .local/release/testflight/$I_BUILD/}"
    [ "$A_ST" != FAIL ] || echo "retry: make release-internal PLATFORM=android$([ "$DEST" = export ] && echo ' DEST=export')"
    [ "$I_ST" != FAIL ] || echo "retry: make release-internal PLATFORM=ios$([ "$DEST" = export ] && echo ' DEST=export')"
    echo "host: jailbee unmount release-secrets $CONTAINER"
    if [ "$WANT_A" = 1 ]; then for h in "${PLAY_HOSTS[@]}"; do echo "host: jailbee net egress rm $h $CONTAINER"; done; fi
} > "$SUMMARY"
cat "$SUMMARY"
[ "$A_ST" != FAIL ] && [ "$I_ST" != FAIL ]
