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

# Only kill the PID from the pidfile if it is still our rclone (the PID may have been reused).
stop_server() {
  [ -f "$PIDFILE" ] || return 0
  pid="$(cat "$PIDFILE" 2>/dev/null || true)"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null \
      && tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -qF "$RCLONE"; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 50); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
    if kill -0 "$pid" 2>/dev/null; then kill -9 "$pid" 2>/dev/null || true; fi
    echo "stopped rclone (pid $pid)"
  elif [ -n "$pid" ]; then
    echo "stale pidfile (pid $pid is not our rclone); not killing it"
  fi
  rm -f "$PIDFILE"
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
  if ! openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2" \
    -keyout "$DIR/key.pem" -out "$DIR/cert.pem"; then
    echo "certificate generation failed (openssl >= 1.1.1 is needed for -addext)" >&2
    rm -f "$DIR/key.pem" "$DIR/cert.pem"
    exit 1
  fi
  echo "generated a new certificate"
fi

stop_server
# 0.0.0.0 so the emulator can reach the host as 10.0.2.2; the password is the documented test password.
setsid "$RCLONE" serve webdav "$DIR/data" --addr "0.0.0.0:$PORT" \
  --cert "$DIR/cert.pem" --key "$DIR/key.pem" \
  --user andbible --pass andbible-test-pw >"$DIR/rclone.log" 2>&1 < /dev/null &
pid=$!
echo "$pid" > "$PIDFILE"

ready=0
for _ in $(seq 50); do
  kill -0 "$pid" 2>/dev/null || break
  if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then ready=1; break; fi
  sleep 0.1
done
if [ "$ready" != 1 ]; then
  echo "rclone did not become ready on port $PORT; log tail:" >&2
  tail -n 20 "$DIR/rclone.log" >&2 || true
  kill "$pid" 2>/dev/null || true
  rm -f "$PIDFILE"
  exit 1
fi
echo "rclone serving https://localhost:$PORT/ (pid $pid, log $DIR/rclone.log)"
openssl x509 -noout -fingerprint -sha256 -in "$DIR/cert.pem"
