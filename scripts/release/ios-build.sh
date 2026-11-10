#!/usr/bin/env bash
# iOS branch of the internal release (TestFlight): stream the ASC API key from the caller's opened secrets bundle onto a
# RAM disk on the build Mac, run scripts/release/mac/archive.sh there under the mac-remote lock, detach the RAM disk,
# take the evidence from the run capture, and detach the Mac RAM disk whatever happens (EXIT trap). It never decrypts
# and never closes the caller's RAM dir: release-internal.sh opens the bundle (one touch) and wipes it.
#
# Usage: scripts/release/ios-build.sh [--export-only] [--dry-run] [--reuse <build>]
#   --export-only    archive and export the IPA but do not upload it
#   --dry-run        print the steps (the key as <session>/AuthKey.p8); run no ssh or mac-remote except `check`
#   --reuse <build>  export/upload the Mac's existing archive of <build> (archive.sh `reuse`); exit 3 = no such archive
# Writes .local/release/<versionCode>/ios/artifact.env (COMMIT, VERSION_CODE, VERSION_NAME, BUILD, UPLOADED) once the
# archive exists. Refuses a dirty tree (parent status incl. jsword/kdom gitlinks; docs/superpowers and
# app/src/test/roborazzi ignored).
# Env: RELEASE_SECRETS_DIR (required except --dry-run; members asc-api.env and AuthKey.p8), REPO_ROOT, MAC_REMOTE
#      (default scripts/mac-remote.sh), MAC_SSH_CONFIG (~/.ssh-mac/config), MAC_HOST (andbible-mac), MAC_TIMEOUT (5400),
#      MAC_LOCAL_DIR (mac-remote's local dir), MAC_DETACH_TIMEOUT (60), MAC_DETACH_KILL_AFTER (5).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO_ROOT:-$(cd "$HERE/../.." && pwd)}"
MAC_REMOTE="${MAC_REMOTE:-$REPO/scripts/mac-remote.sh}"
TEAM=X6A6CDK8T7
DEST=upload
DRY=0
REUSE=""
usage() { echo "usage: ios-build.sh [--export-only] [--dry-run] [--reuse <build>]" >&2; exit 2; }
while [ $# -gt 0 ]; do
    case "$1" in
    --export-only) DEST=export; shift ;;
    --dry-run) DRY=1; shift ;;
    --reuse) [ $# -ge 2 ] || usage; REUSE="$2"; shift 2
        [[ "$REUSE" =~ ^20[0-9]{2}\.[0-9]{4}\.[0-9]{6}$ ]] || { echo "ios-build: bad --reuse build '$REUSE'" >&2; exit 2; } ;;
    *) usage ;;
    esac
done
die() { echo "ios-build: $*" >&2; exit 1; }

# shellcheck source=lib-secrets.sh
. "$HERE/lib-secrets.sh"
# shellcheck source=../lib-version.sh
. "$REPO/scripts/lib-version.sh"

# 1. Clean tree (checked again after the decrypt wait, before anything is synced).
check_clean() {
    local dirty
    dirty=$(git -C "$REPO" status --porcelain --ignore-submodules=none -- . ':(exclude)docs/superpowers' ':(exclude)app/src/test/roborazzi') \
        || die "git status failed"
    [ -z "$dirty" ] || { echo "$dirty" >&2; die "the tree is not clean (commit or stash; docs/superpowers is ignored)"; }
}
check_clean
COMMIT=$(git -C "$REPO" rev-parse --short=10 HEAD) || die "no HEAD"
VERSION=$(version_name "") || die "no version name"
VCODE=$(version_code) || die "no version code"
BUILD=${REUSE:-$(date -u +%Y.%m%d.%H%M%S)}
echo "build $BUILD"
echo "version $VERSION"
echo "commit $COMMIT"

# 2. Mac lock: stop (do not wait, do not decrypt) when another container holds it.
# A stale ('holder GONE') lock of another container refuses too, deliberately: breaking it is mac-remote's call.
check_out=$("$MAC_REMOTE" check 2>&1) || die "mac-remote check failed"
if holder=$(printf '%s\n' "$check_out" | grep -m1 '^BUILD LOCK held by:'); then
    if ! printf '%s\n' "$check_out" | grep -q 'holder is THIS container'; then
        die "the Mac build lock is held by another container: ${holder#BUILD LOCK held by: }"
    fi
fi

SSH=(ssh -F "${MAC_SSH_CONFIG:-$HOME/.ssh-mac/config}" "${MAC_HOST:-andbible-mac}")
if ENV_FILE=$(secrets_member asc-api.env 2>/dev/null) && KEY_FILE=$(secrets_member AuthKey.p8 2>/dev/null); then
    ASC_KEY_ID=$(sed -n 's/^ASC_KEY_ID=//p' "$ENV_FILE" | head -n 1)
    ASC_ISSUER_ID=$(sed -n 's/^ASC_ISSUER_ID=//p' "$ENV_FILE" | head -n 1)
    [ -n "$ASC_KEY_ID" ] && [ -n "$ASC_ISSUER_ID" ] || die "asc-api.env in the secrets bundle lacks ASC_KEY_ID or ASC_ISSUER_ID"
elif [ "$DRY" = 1 ]; then
    ASC_KEY_ID="<key-id>"; ASC_ISSUER_ID="<issuer-id>"; KEY_FILE="<session>/AuthKey.p8"
else
    die "no open secrets bundle (RELEASE_SECRETS_DIR); run it through scripts/release/release-internal.sh"
fi
VOL="asckey-$$"
FILE="/Volumes/$VOL/AuthKey_${ASC_KEY_ID}.p8"
ARCHIVE_CMD="bash scripts/release/mac/archive.sh /Volumes/$VOL $ASC_KEY_ID $ASC_ISSUER_ID $TEAM $BUILD $VERSION $COMMIT $DEST${REUSE:+ reuse}"
EVID_REMOTE="build/release/testflight-$BUILD/"
EVID_LOCAL="$REPO/.local/release/testflight/$BUILD/"
IOS_ENV="$REPO/.local/release/$VCODE/ios/artifact.env"
ATTACH_CMD="set -e; dev=\$(hdiutil attach -nomount ram://40960 | tr -d '[:space:]'); diskutil erasevolume HFS+ $VOL \$dev >/dev/null; f=$FILE; touch \$f; chmod 600 \$f; cat > \$f; ls -l \$f | awk '{print \$1, \$5}'"

if [ "$DRY" = 1 ]; then
    echo "[dry-run] ${SSH[*]} '$ATTACH_CMD' < $KEY_FILE"
    echo "[dry-run] MAC_TIMEOUT=${MAC_TIMEOUT:-5400} $MAC_REMOTE run \"$ARCHIVE_CMD\""
    echo "[dry-run] detach /Volumes/$VOL over ssh (right after run)"
    echo "[dry-run] copy logs/summary/entitlements from the run capture under .local/mac/<container>/runs/*/run/step*/release/testflight-$BUILD/ to $EVID_LOCAL; only if missing: $MAC_REMOTE fetch $EVID_REMOTE $EVID_LOCAL"
    echo "[dry-run] write $IOS_ENV once the archive exists"
    echo "[dry-run] EXIT trap: detach /Volumes/$VOL over ssh"
    exit 0
fi

# 3. Everything below is covered by the EXIT trap: the Mac volume is detached (if it was ever attached) after success,
#    failure or a signal. The caller's RAM dir is the caller's to close.
# detach_mac: detach /Volumes/$VOL on the Mac, bounded (ssh inherits the ignored INT/TERM/HUP, so only timeout can end a
# stalled connection). The remote command fails when the volume is still mounted afterwards. Never exits.
detach_mac() {
    local out
    [ -n "$VOL" ] || return 0
    if out=$(timeout -k "${MAC_DETACH_KILL_AFTER:-5}" "${MAC_DETACH_TIMEOUT:-60}" "${SSH[@]:0:3}" -o ConnectTimeout=20 -o ServerAliveInterval=10 -o ServerAliveCountMax=3 "${SSH[@]:3}" \
        "d=\$(df /Volumes/$VOL 2>/dev/null | awk 'NR==2{print \$1}'); [ -n \"\$d\" ] && hdiutil detach \$d >/dev/null; echo \$(hdiutil info | grep -c asckey || true); [ ! -e /Volumes/$VOL ]" 2>&1 </dev/null); then
        echo "mac asckey volumes after detach: $out"
        VOL=""
    else
        echo "ios-build: WARNING: could not detach /Volumes/$VOL on the Mac; the API key may still be there (asckey volumes: ${out:-unknown}). Detach by hand from the container: scripts/mac-remote.sh ssh 'hdiutil detach -force /Volumes/$VOL'" >&2
    fi
    return 0
}
mac_detach() {
    local rc=$?
    trap '' INT TERM HUP    # a second Ctrl-C must not interrupt the detach
    trap - EXIT
    detach_mac
    exit "$rc"
}
VOL=""
trap mac_detach EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

[ -s "$KEY_FILE" ] || die "AuthKey.p8 in the secrets bundle is empty"
check_clean    # edits made since the start would be synced into the build
VOL="asckey-$$"    # set before the stream: a half-done attach is detached too
"${SSH[@]}" "$ATTACH_CMD" < "$KEY_FILE" || die "streaming the key to the Mac failed"

t0=$(date +%s)
MAC_TIMEOUT="${MAC_TIMEOUT:-5400}" "$MAC_REMOTE" run "$ARCHIVE_CMD"
rc=$?
detach_mac    # the key is no longer needed: do not hold it on the Mac while the evidence is collected

# Evidence: `run` already captured the new build/release entries locally; copy only the small files (not export/, ipa/,
# upload/). `mac-remote.sh fetch` (re-takes the lock) is only the fallback when the capture is missing.
MR_LOCAL="${MAC_LOCAL_DIR:-$REPO/.local/mac/${MAC_CONTAINER:-$(hostname)}}"
have_ev=0
mkdir -p "$EVID_LOCAL"
for cap in "$MR_LOCAL"/runs/*/run/step*/release/testflight-"$BUILD"; do
    [ -d "$cap" ] || continue
    for f in "$cap"/*.log "$cap"/summary.txt "$cap"/release.entitlements "$cap"/ipa-entitlements.txt; do
        [ -f "$f" ] && cp "$f" "$EVID_LOCAL" && have_ev=1
    done
done
if [ "$have_ev" = 0 ]; then
    if "$MAC_REMOTE" fetch "$EVID_REMOTE" "$EVID_LOCAL"; then have_ev=1; fi
fi
if [ "$have_ev" = 0 ]; then
    echo "ios-build: WARNING: no evidence for build $BUILD (run capture missing, fetch failed); run rc=$rc stands" >&2
fi
# artifact.env once the archive exists (a failure after the archive step can be retried with --reuse).
result=$(sed -n 's/^result=//p' "$EVID_LOCAL/summary.txt" 2>/dev/null | tail -n 1)
case "$result" in
"PASS upload") uploaded=1 ;;
"PASS export"|"FAIL resign"|"FAIL export"|"FAIL ipa-checks"|"FAIL upload") uploaded=0 ;;
*) uploaded="" ;;
esac
if [ -n "$uploaded" ]; then
    mkdir -p "$(dirname "$IOS_ENV")"
    { echo "COMMIT=$COMMIT"; echo "VERSION_CODE=$VCODE"; echo "VERSION_NAME=$VERSION"; echo "BUILD=$BUILD"; echo "UPLOADED=$uploaded"; } > "$IOS_ENV"
fi
echo "testflight rc=$rc in $(( $(date +%s) - t0 ))s (destination $DEST)"
exit "$rc"
