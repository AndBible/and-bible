#!/usr/bin/env bash
# Chrome DevTools Protocol against a WebView on a real phone OR an in-container
# emulator, from inside the jailbee container.
#
#   scripts/webview-cdp.sh <package> list
#   scripts/webview-cdp.sh <package> scripts [--filter S]
#   scripts/webview-cdp.sh <package> eval '<js expr>'
#   scripts/webview-cdp.sh <package> tail [--seconds N] [--exceptions]
#   scripts/webview-cdp.sh <package> break <urlSubstr:line> [...] [--exceptions]
#                                          [--eval '<js>'] [--seconds N] [--hold]
#   scripts/webview-cdp.sh <package> screenshot <out.png>
#
# e.g.  scripts/webview-cdp.sh net.bible.android.activity.debug \
#           eval 'JSON.stringify(bibleViewDebug.config)'
#       scripts/webview-cdp.sh net.bible.android.activity.debug tail --exceptions --seconds 20
#
# WHICH DEVICE: this follows adb's own ANDROID_ADB_SERVER_PORT, so it agrees with the
# `adb` CLI and with scripts/with-container-adb.sh —
#   (unset) / 5037  the shared HOST adb-server = the user's phone
#   5038            a CONTAINER-local adb-server = an in-container emulator
# so an emulator session is just:
#   ANDROID_ADB_SERVER_PORT=5038 scripts/webview-cdp.sh <pkg> eval '…'
#   (or run this under with-container-adb.sh, which exports it for you)
# With several devices on one server, pick one with --device <serial>.
#
# TARGET SOCKET: by default the app's own @webview_devtools_remote_<pid>. Override with
# --socket <name> to attach to any other devtools socket on the device — e.g.
# chrome_devtools_remote (Chrome), or a @stetho_<pkg>_devtools_remote. `--socket` skips
# the pid lookup, so it also works when the package name is not the socket's owner
# (pass any package, or `-`, as the first argument then).
#
# Why not a plain `adb forward`: for a HOST-connected device its listener opens on the
# host, which the container cannot reach. adb-localabstract-proxy.py speaks the adb
# wire protocol itself and binds the listener INSIDE the container instead — one code
# path that works for phone and emulator alike. All hops are loopback, so no
# `jailbee net loose` is needed.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

pkg="${1:?usage: webview-cdp.sh <package> list|scripts|eval|tail|break|screenshot [args]}"
shift

serial=""
sock_override=""
args=()
while [ $# -gt 0 ]; do
    case "$1" in
        --device) serial="${2:?--device needs a serial}"; shift 2 ;;
        --socket) sock_override="${2:?--socket needs a socket name}"; shift 2 ;;
        *) args+=("$1"); shift ;;
    esac
done

adb_server_port="${ANDROID_ADB_SERVER_PORT:-5037}"
adb() { command adb ${serial:+-s "$serial"} "$@"; }

# Resolve the device up front so "no devices" reads as a fact, not as a CDP failure.
devices="$(command adb devices | awk 'NR>1 && $2=="device" {print $1}')"
[ -n "$devices" ] || {
    echo "no device on adb-server 127.0.0.1:$adb_server_port." >&2
    echo "  phone?    leave ANDROID_ADB_SERVER_PORT unset (host server on 5037)" >&2
    echo "  emulator? ANDROID_ADB_SERVER_PORT=5038, and boot it first" >&2
    exit 1
}
if [ -z "$serial" ] && [ "$(echo "$devices" | wc -l)" -gt 1 ]; then
    echo "several devices on 127.0.0.1:$adb_server_port — pick one with --device <serial>:" >&2
    echo "$devices" | sed 's/^/  /' >&2
    exit 1
fi

if [ -n "$sock_override" ]; then
    sock="${sock_override#@}"
else
    pid="$(adb shell pidof "$pkg" | tr -d '\r' | awk '{print $1}')"
    [ -n "$pid" ] || {
        echo "no running process for $pkg — launch it first, e.g." >&2
        echo "  adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1" >&2
        exit 1
    }
    sock="webview_devtools_remote_$pid"
fi
# Read once into a variable: piping `adb shell cat` straight into `grep -q` is a
# pipefail trap — grep exits on the first match, adb takes SIGPIPE, and the pipeline
# reports failure even though the socket WAS found.
unix_sockets="$(adb shell cat /proc/net/unix | tr -d '\r')"
if ! grep -q "@$sock\$" <<<"$unix_sockets"; then
    echo "device has no @$sock socket." >&2
    echo "  Is the build debuggable and setWebContentsDebuggingEnabled(true) on," >&2
    echo "  and is a WebView actually alive in $pkg right now?" >&2
    echo "  devtools sockets present on the device:" >&2
    grep -o '@[a-zA-Z0-9_.:-]*devtools_remote[a-zA-Z0-9_]*' <<<"$unix_sockets" |
        sort -u | sed 's/^/    /' >&2
    echo "  (attach to one of those with --socket <name>)" >&2
    exit 1
fi

# A free local port, so two debug sessions (phone + emulator) can run side by side.
port="${CDP_PORT:-}"
if [ -z "$port" ]; then
    port="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
fi

log="$(mktemp -t webview-cdp-proxy-XXXXXX.log)"
ANDROID_ADB_SERVER_PORT="$adb_server_port" \
    python3 "$here/adb-localabstract-proxy.py" "$port" "localabstract:$sock" ${serial:+"$serial"} >"$log" 2>&1 &
proxy=$!
trap 'kill $proxy 2>/dev/null || true' EXIT

for _ in $(seq 50); do
    curl -s --max-time 2 "http://127.0.0.1:$port/json/version" >/dev/null 2>&1 && break
    kill -0 $proxy 2>/dev/null || { echo "proxy died:" >&2; cat "$log" >&2; exit 1; }
    sleep 0.2
done
curl -s --max-time 2 "http://127.0.0.1:$port/json/version" >/dev/null 2>&1 || {
    echo "DevTools endpoint never answered on 127.0.0.1:$port; proxy log:" >&2; cat "$log" >&2; exit 1; }

node "$here/cdp.mjs" "$port" "${args[@]}"
