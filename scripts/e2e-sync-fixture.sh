#!/usr/bin/env bash
# https fixture for the iOS end-to-end sync XCUITest (iosApp/iosAppUITests/EndToEndUITests.swift).
#
#   scripts/e2e-sync-fixture.sh up      Nextcloud (nextcloud-local.sh, installed on demand) + TLS relay with a
#                                       throwaway self-signed cert for localhost + reverse tunnel Mac
#                                       localhost:$E2E_PORT -> container relay; empties the remote sync folder
#   scripts/e2e-sync-fixture.sh json    print the TEST_RUNNER_E2E_SYNC_FIXTURE_JSON value (url, user, password, sha256)
#   scripts/e2e-sync-fixture.sh reset   empty the remote sync folder (needed before EVERY test run)
#   scripts/e2e-sync-fixture.sh down    stop tunnel, relay and Nextcloud; delete the cert dir
#   scripts/e2e-sync-fixture.sh run [mac-remote xcode-test args...]
#                                       up + xcode-test iphone -only-testing:iosAppUITests/EndToEndUITests/<chain test>
#                                       (override the selection by passing your own args), then down
#
# The cert is generated at run time into .local/e2e-sync-fixture/ (never committed). Env: E2E_PORT (default 18473,
# the port on the Mac), E2E_RELAY_PORT (container side, default 8444). The test uses the WebDAV backend with the
# Nextcloud DAV URL and pins the certificate by its SHA-256 (the app's "Untrusted server certificate" dialog).
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
dir="$repo/.local/e2e-sync-fixture"
port="${E2E_PORT:-18473}"
relay="${E2E_RELAY_PORT:-8444}"
ssh_cfg="${MAC_SSH_CONFIG:-$HOME/.ssh-mac/config}"
host="${MAC_HOST:-andbible-mac}"
user=admin
pass=andbible-test-pw
dav="remote.php/dav/files/$user"
folder="org.andbible.ios.dev-sync-bookmarks"

pid_alive() { [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null; }

fingerprint() { openssl x509 -in "$dir/cert.pem" -noout -fingerprint -sha256 | sed 's/.*=//; s/://g'; }

reset() {
    curl -s --noproxy '*' -o /dev/null -u "$user:$pass" -X DELETE "http://127.0.0.1:8080/$dav/$folder/" || true
    echo "remote folder $folder emptied"
}

up() {
    mkdir -p "$dir"
    [ -d "$HOME/nextcloud-emu/nextcloud" ] || "$repo/scripts/nextcloud-local.sh" install
    "$repo/scripts/nextcloud-local.sh" status | grep -q '^running' || "$repo/scripts/nextcloud-local.sh" start
    # Subject/issuer are what the app's trust dialog shows and what the test checks.
    [ -f "$dir/cert.pem" ] || openssl req -x509 -newkey rsa:2048 -nodes -days 2 -keyout "$dir/key.pem" \
        -out "$dir/cert.pem" -subj "/CN=localhost/O=AndBible disposable test" \
        -addext "subjectAltName=DNS:localhost" 2>/dev/null
    if ! pid_alive "$dir/relay.pid"; then
        nohup python3 "$repo/scripts/tls-proxy.py" --listen "127.0.0.1:$relay" --target 127.0.0.1:8080 \
            --cert "$dir/cert.pem" --key "$dir/key.pem" >"$dir/relay.log" 2>&1 & echo $! >"$dir/relay.pid"
    fi
    if ! pid_alive "$dir/tunnel.pid"; then
        nohup ssh -F "$ssh_cfg" -o ControlMaster=no -o ControlPath=none -o ExitOnForwardFailure=yes -N \
            -R "127.0.0.1:$port:127.0.0.1:$relay" "$host" >"$dir/tunnel.log" 2>&1 & echo $! >"$dir/tunnel.pid"
    fi
    sleep 2
    pid_alive "$dir/tunnel.pid" || { cat "$dir/tunnel.log" >&2; echo "tunnel failed (port $port busy on the Mac? set E2E_PORT)" >&2; exit 1; }
    reset
    echo "up: https://localhost:$port/$dav/ (sha256 $(fingerprint))"
}

down() {
    for n in tunnel relay; do
        pid_alive "$dir/$n.pid" && kill "$(cat "$dir/$n.pid")" || true
    done
    "$repo/scripts/nextcloud-local.sh" stop || true
    rm -rf "$dir"
}

json() {
    printf '{"url":"https://localhost:%s/%s/","user":"%s","password":"%s","sha256":"%s"}' \
        "$port" "$dav" "$user" "$pass" "$(fingerprint)"
}

case "${1:-}" in
    up) up ;;
    json) json; echo ;;
    reset) reset ;;
    down) down ;;
    run)
        shift
        up
        trap down EXIT
        [ $# -gt 0 ] || set -- iphone -only-testing:iosAppUITests/EndToEndUITests/testFreshInstallToBackupChain
        TEST_RUNNER_E2E_SYNC_FIXTURE_JSON="$(json)" "$repo/scripts/mac-remote.sh" xcode-test "$@"
        ;;
    *) sed -n 2,16p "$0"; exit 2 ;;
esac
