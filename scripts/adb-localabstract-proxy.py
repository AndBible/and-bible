#!/usr/bin/env python3
"""Expose a device localabstract socket on a CONTAINER-local TCP port.

    adb-localabstract-proxy.py <local-port> localabstract:<name> [device-serial]

For a HOST-connected device, `adb forward` opens its listener on the machine running
the adb-server — the host — which the container cannot reach. But the adb protocol
endpoint itself IS reachable, and it can open a raw stream to any device-side
localabstract socket, so we do the forwarding ourselves and bind the listener here.

Which adb-server to use comes from ANDROID_ADB_SERVER_PORT (adb's own variable, so
this agrees with the `adb` CLI and with scripts/with-container-adb.sh):
5037 = the shared HOST server (the user's phone), 5038 = a CONTAINER-local server (an
in-container emulator). For the emulator case a plain `adb forward` would in fact work
(that server is container-local), but one code path for both is simpler and this one
needs no forward table entry to clean up.
"""
import os, socket, sys, threading

ADB = ("127.0.0.1", int(os.environ.get("ANDROID_ADB_SERVER_PORT", "5037")))

def adb_send(sock, msg):
    sock.sendall(("%04x%s" % (len(msg), msg)).encode())
    st = b""
    while len(st) < 4:
        c = sock.recv(4 - len(st))
        if not c:
            raise IOError("adb closed while awaiting status")
        st += c
    if st != b"OKAY":
        n = int(sock.recv(4), 16)
        raise IOError("adb FAIL: %s" % sock.recv(n).decode())

def open_device_stream(serial, remote):
    try:
        s = socket.create_connection(ADB, timeout=10)
    except OSError as e:
        raise IOError("no adb-server at %s:%d (%s) — for an in-container emulator set "
                      "ANDROID_ADB_SERVER_PORT=5038" % (ADB[0], ADB[1], e))
    s.settimeout(None)
    adb_send(s, "host:transport:%s" % serial if serial else "host:transport-any")
    adb_send(s, remote)
    return s

def pump(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d:
                break
            b.sendall(d)
    except OSError:
        pass
    finally:
        for x in (a, b):
            try:
                x.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

def handle(client, serial, remote):
    try:
        dev = open_device_stream(serial, remote)
    except Exception as e:
        sys.stderr.write("stream open failed: %s\n" % e)
        client.close()
        return
    threading.Thread(target=pump, args=(client, dev), daemon=True).start()
    pump(dev, client)

def main():
    port = int(sys.argv[1])
    remote = sys.argv[2]
    serial = sys.argv[3] if len(sys.argv) > 3 else None
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(16)
    print("listening on 127.0.0.1:%d -> %s (via adb-server %s:%d)"
          % (port, remote, ADB[0], ADB[1]), flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c, serial, remote), daemon=True).start()

main()
