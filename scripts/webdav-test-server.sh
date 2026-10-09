#!/usr/bin/env bash
# Local rclone WebDAV test server (HTTPS, self-signed) for WebDavIntegrationTest.
# Usage: scripts/webdav-test-server.sh [--new-cert] start | stop
# Needs .local/rclone/rclone (never downloaded automatically). Data and certs live in .local/webdav-test/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RCLONE="$ROOT/.local/rclone/rclone"
DIR="$ROOT/.local/webdav-test"
PIDFILE="$DIR/rclone.pid"
PORT="${PORT:-8443}"

NEW_CERT=0
CMD=""
for a in "$@"; do
  case "$a" in
    --new-cert) NEW_CERT=1 ;;
    start|stop) CMD="$a" ;;
    *) echo "usage: $0 [--new-cert] start|stop" >&2; exit 64 ;;
  esac
done
[ -n "$CMD" ] || { echo "usage: $0 [--new-cert] start|stop" >&2; exit 64; }

stop_server() {
  if [ -f "$PIDFILE" ]; then
    pid="$(cat "$PIDFILE")"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      echo "stopped rclone (pid $pid)"
    fi
    rm -f "$PIDFILE"
  fi
}

if [ "$CMD" = stop ]; then
  stop_server
  exit 0
fi

if [ ! -x "$RCLONE" ]; then
  echo "rclone not found at $RCLONE" >&2
  echo "Download it from https://downloads.rclone.org/rclone-current-linux-amd64.zip and place the binary at .local/rclone/rclone" >&2
  exit 2
fi

mkdir -p "$DIR/data"
if [ "$NEW_CERT" = 1 ] || [ ! -f "$DIR/cert.pem" ] || [ ! -f "$DIR/key.pem" ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2" \
    -keyout "$DIR/key.pem" -out "$DIR/cert.pem" 2>/dev/null
  echo "generated a new certificate"
fi

stop_server
setsid "$RCLONE" serve webdav "$DIR/data" --addr "0.0.0.0:$PORT" \
  --cert "$DIR/cert.pem" --key "$DIR/key.pem" \
  --user andbible --pass andbible-test-pw >"$DIR/rclone.log" 2>&1 < /dev/null &
echo $! > "$PIDFILE"
echo "rclone serving https://localhost:$PORT/ (pid $(cat "$PIDFILE"), log $DIR/rclone.log)"
openssl x509 -noout -fingerprint -sha256 -in "$DIR/cert.pem"
