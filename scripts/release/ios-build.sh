#!/usr/bin/env bash
# Container side of the TestFlight internal build: decrypt the ASC API key (one YubiKey touch), stream it onto a
# RAM disk on the build Mac, run scripts/release/mac/archive.sh there under the mac-remote lock, fetch the evidence,
# and detach the Mac RAM disk whatever happens (EXIT trap).
#
# Usage: scripts/release/ios-build.sh [--export-only] [--dry-run]
#   --export-only   archive and export the IPA but do not upload it
#   --dry-run       print the steps (the key as <ramdir>/key.p8); run no ssh, gpg or mac-remote except `check`
# Refuses a dirty tree (parent status incl. jsword/kdom gitlinks; docs/superpowers and app/src/test/roborazzi ignored).
# Env: REPO_ROOT, MAC_REMOTE (default scripts/mac-remote.sh), MAC_SSH_CONFIG (~/.ssh-mac/config), MAC_HOST
#      (andbible-mac), MAC_TIMEOUT (5400), RELEASE_SECRETS_SRC (~/.release-secrets, holds asc-api.env and
#      AuthKey_<id>.p8.gpg), RELEASE_RAMDIR_MOUNT=none (tests only).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO_ROOT:-$(cd "$HERE/../.." && pwd)}"
MAC_REMOTE="${MAC_REMOTE:-$REPO/scripts/mac-remote.sh}"
TEAM=X6A6CDK8T7
DEST=upload
DRY=0
for a in "$@"; do
    case "$a" in
    --export-only) DEST=export ;;
    --dry-run) DRY=1 ;;
    *) echo "usage: ios-build.sh [--export-only] [--dry-run]" >&2; exit 2 ;;
    esac
done
die() { echo "ios-build: $*" >&2; exit 1; }

# shellcheck source=lib-secrets.sh
. "$HERE/lib-secrets.sh"
# shellcheck source=../lib-version.sh
. "$REPO/scripts/lib-version.sh"

# 1. Clean tree.
dirty=$(git -C "$REPO" status --porcelain --ignore-submodules=none -- . ':(exclude)docs/superpowers' ':(exclude)app/src/test/roborazzi') \
    || die "git status failed"
[ -z "$dirty" ] || { echo "$dirty" >&2; die "the tree is not clean (commit or stash; docs/superpowers is ignored)"; }
COMMIT=$(git -C "$REPO" rev-parse --short=10 HEAD) || die "no HEAD"
VERSION=$(version_name "") || die "no version name"
BUILD=$(date -u +%Y.%m%d.%H%M%S)
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
ENV_FILE="${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}/asc-api.env"
if [ -f "$ENV_FILE" ]; then
    set -a; . "$ENV_FILE"; set +a
elif [ "$DRY" = 1 ]; then
    ASC_KEY_ID=${ASC_KEY_ID:-"<key-id>"}; ASC_ISSUER_ID=${ASC_ISSUER_ID:-"<issuer-id>"}
else
    die "missing $ENV_FILE (is the release-secrets mount attached?)"
fi
VOL="asckey-$$"
FILE="/Volumes/$VOL/AuthKey_${ASC_KEY_ID}.p8"
ARCHIVE_CMD="bash scripts/release/mac/archive.sh /Volumes/$VOL $ASC_KEY_ID $ASC_ISSUER_ID $TEAM $BUILD $VERSION $COMMIT $DEST"
EVID_REMOTE="build/release/testflight-$BUILD/"
EVID_LOCAL="$REPO/.local/release/testflight/$BUILD/"
ATTACH_CMD="set -e; dev=\$(hdiutil attach -nomount ram://40960 | tr -d '[:space:]'); diskutil erasevolume HFS+ $VOL \$dev >/dev/null; f=$FILE; touch \$f; chmod 600 \$f; cat > \$f; ls -l \$f | awk '{print \$1, \$5}'"

if [ "$DRY" = 1 ]; then
    echo "[dry-run] gpg --decrypt AuthKey_${ASC_KEY_ID}.p8.gpg > <ramdir>/key.p8"
    echo "[dry-run] ${SSH[*]} '$ATTACH_CMD' < <ramdir>/key.p8"
    echo "[dry-run] secrets_close"
    echo "[dry-run] MAC_TIMEOUT=${MAC_TIMEOUT:-5400} $MAC_REMOTE run \"$ARCHIVE_CMD\""
    echo "[dry-run] $MAC_REMOTE fetch $EVID_REMOTE $EVID_LOCAL"
    echo "[dry-run] EXIT trap: detach /Volumes/$VOL over ssh, secrets_close"
    exit 0
fi

# 3. Everything below is covered by the EXIT trap: the Mac volume is detached (if it was ever attached) and the
#    container RAM dir closed after success, failure or a signal.
mac_detach() {
    local rc=$? out
    trap '' INT TERM HUP    # a second Ctrl-C must not interrupt the detach or the RAM dir cleanup
    trap - EXIT
    if [ -n "$VOL" ]; then
        if out=$("${SSH[@]}" -o ConnectTimeout=20 "d=\$(df /Volumes/$VOL 2>/dev/null | awk 'NR==2{print \$1}'); [ -n \"\$d\" ] && hdiutil detach \$d >/dev/null; hdiutil info | grep -c asckey || true" 2>&1 </dev/null); then
            echo "mac asckey volumes after detach: $out"
        else
            echo "ios-build: WARNING: could not detach /Volumes/$VOL on the Mac; the API key may still be there. Detach by hand: ssh ${MAC_HOST:-andbible-mac} 'hdiutil detach /Volumes/$VOL'" >&2
        fi
    fi
    secrets_close
    exit "$rc"
}
VOL=""
trap mac_detach EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

secrets_open || exit 1
secrets_decrypt "AuthKey_${ASC_KEY_ID}.p8.gpg" key.p8 || exit 1
[ -s "$SECRETS_DIR/key.p8" ] || die "decrypted key is empty"
VOL="asckey-$$"    # set before the stream: a half-done attach is detached too
"${SSH[@]}" "$ATTACH_CMD" < "$SECRETS_DIR/key.p8" || die "streaming the key to the Mac failed"
secrets_close

t0=$(date +%s)
MAC_TIMEOUT="${MAC_TIMEOUT:-5400}" "$MAC_REMOTE" run "$ARCHIVE_CMD"
rc=$?
mkdir -p "$EVID_LOCAL"
if ! "$MAC_REMOTE" fetch "$EVID_REMOTE" "$EVID_LOCAL"; then
    echo "ios-build: could not fetch $EVID_REMOTE" >&2
    [ "$rc" -ne 0 ] || rc=1
fi
echo "testflight rc=$rc in $(( $(date +%s) - t0 ))s (destination $DEST)"
exit "$rc"
