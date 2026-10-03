#!/usr/bin/env bash
# Run a command against a CONTAINER-LOCAL adb-server instead of the shared
# host adb-server.
#
# Background: this dev container reaches the HOST adb-server over a jailbee
# TCP port forward — Incus proxies the container's 127.0.0.1:5037 to the
# host's 127.0.0.1:5037 (see `host_ports` in the repo's jailbee config).
# Because that is adb's own default endpoint, a bare `adb` in this container
# sees the real phone paired on the host with no env set at all. That is the
# right mode for debugging on the phone and installing debug builds.
#
# The in-container Android emulator, however, binds its console/adbd ports on
# the CONTAINER's localhost. The host adb-server cannot reach those, so it
# never lists the emulator. A separate adb-server running inside the container
# does see it. Use this wrapper for all in-container emulator work
# (screenshots, instrumented tests).
#
# PORT 5038, NOT 5037: the Incus proxy device already occupies
# 127.0.0.1:5037 inside the container, so a container-local adb-server cannot
# bind the default port — and an adb client left on the default would silently
# talk to the HOST server and see no emulator. So this wrapper moves the
# container-local server to 5038 via ANDROID_ADB_SERVER_PORT, which adb
# auto-spawns on first use and which the emulator honours too (it reads the
# same variable, and the server's localhost adbd scan finds it either way).
#
#   5037 = host adb-server  (real phone, via the jailbee port forward)
#   5038 = container-local adb-server (in-container emulator) — this wrapper
#
# The two coexist: the host forward is untouched; this wrapper only redirects
# the wrapped command (and any emulator it launches) to the local server.
#
# ADB_SERVER_SOCKET is unset defensively. It is no longer set container-wide
# (the port forward replaced the old ~/.adb.sock bind-mount), but if anything
# reintroduces it, a `tcp:` value would make adb treat the server as REMOTE
# and refuse to spawn a local daemon ("cannot start server on remote host").
#
# Usage:
#   scripts/with-container-adb.sh scripts/andbible-emu.sh boot
#   scripts/with-container-adb.sh ./gradlew connectedCheck
#   scripts/with-container-adb.sh adb devices
set -euo pipefail

unset ADB_SERVER_SOCKET
export ANDROID_ADB_SERVER_PORT="${ANDROID_ADB_SERVER_PORT:-5038}"

if [[ $# -eq 0 ]]; then
    echo "usage: $0 <command> [args...]" >&2
    echo "  runs <command> against the container-local adb-server on port ${ANDROID_ADB_SERVER_PORT}" >&2
    exit 2
fi

exec "$@"
