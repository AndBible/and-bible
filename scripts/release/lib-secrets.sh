#!/usr/bin/env bash
# Sourced library: release secrets decrypted into a private RAM filesystem (container side).
#
#   secrets_open                       mount the RAM dir (ramfs, else tmpfs), set and export SECRETS_DIR
#   secrets_decrypt <src.gpg> <name>   gpg --decrypt into $SECRETS_DIR/<name> (YubiKey touch/PIN on the host)
#   secrets_close                      shred, unmount, rmdir; idempotent (call it from your own EXIT trap)
#   secrets_open_bundle [bundle]       secrets_open + ONE gpg --decrypt of the release bundle piped into tar -x inside the
#                                      RAM dir (no intermediate file); checks MANIFEST; exports RELEASE_SECRETS_DIR.
#                                      Default bundle: $RELEASE_SECRETS_SRC/andbible-release-secrets.tar.gpg. On failure
#                                      the RAM dir is closed and the message names the step, never the content.
#   secrets_member <name>              print $RELEASE_SECRETS_DIR/<name>; nonzero if not open or missing
#   secrets_sweep_stale                shred/unmount/remove leftover release-secrets.* dirs of a killed run (only while
#                                      holding the release lock, see release-internal.sh)
#
# Bundle format 1 (spec docs/superpowers/specs/2026-11-10-release-internal-one-touch-design.md §5.1): MANIFEST
# (`format=1`, then one `member=<name>` line per member) and the members in SECRETS_BUNDLE_MEMBERS, all regular files.
#
# Files are created 0600 before any plaintext is written. The RAM dir path and the decrypted content are never
# printed. A relative <src.gpg> is looked up in ${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}.
# Env: RELEASE_SECRETS_SRC, RELEASE_RAMDIR_MOUNT=none (tests only: a plain 0700 temp dir, no sudo, no mount).

secrets_open() {
    local old_umask
    old_umask=$(umask)
    umask 077
    SECRETS_DIR="$(mktemp -d "${TMPDIR:-/tmp}/release-secrets.XXXXXX")" || { umask "$old_umask"; return 1; }
    umask "$old_umask"
    chmod 700 "$SECRETS_DIR"
    if [ "${RELEASE_RAMDIR_MOUNT:-}" = none ]; then
        SECRETS_FS=plain
    elif sudo -n mount -t ramfs -o mode=0700 ramfs "$SECRETS_DIR" 2>/dev/null; then
        sudo -n chown "$(id -u):$(id -g)" "$SECRETS_DIR"
        SECRETS_FS=ramfs
    elif sudo -n mount -t tmpfs -o size=8m,mode=0700,uid="$(id -u)",gid="$(id -g)" tmpfs "$SECRETS_DIR" 2>/dev/null; then
        SECRETS_FS=tmpfs
    else
        echo "secrets_open: cannot mount a RAM filesystem" >&2
        rmdir "$SECRETS_DIR"
        SECRETS_DIR=""
        return 1
    fi
    export SECRETS_DIR SECRETS_FS
    echo "secrets: $SECRETS_FS RAM dir ready" >&2
}

secrets_decrypt() {
    local src="$1" name="$2" out
    [ -n "${SECRETS_DIR:-}" ] || { echo "secrets_decrypt: secrets_open first" >&2; return 1; }
    case "$src" in */*) ;; *) src="${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}/$src" ;; esac
    [ -f "$src" ] || { echo "secrets_decrypt: missing $1" >&2; return 1; }
    out="$SECRETS_DIR/$name"
    install -m 600 /dev/null "$out"
    echo "secrets: decrypting $(basename "$src"): touch the YubiKey / enter the PIN on the host now" >&2
    gpg --quiet --batch --no-autostart --decrypt "$src" > "$out"
}

secrets_close() {
    [ -n "${SECRETS_DIR:-}" ] || return 0
    if [ -d "$SECRETS_DIR" ]; then
        find "$SECRETS_DIR" -type f -exec shred -u {} + 2>/dev/null
        if [ "${RELEASE_RAMDIR_MOUNT:-}" != none ] && ! sudo -n umount "$SECRETS_DIR" 2>/dev/null; then
            echo "secrets: WARNING: could not unmount the RAM dir (files were shredded)" >&2
        fi
        rm -rf "$SECRETS_DIR" 2>/dev/null
        echo "secrets: RAM dir closed" >&2
    fi
    SECRETS_DIR=""
}

SECRETS_BUNDLE_NAME=andbible-release-secrets.tar.gpg
SECRETS_BUNDLE_MEMBERS=(andbible.p12 keystore.properties play-service-account.json AuthKey.p8 asc-api.env)

secrets_open_bundle() {
    local bundle="${1:-${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}/$SECRETS_BUNDLE_NAME}" m bad manifest
    RELEASE_SECRETS_DIR=""
    [ -f "$bundle" ] || { echo "secrets: no release bundle at $bundle (pack it once with scripts/release/secrets-pack.sh)" >&2; return 1; }
    secrets_open || return 1
    echo "secrets: decrypting $(basename "$bundle"): touch the YubiKey / enter the PIN on the host now" >&2
    if ! ( set -o pipefail; umask 077
           gpg --quiet --batch --no-autostart --decrypt "$bundle" 2>/dev/null \
               | tar -x -C "$SECRETS_DIR" --no-same-owner --no-same-permissions -f - 2>/dev/null ); then
        echo "secrets: decrypting the release bundle failed (no touch in time, wrong key or a damaged file)" >&2
        secrets_close; return 1
    fi
    # Only regular files directly in the dir: no links, no subdirectories.
    bad=$(find "$SECRETS_DIR" -mindepth 1 \( ! -type f -o -path "$SECRETS_DIR/*/*" \) -printf '%P\n' | head -3)
    [ -z "$bad" ] || { echo "secrets: unexpected entries in the bundle: $bad" >&2; secrets_close; return 1; }
    manifest="$SECRETS_DIR/MANIFEST"
    if [ ! -f "$manifest" ] || [ "$(head -n 1 "$manifest")" != format=1 ]; then
        echo "secrets: bundle MANIFEST missing or not format=1" >&2; secrets_close; return 1
    fi
    for m in "${SECRETS_BUNDLE_MEMBERS[@]}"; do
        if ! grep -qx "member=$m" "$manifest" || [ ! -s "$SECRETS_DIR/$m" ]; then
            echo "secrets: bundle member $m missing or empty" >&2; secrets_close; return 1
        fi
    done
    find "$SECRETS_DIR" -mindepth 1 -type f -exec chmod 600 {} +
    RELEASE_SECRETS_DIR="$SECRETS_DIR"
    export RELEASE_SECRETS_DIR
    echo "secrets: release bundle open (${#SECRETS_BUNDLE_MEMBERS[@]} members)" >&2
}

secrets_member() {
    local name="$1"
    case "$name" in ''|*/*|.|..) echo "secrets_member: bad name" >&2; return 1 ;; esac
    [ -n "${RELEASE_SECRETS_DIR:-}" ] || { echo "secrets_member: no open release bundle" >&2; return 1; }
    [ -f "$RELEASE_SECRETS_DIR/$name" ] || { echo "secrets_member: $name not in the bundle" >&2; return 1; }
    printf '%s\n' "$RELEASE_SECRETS_DIR/$name"
}

secrets_sweep_stale() {
    local d n=0
    for d in "${TMPDIR:-/tmp}"/release-secrets.*; do
        [ -d "$d" ] && [ -O "$d" ] || continue
        [ "$d" != "${SECRETS_DIR:-}" ] || continue
        find "$d" -type f -exec shred -u {} + 2>/dev/null
        if mountpoint -q "$d" 2>/dev/null; then sudo -n umount "$d" 2>/dev/null || echo "secrets: WARNING: cannot unmount stale $d" >&2; fi
        rm -rf "$d" 2>/dev/null && n=$((n + 1))
    done
    [ "$n" = 0 ] || echo "secrets: wiped $n stale RAM dir(s) of an earlier killed run" >&2
    return 0
}
