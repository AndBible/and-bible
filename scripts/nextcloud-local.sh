#!/usr/bin/env bash
# Throwaway local Nextcloud for emulator sync verification (no network needed).
#
#   scripts/nextcloud-local.sh install|start|stop|status|selftest
#
# install   unpack .local/emu-verify/nextcloud/nextcloud-35.0.1.tar.bz2 (sha256 checked) into
#           ~/nextcloud-emu/, run occ maintenance:install (SQLite, admin/andbible-test-pw), trust
#           localhost and 10.0.2.2 (the emulator's view of the host).
# start     php -S 0.0.0.0:8080 detached, pid in ~/nextcloud-emu/php.pid
# stop      kill that server
# status    running / not running
# selftest  exit 0 only if status.php says installed:true and DAV PROPFIND on the admin root gives 207
#
# Never point this at a real account: the admin is throwaway. Reach it from the guest at
# http://10.0.2.2:8080 (clear the guest http_proxy first if it would route 10.0.2.2 elsewhere).
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
kit="$repo/.local/emu-verify/nextcloud"
tarball="$kit/nextcloud-35.0.1.tar.bz2"
base="${NEXTCLOUD_EMU_DIR:-$HOME/nextcloud-emu}"
port="${NEXTCLOUD_EMU_PORT:-8080}"
admin=admin
pass=andbible-test-pw
pidfile="$base/php.pid"

die() { echo "nextcloud-local: $*" >&2; exit 1; }

running() { [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; }

# True if the pid's command line mentions php (guards against a pid reused by another process).
is_php() { tr '\0' ' ' <"/proc/$1/cmdline" 2>/dev/null | grep -q php; }

# True if status.php answers on the port.
answering() { curl -s --noproxy '*' -o /dev/null "http://127.0.0.1:$port/status.php"; }

# Install may only wipe $base when it is clearly ours: a nextcloud-emu path, a dir with a
# nextcloud/config subfolder, or an empty/nonexistent path.
safe_to_wipe() {
    [ -n "$base" ] || return 1
    case "$base" in */nextcloud-emu) return 0 ;; esac
    [ -d "$base/nextcloud/config" ] && return 0
    [ -e "$base" ] || return 0
    [ -d "$base" ] && [ -z "$(ls -A "$base")" ] && return 0
    return 1
}

occ() { (cd "$base/nextcloud" && php occ "$@"); }

case "${1:-}" in
    install)
        [ -f "$tarball" ] || die "missing $tarball"
        expected="$(awk '/tar\.bz2$/ {print $1}' "$tarball.sha256")"
        actual="$(sha256sum "$tarball" | awk '{print $1}')"
        [ "$expected" = "$actual" ] || die "sha256 mismatch for $tarball"
        php -v >/dev/null 2>&1 || die "php is not installed"
        if [ -f "$base/nextcloud/config/config.php" ] && occ status 2>/dev/null | grep -q 'installed: true'; then
            echo "already installed in $base"; exit 0
        fi
        safe_to_wipe || die "refusing to rm -rf '$base': not a nextcloud-emu path, no nextcloud/config inside, and not empty"
        rm -rf "$base"; mkdir -p "$base/data"
        tar -xjf "$tarball" -C "$base"
        occ maintenance:install --database sqlite --admin-user "$admin" --admin-pass "$pass" \
            --data-dir "$base/data"
        occ config:system:set trusted_domains 1 --value=localhost
        occ config:system:set trusted_domains 2 --value=10.0.2.2
        echo "installed in $base"
        ;;
    start)
        [ -d "$base/nextcloud" ] || die "not installed (run install)"
        running && { echo "already running (pid $(cat "$pidfile"))"; exit 0; }
        # PHP's built-in server is single-process by default; give it workers so DAV and status overlap.
        PHP_CLI_SERVER_WORKERS=4 setsid nohup php -S 0.0.0.0:"$port" -t "$base/nextcloud" \
            >"$base/php.log" 2>&1 &
        echo $! >"$pidfile"
        up=
        for _ in $(seq 1 30); do
            if answering; then up=1; break; fi
            sleep 1
        done
        [ -n "$up" ] || { echo "nextcloud-local: no answer on :$port/status.php after 30s (see $base/php.log)" >&2; exit 1; }
        echo "started (pid $(cat "$pidfile")) on :$port"
        ;;
    stop)
        if running; then
            pid="$(cat "$pidfile")"
            if is_php "$pid"; then
                pkill -P "$pid" 2>/dev/null || true
                kill "$pid" 2>/dev/null || true
                for _ in $(seq 1 10); do
                    answering || break
                    sleep 1
                done
                rm -f "$pidfile"
                echo "stopped"
            else
                rm -f "$pidfile"
                echo "pid $pid is not php; pidfile removed, nothing killed"
            fi
        else
            rm -f "$pidfile"; echo "not running"
        fi
        ;;
    status)
        if running; then echo "running (pid $(cat "$pidfile"))"; else echo "not running"; exit 1; fi
        ;;
    selftest)
        s="$(curl -s --noproxy '*' "http://127.0.0.1:$port/status.php")" || die "status.php unreachable"
        echo "$s" | grep -q '"installed":true' || die "status.php: $s"
        code="$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' -u "$admin:$pass" -X PROPFIND \
            -H 'Depth: 1' "http://127.0.0.1:$port/remote.php/dav/files/$admin/")"
        [ "$code" = 207 ] || die "PROPFIND gave $code, expected 207"
        echo "selftest ok (installed:true, PROPFIND 207)"
        ;;
    *) sed -n '2,15p' "$0"; exit 2 ;;
esac
