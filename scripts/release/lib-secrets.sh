#!/usr/bin/env bash
# Sourced library: release secrets decrypted into a private RAM filesystem (container side).
#
#   secrets_open                       mount the RAM dir (ramfs, else tmpfs), set and export SECRETS_DIR
#   secrets_decrypt <src.gpg> <name>   gpg --decrypt into $SECRETS_DIR/<name> (YubiKey touch/PIN on the host)
#   secrets_close                      shred, unmount, rmdir; idempotent (call it from your own EXIT trap)
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
    [ -f "$src" ] || src="${RELEASE_SECRETS_SRC:-$HOME/.release-secrets}/$src"
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
        [ "${RELEASE_RAMDIR_MOUNT:-}" = none ] || sudo -n umount "$SECRETS_DIR" 2>/dev/null
        rm -rf "$SECRETS_DIR" 2>/dev/null
        echo "secrets: RAM dir closed" >&2
    fi
    SECRETS_DIR=""
}
